package com.figureerp.module.product.converter;

import com.figureerp.module.product.entity.FigureProduct;
import com.figureerp.module.product.vo.FigureProductVO;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 手办商品实体 → 出参，以及图片 URL 列表与逗号分隔字符串之间的互转。
 */
public final class FigureProductConverter {

    /** 图片 URL 之间的分隔符（数据库里以逗号拼接存储） */
    public static final String SEPARATOR = ",";

    private FigureProductConverter() {
    }

    public static FigureProductVO toVO(FigureProduct p) {
        FigureProductVO vo = new FigureProductVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setCode(p.getCode());
        vo.setManufacturerId(p.getManufacturerId());
        vo.setBrand(p.getBrand());
        vo.setSeries(p.getSeries());
        vo.setCharacterName(p.getCharacterName());
        vo.setMaterial(p.getMaterial());
        vo.setLengthCm(p.getLengthCm());
        vo.setWidthCm(p.getWidthCm());
        vo.setHeightCm(p.getHeightCm());
        vo.setWeightWithBox(p.getWeightWithBox());
        vo.setImages(toList(p.getImages()));
        vo.setSpecLabel(specLabel(p.getLengthCm(), p.getWidthCm(), p.getHeightCm(), p.getWeightWithBox()));
        vo.setRemark(p.getRemark());
        vo.setStatus(p.getStatus());
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
        return vo;
    }

    /** 把逗号分隔的 URL 字符串拆成列表，过滤空串 */
    public static List<String> toList(String images) {
        if (!StringUtils.hasText(images)) {
            return List.of();
        }
        return Arrays.stream(images.split(SEPARATOR))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /** 把图片 URL 列表拼成逗号分隔字符串；null/空列表返回 null（不写列） */
    public static String join(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .reduce((a, b) -> a + SEPARATOR + b)
                .orElse(null);
    }

    /** 规格文案：长×宽×高 cm / 带盒 1200 g，缺哪段就省略哪段 */
    public static String specLabel(BigDecimal length, BigDecimal width, BigDecimal height, Integer weight) {
        StringBuilder sb = new StringBuilder();
        boolean hasDimension = length != null || width != null || height != null;
        if (hasDimension) {
            sb.append(num(length)).append('×').append(num(width)).append('×').append(num(height)).append(" cm");
        }
        if (weight != null) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append("带盒 ").append(weight).append(" g");
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }

    private static String num(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
