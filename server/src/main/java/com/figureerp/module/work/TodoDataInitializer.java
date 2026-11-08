package com.figureerp.module.work;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.common.constant.UserRole;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import com.figureerp.module.work.converter.TodoDict;
import com.figureerp.module.work.entity.WorkTodo;
import com.figureerp.module.work.mapper.WorkTodoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 首次启动时给管理员生成几条演示待办，让「工作台」一上线就有内容可看。
 * <p>
 * 只在待办表为空时执行；真实使用后（哪怕是清空）都不会再自动补数据。
 * 数据覆盖三种优先级与两种状态，另外故意留一条「昨天未完成」，
 * 用来演示顶部的「逾期未完成」提示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.init-todo.enabled", havingValue = "true", matchIfMissing = true)
public class TodoDataInitializer implements ApplicationRunner {

    private final WorkTodoMapper todoMapper;
    private final SysUserMapper userMapper;

    /**
     * @param dayOffset 相对今天的天数：0 今天，-1 昨天（用于造逾期数据）
     * @param done      是否已完成
     */
    private record Seed(String title, String content, String priority, int dayOffset, boolean done) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = todoMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("待办已有 {} 条记录，跳过初始化", count);
                return;
            }

            SysUser owner = resolveOwner();
            if (owner == null) {
                log.warn("没有找到可用账号，跳过待办初始化（请先登录一次让账号落库）");
                return;
            }

            List<Seed> seeds = List.of(
                    new Seed("整理 11 月新品上架清单",
                            "按 IP 分类，标出限定款与补货款，下班前发给销售部", TodoDict.PRIORITY_HIGH, 0, false),
                    new Seed("核对上海库 A 区入库差异",
                            "上周入库单 RK20261101003 少 2 件，与仓管一起复核", TodoDict.PRIORITY_NORMAL, 0, false),
                    new Seed("回复销售部限定款配货邮件", null, TodoDict.PRIORITY_NORMAL, 0, false),
                    new Seed("检查货架标签与系统是否一致", null, TodoDict.PRIORITY_LOW, 0, true),
                    new Seed("提交 10 月库存周转报表", "已上传至共享盘", TodoDict.PRIORITY_NORMAL, 0, true),
                    new Seed("跟进物流价格调整审批", "昨天提交的调价单还停在审批中", TodoDict.PRIORITY_HIGH, -1, false)
            );

            LocalDate today = LocalDate.now();
            LocalDateTime now = LocalDateTime.now().withNano(0);
            int index = 0;
            for (Seed s : seeds) {
                WorkTodo t = new WorkTodo();
                t.setUserId(owner.getId());
                t.setTitle(s.title());
                t.setContent(s.content());
                t.setPlanDate(today.plusDays(s.dayOffset()));
                t.setPriority(s.priority());
                t.setStatus(s.done() ? TodoDict.STATUS_DONE : TodoDict.STATUS_PENDING);
                t.setSortNo(index * 10);
                // 已完成的时间造得比现在早一点，看起来像上午已经处理完
                t.setFinishTime(s.done() ? now.minusHours(2) : null);
                todoMapper.insert(t);
                index++;
            }
            log.warn("已为账号 {} 生成 {} 条演示待办", owner.getUsername(), seeds.size());
        } catch (Exception e) {
            // 表没建时不影响应用启动，只提示怎么处理
            log.error("初始化待办数据失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }

    /** 优先 admin 账号，其次任一管理员，最后退回第一个未删除的账号 */
    private SysUser resolveOwner() {
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
