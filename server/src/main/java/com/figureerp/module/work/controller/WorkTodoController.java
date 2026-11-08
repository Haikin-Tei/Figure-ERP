package com.figureerp.module.work.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.work.converter.TodoDict;
import com.figureerp.module.work.dto.TodoQuery;
import com.figureerp.module.work.dto.TodoSaveRequest;
import com.figureerp.module.work.service.WorkTodoService;
import com.figureerp.module.work.vo.TodayTodoVO;
import com.figureerp.module.work.vo.TodoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人待办接口（页面：工作台 → 今日工作计划）。
 * <p>
 * 权限：所有接口只需登录；数据按当前登录用户隔离，Service 里强制校验归属，
 * 不需要「主管才能建待办」这类限制 —— 待办是每个人自己的记事本。
 */
@Tag(name = "个人待办", description = "今日工作计划：今日汇总、分页、增删改与勾选完成")
@RestController
@RequestMapping("/api/todo")
@RequiredArgsConstructor
public class WorkTodoController {

    private final WorkTodoService todoService;

    @Operation(summary = "今日待办汇总", description = "工作台首屏：今日列表 + 总数/完成/未完成/逾期/完成率，一次请求拿全")
    @GetMapping("/today")
    public Result<TodayTodoVO> today() {
        return Result.success(todoService.today());
    }

    @Operation(summary = "分页查询我的待办", description = "可按计划日期区间、状态、优先级过滤，关键字匹配标题与说明")
    @GetMapping("/page")
    public Result<PageResult<TodoVO>> page(@Valid TodoQuery q) {
        return Result.success(todoService.page(q));
    }

    @Operation(summary = "待办字典", description = "优先级与状态的编码/文案，前端下拉直接取，不用写死")
    @GetMapping("/dicts")
    public Result<Map<String, List<Map<String, String>>>> dicts() {
        Map<String, List<Map<String, String>>> data = new LinkedHashMap<>();
        data.put("priorities", entries(TodoDict.priorityEntries()));
        data.put("statuses", List.of(
                entry(TodoDict.STATUS_PENDING, TodoDict.statusText(TodoDict.STATUS_PENDING)),
                entry(TodoDict.STATUS_DONE, TodoDict.statusText(TodoDict.STATUS_DONE))
        ));
        return Result.success(data);
    }

    @Operation(summary = "待办详情")
    @GetMapping("/{id}")
    public Result<TodoVO> detail(@PathVariable Long id) {
        return Result.success(todoService.detail(id));
    }

    @Operation(summary = "新增待办", description = "计划日期不传默认今天，优先级不传默认普通")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TodoSaveRequest req) {
        return Result.success("已添加到今日计划", todoService.create(req));
    }

    @Operation(summary = "编辑待办", description = "全量提交；状态不在这里改，勾选走 status 接口")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TodoSaveRequest req) {
        todoService.update(id, req);
        return Result.success("已保存", null);
    }

    @Operation(summary = "勾选 / 取消完成", description = "status=P 待办，status=D 已完成（记完成时间）")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        todoService.updateStatus(id, status);
        return Result.success(TodoDict.STATUS_DONE.equals(status) ? "已完成" : "已恢复为待办", null);
    }

    @Operation(summary = "删除待办", description = "逻辑删除，只能删自己的待办")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        todoService.delete(id);
        return Result.success("已删除", null);
    }

    private List<Map<String, String>> entries(List<Map.Entry<String, String>> source) {
        return source.stream()
                .map(e -> entry(e.getKey(), e.getValue()))
                .toList();
    }

    private Map<String, String> entry(String code, String text) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("text", text);
        return item;
    }
}
