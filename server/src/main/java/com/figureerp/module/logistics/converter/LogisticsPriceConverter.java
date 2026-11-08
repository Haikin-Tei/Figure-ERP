package com.figureerp.module.logistics.converter;

import com.figureerp.common.constant.ChinaRegions;
import com.figureerp.common.constant.LogisticsSupplier;
import com.figureerp.module.logistics.entity.LogisticsPrice;
import com.figureerp.module.logistics.vo.LogisticsPriceVO;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 物流价格实体 → 出参，以及各种展示文案的拼装。
 * <p>
 * 集中在一处，避免列表页、详情页、报价页各写一套格式化逻辑导致文案不一致。
 */
public final class LogisticsPriceConverter {

    private LogisticsPriceConverter() {
    }

    public static LogisticsPriceVO toVO(LogisticsPrice price) {
        LogisticsPriceVO vo = new LogisticsPriceVO();
        vo.setId(price.getId());
        vo.setSupplierCode(price.getSupplierCode());
        vo.setSupplierName(LogisticsSupplier.labelOf(price.getSupplierCode()));
        vo.setOriginProvince(price.getOriginProvince());
        vo.setOriginCity(price.getOriginCity());
        vo.setOriginDistrict(price.getOriginDistrict());
        vo.setOriginLabel(originLabel(price.getOriginProvince(), price.getOriginCity(), price.getOriginDistrict()));
        vo.setDestProvince(price.getDestProvince());
        vo.setDestCity(price.getDestCity());
        vo.setDestLabel(destLabel(price.getDestProvince(), price.getDestCity()));
        vo.setZoneLabel(ChinaRegions.zoneLabelOf(price.getDestProvince()));
        vo.setFirstWeight(price.getFirstWeight());
        vo.setFirstPrice(price.getFirstPrice());
        vo.setAdditionalWeight(price.getAdditionalWeight());
        vo.setAdditionalPrice(price.getAdditionalPrice());
        vo.setPriceLabel(priceLabel(price.getFirstWeight(), price.getFirstPrice(),
                price.getAdditionalWeight(), price.getAdditionalPrice()));
        vo.setEstimatedDays(price.getEstimatedDays());
        vo.setStatus(price.getStatus());
        vo.setRemark(price.getRemark());
        vo.setCreateTime(price.getCreateTime());
        vo.setUpdateTime(price.getUpdateTime());
        return vo;
    }

    /**
     * 出发地文案：直辖市省市同名时只显示一次，例如「上海市 浦东新区」而不是「上海市 上海市 浦东新区」。
     */
    public static String originLabel(String province, String city, String district) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(city) && !city.equals(province)) {
            sb.append(province).append(' ');
        }
        sb.append(StringUtils.hasText(city) ? city : province);
        if (StringUtils.hasText(district) && !district.equals(city)) {
            sb.append(' ').append(district);
        }
        return sb.toString();
    }

    /**
     * 目的地文案：没有城市时按行政区类型标注范围，
     * 例如「广东省（全省）」「上海市（全市）」「新疆维吾尔自治区（全区）」，
     * 让用户一眼看出这是省级统一价而不是某个城市的价格。
     */
    public static String destLabel(String province, String city) {
        if (!StringUtils.hasText(city)) {
            return province + "（" + scopeWord(province) + "）";
        }
        return city.equals(province) ? province : province + " " + city;
    }

    private static String scopeWord(String province) {
        if (province == null) {
            return "全部";
        }
        if (province.endsWith("自治区")) {
            return "全区";
        }
        if (province.endsWith("特别行政区")) {
            return "全境";
        }
        if (province.endsWith("市")) {
            return "全市";
        }
        return "全省";
    }

    /** 计价说明：首重 1kg 12 元，续重每 1kg 2 元 */
    public static String priceLabel(BigDecimal firstWeight, BigDecimal firstPrice,
                                    BigDecimal additionalWeight, BigDecimal additionalPrice) {
        return "首重 " + weight(firstWeight) + "kg " + money(firstPrice)
                + " 元，续重每 " + weight(additionalWeight) + "kg " + money(additionalPrice) + " 元";
    }

    /** 金额：保留两位小数，12 → 12.00 */
    public static String money(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 重量：去掉无意义的尾随零，1.00 → 1，0.50 → 0.5 */
    public static String weight(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
