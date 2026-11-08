package com.figureerp.module.message.converter;

import com.figureerp.module.message.entity.SysMessage;
import com.figureerp.module.message.vo.MessageVO;

/**
 * 消息实体 → 出参。
 * <p>
 * 除了字段拷贝，还顺手把「是否未读 / 是否已处理 / 是否紧急」算成布尔值，
 * 前端列表打红点、显示按钮时不用再写一串字符串比较。
 */
public final class MessageConverter {

    private MessageConverter() {
    }

    public static MessageVO toVO(SysMessage m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setTitle(m.getTitle());
        vo.setContent(m.getContent());
        vo.setMsgType(m.getMsgType());
        vo.setMsgTypeText(MessageDict.typeText(m.getMsgType()));
        vo.setBizType(m.getBizType());
        vo.setBizId(m.getBizId());
        vo.setBizUrl(m.getBizUrl());
        vo.setSenderName(m.getSenderName());
        vo.setPriority(m.getPriority());
        vo.setPriorityText(MessageDict.priorityText(m.getPriority()));
        vo.setStatus(m.getStatus());
        vo.setStatusText(MessageDict.statusText(m.getStatus()));
        vo.setUnread(MessageDict.STATUS_UNREAD.equals(m.getStatus()));
        vo.setHandled(MessageDict.STATUS_HANDLED.equals(m.getStatus()));
        vo.setUrgent(MessageDict.PRIORITY_URGENT.equals(m.getPriority()));
        vo.setReadTime(m.getReadTime());
        vo.setHandleTime(m.getHandleTime());
        vo.setHandleRemark(m.getHandleRemark());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }
}
