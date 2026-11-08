package com.figureerp.module.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 员工列表查询条件。
 */
@Data
@Schema(description = "员工查询条件")
public class UserQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private long page = 1;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 200, message = "每页条数不能超过 200")
    private long size = 10;

    @Schema(description = "关键字，匹配账号或姓名")
    private String keyword;

    @Schema(description = "角色：ADMIN / MANAGER / STAFF")
    private String role;

    @Schema(description = "状态：1 启用，0 停用")
    private Integer status;
}
