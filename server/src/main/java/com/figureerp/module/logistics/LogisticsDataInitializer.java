package com.figureerp.module.logistics;

import com.figureerp.common.constant.ChinaRegions;
import com.figureerp.common.constant.FreightZone;
import com.figureerp.common.constant.LogisticsSupplier;
import com.figureerp.module.logistics.entity.LogisticsPrice;
import com.figureerp.module.logistics.mapper.LogisticsPriceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 首次启动时生成「8 家供应商 × 34 个省级行政区」的全国参考价。
 * <p>
 * 只在价格表为空时执行，已有数据不会被覆盖，所以之后在页面上调过的价格不会因为重启而还原。
 * 生成规则：供应商基准价（上海 → 华东）叠加目的地所属区域的偏移量，
 * 得到的只是一份「能用」的初始数据，真实合同价请在「物流价格维护」里改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.init-logistics.enabled", havingValue = "true", matchIfMissing = true)
public class LogisticsDataInitializer implements ApplicationRunner {

    /** 默认出发地：上海市浦东新区 */
    private static final String ORIGIN_PROVINCE = "上海市";
    private static final String ORIGIN_CITY = "上海市";
    private static final String ORIGIN_DISTRICT = "浦东新区";

    private static final BigDecimal MIN_FIRST_PRICE = new BigDecimal("3.00");

    private final LogisticsPriceMapper priceMapper;

    @Value("${app.init-logistics.origin-province:上海市}")
    private String originProvince;

    @Value("${app.init-logistics.origin-city:上海市}")
    private String originCity;

    @Value("${app.init-logistics.origin-district:浦东新区}")
    private String originDistrict;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = priceMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("物流价格已有 {} 条记录，跳过初始化", count);
                return;
            }

            int inserted = 0;
            for (LogisticsSupplier supplier : LogisticsSupplier.values()) {
                for (ChinaRegions.Province province : ChinaRegions.all()) {
                    priceMapper.insert(build(supplier, province));
                    inserted++;
                }
            }
            log.warn("已生成物流参考价 {} 条（{} 家供应商 × {} 个省级行政区），出发地 {} {} {}",
                    inserted, LogisticsSupplier.values().length, ChinaRegions.all().size(),
                    ORIGIN_PROVINCE, ORIGIN_CITY, ORIGIN_DISTRICT);
        } catch (Exception e) {
            // 表没建时不影响应用启动，只提示怎么处理
            log.error("初始化物流价格失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }

    private LogisticsPrice build(LogisticsSupplier supplier, ChinaRegions.Province province) {
        FreightZone zone = province.zone();

        BigDecimal firstPrice = supplier.getBaseFirstPrice().add(BigDecimal.valueOf(zone.getFirstOffset()));
        BigDecimal additionalPrice = supplier.getBaseAdditionalPrice().add(BigDecimal.valueOf(zone.getAdditionalOffset()));

        // 上海市内件普遍比跨省便宜，基准价里减去 2 元并保底
        if (province.name().equals(originProvince)) {
            firstPrice = firstPrice.subtract(new BigDecimal("2.00")).max(MIN_FIRST_PRICE);
        }

        LogisticsPrice price = new LogisticsPrice();
        price.setSupplierCode(supplier.getCode());
        price.setOriginProvince(originProvince);
        price.setOriginCity(originCity);
        price.setOriginDistrict(originDistrict);
        price.setDestProvince(province.name());
        // 初始按省统一价，个别城市需要单独定价时在页面上再补一条（查询时城市级优先）
        price.setDestCity(null);
        price.setFirstWeight(new BigDecimal("1.00"));
        price.setFirstPrice(firstPrice.setScale(2, java.math.RoundingMode.HALF_UP));
        price.setAdditionalWeight(new BigDecimal("1.00"));
        price.setAdditionalPrice(additionalPrice.setScale(2, java.math.RoundingMode.HALF_UP));
        price.setEstimatedDays(supplier.getBaseDays() + zone.getDaysOffset());
        price.setStatus(1);
        price.setRemark("系统初始化参考价，可按实际合同价调整");
        return price;
    }
}
