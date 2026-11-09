package com.figureerp.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.manufacturer.entity.Manufacturer;
import com.figureerp.module.manufacturer.mapper.ManufacturerMapper;
import com.figureerp.module.product.converter.FigureProductConverter;
import com.figureerp.module.product.dto.FigureProductQuery;
import com.figureerp.module.product.dto.FigureProductSaveRequest;
import com.figureerp.module.product.entity.FigureProduct;
import com.figureerp.module.product.mapper.FigureProductMapper;
import com.figureerp.module.product.vo.FigureProductVO;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 手办商品档案的查询与维护。
 * <p>
 * 关键业务规则：
 * <ul>
 *   <li>带盒重量必须大于 0；</li>
 *   <li>商品图片必须 3~4 张（容量要求）；</li>
 *   <li>货号（code）填写后不可与其他商品重复；</li>
 *   <li>查询所有登录用户可用；增删改需要管理员或主管。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FigureProductService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final FigureProductMapper mapper;
    private final ManufacturerMapper manufacturerMapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<FigureProductVO> page(FigureProductQuery q) {
        LambdaQueryWrapper<FigureProduct> w = Wrappers.lambdaQuery();

        if (StringUtils.hasText(q.getName())) {
            w.like(FigureProduct::getName, q.getName().trim());
        }
        if (StringUtils.hasText(q.getCode())) {
            w.like(FigureProduct::getCode, q.getCode().trim());
        }
        if (StringUtils.hasText(q.getBrand())) {
            w.like(FigureProduct::getBrand, q.getBrand().trim());
        }
        if (q.getManufacturerId() != null) {
            w.eq(FigureProduct::getManufacturerId, q.getManufacturerId());
        }
        if (StringUtils.hasText(q.getSeries())) {
            w.like(FigureProduct::getSeries, q.getSeries().trim());
        }
        if (StringUtils.hasText(q.getCharacterName())) {
            w.like(FigureProduct::getCharacterName, q.getCharacterName().trim());
        }
        if (q.getStatus() != null) {
            w.eq(FigureProduct::getStatus, q.getStatus());
        }

        // 默认按创建时间倒序，新录入的商品排在前面
        w.orderByDesc(FigureProduct::getCreateTime);

        Page<FigureProduct> page = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        Map<Long, Manufacturer> manufacturers = loadManufacturers(page.getRecords());
        return PageResult.of(page, p -> toVO(p, manufacturers));
    }

    public FigureProductVO detail(Long id) {
        FigureProduct p = getExisting(id);
        return toVO(p, loadManufacturers(List.of(p)));
    }

    /* ================= 维护 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(FigureProductSaveRequest req) {
        currentUserService.requireManager("维护手办商品");
        validate(req);
        checkCodeUnique(req.getCode(), null);
        Manufacturer manufacturer = resolveManufacturer(req.getManufacturerId());

        FigureProduct p = new FigureProduct();
        apply(p, req, manufacturer);
        mapper.insert(p);
        log.info("新增手办商品: id={} name={} manufacturerId={}", p.getId(), req.getName(), p.getManufacturerId());
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FigureProductSaveRequest req) {
        currentUserService.requireManager("维护手办商品");
        validate(req);
        getExisting(id);
        checkCodeUnique(req.getCode(), id);
        Manufacturer manufacturer = resolveManufacturer(req.getManufacturerId());

        FigureProduct p = new FigureProduct();
        p.setId(id);
        apply(p, req, manufacturer);
        mapper.updateById(p);
        log.info("编辑手办商品: id={} name={} manufacturerId={}", id, req.getName(), p.getManufacturerId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        currentUserService.requireManager("删除手办商品");
        getExisting(id);
        mapper.deleteById(id);
        log.info("删除手办商品: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        currentUserService.requireManager("修改手办商品状态");
        if (!Integer.valueOf(STATUS_ENABLED).equals(status) && !Integer.valueOf(STATUS_DISABLED).equals(status)) {
            throw new BusinessException("状态值只能是 0 或 1");
        }
        getExisting(id);

        // 只更新状态列：manufacturer_id / brand 是 ALWAYS 策略，用空实体 updateById 会把它们写成 NULL
        mapper.update(null, Wrappers.<FigureProduct>lambdaUpdate()
                .eq(FigureProduct::getId, id)
                .set(FigureProduct::getStatus, status)
                .set(FigureProduct::getUpdateTime, LocalDateTime.now().withNano(0)));
    }

    /* ================= 内部方法 ================= */

    /** 实体 → 出参：已关联厂商时，品牌名称与 Logo 取自厂商目录最新数据 */
    private FigureProductVO toVO(FigureProduct p, Map<Long, Manufacturer> manufacturers) {
        FigureProductVO vo = FigureProductConverter.toVO(p);
        // 注意：未关联厂商时 manufacturerId 为 null，不能用 Map.of() 去 get(null)（会抛 NPE）
        Manufacturer m = p.getManufacturerId() == null ? null : manufacturers.get(p.getManufacturerId());
        if (m != null) {
            vo.setBrand(m.getName());
            vo.setManufacturerLogo(m.getLogo());
        }
        return vo;
    }

    /** 批量查当前页涉及的厂商，避免 N+1 */
    private Map<Long, Manufacturer> loadManufacturers(List<FigureProduct> products) {
        List<Long> ids = products.stream()
                .map(FigureProduct::getManufacturerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Manufacturer> map = new HashMap<>();
        for (Manufacturer m : manufacturerMapper.selectByIds(ids)) {
            map.put(m.getId(), m);
        }
        return map;
    }

    /** 校验并取出厂商；传 null 表示清空关联。停用的厂商仍允许保存（历史数据可编辑） */
    private Manufacturer resolveManufacturer(Long manufacturerId) {
        if (manufacturerId == null) {
            return null;
        }
        Manufacturer m = manufacturerMapper.selectById(manufacturerId);
        if (m == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "所选厂商不存在或已被删除");
        }
        return m;
    }

    private void validate(FigureProductSaveRequest req) {
        if (req.getWeightWithBox() == null || req.getWeightWithBox() <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "带盒重量必须大于 0");
        }
        List<String> imgs = req.getImages();
        if (imgs == null || imgs.size() < 3 || imgs.size() > 4) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "商品图片需 3~4 张");
        }
    }

    private void checkCodeUnique(String code, Long id) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        String trimmed = code.trim();
        LambdaQueryWrapper<FigureProduct> w = Wrappers.<FigureProduct>lambdaQuery()
                .eq(FigureProduct::getCode, trimmed);
        if (id != null) {
            w.ne(FigureProduct::getId, id);
        }
        w.last("LIMIT 1");
        FigureProduct exist = mapper.selectOne(w);
        if (exist != null) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "货号已存在：" + trimmed);
        }
    }

    private void apply(FigureProduct p, FigureProductSaveRequest req, Manufacturer manufacturer) {
        p.setName(req.getName().trim());
        p.setCode(StringUtils.hasText(req.getCode()) ? req.getCode().trim() : null);
        // 选了厂商就以厂商目录为准（名称随厂商改名自动同步）；没选则保留自由文本，兼容老数据
        p.setManufacturerId(manufacturer == null ? null : manufacturer.getId());
        p.setBrand(manufacturer != null ? manufacturer.getName() : blankToNull(req.getBrand()));
        p.setSeries(blankToNull(req.getSeries()));
        p.setCharacterName(blankToNull(req.getCharacterName()));
        p.setMaterial(blankToNull(req.getMaterial()));
        p.setLengthCm(req.getLengthCm());
        p.setWidthCm(req.getWidthCm());
        p.setHeightCm(req.getHeightCm());
        p.setWeightWithBox(req.getWeightWithBox());
        p.setImages(FigureProductConverter.join(req.getImages()));
        p.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        p.setRemark(blankToNull(req.getRemark()));
    }

    private FigureProduct getExisting(Long id) {
        FigureProduct p = mapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "商品不存在或已被删除");
        }
        return p;
    }

    private static String blankToNull(String value) {
        return value == null ? null : value.trim();
    }
}
