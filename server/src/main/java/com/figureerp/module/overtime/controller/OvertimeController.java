package com.figureerp.module.overtime.controller;

import com.figureerp.common.dto.ApproveRequest;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.overtime.dto.OvertimeQuery;
import com.figureerp.module.overtime.dto.OvertimeSaveRequest;
import com.figureerp.module.overtime.service.OvertimeService;
import com.figureerp.module.overtime.vo.OvertimeSummaryVO;
import com.figureerp.module.overtime.vo.OvertimeVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 员工加班申请接口（员工权限管理 → 员工加班申请 页面使用）。
 * <p>
 * 与请假申请同一套规则：本人提交、主管审批（不可自审）、本人撤销。
 */
@Tag(name = "员工加班", description = "加班申请：提交、撤销、审批、统计")
@RestController
@RequestMapping("/api/overtime")
@RequiredArgsConstructor
public class OvertimeController {

    private final OvertimeService overtimeService;

    @Operation(summary = "分页查询加班单", description = "支持单号/姓名/事由关键字、类型、状态、日期区间过滤")
    @GetMapping("/page")
    public Result<PageResult<OvertimeVO>> page(@Valid OvertimeQuery q) {
        return Result.success(overtimeService.page(q));
    }

    @Operation(summary = "加班单详情", description = "带 canApprove / canCancel 等按钮权限标记")
    @GetMapping("/{id}")
    public Result<OvertimeVO> detail(@PathVariable Long id) {
        return Result.success(overtimeService.detail(id));
    }

    @Operation(summary = "加班统计", description = "待审批单数 + 本月已通过的单数 / 时长 / 人数")
    @GetMapping("/summary")
    public Result<OvertimeSummaryVO> summary() {
        return Result.success(overtimeService.summary());
    }

    @Operation(summary = "加班字典", description = "加班类型与单据状态，供前端下拉框使用")
    @GetMapping("/dicts")
    public Result<Map<String, Object>> dicts() {
        return Result.success(overtimeService.dicts());
    }

    @Operation(summary = "提交加班申请", description = "单号自动生成，时长由后端计算，状态为待审批")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody OvertimeSaveRequest req) {
        return Result.success("提交成功", overtimeService.create(req));
    }

    @Operation(summary = "编辑加班申请", description = "仅待审批可改；不能更换申请人")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody OvertimeSaveRequest req) {
        overtimeService.update(id, req);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "撤销加班申请", description = "仅本人 + 待审批")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        overtimeService.cancel(id);
        return Result.success("已撤销", null);
    }

    @Operation(summary = "审批加班申请", description = "主管级及以上；不能审批自己提交的单；驳回要填原因")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest req) {
        overtimeService.approve(id, req);
        return Result.success("审批完成", null);
    }

    @Operation(summary = "删除加班申请", description = "仅待审批可删，逻辑删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        overtimeService.delete(id);
        return Result.success("删除成功", null);
    }
}
