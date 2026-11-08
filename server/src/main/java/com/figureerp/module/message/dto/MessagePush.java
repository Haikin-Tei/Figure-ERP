package com.figureerp.module.message.dto;

import com.figureerp.module.message.converter.MessageDict;

/**
 * 内部推送消息的载体（不经过 HTTP，各业务模块直接调 {@code MessageService.push}）。
 * <p>
 * 为什么单独定义而不是直接用 {@link MessageSendRequest}：
 * 入参对象带 Web 校验注解、也不该承载「发送人」这类服务端信息；
 * 这个 record 是模块之间约定的推送契约，后续订单/入库审核直接构造它即可。
 * <p>
 * 用法：
 * <pre>{@code
 * messageService.push(MessagePush.system(userId, "入库单待审批", "单号 RK20261106001", MessageDict.TYPE_APPROVAL, "/stock/inbound"));
 * }</pre>
 */
public record MessagePush(
        Long receiverId,
        String title,
        String content,
        String msgType,
        String bizType,
        Long bizId,
        String bizUrl,
        String priority,
        Long senderId,
        String senderName
) {

    /** 系统自动产生的通知：无发送人、优先级普通 */
    public static MessagePush system(Long receiverId, String title, String content, String msgType, String bizUrl) {
        return new MessagePush(receiverId, title, content, msgType,
                null, null, bizUrl, MessageDict.PRIORITY_NORMAL, null, "系统");
    }

    /** 系统自动产生 + 指向具体单据 */
    public static MessagePush ofBiz(Long receiverId, String title, String content, String msgType,
                                    String bizType, Long bizId, String bizUrl) {
        return new MessagePush(receiverId, title, content, msgType,
                bizType, bizId, bizUrl, MessageDict.PRIORITY_NORMAL, null, "系统");
    }

    /** 由手工发送接口转换而来，发送人取当前登录用户 */
    public static MessagePush from(MessageSendRequest req, Long senderId, String senderName) {
        return new MessagePush(req.getReceiverId(), req.getTitle(), req.getContent(),
                req.getMsgType(), req.getBizType(), req.getBizId(), req.getBizUrl(),
                req.getPriority(), senderId, senderName);
    }
}
