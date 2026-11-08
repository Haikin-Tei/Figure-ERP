package com.figureerp.module.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 消息列表查询条件。
 * <p>
 * 接收人固定为当前登录用户，不在参数里出现 —— 避免有人传别人的 id 翻别人的消息。
 */
@Data
@Schema(description = "消息查询条件")
public class MessageQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private long page = 1;

    @Schema(description = "每页条数", example = "20")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 200, message = "每页条数不能超过 200")
    private long size = 20;

    @Schema(description = "类型：APPROVAL 审批 / ORDER 订单 / INVENTORY 库存 / SYSTEM 系统，不传表示全部")
    private String msgType;

    @Schema(description = "状态：U 未读 / R 已读 / H 已处理，不传表示全部")
    private String status;

    @Schema(description = "优先级：N 普通 / U 紧急")
    private String priority;

    @Schema(description = "关键字，匹配标题或正文")
    private String keyword;
}
