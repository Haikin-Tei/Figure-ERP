package com.figureerp.module.product;

import com.figureerp.module.product.entity.FigureProduct;
import com.figureerp.module.product.mapper.FigureProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首次启动时写入一批手办商品演示数据。
 * <p>
 * 只在商品表为空时执行，已有数据不会被覆盖，之后在页面上改过的商品不会因重启还原。
 * 演示数据不带图片（图片由页面上传），列表页对空图片做了兼容展示。
 * <p>
 * {@code @Order(30)}：必须早于 {@link com.figureerp.module.stock.InboundDataInitializer}（40），
 * 后者的演示入库单要按货号关联这些手办。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(30)
@ConditionalOnProperty(name = "app.init-product.enabled", havingValue = "true", matchIfMissing = true)
public class ProductDataInitializer implements ApplicationRunner {

    private final FigureProductMapper productMapper;

    private record Seed(
            String name, String code, String brand, String series, String character,
            String material, double length, double width, double height, int weight, String remark
    ) {}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = productMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("手办商品已有 {} 条记录，跳过初始化", count);
                return;
            }

            List<Seed> seeds = List.of(
                    new Seed("初音未来 雪未来 1/7 手办", "GSC-001", "Good Smile Company", "Scale", "初音未来",
                            "PVC/ABS", 18.0, 18.0, 28.5, 1200, "冬季限定款，带特效件"),
                    new Seed("明日香 测试用 1/6 手办", "ALTER-220", "ALTER", "Scale", "明日香",
                            "PVC/ABS", 22.0, 20.0, 30.0, 1500, "合金底座"),
                    new Seed("ねんどろいど 御坂美琴", "GSC-N123", "Good Smile Company", "Nendoroid", "御坂美琴",
                            "PVC/ABS", 10.0, 10.0, 10.0, 380, "可换表情 + 武器配件"),
                    new Seed("蕾姆 1/7 手办（和风版）", "FIGMA-R01", "Max Factory", "figma", "蕾姆",
                            "PVC/ABS", 14.0, 12.0, 16.0, 720, "可动关节，含可替换手型"),
                    new Seed("鏈鋸人 帕瓦 1/7 手办", "SEGA-P07", "SEGA", "Scale", "帕瓦",
                            "PVC/ABS", 16.0, 15.0, 24.0, 980, "血之恶魔造型"),
                    new Seed("Hatsune Miku 樱花版", "GSC-002", "Good Smile Company", "Scale", "初音未来",
                            "PVC/ABS", 19.0, 19.0, 26.5, 1100, "春季限定，半透明材质")
            );

            for (Seed s : seeds) {
                FigureProduct p = new FigureProduct();
                p.setName(s.name);
                p.setCode(s.code);
                p.setBrand(s.brand);
                p.setSeries(s.series);
                p.setCharacterName(s.character);
                p.setMaterial(s.material);
                p.setLengthCm(BigDecimal.valueOf(s.length));
                p.setWidthCm(BigDecimal.valueOf(s.width));
                p.setHeightCm(BigDecimal.valueOf(s.height));
                p.setWeightWithBox(s.weight);
                p.setRemark(s.remark);
                p.setStatus(1);
                productMapper.insert(p);
            }
            log.warn("已生成 {} 条手办商品演示数据（不含图片，请在页面上传）", seeds.size());
        } catch (Exception e) {
            // 表没建时不影响应用启动，只提示怎么处理
            log.error("初始化手办商品失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }
}
