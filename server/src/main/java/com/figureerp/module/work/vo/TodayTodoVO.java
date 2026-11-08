package com.figureerp.module.work.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作台「今日工作计划」的整块数据。
 * <p>
 * 统计口径都按「当前登录用户 + 今天」：
 * <ul>
 *   <li>{@code total / done / pending}：今天的待办总数 / 已完成 / 未完成；</li>
 *   <li>{@code overdue}：日期早于今天且仍未完成的条数（不含今天），用来在顶部提示「有 N 项逾期」；</li>
 *   <li>{@code rate}：今天的完成率（0~100 的整数），前端进度条直接用。</li>
 * </ul>
 * 三项统计放同一个接口返回，工作台首屏只发一个请求就能画完。
 */
@Data
@Schema(description = "今日待办汇总")
public class TodayTodoVO {

    @Schema(description = "今天日期", example = "2026-11-07")
    private LocalDate date;

    @Schema(description = "星期文案", example = "星期六")
    private String weekdayText;

    @Schema(description = "今日待办总数")
    private Long total;

    @Schema(description = "今日已完成")
    private Long done;

    @Schema(description = "今日未完成")
    private Long pending;

    @Schema(description = "逾期未完成（计划日期早于今天）")
    private Long overdue;

    @Schema(description = "今日完成率，0~100")
    private Integer rate;

    @Schema(description = "今日待办列表，未完成的在前；同状态按优先级、排序号、创建顺序")
    private List<TodoVO> items;
}
