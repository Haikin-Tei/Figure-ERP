package com.figureerp.module.stock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.module.product.entity.FigureProduct;
import com.figureerp.module.product.mapper.FigureProductMapper;
import com.figureerp.module.stock.converter.InboundStatus;
import com.figureerp.module.stock.converter.InboundType;
import com.figureerp.module.stock.entity.Area;
import com.figureerp.module.stock.entity.InboundItem;
import com.figureerp.module.stock.entity.InboundOrder;
import com.figureerp.module.stock.entity.Shelf;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.mapper.AreaMapper;
import com.figureerp.module.stock.mapper.InboundItemMapper;
import com.figureerp.module.stock.mapper.InboundOrderMapper;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 首次启动时写入 4 条演示入库单（含明细），让入库记录页面一打开就有数据可看。
 * <p>
 * 只在入库单表为空时执行；已有数据不会被覆盖，页面上改过 / 删过的数据也不会因重启还原。
 * 依赖链：仓库（5）→ 区域（10）→ 货架（20）→ 手办（30）→ 这里（40）。
 * 找不到对应仓库 / 手办的种子会被跳过，不会造出指向不存在主数据的脏单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(40)
@ConditionalOnProperty(name = "app.init-inbound.enabled", havingValue = "true", matchIfMissing = true)
public class InboundDataInitializer implements ApplicationRunner {

    private final InboundOrderMapper orderMapper;
    private final InboundItemMapper itemMapper;
    private final WarehouseMapper warehouseMapper;
    private final AreaMapper areaMapper;
    private final ShelfMapper shelfMapper;
    private final FigureProductMapper productMapper;

    /** 一条明细种子：货号 + 数量 + 单价 */
    private record ItemSeed(String productCode, int quantity, String unitPrice, String remark) {}

