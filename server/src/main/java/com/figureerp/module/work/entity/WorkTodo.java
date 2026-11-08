package com.figureerp.module.work.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 个人待办 / 今日工作计划（页面：工作台 → 今日工作计划）。
 * <p>
 * 一条记录 = 某个员工给自己排的一件事。数据按 {@code user_id} 隔离，
 * 查询与写操作都只认当前登录用户，不存在「帮别人勾掉待办」的入口。
 * <p>
 * 与个人消息（sys_message）的分工：消息是别人推给你的，待办是你自己记的，
 * 工作台上并排展示但互不生成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_todo")
public class WorkTodo extends BaseEntity {

    /** 归属员工（sys_user.id） */
    private Long userId;

    /** 待办标题，如「整理 11 月新品上架清单」 */
    private String title;

    /**
     * 补充说明，可空。
     * <p>
     * {@code updateStrategy = ALWAYS}：MyBatis-Plus 默认只更新非 null 字段，
     * 不加这个注解「把说明清空」会被静默忽略（库里还是旧值）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String content;

    /** 计划日期，工作台只展示当天 */
    private LocalDate planDate;

    /** 优先级：H 高 / N 普通 / L 低，见 TodoDict */
    private String priority;

    /** 状态：P 待办 / D 已完成 */
    private String status;

    /**
     * 完成时间；状态回到待办时清空。
     * <p>
     * 同样需要 ALWAYS：清空就是写 NULL，默认策略会跳过 null 导致「取消完成」后还留着上次的完成时间。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime finishTime;

    /** 排序号，越小越靠前 */
    private Integer sortNo;
}
