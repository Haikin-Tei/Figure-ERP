package com.figureerp.module.stock.converter;

import com.figureerp.module.product.entity.FigureProduct;
import com.figureerp.module.stock.entity.Shelf;
import com.figureerp.module.stock.entity.Stock;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.vo.StockLocationVO;
import com.figureerp.module.stock.vo.StockVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 库存实体 → 出参。
 * <p>
 * 两种来源：
 * <ul>
 *   <li>{@link #toLocationVO}：stock 行 + 仓库 / 货架，用于库位明细；</li>
 *   <li>{@link #toVO}：SQL 聚合出来的 Map + 商品档案，用于列表。</li>
 * </ul>
 * 商品为 null 说明已被逻辑删除，此时退回 stock 里的快照字段，并标「已下架」。
 */
public final class StockConverter {

    private StockConverter() {
    }

    /** 库位明细：关联对象为 null 说明主数据被删，只留 id，前端展示「已删除」 */
    public static StockLocationVO toLocationVO(Stock s, Warehouse warehouse, Shelf shelf) {
        StockLocationVO vo = new StockLocationVO();
        vo.setId(s.getId());
        vo.setWarehouseId(s.getWarehouseId());
        if (warehouse != null) {
            vo.setWarehouseCode(warehouse.getWarehouseCode());
            vo.setWarehouseName(warehouse.getWarehouseName());
        }
        vo.setShelfId(s.getShelfId());
        if (shelf != null) {
            vo.setShelfCode(shelf.getShelfCode());
            vo.setShelfLocation(shelf.getLocation());
        }
        vo.setQuantity(s.getQuantity() == null ? 0 : s.getQuantity());
        vo.setAmount(scale(s.getTotalCost()));
        vo.setAvgCost(average(s.getTotalCost(), vo.getQuantity()));
        vo.setLastInboundTime(s.getLastInboundTime());
        return vo;
    }

    /**
     * 列表行：聚合结果 + 商品档案。
     *
     * @param row       groupBy product_id 的结果，键为别名：productName / productCode / totalQuantity /
     *                  totalAmount / locationCount / lastInboundTime / brand
     * @param product   商品档案，已下架时为 null
     */
    public static StockVO toVO(Map<String, Object> row, FigureProduct product) {
        StockVO vo = new StockVO();
        vo.setProductId(toLong(row.get("productId")));

        int quantity = toInt(row.get("totalQuantity"));
        BigDecimal amount = scale(toDecimal(row.get("totalAmount")));

        if (product != null) {
            // 商品还在：名称 / 货号 / 品牌以档案当前值为准，厂商改名后这里自动跟着变
            vo.setProductName(product.getName());
            vo.setProductCode(product.getCode());
            vo.setBrand(product.getBrand());
            vo.setImageUrl(firstImage(product.getImages()));
            vo.setProductDeleted(false);
            vo.setProductStatus(product.getStatus());
            vo.setProductStatusText(Integer.valueOf(1).equals(product.getStatus()) ? "在售" : "已停用");
        } else {
            // 商品已下架：只能用入库时的快照
            vo.setProductName(toStr(row.get("productName")));
            vo.setProductCode(toStr(row.get("productCode")));
            vo.setBrand(toStr(row.get("brand")));
            vo.setProductDeleted(true);
            vo.setProductStatusText("已下架");
        }

        vo.setTotalQuantity(quantity);
        vo.setTotalAmount(amount);
        vo.setAvgCost(average(amount, quantity));
        vo.setLocationCount(toInt(row.get("locationCount")));
        vo.setLastInboundTime(toDateTime(row.get("lastInboundTime")));
        return vo;
    }

    /** 均价 = 成本总额 / 数量；数量为 0 时返回 0，避免除零 */
    private static BigDecimal average(BigDecimal totalCost, int quantity) {
        if (quantity <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal cost = totalCost == null ? BigDecimal.ZERO : totalCost;
        return cost.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
    }

    private static String firstImage(String images) {
        if (images == null || images.isBlank()) {
            return null;
        }
        for (String part : images.split(",")) {
            String url = part.trim();
            if (!url.isEmpty()) {
                return url;
            }
        }
        return null;
    }

    /* ================= 类型转换：selectMaps 返回的是 Object，按需安全转 ================= */

    private static String toStr(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        return v instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(v));
    }

    private static int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v));
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return v instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(v));
    }

    private static java.time.LocalDateTime toDateTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof java.time.LocalDateTime dt) {
            return dt;
        }
        // 不同 JDBC 驱动 / 列类型可能返回 Timestamp
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    private static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
