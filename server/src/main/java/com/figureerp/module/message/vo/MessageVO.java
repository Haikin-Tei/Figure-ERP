package com.figureerp.module.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息出参（列表 / 抽屉 / 详情共用）。
 * <p>
 * 各类文案由后端翻译好，前端只负责展示与配色。
 */
@Data
@Schema(description = "个人消息")
public class MessageVO {

    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文")
    private String content;

    @Schema(description = "类型：APPROVAL 审批 / ORDER 订单 / INVENTORY 库存 / SYSTEM 系统")
    private String msgType;

    @Schema(description = "类型文案")
    private String msgTypeText;

    @Schema(description = "业务对象类型，如 ORDER / PRODUCT")
    private String bizType;

    @Schema(description = "业务对象 ID")
    private Long bizId;

    @Schema(description = "「去处理」跳转的前端路由")
    private String bizUrl;

    @Schema(description = "发送人姓名，系统消息为空")
    private String senderName;

    @Schema(description = "优先级：N 普通 / U 紧急")
    private String priority;

    @Schema(description = "优先级文案")
    private String priorityText;

    @Schema(description = "状态：U 未读 / R 已读 / H 已处理")
    private String status;

    @Schema(description = "状态文案")
    private String statusText;

    @Schema(description = "是否未读")
    private Boolean unread;

    @Schema(description = "是否已处理")
    private Boolean handled;

    @Schema(description = "是否紧急")
    private Boolean urgent;

    @Schema(description = "首次读取时间")
    private LocalDateTime readTime;

    @Schema(description = "处理完成时间")
    private LocalDateTime handleTime;

    @Schema(description = "处理备注")
    private String handleRemark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
