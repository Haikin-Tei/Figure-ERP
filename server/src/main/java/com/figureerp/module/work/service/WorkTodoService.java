package com.figureerp.module.work.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.context.UserContext;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.work.converter.TodoConverter;
import com.figureerp.module.work.converter.TodoDict;
import com.figureerp.module.work.dto.TodoQuery;
import com.figureerp.module.work.dto.TodoSaveRequest;
import com.figureerp.module.work.entity.WorkTodo;
import com.figureerp.module.work.mapper.WorkTodoMapper;
import com.figureerp.module.work.vo.TodayTodoVO;
import com.figureerp.module.work.vo.TodoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 个人待办 / 今日工作计划的查询与维护。
 * <p>
 * 两条硬规则：
 * <ul>
 *   <li>所有查询都带 {@code user_id = 当前登录用户}，翻不到别人的待办；</li>
 *   <li>写操作先按 id 查出来再比对归属，别人的待办直接 403，而不是静默改别人的数据。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkTodoService {

    private final WorkTodoMapper mapper;

    /* ================= 查询 ================= */

    /**
     * 工作台首屏：今天的待办 + 今日统计 + 逾期数。
     * <p>
     * 一个接口把统计和列表都返回，前端首屏只发一次请求；
     * 逾期数是「日期早于今天且仍未完成」，单独统计一次，不含在今天列表里。
     */
    public TodayTodoVO today() {
        Long uid = UserContext.requireId();
        LocalDate today = LocalDate.now();

        List<WorkTodo> items = mapper.selectList(Wrappers.<WorkTodo>lambdaQuery()
                .eq(WorkTodo::getUserId, uid)
                .eq(WorkTodo::getPlanDate, today));

        Long overdue = mapper.selectCount(Wrappers.<WorkTodo>lambdaQuery()
                .eq(WorkTodo::getUserId, uid)
                .eq(WorkTodo::getStatus, TodoDict.STATUS_PENDING)
                .lt(WorkTodo::getPlanDate, today));

        long done = items.stream()
                .filter(t -> TodoDict.STATUS_DONE.equals(t.getStatus()))
                .count();
        long total = items.size();
        long pending = total - done;

        // 未完成在前（先做要紧的），同状态按优先级 / 排序号；
        // 排序号打平时让新建的排在前面（id 倒序），刚记下的事不会沉到列表底部
        List<WorkTodo> sorted = items.stream()
                .sorted(Comparator
                        .comparingInt((WorkTodo t) -> TodoDict.STATUS_DONE.equals(t.getStatus()) ? 1 : 0)
                        .thenComparingInt(t -> TodoDict.priorityWeight(t.getPriority()))
                        .thenComparingInt(t -> t.getSortNo() == null ? 0 : t.getSortNo())
                        .thenComparing(WorkTodo::getId, Comparator.reverseOrder()))
                .toList();

        TodayTodoVO vo = new TodayTodoVO();
        vo.setDate(today);
        vo.setWeekdayText(today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.SIMPLIFIED_CHINESE));
        vo.setTotal(total);
        vo.setDone(done);
        vo.setPending(pending);
        vo.setOverdue(overdue == null ? 0L : overdue);
        vo.setRate(total == 0 ? 0 : (int) Math.round(done * 100.0 / total));
        vo.setItems(sorted.stream().map(t -> TodoConverter.toVO(t, today)).toList());
        return vo;
    }

    /** 历史待办分页：按日期区间 / 状态 / 优先级 / 关键字过滤 */
    public PageResult<TodoVO> page(TodoQuery q) {
        Long uid = UserContext.requireId();
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<WorkTodo> w = Wrappers.<WorkTodo>lambdaQuery()
                .eq(WorkTodo::getUserId, uid);

        if (StringUtils.hasText(q.getStatus())) {
            w.eq(WorkTodo::getStatus, q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getPriority())) {
            w.eq(WorkTodo::getPriority, q.getPriority().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            w.and(x -> x.like(WorkTodo::getTitle, kw).or().like(WorkTodo::getContent, kw));
        }
        if (q.getStartDate() != null) {
            w.ge(WorkTodo::getPlanDate, q.getStartDate());
        }
        if (q.getEndDate() != null) {
            w.le(WorkTodo::getPlanDate, q.getEndDate());
        }

        // 日期倒序（最近的在上面），同一天里未完成在前、再按排序号；同序时新建的在前
        w.last("ORDER BY plan_date DESC, FIELD(status, 'P', 'D'), sort_no ASC, id DESC");

        Page<WorkTodo> page = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return PageResult.of(page, t -> TodoConverter.toVO(t, today));
    }

    public TodoVO detail(Long id) {
        return TodoConverter.toVO(getOwned(id));
    }

    /* ================= 维护 ================= */

    public Long create(TodoSaveRequest req) {
        Long uid = UserContext.requireId();

        WorkTodo t = new WorkTodo();
        t.setUserId(uid);
        t.setTitle(requireTitle(req.getTitle()));
        t.setContent(blankToNull(req.getContent()));
        t.setPlanDate(req.getPlanDate() == null ? LocalDate.now() : req.getPlanDate());
        t.setPriority(normalizePriority(req.getPriority()));
        t.setStatus(TodoDict.STATUS_PENDING);
        t.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());

        mapper.insert(t);
        log.info("新增待办: id={}, userId={}, title={}", t.getId(), uid, t.getTitle());
        return t.getId();
    }

    public void update(Long id, TodoSaveRequest req) {
        WorkTodo existing = getOwned(id);

        existing.setTitle(requireTitle(req.getTitle()));
        existing.setContent(blankToNull(req.getContent()));
        if (req.getPlanDate() != null) {
            existing.setPlanDate(req.getPlanDate());
        }
        existing.setPriority(normalizePriority(req.getPriority()));
        if (req.getSortNo() != null) {
            existing.setSortNo(req.getSortNo());
        }

        mapper.updateById(existing);
        log.info("编辑待办: id={}, title={}", id, existing.getTitle());
    }

    /** 勾选 / 取消勾选：D 时记完成时间，改回 P 时清空 */
    public void updateStatus(Long id, String status) {
        if (!TodoDict.isValidStatus(status)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "待办状态只能是 P 或 D");
        }
        WorkTodo existing = getOwned(id);

        existing.setStatus(status);
        existing.setFinishTime(TodoDict.STATUS_DONE.equals(status)
                ? LocalDateTime.now().withNano(0)
                : null);

        mapper.updateById(existing);
        log.info("待办状态变更: id={}, status={}", id, status);
    }

    public void delete(Long id) {
        getOwned(id);
        mapper.deleteById(id);
        log.info("删除待办: id={}", id);
    }

    /* ================= 内部 ================= */

    /** 按 id 取自己的待办：不存在 1001，别人的 403 */
    private WorkTodo getOwned(Long id) {
        WorkTodo t = mapper.selectById(id);
        if (t == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "待办不存在或已被删除");
        }
        if (!t.getUserId().equals(UserContext.requireId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己的待办");
        }
        return t;
    }

    private String requireTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请填写待办标题");
        }
        String trimmed = title.trim();
        if (trimmed.length() > 120) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "标题不能超过 120 个字符");
        }
        return trimmed;
    }

    private String normalizePriority(String priority) {
        if (!StringUtils.hasText(priority)) {
            return TodoDict.PRIORITY_NORMAL;
        }
        String code = priority.trim();
        if (!TodoDict.isValidPriority(code)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "优先级只能是 H、N 或 L");
        }
        return code;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
