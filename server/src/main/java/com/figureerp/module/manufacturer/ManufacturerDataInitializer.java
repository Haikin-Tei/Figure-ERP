package com.figureerp.module.manufacturer;

import com.figureerp.module.manufacturer.entity.Manufacturer;
import com.figureerp.module.manufacturer.mapper.ManufacturerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 首次启动时写入一批知名手办 / 景品厂商种子数据，方便直接看到列表效果。
 * <p>
 * 只在厂商表为空时执行；已有数据不会覆盖，页面上改过 / 删过的数据也不会因重启还原。
 * 创立时间统一用「年份-01-01」占位（多数厂商只公开到年份），仅作演示，
 * 实际请以官方信息为准并在页面上修正 —— 本模块就是让用户随时改写这些资料。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.init-manufacturer.enabled", havingValue = "true", matchIfMissing = true)
public class ManufacturerDataInitializer implements ApplicationRunner {

    private final ManufacturerMapper manufacturerMapper;

    private record Seed(String name, int foundedYear, String country, String remark) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = manufacturerMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("厂商目录已有 {} 条记录，跳过初始化", count);
                return;
            }

            List<Seed> seeds = List.of(
                    new Seed("万代 BANDAI", 1950, "日本", "综合娱乐 / 玩具，高达、龙珠等"),
                    new Seed("万代南梦宫 BANDAI NAMCO", 2005, "日本", "万代与南梦宫合并后的集团"),
                    new Seed("BANPRESTO 眼镜厂", 1977, "日本", "主打景品与扭蛋，万代南梦宫旗下"),
                    new Seed("MegaHouse", 1997, "日本", "主打燃系动漫人物手办"),
                    new Seed("Good Smile Company", 2001, "日本", "昵称「好微笑」，粘土人系列"),
                    new Seed("寿屋 KOTOBUKIYA", 1951, "日本", "PVC 手办与拼装模型"),
                    new Seed("Max Factory", 1987, "日本", "御三家之一，figma 系列"),
                    new Seed("Alter", 2002, "日本", "御三家之一，高品质 PVC 手办"),
                    new Seed("海洋堂 KAIYODO", 1964, "日本", "山口式可动手办"),
                    new Seed("FuRyu", 2007, "日本", "景品质量较高"),
                    new Seed("Prime 1 Studio", 2012, "日本", "大型雕像 / 高端收藏"),
                    new Seed("Hot Toys", 2000, "中国香港", "1/6 比例可动人偶"),
                    new Seed("Sideshow Collectibles", 1994, "美国", "欧美雕像与收藏品")
            );

            for (Seed s : seeds) {
                Manufacturer m = new Manufacturer();
                m.setName(s.name);
                m.setFoundedDate(LocalDate.of(s.foundedYear, 1, 1));
                m.setCountry(s.country);
                m.setRemark(s.remark + "（演示数据，可在页面上修改）");
                m.setStatus(1);
                manufacturerMapper.insert(m);
            }
            log.warn("已生成 {} 条厂商目录初始数据", seeds.size());
        } catch (Exception e) {
            // 表没建时不影响应用启动，只提示怎么处理
            log.error("初始化厂商目录数据失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }
}
