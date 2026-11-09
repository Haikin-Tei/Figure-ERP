package com.figureerp.module.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 新增 / 编辑入库单。
 * <p>
 * inboundNo 留空时后端按「RK + yyyyMMdd + 4 位序号」自动生成，手填则校验未删除记录内唯一。
 * inboundTime 留空时取当前时间。明细至少一条，数量与金额合计由后端按明细重算。
 */
@Data
@Schema(description = "入库单保存参数")
public class InboundSaveRequest {

    @Schema(description = "入库单号，留空自动生成", example = "RK202611070001")
    @Size(max = 50, message = "入库单号不能超过 50 个字符")
    private String inboundNo;

    @Schema(description = "入库类型：P 采购 / R 退货 / T 调拨 / O 其他，默认 P")
    @Pattern(regexp = "^[PRTO]$", message = "入库类型只能是 P / R / T / O")
    private String inboundType;

    @Schema(description = "入库仓库 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择入库仓库")
    private Long warehouseId;

    @Schema(description = "入库货架 ID，可空；须与仓库一致")
    private Long shelfId;

    @Schema(description = "供应商 / 来源单位")
    @Size(max = 100, message = "供应商不能超过 100 个字符")
    private String supplier;

    @Schema(description = "入库时间，ISO-8601，留空取当前时间", example = "2026-11-07T10:30:00")
    private LocalDateTime inboundTime;

    @Schema(description = "经办人，留空取当前登录用户姓名")
    @Size(max = 50, message = "经办人不能超过 50 个字符")
    private String operator;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

    @Schema(description = "手办明细，至少一条", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少添加一条手办明细")
    @Valid
    private List<InboundItemRequest> items;
}
