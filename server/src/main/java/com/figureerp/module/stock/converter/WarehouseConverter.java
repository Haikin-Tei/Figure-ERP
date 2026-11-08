package com.figureerp.module.stock.converter;

import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.vo.WarehouseVO;

import java.util.Map;

/**
 * 仓库实体 → 出参，并负责把单字符状态翻译成中文文案。
 */
public final class WarehouseConverter {

    /** 状态 → 文案 */
    private static final Map<String, String> STATUS_TEXT = Map.of(
            WarehouseStatus.IN_USE, "使用中",
            WarehouseStatus.STOCKTAKING, "盘库中",
            WarehouseStatus.DISABLED, "停用中"
    );

    /** 是否退货库 → 文案 */
    private static final Map<String, String> RETURN_TEXT = Map.of(
            WarehouseStatus.NORMAL, "正常仓",
            WarehouseStatus.RETURN, "退货仓"
    );

    private WarehouseConverter() {
    }

    public static WarehouseVO toVO(Warehouse w) {
        WarehouseVO vo = new WarehouseVO();
        vo.setId(w.getId());
        vo.setWarehouseCode(w.getWarehouseCode());
        vo.setWarehouseName(w.getWarehouseName());
        vo.setWarehouseAddress(w.getWarehouseAddress());
        vo.setIsReturn(w.getIsReturn());
        vo.setIsReturnText(RETURN_TEXT.getOrDefault(w.getIsReturn(), w.getIsReturn()));
        vo.setStatus(w.getStatus());
        vo.setStatusText(STATUS_TEXT.getOrDefault(w.getStatus(), w.getStatus()));
        vo.setRemark(w.getRemark());
        vo.setSortNo(w.getSortNo());
        vo.setCreateTime(w.getCreateTime());
        vo.setUpdateTime(w.getUpdateTime());
        return vo;
    }
}
