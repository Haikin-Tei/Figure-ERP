package com.figureerp.module.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 个人消息（右上角用户菜单 → 个人消息）。
 * <p>
 * 一条记录 = 一条发给某个人的站内通知。消息按接收人隔离，
 * 查询时永远带 {@code receiver_id = 当前登录用户}，不能跨人查看。
 * <p>
 * bizUrl 是「去处理」要跳转的前端路由，例如审批消息指向审批页面、订单消息指向订单详情，
 * 为空表示纯通知（只需要读，不需要办）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message")
public class SysMessage extends BaseEntity {

    /** 标题，如「有新订单待审核」 */
    private String title;

    /** 正文，可写多行 */
    private String content;

    /** 类型：APPROVAL 审批 / ORDER 订单 / INVENTORY 库存 / SYSTEM 系统，见 MessageDict */
    private String msgType;

    /** 业务对象类型，如 PRODUCT / ORDER / WAREHOUSE */
    private String bizType;

    /** 业务对象 ID，用于从消息直接定位单据 */
    private Long bizId;

    /** 「去处理」跳转的前端路由 */
    private String bizUrl;

    /** 接收人（sys_user.id） */
    private Long receiverId;

    /** 发送人（sys_user.id），系统自动产生时为空 */
    private Long senderId;

    /** 发送人姓名快照，避免发送人账号被删后消息里显示空白 */
    private String senderName;

    /** 优先级：N 普通 / U 紧急 */
    private String priority;

    /** 状态：U 未读 / R 已读 / H 已处理 */
    private String status;

    /** 首次读取时间 */
    private LocalDateTime readTime;

    /** 处理完成时间 */
    private LocalDateTime handleTime;

    /** 处理备注 / 处理结果 */
    private String handleRemark;

    /** 备注 */
    private String remark;
}
