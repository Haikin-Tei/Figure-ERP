package com.figureerp.module.attendance.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.attendance.dto.AttendanceQuery;
import com.figureerp.module.attendance.dto.AttendanceSaveRequest;
import com.figureerp.module.attendance.service.AttendanceService;
import com.figureerp.module.attendance.vo.AttendanceSummaryVO;
import com.figureerp.module.attendance.vo.AttendanceVO;
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
 * 员工考勤接口（员工权限管理 → 员工考勤记录 页面使用）。
 * <p>
 * 考勤是手动录入的：员工填自己的，主管 / 人事可以代录全部员工；
 * 数据范围与权限校验都在 {@link AttendanceService} 里做。
 */
@Tag(name = "员工考勤", description = "考勤记录：上下班时间、迟到早退判定、月度统计")
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "分页查询考勤", description = "支持姓名/账号关键字、员工、上下班状态、日期区间过滤")
    @GetMapping("/page")
    public Result<PageResult<AttendanceVO>> page(@Valid AttendanceQuery q) {
        return Result.success(attendanceService.page(q));
    }

    @Operation(summary = "考勤详情")
    @GetMapping("/{id}")
    public Result<AttendanceVO> detail(@PathVariable Long id) {
        return Result.success(attendanceService.detail(id));
    }

    @Operation(summary = "本月考勤统计", description = "记录数 / 迟到 / 早退 / 未打卡 / 涉及员工数")
    @GetMapping("/summary")
    public Result<AttendanceSummaryVO> summary() {
        return Result.success(attendanceService.summary());
    }

    @Operation(summary = "考勤字典", description = "上班、下班状态，供前端筛选下拉使用")
    @GetMapping("/dicts")
    public Result<Map<String, Object>> dicts() {
        return Result.success(attendanceService.dicts());
    }

    @Operation(summary = "新增考勤记录", description = "一人一天一条；销售岗只录上班时间")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AttendanceSaveRequest req) {
        return Result.success("录入成功", attendanceService.create(req));
    }

    @Operation(summary = "编辑考勤记录", description = "不能更换员工；状态与工时由后端重算")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AttendanceSaveRequest req) {
        attendanceService.update(id, req);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "删除考勤记录", description = "逻辑删除，本人或主管 / 人事可删")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        attendanceService.delete(id);
        return Result.success("删除成功", null);
    }
}
