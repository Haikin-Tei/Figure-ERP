package com.figureerp.module.stock;

import com.figureerp.module.stock.converter.WarehouseStatus;
import com.figureerp.module.stock.entity.Warehouse;
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
 * 首次启动时把原先写死在前端的 12 条仓库写入数据库。
 * <p>
 * 只在仓库表为空时执行；已有数据不会被覆盖，页面上改过/删过的数据也不会因重启还原。
 * <p>
 * {@code @Order(5)}：排在最前面，保证后面的区域（10）/ 货架（20）/ 入库单（40）
 * 都能按仓库代码找到刚建好的仓库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(5)
@ConditionalOnProperty(name = "app.init-warehouse.enabled", havingValue = "true", matchIfMissing = true)
public class WarehouseDataInitializer implements ApplicationRunner {

    private final WarehouseMapper warehouseMapper;

    private record Seed(String code, String name, String address, String isReturn, String status) {}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = warehouseMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("仓库已有 {} 条记录，跳过初始化", count);
                return;
            }

            List<Seed> seeds = List.of(
                    new Seed("3101", "上海库", "上海市浦东新区新川路180号", WarehouseStatus.NORMAL, WarehouseStatus.IN_USE),
                    new Seed("3102", "上海退货库", "上海市浦东新区新川路180号", WarehouseStatus.RETURN, WarehouseStatus.IN_USE),
                    new Seed("1101", "北京库", "北京市海淀区清华紫光大厦", WarehouseStatus.NORMAL, WarehouseStatus.STOCKTAKING),
                    new Seed("1102", "北京退货库", "北京市海淀区清华紫光大厦", WarehouseStatus.NORMAL, WarehouseStatus.STOCKTAKING),
                    new Seed("4401", "广州库", "广州市荔湾区黄沙大道144号", WarehouseStatus.NORMAL, WarehouseStatus.DISABLED),
                    new Seed("4402", "广州退货库", "广州市荔湾区黄沙大道144号", WarehouseStatus.NORMAL, WarehouseStatus.DISABLED),
                    new Seed("5101", "成都库", "成都市青羊区培风路辅路与苏坡西路", WarehouseStatus.NORMAL, WarehouseStatus.IN_USE),
                    new Seed("5102", "成都退货库", "成都市青羊区培风路辅路与苏坡西路", WarehouseStatus.NORMAL, WarehouseStatus.IN_USE),
                    new Seed("3301", "杭州库", "杭州市滨江区联慧街6号", WarehouseStatus.NORMAL, WarehouseStatus.IN_USE),
                    new Seed("3302", "杭州退货库", "杭州市滨江区联慧街6号", WarehouseStatus.NORMAL, WarehouseStatus.IN_USE),
                    new Seed("4403", "深圳库", "深圳市龙岗区龙河路3号", WarehouseStatus.NORMAL, WarehouseStatus.IN_USE),
                    new Seed("4404", "深圳退货库", "深圳市龙岗区龙河路3号", WarehouseStatus.NORMAL, WarehouseStatus.IN_USE)
            );

            int sortNo = 10;
            for (Seed s : seeds) {
                Warehouse w = new Warehouse();
                w.setWarehouseCode(s.code);
                w.setWarehouseName(s.name);
                w.setWarehouseAddress(s.address);
                w.setIsReturn(s.isReturn);
                w.setStatus(s.status);
                w.setSortNo(sortNo);
                sortNo += 10;
                warehouseMapper.insert(w);
            }
            log.warn("已生成 {} 条仓库初始数据", seeds.size());
        } catch (Exception e) {
            // 表没建时不影响应用启动，只提示怎么处理
            log.error("初始化仓库数据失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }
}
