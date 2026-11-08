package com.figureerp.module.stock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.module.stock.converter.ShelfStatus;
import com.figureerp.module.stock.entity.Area;
import com.figureerp.module.stock.entity.Shelf;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.mapper.AreaMapper;
import com.figureerp.module.stock.mapper.ShelfMapper;
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
 * 首次启动时把原先写死在货架管理页面里的 3 条演示数据写入数据库。
 * <p>
 * 只在货架表为空时执行；已有数据不会被覆盖，页面上改过/删过的数据也不会因重启还原。
 * 依赖仓库已初始化：按仓库代码找 warehouse_id，找不到对应仓库的种子直接跳过，
 * 避免出现指向不存在仓库的脏数据。
 * <p>
 * {@code @Order(20)}：排在 {@link AreaDataInitializer}（10）之后，这样能按仓库 + 区域编号
 * 找到刚建好的区域，把货架挂到对应的区域下；找不到区域时 area_id 留空，不影响货架本身。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(20)
@ConditionalOnProperty(name = "app.init-shelf.enabled", havingValue = "true", matchIfMissing = true)
public class ShelfDataInitializer implements ApplicationRunner {

    private final ShelfMapper shelfMapper;
    private final WarehouseMapper warehouseMapper;
    private final AreaMapper areaMapper;

    private record Seed(String warehouseCode, String shelfCode, String location, String status, String remark) {}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = shelfMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("货架已有 {} 条记录，跳过初始化", count);
                return;
            }

            List<Seed> seeds = List.of(
                    new Seed("3101", "01-1", "01区-1层", ShelfStatus.NORMAL, "存储大件手办"),
                    new Seed("3101", "01-2", "01区-2层", ShelfStatus.NORMAL, "存储周边产品"),
                    new Seed("3102", "02-1", "02区-1层", ShelfStatus.MAINTAINING, "本周进行检修")
            );

            int sortNo = 10;
            int inserted = 0;
            for (Seed s : seeds) {
                Warehouse w = warehouseMapper.selectOne(Wrappers.<Warehouse>lambdaQuery()
                        .eq(Warehouse::getWarehouseCode, s.warehouseCode())
                        .last("LIMIT 1"));
                if (w == null) {
                    log.warn("跳过货架种子 {}：仓库 {} 不存在", s.shelfCode(), s.warehouseCode());
                    continue;
                }
                Shelf shelf = new Shelf();
                shelf.setShelfCode(s.shelfCode());
                shelf.setWarehouseId(w.getId());
                // 同一仓库下的 A 区（区域初始化的第一条），没有就留空
                Area area = areaMapper.selectOne(Wrappers.<Area>lambdaQuery()
                        .eq(Area::getWarehouseId, w.getId())
                        .eq(Area::getAreaCode, "A")
                        .last("LIMIT 1"));
                shelf.setAreaId(area == null ? null : area.getId());
                shelf.setLocation(s.location());
                shelf.setStatus(s.status());
                shelf.setRemark(s.remark());
                shelf.setSortNo(sortNo);
                sortNo += 10;
                shelfMapper.insert(shelf);
                inserted++;
            }
            log.warn("已生成 {} 条货架初始数据（共 {} 条种子）", inserted, seeds.size());
        } catch (Exception e) {
            // 表没建时不影响应用启动，只提示怎么处理
            log.error("初始化货架数据失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }
}
