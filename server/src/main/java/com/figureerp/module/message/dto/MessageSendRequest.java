package com.figureerp.module.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送消息（管理员 / 主管手工发给某人，或其它模块通过接口发）。
 * <p>
 * 正常业务里的审批、订单消息由各模块调用 {@code MessageService.push(...)} 直接推送，
 * 这个接口更多用于运营手动通知与联调测试。
 */
@Data
@Schema(description = "发送消息参数")
public class MessageSendRequest {

    @Schema(description = "接收人 ID（sys_user.id）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择接收人")
    private Long receiverId;

    @Schema(description = "标题", example = "有新的入库单待审批", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写消息标题")
    @Size(max = 120, message = "标题不能超过 120 个字符")
    private String title;

    @Schema(description = "正文")
    @Size(max = 1000, message = "正文不能超过 1000 个字符")
    private String content;

    @Schema(description = "类型：APPROVAL / ORDER / INVENTORY / SYSTEM，默认 SYSTEM")
    @Pattern(regexp = "^(APPROVAL|ORDER|INVENTORY|SYSTEM)$", message = "消息类型不合法")
    private String msgType;

    @Schema(description = "业务对象类型，如 ORDER / PRODUCT")
    @Size(max = 30, message = "业务对象类型不能超过 30 个字符")
    private String bizType;

    @Schema(description = "业务对象 ID")
    private Long bizId;

    @Schema(description = "「去处理」跳转的前端路由，如 /order/list")
    @Size(max = 255, message = "跳转地址不能超过 255 个字符")
    private String bizUrl;

    @Schema(description = "优先级：N 普通 / U 紧急，默认 N")
    @Pattern(regexp = "^[NU]$", message = "优先级只能是 N 或 U")
    private String priority;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
