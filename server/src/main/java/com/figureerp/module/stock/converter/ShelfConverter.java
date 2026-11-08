package com.figureerp.module.stock.converter;

import com.figureerp.module.stock.entity.Area;
import com.figureerp.module.stock.entity.Shelf;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.vo.ShelfVO;

import java.util.Map;

/**
 * 货架实体 → 出参。
 * <p>
 * 仓库、区域信息由调用方传进来（列表场景是批量查一次再拼，避免逐行查库）。
 * warehouse / area 为 null 说明被删了或本来没挂，此时只留 id，前端展示「-」。
 */
public final class ShelfConverter {

    /** 状态 → 文案 */
    private static final Map<String, String> STATUS_TEXT = Map.of(
            ShelfStatus.NORMAL, "正常",
            ShelfStatus.MAINTAINING, "维护中",
            ShelfStatus.DISABLED, "停用"
    );

    private ShelfConverter() {
    }

    public static ShelfVO toVO(Shelf s, Warehouse warehouse, Area area) {
        ShelfVO vo = new ShelfVO();
        vo.setId(s.getId());
        vo.setShelfCode(s.getShelfCode());
        vo.setWarehouseId(s.getWarehouseId());
        if (warehouse != null) {
            vo.setWarehouseCode(warehouse.getWarehouseCode());
            vo.setWarehouseName(warehouse.getWarehouseName());
        }
        vo.setAreaId(s.getAreaId());
        if (area != null) {
            vo.setAreaName(area.getAreaName());
        }
        vo.setLocation(s.getLocation());
        vo.setStatus(s.getStatus());
        vo.setStatusText(STATUS_TEXT.getOrDefault(s.getStatus(), s.getStatus()));
        vo.setRemark(s.getRemark());
        vo.setSortNo(s.getSortNo());
        vo.setCreateTime(s.getCreateTime());
        vo.setUpdateTime(s.getUpdateTime());
        return vo;
    }

    public static String statusText(String status) {
        return STATUS_TEXT.getOrDefault(status, status);
    }
}
