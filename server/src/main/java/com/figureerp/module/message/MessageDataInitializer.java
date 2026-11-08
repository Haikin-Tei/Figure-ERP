package com.figureerp.module.message;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.common.constant.UserRole;
import com.figureerp.module.message.converter.MessageDict;
import com.figureerp.module.message.entity.SysMessage;
import com.figureerp.module.message.mapper.SysMessageMapper;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首次启动时给管理员生成一批演示消息，让「个人消息」一上线就有内容可看。
 * <p>
 * 只在消息表为空时执行；真实使用后（哪怕是清空）都不会再自动补数据。
 * 演示消息覆盖三种状态与四个类型，并指向现有的业务页面，方便直接点「去处理」验证跳转。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.init-message.enabled", havingValue = "true", matchIfMissing = true)
public class MessageDataInitializer implements ApplicationRunner {

    private final SysMessageMapper messageMapper;
    private final SysUserMapper userMapper;

    /**
     * @param hoursAgo 相对当前时间的小时数，用于制造「几小时前 / 昨天」的真实时间分布
     */
    private record Seed(String title, String content, String msgType, String priority, String status,
                        String bizType, String bizUrl, int hoursAgo, String handleRemark) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = messageMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("消息已有 {} 条记录，跳过初始化", count);
                return;
            }

            SysUser receiver = resolveReceiver();
            if (receiver == null) {
                log.warn("没有找到可用账号，跳过消息初始化（请先登录一次让账号落库）");
                return;
            }

            List<Seed> seeds = List.of(
                    new Seed("入库单待你审批",
                            "入库单 RK20261106001 已提交，共 3 个 SKU、86 件，等待你审批后安排上架。",
                            MessageDict.TYPE_APPROVAL, MessageDict.PRIORITY_URGENT, MessageDict.STATUS_UNREAD,
                            "INBOUND", "/stock/inbound", 1, null),
                    new Seed("商品资料变更待审核",
                            "「GSC 初音未来 1/7 手办」提交了带盒重量修正（620g → 645g），请确认后生效。",
                            MessageDict.TYPE_APPROVAL, MessageDict.PRIORITY_NORMAL, MessageDict.STATUS_UNREAD,
                            "PRODUCT", "/product/list", 3, null),
                    new Seed("新订单待处理",
                            "订单 SO20261106007 已付款，商品为 2 个手办，请在 24 小时内安排出库。",
                            MessageDict.TYPE_ORDER, MessageDict.PRIORITY_URGENT, MessageDict.STATUS_UNREAD,
                            "ORDER", "/order/list", 5, null),
                    new Seed("库位容量预警",
                            "上海库 A-03-02 库位使用率已达 92%，建议把新到货品分流到 A-04 区域。",
                            MessageDict.TYPE_INVENTORY, MessageDict.PRIORITY_NORMAL, MessageDict.STATUS_UNREAD,
                            "WAREHOUSE", "/stock/location", 8, null),
                    new Seed("订单 SO20261105012 已发货",
                            "顺丰 SF1234567890 已揽收，预计明天 18:00 前送达，请关注签收状态。",
                            MessageDict.TYPE_ORDER, MessageDict.PRIORITY_NORMAL, MessageDict.STATUS_READ,
                            "ORDER", "/order/list", 27, null),
                    new Seed("仓库盘点任务已下发",
                            "北京库 11 月度盘点已开始，请在本周五前完成初盘并回填差异。",
                            MessageDict.TYPE_INVENTORY, MessageDict.PRIORITY_NORMAL, MessageDict.STATUS_HANDLED,
                            "WAREHOUSE", "/stock/location", 50, "已安排 3 人小组，周四完成初盘"),
                    new Seed("系统维护通知",
                            "本周日 02:00-04:00 进行数据库升级，期间系统只读，请提前保存工作。",
                            MessageDict.TYPE_SYSTEM, MessageDict.PRIORITY_NORMAL, MessageDict.STATUS_READ,
                            null, "/dashboard", 72, null),
                    new Seed("欢迎使用 Figure-ERP",
                            "「个人消息」会集中接收审批、订单、库存等提醒，点开可查看详情并直接跳转处理。",
                            MessageDict.TYPE_SYSTEM, MessageDict.PRIORITY_NORMAL, MessageDict.STATUS_HANDLED,
                            null, null, 96, "已阅读")
            );

            LocalDateTime now = LocalDateTime.now();
            for (Seed s : seeds) {
                LocalDateTime created = now.minusHours(s.hoursAgo());

                SysMessage m = new SysMessage();
                m.setTitle(s.title());
                m.setContent(s.content());
                m.setMsgType(s.msgType());
                m.setPriority(s.priority());
                m.setStatus(s.status());
                m.setBizType(s.bizType());
                m.setBizUrl(s.bizUrl());
                m.setReceiverId(receiver.getId());
                m.setSenderName("系统");
                // 手动赋值后 insertFill 不会再覆盖，这样能造出真实的时间分布
                m.setCreateTime(created);
                m.setUpdateTime(created);

                if (!MessageDict.STATUS_UNREAD.equals(s.status())) {
                    m.setReadTime(created.plusMinutes(12));
                }
                if (MessageDict.STATUS_HANDLED.equals(s.status())) {
                    m.setHandleTime(created.plusMinutes(35));
                    m.setHandleRemark(s.handleRemark());
                }
                messageMapper.insert(m);
            }
            log.warn("已为账号 {} 生成 {} 条演示消息", receiver.getUsername(), seeds.size());
        } catch (Exception e) {
            // 表没建时不影响应用启动，只提示怎么处理
            log.error("初始化消息数据失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }

    /** 优先 admin 账号，其次任一管理员，最后退回第一个未删除的账号 */
    private SysUser resolveReceiver() {
        SysUser admin = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, "admin")
                .last("LIMIT 1"));
        if (admin != null) {
            return admin;
        }
        SysUser adminRole = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, UserRole.ADMIN.getCode())
                .last("LIMIT 1"));
        if (adminRole != null) {
            return adminRole;
        }
        List<SysUser> any = userMapper.selectList(Wrappers.<SysUser>lambdaQuery().last("LIMIT 1"));
        return any.isEmpty() ? null : any.get(0);
    }
}
