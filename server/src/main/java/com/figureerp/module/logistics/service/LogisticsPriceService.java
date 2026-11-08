package com.figureerp.module.logistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.constant.ChinaRegions;
import com.figureerp.common.constant.LogisticsSupplier;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.logistics.converter.LogisticsPriceConverter;
import com.figureerp.module.logistics.dto.BatchPriceRequest;
import com.figureerp.module.logistics.dto.LogisticsPriceQuery;
import com.figureerp.module.logistics.dto.LogisticsPriceSaveRequest;
import com.figureerp.module.logistics.dto.QuoteQuery;
import com.figureerp.module.logistics.entity.LogisticsPrice;
import com.figureerp.module.logistics.mapper.LogisticsPriceMapper;
import com.figureerp.module.logistics.vo.LogisticsPriceVO;
import com.figureerp.module.logistics.vo.QuoteItemVO;
import com.figureerp.module.logistics.vo.QuoteResultVO;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物流价格维护与运费报价。
 * <p>
 * 计价模型（国内快递通用）：
 * <pre>
 *   重量 ≤ 首重                    → 总价 = 首重价
 *   重量 &gt; 首重                    → 总价 = 首重价 + ceil((重量 - 首重) / 续重单位) × 续重单价
 * </pre>
 * 目的地按「省」配置，个别城市可以单独设价；查询时城市级价格优先于省级统一价。
 * <p>
 * 权限：查询所有登录用户可用；维护（增删改）需要管理员或主管。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsPriceService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final LogisticsPriceMapper priceMapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    /** 分页查询价格 */
    public PageResult<LogisticsPriceVO> page(LogisticsPriceQuery query) {
        LambdaQueryWrapper<LogisticsPrice> wrapper = Wrappers.lambdaQuery();

        if (StringUtils.hasText(query.getSupplierCode())) {
            wrapper.eq(LogisticsPrice::getSupplierCode, query.getSupplierCode());
        }
        if (StringUtils.hasText(query.getOriginProvince())) {
            wrapper.eq(LogisticsPrice::getOriginProvince, query.getOriginProvince());
        }
        if (StringUtils.hasText(query.getOriginCity())) {
            wrapper.eq(LogisticsPrice::getOriginCity, query.getOriginCity());
        }
        if (StringUtils.hasText(query.getDestProvince())) {
            wrapper.eq(LogisticsPrice::getDestProvince, query.getDestProvince());
        }
        if (StringUtils.hasText(query.getDestCity())) {
            wrapper.eq(LogisticsPrice::getDestCity, query.getDestCity());
        }
        if (query.getStatus() != null) {
            wrapper.eq(LogisticsPrice::getStatus, query.getStatus());
        }
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        if (keyword != null) {
            wrapper.and(w -> w.like(LogisticsPrice::getDestProvince, keyword)
                    .or().like(LogisticsPrice::getDestCity, keyword));
        }

        wrapper.orderByAsc(LogisticsPrice::getSupplierCode)
                .orderByAsc(LogisticsPrice::getDestProvince)
                .orderByAsc(LogisticsPrice::getDestCity);

        Page<LogisticsPrice> page = priceMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        return PageResult.of(page, LogisticsPriceConverter::toVO);
    }

    /** 价格详情 */
    public LogisticsPriceVO detail(Long id) {
        return LogisticsPriceConverter.toVO(getExisting(id));
    }

    /**
     * 运费报价：返回所有启用供应商在同一路线上的价格，按总价升序。
     * <p>
     * 城市级价格优先于省级统一价：给深圳单独设了价，查深圳就用深圳的，
     * 查广州（未单独设价）则回落到广东省的统一价。
     */
    public QuoteResultVO quote(QuoteQuery query) {
        String originProvince = defaultIfBlank(query.getOriginProvince(), "上海市");
        String originCity = defaultIfBlank(query.getOriginCity(), originProvince);
        String originDistrict = query.getOriginDistrict();
        String destProvince = query.getDestProvince();
        String destCity = StringUtils.hasText(query.getDestCity()) ? query.getDestCity().trim() : null;
        BigDecimal weight = query.getWeight() == null ? BigDecimal.ONE : query.getWeight();

        List<LogisticsPrice> candidates = priceMapper.selectList(Wrappers.<LogisticsPrice>lambdaQuery()
                .eq(LogisticsPrice::getStatus, STATUS_ENABLED)
                .eq(LogisticsPrice::getOriginProvince, originProvince)
                .eq(LogisticsPrice::getOriginCity, originCity)
                .eq(LogisticsPrice::getDestProvince, destProvince));

        // 同一供应商只留一条：城市级 > 省级
        Map<String, LogisticsPrice> selected = new LinkedHashMap<>();
        for (LogisticsPrice candidate : candidates) {
            boolean exactCity = destCity != null && destCity.equals(candidate.getDestCity());
            boolean provinceWide = !StringUtils.hasText(candidate.getDestCity());
            if (!exactCity && !provinceWide) {
                continue;
            }
            LogisticsPrice current = selected.get(candidate.getSupplierCode());
            if (current == null) {
                selected.put(candidate.getSupplierCode(), candidate);
            } else if (exactCity && !StringUtils.hasText(current.getDestCity())) {
                selected.put(candidate.getSupplierCode(), candidate);
            }
        }

        List<QuoteItemVO> items = new ArrayList<>(selected.size());
        for (LogisticsPrice price : selected.values()) {
            items.add(calculate(price, weight));
        }

        // 总价低的排前面，同价则时效短的排前面
        items.sort(Comparator.comparing(QuoteItemVO::getTotalPrice)
                .thenComparing(i -> i.getEstimatedDays() == null ? Integer.MAX_VALUE : i.getEstimatedDays()));

        QuoteResultVO result = new QuoteResultVO();
        result.setOriginLabel(LogisticsPriceConverter.originLabel(originProvince, originCity, originDistrict));
        result.setDestLabel(LogisticsPriceConverter.destLabel(destProvince, destCity));
        result.setZoneLabel(ChinaRegions.zoneLabelOf(destProvince));
        result.setWeight(weight);
        result.setItems(items);
        result.setCount(items.size());

        if (items.isEmpty()) {
            result.setAdvice("该路线暂未配置价格，可到「物流价格维护」里补充，或用「批量设置全国价」一键生成");
            return result;
        }

        QuoteItemVO cheapest = items.get(0);
        // 并列最低价的都标出来（不同供应商经常同价，只标一家会误导）
        BigDecimal lowest = cheapest.getTotalPrice();
        items.stream()
                .filter(i -> i.getTotalPrice().compareTo(lowest) == 0)
                .forEach(i -> i.setCheapest(true));
        result.setLowestPrice(lowest);
        result.setLowestSupplier(cheapest.getSupplierName());

        QuoteItemVO fastest = items.stream()
                .filter(i -> i.getEstimatedDays() != null)
                .min(Comparator.comparing(QuoteItemVO::getEstimatedDays))
                .orElse(null);
        if (fastest != null) {
            // 时效相同的都标上「最快」
            int minDays = fastest.getEstimatedDays();
            items.stream()
                    .filter(i -> i.getEstimatedDays() != null && i.getEstimatedDays() == minDays)
                    .forEach(i -> i.setFastest(true));
            result.setFastestDays(minDays);
        }

        if (fastest == null) {
            result.setAdvice("最低价：" + cheapest.getSupplierName() + " " + money(cheapest.getTotalPrice()) + " 元");
        } else if (fastest.getSupplierCode().equals(cheapest.getSupplierCode())) {
            result.setAdvice(cheapest.getSupplierName() + " 价格最低且时效最快，优先选它");
        } else {
            result.setAdvice("最便宜：" + cheapest.getSupplierName() + " " + money(cheapest.getTotalPrice())
                    + " 元（" + days(cheapest.getEstimatedDays()) + "）；最快："
                    + fastest.getSupplierName() + " " + days(fastest.getEstimatedDays())
                    + "（" + money(fastest.getTotalPrice()) + " 元）");
        }
        return result;
    }

    /* ================= 维护 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(LogisticsPriceSaveRequest request) {
        currentUserService.requireManager("维护物流价格");
        validate(request);

        LogisticsPrice same = findSameKey(request.getSupplierCode(), request.getOriginProvince(),
                request.getOriginCity(), request.getDestProvince(), request.getDestCity());
        if (same != null) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS,
                    "该路线已存在价格：" + LogisticsSupplier.labelOf(request.getSupplierCode())
                            + " " + LogisticsPriceConverter.destLabel(request.getDestProvince(), request.getDestCity()));
        }

        LogisticsPrice price = new LogisticsPrice();
        applyFields(price, request);
        priceMapper.insert(price);

        log.info("新增物流价格: {} {} → {}，首重 {} 元", request.getSupplierCode(),
                LogisticsPriceConverter.originLabel(request.getOriginProvince(), request.getOriginCity(), null),
                LogisticsPriceConverter.destLabel(request.getDestProvince(), request.getDestCity()),
                request.getFirstPrice());
        return price.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, LogisticsPriceSaveRequest request) {
        currentUserService.requireManager("维护物流价格");
        validate(request);

        LogisticsPrice existing = getExisting(id);
        LogisticsPrice same = findSameKey(request.getSupplierCode(), request.getOriginProvince(),
                request.getOriginCity(), request.getDestProvince(), request.getDestCity());
        if (same != null && !same.getId().equals(id)) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS,
                    "该路线已存在另一条价格，请直接编辑那一条");
        }

        LogisticsPrice update = new LogisticsPrice();
        update.setId(id);
        applyFields(update, request);
        priceMapper.updateById(update);

        log.info("编辑物流价格: id={} {} {} → {}", id, request.getSupplierCode(),
                existing.getDestProvince(), LogisticsPriceConverter.destLabel(request.getDestProvince(), request.getDestCity()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        currentUserService.requireManager("删除物流价格");
        LogisticsPrice existing = getExisting(id);
        priceMapper.deleteById(id);
        log.info("删除物流价格: id={} {} {} → {}", id, existing.getSupplierCode(),
                existing.getOriginCity(), LogisticsPriceConverter.destLabel(existing.getDestProvince(), existing.getDestCity()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        currentUserService.requireManager("修改物流价格状态");
        if (!Integer.valueOf(STATUS_ENABLED).equals(status) && !Integer.valueOf(STATUS_DISABLED).equals(status)) {
            throw new BusinessException("状态值只能是 0 或 1");
        }
        getExisting(id);

        LogisticsPrice update = new LogisticsPrice();
        update.setId(id);
        update.setStatus(status);
        priceMapper.updateById(update);
    }

    /**
     * 批量保存某供应商「从固定出发地发往全国」的价格。
     * <p>
     * 按「供应商 + 出发地 + 目的地省 + 目的地市」匹配：已存在则更新，不存在则新增。
     *
     * @return 新增 + 更新的条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchSave(BatchPriceRequest request) {
        currentUserService.requireManager("批量维护物流价格");
        if (!LogisticsSupplier.isValid(request.getSupplierCode())) {
            throw new BusinessException("不支持的物流供应商：" + request.getSupplierCode());
        }

        int affected = 0;
        for (BatchPriceRequest.Item item : request.getItems()) {
            if (!StringUtils.hasText(item.getDestProvince())) {
                continue;
            }
            String destCity = StringUtils.hasText(item.getDestCity()) ? item.getDestCity().trim() : null;

            LogisticsPrice existing = findSameKey(request.getSupplierCode(), request.getOriginProvince(),
                    request.getOriginCity(), item.getDestProvince(), destCity);

            LogisticsPrice target = existing == null ? new LogisticsPrice() : existing;
            target.setSupplierCode(request.getSupplierCode());
            target.setOriginProvince(request.getOriginProvince());
            target.setOriginCity(request.getOriginCity());
            target.setOriginDistrict(nullToEmpty(request.getOriginDistrict(), ""));
            target.setDestProvince(item.getDestProvince());
            target.setDestCity(destCity);
            target.setFirstWeight(orDefault(item.getFirstWeight(), BigDecimal.ONE));
            target.setFirstPrice(item.getFirstPrice());
            target.setAdditionalWeight(orDefault(item.getAdditionalWeight(), BigDecimal.ONE));
            target.setAdditionalPrice(item.getAdditionalPrice());
            target.setEstimatedDays(item.getEstimatedDays());
            target.setStatus(item.getStatus() == null ? STATUS_ENABLED : item.getStatus());
            target.setRemark(item.getRemark() == null ? "" : item.getRemark());

            if (existing == null) {
                priceMapper.insert(target);
            } else {
                priceMapper.updateById(target);
            }
            affected++;
        }

        log.info("批量维护物流价格: {} {} → 共 {} 条", request.getSupplierCode(),
                LogisticsPriceConverter.originLabel(request.getOriginProvince(), request.getOriginCity(), null), affected);
        return affected;
    }

    /* ================= 字典 ================= */

    /** 下拉框数据：供应商、省份（含城市与所属区域）、已配置的出发地 */
    public Map<String, Object> dicts() {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("suppliers", java.util.Arrays.stream(LogisticsSupplier.values())
                .map(s -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("code", s.getCode());
                    item.put("label", s.getLabel());
                    item.put("baseFirstPrice", s.getBaseFirstPrice());
                    item.put("baseAdditionalPrice", s.getBaseAdditionalPrice());
                    return item;
                })
                .toList());

        data.put("provinces", ChinaRegions.all().stream()
                .map(p -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", p.name());
                    item.put("zone", p.zone().name());
                    item.put("zoneLabel", p.zone().getLabel());
                    item.put("cities", p.cities());
                    item.put("municipality", p.isMunicipality());
                    return item;
                })
                .toList());

        data.put("zones", java.util.Arrays.stream(com.figureerp.common.constant.FreightZone.values())
                .map(z -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("code", z.name());
                    item.put("label", z.getLabel());
                    item.put("firstOffset", z.getFirstOffset());
                    item.put("additionalOffset", z.getAdditionalOffset());
                    item.put("daysOffset", z.getDaysOffset());
                    return item;
                })
                .toList());

        // 已配置过的出发地，供查询页下拉使用（未来开新仓时自动出现，无需改前端）
        List<Map<String, Object>> origins = priceMapper.selectList(Wrappers.<LogisticsPrice>lambdaQuery()
                        .select(LogisticsPrice::getOriginProvince, LogisticsPrice::getOriginCity, LogisticsPrice::getOriginDistrict)
                        .groupBy(LogisticsPrice::getOriginProvince, LogisticsPrice::getOriginCity, LogisticsPrice::getOriginDistrict)
                        .orderByAsc(LogisticsPrice::getOriginProvince))
                .stream()
                .map(p -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("province", p.getOriginProvince());
                    item.put("city", p.getOriginCity());
                    item.put("district", p.getOriginDistrict());
                    item.put("label", LogisticsPriceConverter.originLabel(
                            p.getOriginProvince(), p.getOriginCity(), p.getOriginDistrict()));
                    return item;
                })
                .toList();
        data.put("origins", origins);

        return data;
    }

    /* ================= 内部方法 ================= */

    /** 按首重 + 续重算出总价 */
    private QuoteItemVO calculate(LogisticsPrice price, BigDecimal weight) {
        BigDecimal firstWeight = orDefault(price.getFirstWeight(), BigDecimal.ONE);
        BigDecimal firstPrice = orDefault(price.getFirstPrice(), BigDecimal.ZERO);
        BigDecimal additionalWeight = orDefault(price.getAdditionalWeight(), BigDecimal.ONE);
        BigDecimal additionalPrice = orDefault(price.getAdditionalPrice(), BigDecimal.ZERO);

        BigDecimal total;
        BigDecimal units = BigDecimal.ZERO;
        String formula;

        if (weight.compareTo(firstWeight) <= 0) {
            total = firstPrice.setScale(2, RoundingMode.HALF_UP);
            formula = "首重 " + LogisticsPriceConverter.weight(firstWeight) + "kg 内 "
                    + LogisticsPriceConverter.money(firstPrice) + " 元";
        } else {
            BigDecimal extra = weight.subtract(firstWeight);
            units = extra.divide(additionalWeight, 0, RoundingMode.CEILING);
            total = firstPrice.add(units.multiply(additionalPrice)).setScale(2, RoundingMode.HALF_UP);
            formula = "首重 " + LogisticsPriceConverter.weight(firstWeight) + "kg "
                    + LogisticsPriceConverter.money(firstPrice) + " 元 + 续重 "
                    + LogisticsPriceConverter.weight(units) + "×" + LogisticsPriceConverter.weight(additionalWeight)
                    + "kg × " + LogisticsPriceConverter.money(additionalPrice) + " 元";
        }

        QuoteItemVO item = new QuoteItemVO();
        item.setSupplierCode(price.getSupplierCode());
        item.setSupplierName(LogisticsSupplier.labelOf(price.getSupplierCode()));
        item.setTotalPrice(total);
        item.setFirstWeight(firstWeight);
        item.setFirstPrice(firstPrice);
        item.setAdditionalWeight(additionalWeight);
        item.setAdditionalPrice(additionalPrice);
        item.setAdditionalUnits(units);
        item.setFormula(formula);
        item.setEstimatedDays(price.getEstimatedDays());
        item.setRemark(price.getRemark());
        return item;
    }

    private void applyFields(LogisticsPrice target, LogisticsPriceSaveRequest request) {
        target.setSupplierCode(request.getSupplierCode());
        target.setOriginProvince(request.getOriginProvince());
        target.setOriginCity(request.getOriginCity());
        target.setOriginDistrict(nullToEmpty(request.getOriginDistrict(), ""));
        target.setDestProvince(request.getDestProvince());
        target.setDestCity(StringUtils.hasText(request.getDestCity()) ? request.getDestCity().trim() : null);
        target.setFirstWeight(orDefault(request.getFirstWeight(), BigDecimal.ONE));
        target.setFirstPrice(request.getFirstPrice());
        target.setAdditionalWeight(orDefault(request.getAdditionalWeight(), BigDecimal.ONE));
        target.setAdditionalPrice(request.getAdditionalPrice());
        target.setEstimatedDays(request.getEstimatedDays());
        target.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
        target.setRemark(request.getRemark() == null ? "" : request.getRemark());
    }

    private void validate(LogisticsPriceSaveRequest request) {
        if (!LogisticsSupplier.isValid(request.getSupplierCode())) {
            throw new BusinessException("不支持的物流供应商：" + request.getSupplierCode());
        }
        if (request.getFirstPrice() == null || request.getAdditionalPrice() == null) {
            throw new BusinessException("请填写完整的首重价与续重价");
        }
    }

    /** 按业务唯一键查已有记录（目的地城市为空时按 IS NULL 或空串匹配） */
    private LogisticsPrice findSameKey(String supplierCode, String originProvince, String originCity,
                                       String destProvince, String destCity) {
        LambdaQueryWrapper<LogisticsPrice> wrapper = Wrappers.<LogisticsPrice>lambdaQuery()
                .eq(LogisticsPrice::getSupplierCode, supplierCode)
                .eq(LogisticsPrice::getOriginProvince, originProvince)
                .eq(LogisticsPrice::getOriginCity, originCity)
                .eq(LogisticsPrice::getDestProvince, destProvince);
        if (StringUtils.hasText(destCity)) {
            wrapper.eq(LogisticsPrice::getDestCity, destCity.trim());
        } else {
            wrapper.and(w -> w.isNull(LogisticsPrice::getDestCity).or().eq(LogisticsPrice::getDestCity, ""));
        }
        wrapper.last("LIMIT 1");
        return priceMapper.selectOne(wrapper);
    }

    private LogisticsPrice getExisting(Long id) {
        LogisticsPrice price = priceMapper.selectById(id);
        if (price == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "价格记录不存在或已被删除");
        }
        return price;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String nullToEmpty(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private static BigDecimal orDefault(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private static String money(BigDecimal value) {
        return LogisticsPriceConverter.money(value);
    }

    private static String days(Integer value) {
        return value == null ? "时效未知" : value + " 天";
    }
}
