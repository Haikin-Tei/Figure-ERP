package com.figureerp.module.manufacturer.converter;

import com.figureerp.module.manufacturer.entity.Manufacturer;
import com.figureerp.module.manufacturer.vo.ManufacturerVO;

/**
 * 厂商实体 → 出参。业务模块的出参统一从这里出，避免 Controller 里手写映射。
 */
public final class ManufacturerConverter {

    private ManufacturerConverter() {
    }

    public static ManufacturerVO toVO(Manufacturer m) {
        ManufacturerVO vo = new ManufacturerVO();
        vo.setId(m.getId());
        vo.setName(m.getName());
        vo.setLogo(m.getLogo());
        vo.setFoundedDate(m.getFoundedDate());
        vo.setCountry(m.getCountry());
        vo.setRemark(m.getRemark());
        vo.setStatus(m.getStatus());
        vo.setStatusText(statusText(m.getStatus()));
        vo.setCreateTime(m.getCreateTime());
        vo.setUpdateTime(m.getUpdateTime());
        return vo;
    }

    public static String statusText(Integer status) {
        return Integer.valueOf(1).equals(status) ? "启用" : "停用";
    }
}
