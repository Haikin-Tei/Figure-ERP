package com.figureerp.module.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 消息统计（右上角菜单的未读红点 + 抽屉顶部的小结）。
 * <p>
 * byType 会返回所有类型（含未读为 0 的），前端 Tab 直接按顺序渲染，不用再补空项。
 */
@Data
@Schema(description = "消息统计")
public class MessageSummaryVO {

    @Schema(description = "消息总数")
    private Long total;

    @Schema(description = "未读数")
    private Long unread;

    @Schema(description = "紧急且未读的条数")
    private Long urgentUnread;

    @Schema(description = "已处理条数")
    private Long handled;

    @Schema(description = "按类型统计")
    private List<TypeCount> byType;

    @Data
    @Schema(description = "按类型统计项")
    public static class TypeCount {

        @Schema(description = "类型编码")
        private String type;

        @Schema(description = "类型文案")
        private String typeText;

        @Schema(description = "该类型总数")
        private Long total;

        @Schema(description = "该类型未读数")
        private Long unread;
    }
}