    /** 一张单据种子：仓库代码 / 区域编号 / 货架编号 / 类型 / 状态 / 供应商 / 几天前 / 明细 */
    private record OrderSeed(String warehouseCode, String areaCode, String shelfCode, String type, String status,
                             String supplier, int daysAgo, String operator, String remark, List<ItemSeed> items) {}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = orderMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("入库单已有 {} 条记录，跳过初始化", count);
                return;
            }

            List<OrderSeed> seeds = List.of(
                    new OrderSeed("3101", "A", "01-1", InboundType.PURCHASE, InboundStatus.CONFIRMED,
                            "Good Smile Company 中国代理", 2, "系统管理员", "双十一预热备货",
                            List.of(new ItemSeed("GSC-001", 5, "520.00", "冬季限定款"),
                                    new ItemSeed("GSC-N123", 20, "180.00", "常规补货"))),
                    new OrderSeed("3101", "A", "01-2", InboundType.RETURN, InboundStatus.CONFIRMED,
                            "天猫旗舰店退货", 5, "系统管理员", "买家七天无理由退货，外盒轻微压痕",
                            List.of(new ItemSeed("ALTER-220", 2, "880.00", "待质检后重新上架"))),
                    new OrderSeed("3101", "B", "01-1", InboundType.PURCHASE, InboundStatus.DRAFT,
                            "ALTER 官方渠道", 0, "系统管理员", "待到货清点，暂未确认",
                            List.of(new ItemSeed("SEGA-P07", 8, "460.00", null),
                                    new ItemSeed("FIGMA-R01", 6, "310.00", null))),
                    new OrderSeed("3101", "B", "01-2", InboundType.TRANSFER, InboundStatus.CONFIRMED,
                            "北京库调拨", 12, "系统管理员", "华北仓调拨至上海仓",
                            List.of(new ItemSeed("GSC-002", 10, "500.00", "樱花版转仓")))
            );

            int insertedOrders = 0;
            int insertedItems = 0;
            for (OrderSeed s : seeds) {
                Warehouse w = warehouseMapper.selectOne(Wrappers.<Warehouse>lambdaQuery()
                        .eq(Warehouse::getWarehouseCode, s.warehouseCode())
                        .last("LIMIT 1"));
                if (w == null) {
                    log.warn("跳过入库单种子（仓库 {} 不存在），供应商 {}", s.warehouseCode(), s.supplier());
                    continue;
                }

                LocalDateTime inboundTime = LocalDate.now().minusDays(s.daysAgo()).atTime(10, 30);

                InboundOrder order = new InboundOrder();
                order.setInboundNo("RK" + inboundTime.toLocalDate().toString().replace("-", "")
                        + String.format("%04d", insertedOrders + 1));
                order.setInboundType(s.type());
                order.setWarehouseId(w.getId());
                order.setAreaId(findAreaId(w.getId(), s.areaCode()));
                order.setShelfId(findShelfId(w.getId(), s.shelfCode()));
                order.setSupplier(s.supplier());
                order.setStatus(s.status());
                order.setInboundTime(inboundTime);
                order.setOperator(s.operator());
                order.setRemark(s.remark());

                // 先按明细算合计，再落主表，保证列表上的数字和明细一致
                int quantity = 0;
                BigDecimal amount = BigDecimal.ZERO;
                List<FigureProduct> products = new java.util.ArrayList<>();
                for (ItemSeed is : s.items()) {
                    FigureProduct p = productMapper.selectOne(Wrappers.<FigureProduct>lambdaQuery()
                            .eq(FigureProduct::getCode, is.productCode())
                            .last("LIMIT 1"));
                    products.add(p);
                    if (p != null) {
                        quantity += is.quantity();
                        amount = amount.add(new BigDecimal(is.unitPrice())
                                .multiply(BigDecimal.valueOf(is.quantity())));
                    }
                }
                order.setTotalQuantity(quantity);
                order.setTotalAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));

                boolean anyItem = products.stream().anyMatch(java.util.Objects::nonNull);
                if (!anyItem) {
                    log.warn("跳过入库单种子（手办都不存在），供应商 {}", s.supplier());
                    continue;
                }

                orderMapper.insert(order);
                insertedOrders++;

                int sortNo = 10;
                for (int i = 0; i < s.items().size(); i++) {
                    ItemSeed is = s.items().get(i);
                    FigureProduct p = products.get(i);
                    if (p == null) {
                        log.warn("跳过明细种子：货号 {} 不存在", is.productCode());
                        continue;
                    }
                    InboundItem item = new InboundItem();
                    item.setInboundId(order.getId());
                    item.setProductId(p.getId());
                    item.setProductName(p.getName());
                    item.setProductCode(p.getCode());
                    item.setQuantity(is.quantity());
                    item.setUnitPrice(new BigDecimal(is.unitPrice()));
                    item.setAmount(item.getUnitPrice().multiply(BigDecimal.valueOf(is.quantity()))
                            .setScale(2, java.math.RoundingMode.HALF_UP));
                    item.setRemark(is.remark());
                    item.setSortNo(sortNo);
                    sortNo += 10;
                    itemMapper.insert(item);
                    insertedItems++;
                }
            }
            log.warn("已生成 {} 条入库单 / {} 条明细演示数据（共 {} 条单据种子）", insertedOrders, insertedItems, seeds.size());
        } catch (Exception e) {
            // 表没建时不影响应用启动，只提示怎么处理
            log.error("初始化入库单数据失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }

    private Long findAreaId(Long warehouseId, String areaCode) {
        Area area = areaMapper.selectOne(Wrappers.<Area>lambdaQuery()
                .eq(Area::getWarehouseId, warehouseId)
                .eq(Area::getAreaCode, areaCode)
                .last("LIMIT 1"));
        return area == null ? null : area.getId();
    }

    private Long findShelfId(Long warehouseId, String shelfCode) {
        Shelf shelf = shelfMapper.selectOne(Wrappers.<Shelf>lambdaQuery()
                .eq(Shelf::getWarehouseId, warehouseId)
                .eq(Shelf::getShelfCode, shelfCode)
                .last("LIMIT 1"));
        return shelf == null ? null : shelf.getId();
    }
}
