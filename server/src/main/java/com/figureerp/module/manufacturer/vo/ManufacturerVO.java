package com.figureerp.module.manufacturer.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 厂商出参。
 * <p>
 * 除档案字段外带上 {@code statusText}（启用 / 停用），避免前端各处各译一遍。
 */
@Data
public class ManufacturerVO {

    private Long id;

    private String name;

    private String logo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate foundedDate;

    private String country;

    private String remark;

    /** 1 启用 / 0 停用 */
    private Integer status;

    /** 状态文案：启用 / 停用 */
    private String statusText;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
