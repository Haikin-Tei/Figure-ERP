package com.figureerp.module.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.context.UserContext;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.message.converter.MessageConverter;
import com.figureerp.module.message.converter.MessageDict;
import com.figureerp.module.message.dto.MessagePush;
import com.figureerp.module.message.dto.MessageQuery;
import com.figureerp.module.message.dto.MessageSendRequest;
import com.figureerp.module.message.entity.SysMessage;
import com.figureerp.module.message.mapper.SysMessageMapper;
import com.figureerp.module.message.vo.MessageSummaryVO;
import com.figureerp.module.message.vo.MessageVO;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人消息的查询与处理。
 * <p>
 * 三条硬规则：
 * <ul>
 *   <li>所有查询/操作都限定 {@code receiver_id = 当前登录用户}，越权直接 403；</li>
 *   <li>未读数用 summary 一次性算好，前端只轮询这一个接口，不去拉整页数据；</li>
 *   <li>业务模块推消息统一走 {@link #push(MessagePush)}，不直接操作 Mapper。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final SysMessageMapper mapper;
    private final SysUserMapper userMapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<MessageVO> page(MessageQuery q) {
        Long uid = UserContext.requireId();
        LambdaQueryWrapper<SysMessage> w = Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getReceiverId, uid);

        if (StringUtils.hasText(q.getMsgType())) {
            w.eq(SysMessage::getMsgType, q.getMsgType().trim());
        }
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(SysMessage::getStatus, q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getPriority())) {
            w.eq(SysMessage::getPriority, q.getPriority().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            w.and(x -> x.like(SysMessage::getTitle, kw).or().like(SysMessage::getContent, kw));
        }

        // 最新的在上；同秒创建时用 id 兜底，保证分页顺序稳定不会重复/漏项
        w.orderByDesc(SysMessage::getCreateTime).orderByDesc(SysMessage::getId);

        Page<SysMessage> page = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return PageResult.of(page, MessageConverter::toVO);
    }

    /** 未读汇总：右上角红点、抽屉里的分类计数都用它 */
    public MessageSummaryVO summary() {
        Long uid = UserContext.requireId();

        // 只取统计需要的三列，消息正文可能很长，不参与统计就不查出来
        List<SysMessage> list = mapper.selectList(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getReceiverId, uid)
                .select(SysMessage::getMsgType, SysMessage::getStatus, SysMessage::getPriority));

        Map<String, MessageSummaryVO.TypeCount> byType = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : MessageDict.typeEntries()) {
            MessageSummaryVO.TypeCount tc = new MessageSummaryVO.TypeCount();
            tc.setType(e.getKey());
            tc.setTypeText(e.getValue());
            tc.setTotal(0L);
            tc.setUnread(0L);
            byType.put(e.getKey(), tc);
        }

        long unread = 0;
        long urgentUnread = 0;
        long handled = 0;
        for (SysMessage m : list) {
            boolean isUnread = MessageDict.STATUS_UNREAD.equals(m.getStatus());

            MessageSummaryVO.TypeCount tc = byType.get(m.getMsgType());
            if (tc != null) {
                tc.setTotal(tc.getTotal() + 1);
                if (isUnread) {
                    tc.setUnread(tc.getUnread() + 1);
                }
            }

            if (isUnread) {
                unread++;
                if (MessageDict.PRIORITY_URGENT.equals(m.getPriority())) {
                    urgentUnread++;
                }
            } else if (MessageDict.STATUS_HANDLED.equals(m.getStatus())) {
                handled++;
            }
        }

        MessageSummaryVO vo = new MessageSummaryVO();
        vo.setTotal((long) list.size());
        vo.setUnread(unread);
        vo.setUrgentUnread(urgentUnread);
        vo.setHandled(handled);
        vo.setByType(List.copyOf(byType.values()));
        return vo;
    }

    /** 详情：点开即视为已读（消息中心的通用交互，省掉一次额外请求） */
    @Transactional(rollbackFor = Exception.class)
    public MessageVO detail(Long id) {
        SysMessage m = getOwned(id);
        if (MessageDict.STATUS_UNREAD.equals(m.getStatus())) {
            LocalDateTime now = nowToSecond();
            SysMessage upd = new SysMessage();
            upd.setId(id);
            upd.setStatus(MessageDict.STATUS_READ);
            upd.setReadTime(now);
            mapper.updateById(upd);

            m.setStatus(MessageDict.STATUS_READ);
            m.setReadTime(now);
        }
        return MessageConverter.toVO(m);
    }

    /* ================= 状态变更 ================= */

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id) {
        SysMessage m = getOwned(id);
        if (!MessageDict.STATUS_UNREAD.equals(m.getStatus())) {
            return; // 已读/已处理重复点不报错，保持接口幂等
        }
        SysMessage upd = new SysMessage();
        upd.setId(id);
        upd.setStatus(MessageDict.STATUS_READ);
        upd.setReadTime(nowToSecond());
        mapper.updateById(upd);
    }

    /**
     * 全部标为已读，可只处理某一类型。
     *
     * @return 实际更新条数，前端据此提示「已标记 N 条」
     */
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(String msgType) {
        Long uid = UserContext.requireId();
        LocalDateTime now = nowToSecond();

        LambdaUpdateWrapper<SysMessage> u = Wrappers.<SysMessage>lambdaUpdate()
                .eq(SysMessage::getReceiverId, uid)
                .eq(SysMessage::getStatus, MessageDict.STATUS_UNREAD)
                .set(SysMessage::getStatus, MessageDict.STATUS_READ)
                .set(SysMessage::getReadTime, now)
                // 走 UpdateWrapper 时实体填充不生效，update_time 需要手动写
                .set(SysMessage::getUpdateTime, now);

        if (StringUtils.hasText(msgType)) {
            u.eq(SysMessage::getMsgType, msgType.trim());
        }
        return mapper.update(null, u);
    }

    /** 处理完成：状态置为已处理并记录处理备注 */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, String remark) {
        SysMessage m = getOwned(id);
        LocalDateTime now = nowToSecond();

        SysMessage upd = new SysMessage();
        upd.setId(id);
        upd.setStatus(MessageDict.STATUS_HANDLED);
        upd.setHandleTime(now);
        if (StringUtils.hasText(remark)) {
            upd.setHandleRemark(remark.trim());
        }
        // 没读过就直接处理的，把读取时间补上，避免出现「已处理但从未读」的矛盾数据
        if (m.getReadTime() == null) {
            upd.setReadTime(now);
        }
        mapper.updateById(upd);
        log.info("消息处理完成: id={} receiver={}", id, m.getReceiverId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getOwned(id);
        mapper.deleteById(id);
    }

    /* ================= 推送 ================= */

    /** 手工发送（管理员 / 主管） */
    @Transactional(rollbackFor = Exception.class)
    public Long send(MessageSendRequest req) {
        SysUser sender = currentUserService.requireManager("发送消息");

        SysUser receiver = userMapper.selectById(req.getReceiverId());
        if (receiver == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "接收人不存在或已被删除");
        }
        return push(MessagePush.from(req, sender.getId(), sender.getRealName()));
    }

    /**
     * 统一的消息推送入口，供后续审批流、订单、库存预警等模块直接调用。
     * <p>
     * 这里不做权限校验（调用方通常是系统流程），只做数据兜底：类型/优先级非法时按默认值处理，
     * 保证一条脏参数不会把消息中心写坏。
     *
     * @return 新消息 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long push(MessagePush p) {
        if (p.receiverId() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "缺少消息接收人");
        }
        if (!StringUtils.hasText(p.title())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "消息标题不能为空");
        }

        SysMessage m = new SysMessage();
        m.setReceiverId(p.receiverId());
        m.setTitle(p.title().trim());
        m.setContent(blankToNull(p.content()));
        m.setMsgType(MessageDict.isValidType(p.msgType()) ? p.msgType() : MessageDict.TYPE_SYSTEM);
        m.setBizType(blankToNull(p.bizType()));
        m.setBizId(p.bizId());
        m.setBizUrl(blankToNull(p.bizUrl()));
        m.setSenderId(p.senderId());
        m.setSenderName(StringUtils.hasText(p.senderName()) ? p.senderName().trim() : "系统");
        m.setPriority(MessageDict.isValidPriority(p.priority()) ? p.priority() : MessageDict.PRIORITY_NORMAL);
        m.setStatus(MessageDict.STATUS_UNREAD);
        mapper.insert(m);

        log.info("推送消息: id={} type={} receiver={} title={}",
                m.getId(), m.getMsgType(), m.getReceiverId(), m.getTitle());
        return m.getId();
    }

    /* ================= 内部方法 ================= */

    /** 取消息并校验归属，查别人的消息一律 403 */
    private SysMessage getOwned(Long id) {
        SysMessage m = mapper.selectById(id);
        if (m == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "消息不存在或已被删除");
        }
        if (!m.getReceiverId().equals(UserContext.requireId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人的消息");
        }
        return m;
    }

    /** 截到秒，避免序列化出 2026-11-06T07:28:35.62477 这种带纳秒的时间，前端 Date 解析不统一 */
    private static LocalDateTime nowToSecond() {
        return LocalDateTime.now().withNano(0);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
