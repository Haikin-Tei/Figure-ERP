package com.figureerp.module.stock.converter;

import com.figureerp.module.stock.entity.Area;
import com.figureerp.module.stock.entity.InboundItem;
import com.figureerp.module.stock.entity.InboundOrder;
import com.figureerp.module.stock.entity.Shelf;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.vo.InboundItemVO;
import com.figureerp.module.stock.vo.InboundOrderVO;

import java.util.List;

/**
 * 入库单实体 → 出参。
 * <p>
 * 仓库 / 区域 / 货架的名称与明细由调用方批量查好传进来（列表场景一次查一页，避免逐行查库）。
 * 关联对象为 null 说明主数据已被删，此时只留 id，前端展示「-」。
 */
public final class InboundConverter {

    private InboundConverter() {
    }

    /**
     * @param withItems 是否带上明细。列表接口传 false 只给汇总，详情接口传 true
     */
    public static InboundOrderVO toVO(InboundOrder o, Warehouse warehouse, Area area, Shelf shelf,
                                      List<InboundItem> items, boolean withItems) {
        InboundOrderVO vo = new InboundOrderVO();
        vo.setId(o.getId());
        vo.setInboundNo(o.getInboundNo());
        vo.setInboundType(o.getInboundType());
        vo.setInboundTypeText(InboundType.labelOf(o.getInboundType()));
        vo.setWarehouseId(o.getWarehouseId());
        if (warehouse != null) {
            vo.setWarehouseCode(warehouse.getWarehouseCode());
            vo.setWarehouseName(warehouse.getWarehouseName());
        }
        vo.setAreaId(o.getAreaId());
        if (area != null) {
            vo.setAreaName(area.getAreaName());
        }
        vo.setShelfId(o.getShelfId());
        if (shelf != null) {
            vo.setShelfCode(shelf.getShelfCode());
        }
        vo.setSupplier(o.getSupplier());
        vo.setStatus(o.getStatus());
        vo.setStatusText(InboundStatus.labelOf(o.getStatus()));
        vo.setTotalQuantity(o.getTotalQuantity());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setInboundTime(o.getInboundTime());
        vo.setOperator(o.getOperator());
        vo.setRemark(o.getRemark());
        vo.setCreateTime(o.getCreateTime());
        vo.setUpdateTime(o.getUpdateTime());

        int count = items == null ? 0 : items.size();
        vo.setItemCount(count);
        // 列表里一眼看出这批货是什么，不用点进详情
        if (count > 0) {
            String first = items.get(0).getProductName();
            vo.setProductSummary(count == 1 ? first : first + " 等 " + count + " 款");
        }
        if (withItems) {
            vo.setItems(items == null ? List.of() : items.stream().map(InboundConverter::toItemVO).toList());
        }
        return vo;
    }

    public static InboundItemVO toItemVO(InboundItem item) {
        InboundItemVO vo = new InboundItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setProductCode(item.getProductCode());
        vo.setQuantity(item.getQuantity());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setAmount(item.getAmount());
        vo.setRemark(item.getRemark());
        vo.setSortNo(item.getSortNo());
        return vo;
    }
}
