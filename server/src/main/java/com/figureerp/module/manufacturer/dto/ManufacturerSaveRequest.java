package com.figureerp.module.manufacturer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新增 / 编辑厂商的入参。
 * <p>
 * 只有公司名称必填；Logo、创立时间、国家地区、备注可以先空着后补。
 * 编辑时传空字符串表示清空对应字段（Logo / 国家 / 备注）。
 */
@Data
public class ManufacturerSaveRequest {

    @NotBlank(message = "公司名称不能为空")
    @Size(max = 120, message = "公司名称最多 120 个字符")
    private String name;

    @Size(max = 255, message = "Logo 地址过长")
    private String logo;

    /** 创立时间，yyyy-MM-dd */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate foundedDate;

    @Size(max = 50, message = "国家地区最多 50 个字符")
    private String country;

    @Size(max = 500, message = "备注最多 500 个字符")
    private String remark;

    /** 1 启用 / 0 停用，默认 1；编辑时不传则保持原状态 */
    private Integer status;
}
