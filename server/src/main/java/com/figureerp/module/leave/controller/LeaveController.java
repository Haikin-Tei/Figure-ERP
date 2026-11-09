package com.figureerp.module.leave.controller;

import com.figureerp.common.dto.ApproveRequest;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.leave.dto.LeaveQuery;
import com.figureerp.module.leave.dto.LeaveSaveRequest;
import com.figureerp.module.leave.service.LeaveService;
import com.figureerp.module.leave.vo.LeaveSummaryVO;
import com.figureerp.module.leave.vo.LeaveVO;
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
 * 员工请假申请接口（员工权限管理 → 员工请假申请 页面使用）。
 * <p>
 * 员工提交自己的申请；主管级及以上可以代提、编辑、审批（不能审自己的单）；
 * 撤销只能本人操作。权限校验都在 {@link LeaveService} 里。
 */
@Tag(name = "员工请假", description = "请假申请：提交、撤销、审批、统计")
@RestController
@RequestMapping("/api/leave")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @Operation(summary = "分页查询请假单", description = "支持单号/姓名/事由关键字、类型、状态、日期区间过滤")
    @GetMapping("/page")
    public Result<PageResult<LeaveVO>> page(@Valid LeaveQuery q) {
        return Result.success(leaveService.page(q));
    }

    @Operation(summary = "请假单详情", description = "带 canApprove / canCancel 等按钮权限标记")
    @GetMapping("/{id}")
    public Result<LeaveVO> detail(@PathVariable Long id) {
        return Result.success(leaveService.detail(id));
    }

    @Operation(summary = "请假统计", description = "待审批单数 + 本月已通过的单数 / 天数 / 人数")
    @GetMapping("/summary")
    public Result<LeaveSummaryVO> summary() {
        return Result.success(leaveService.summary());
    }

    @Operation(summary = "请假字典", description = "请假类型与单据状态，供前端下拉框使用")
    @GetMapping("/dicts")
    public Result<Map<String, Object>> dicts() {
        return Result.success(leaveService.dicts());
    }

    @Operation(summary = "提交请假申请", description = "单号自动生成，天数由后端计算，状态为待审批")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody LeaveSaveRequest req) {
        return Result.success("提交成功", leaveService.create(req));
    }

    @Operation(summary = "编辑请假申请", description = "仅待审批可改；不能更换申请人")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody LeaveSaveRequest req) {
        leaveService.update(id, req);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "撤销请假申请", description = "仅本人 + 待审批")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        leaveService.cancel(id);
        return Result.success("已撤销", null);
    }

    @Operation(summary = "审批请假申请", description = "主管级及以上；不能审批自己提交的单；驳回要填原因")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest req) {
        leaveService.approve(id, req);
        return Result.success("审批完成", null);
    }

    @Operation(summary = "删除请假申请", description = "仅待审批可删，逻辑删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        leaveService.delete(id);
        return Result.success("删除成功", null);
    }
}
