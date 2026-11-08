package com.figureerp.module.stock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.module.stock.converter.AreaStatus;
import com.figureerp.module.stock.entity.Area;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.mapper.AreaMapper;
import com.figureerp.module.stock.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 首次启动时把原先写死在「区域配置」页面里的 3 条演示数据写入数据库。
 * <p>
 * 只在区域表为空时执行；已有数据不会被覆盖，页面上改过/删过的数据也不会因重启还原。
 * 依赖仓库已初始化：按仓库代码找 warehouse_id，找不到对应仓库的种子直接跳过，
 * 避免出现指向不存在仓库的脏数据。
 * <p>
 * {@code @Order(10)}：必须早于 {@link ShelfDataInitializer}（20），
 * 否则货架初始化时查不到区域，货架的 area_id 会留空。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(10)
@ConditionalOnProperty(name = "app.init-area.enabled", havingValue = "true", matchIfMissing = true)
public class AreaDataInitializer implements ApplicationRunner {

    private final AreaMapper areaMapper;
    private final WarehouseMapper warehouseMapper;

    private record Seed(String warehouseCode, String areaCode, String areaName, String location,
                        int capacity, String status, String remark) {}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = areaMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("区域已有 {} 条记录，跳过初始化", count);
                return;
            }

            List<Seed> seeds = List.of(
                    new Seed("3101", "A", "A区-核心展示柜", "一层-主墙面", 20, AreaStatus.IN_USE, "放置热门款"),
                    new Seed("3101", "B", "B区-收纳箱", "一层-储藏室", 50, AreaStatus.IN_USE, "未拆封库存"),
                    new Seed("3102", "C", "C区-办公桌", "二层-办公区", 5, AreaStatus.DISABLED, "临时摆放")
            );

            int sortNo = 10;
            int inserted = 0;
            for (Seed s : seeds) {
                Warehouse w = warehouseMapper.selectOne(Wrappers.<Warehouse>lambdaQuery()
                        .eq(Warehouse::getWarehouseCode, s.warehouseCode())
                        .last("LIMIT 1"));
                if (w == null) {
                    log.warn("跳过区域种子 {}：仓库 {} 不存在", s.areaCode(), s.warehouseCode());
                    continue;
                }
                Area area = new Area();
                area.setAreaCode(s.areaCode());
                area.setAreaName(s.areaName());
                area.setWarehouseId(w.getId());
                area.setLocation(s.location());
                area.setCapacity(s.capacity());
                area.setStatus(s.status());
                area.setRemark(s.remark());
                area.setSortNo(sortNo);
                sortNo += 10;
                areaMapper.insert(area);
                inserted++;
            }
            log.warn("已生成 {} 条区域初始数据（共 {} 条种子）", inserted, seeds.size());
        } catch (Exception e) {
            // 表没建时不影响应用启动，只提示怎么处理
            log.error("初始化区域数据失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }
}
