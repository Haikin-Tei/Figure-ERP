package com.figureerp.module.stock.converter;

import com.figureerp.module.stock.entity.Area;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.vo.AreaVO;

import java.util.Map;

/**
 * 区域实体 → 出参。
 * <p>
 * 仓库信息与货架数由调用方传进来（列表场景批量查一次再拼，避免逐行查库）。
 * warehouse 为 null 说明仓库被删了，此时只留 warehouseId，前端展示「-」。
 */
public final class AreaConverter {

    /** 状态 → 文案 */
    private static final Map<String, String> STATUS_TEXT = Map.of(
            AreaStatus.IN_USE, "使用中",
            AreaStatus.DISABLED, "停用"
    );

    private AreaConverter() {
    }

    public static AreaVO toVO(Area a, Warehouse warehouse, Long shelfCount) {
        AreaVO vo = new AreaVO();
        vo.setId(a.getId());
        vo.setAreaCode(a.getAreaCode());
        vo.setAreaName(a.getAreaName());
        vo.setWarehouseId(a.getWarehouseId());
        if (warehouse != null) {
            vo.setWarehouseCode(warehouse.getWarehouseCode());
            vo.setWarehouseName(warehouse.getWarehouseName());
        }
        vo.setLocation(a.getLocation());
        vo.setCapacity(a.getCapacity());
        vo.setShelfCount(shelfCount == null ? 0L : shelfCount);
        vo.setStatus(a.getStatus());
        vo.setStatusText(STATUS_TEXT.getOrDefault(a.getStatus(), a.getStatus()));
        vo.setRemark(a.getRemark());
        vo.setSortNo(a.getSortNo());
        vo.setCreateTime(a.getCreateTime());
        vo.setUpdateTime(a.getUpdateTime());
        return vo;
    }

    public static String statusText(String status) {
        return STATUS_TEXT.getOrDefault(status, status);
    }
}
