package com.figureerp.module.work.converter;

import com.figureerp.module.work.entity.WorkTodo;
import com.figureerp.module.work.vo.TodoVO;

import java.time.LocalDate;

/**
 * 待办实体 → 出参。
 * <p>
 * 除了字段拷贝，顺手把「是否完成 / 是否逾期」算成布尔值，
 * 前端勾选框、红色逾期标记不用再写一串字符串比较。
 */
public final class TodoConverter {

    private TodoConverter() {
    }

    public static TodoVO toVO(WorkTodo t) {
        return toVO(t, LocalDate.now());
    }

    /**
     * @param today 传入「今天」而不是每行现取，避免批量转换时跨过零点导致前后行判断不一致
     */
    public static TodoVO toVO(WorkTodo t, LocalDate today) {
        TodoVO vo = new TodoVO();
        vo.setId(t.getId());
        vo.setTitle(t.getTitle());
        vo.setContent(t.getContent());
        vo.setPlanDate(t.getPlanDate());
        vo.setPriority(t.getPriority());
        vo.setPriorityText(TodoDict.priorityText(t.getPriority()));
        vo.setStatus(t.getStatus());
        vo.setStatusText(TodoDict.statusText(t.getStatus()));
        vo.setDone(TodoDict.STATUS_DONE.equals(t.getStatus()));
        vo.setOverdue(TodoDict.STATUS_PENDING.equals(t.getStatus())
                && t.getPlanDate() != null
                && t.getPlanDate().isBefore(today));
        vo.setFinishTime(t.getFinishTime());
        vo.setSortNo(t.getSortNo());
        vo.setCreateTime(t.getCreateTime());
        return vo;
    }
}
