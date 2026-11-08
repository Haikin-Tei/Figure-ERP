package com.figureerp.module.stock.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.stock.dto.InboundQuery;
import com.figureerp.module.stock.dto.InboundSaveRequest;
import com.figureerp.module.stock.service.InboundService;
import com.figureerp.module.stock.vo.InboundOrderVO;
import com.figureerp.module.stock.vo.InboundSummaryVO;
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
 * 入库记录接口（库存管理 → 入库记录 页面使用）。
 * <p>
 * 查询对所有登录用户开放；新增/编辑/删除/确认/作废需要管理员或主管，
 * 权限校验在 {@link InboundService} 内部完成。
 */
@Tag(name = "入库记录", description = "入库单：单号/仓库/区域/货架/供应商/手办明细的查询与维护")
@RestController
@RequestMapping("/api/inbound")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inboundService;

    @Operation(summary = "分页查询入库单", description = "支持单号/供应商/经办人/手办名称模糊，按仓库/状态/类型/日期区间过滤")
    @GetMapping("/page")
    public Result<PageResult<InboundOrderVO>> page(@Valid InboundQuery q) {
        return Result.success(inboundService.page(q));
    }

    @Operation(summary = "入库单详情", description = "返回主表 + 手办明细")
    @GetMapping("/{id}")
    public Result<InboundOrderVO> detail(@PathVariable Long id) {
        return Result.success(inboundService.detail(id));
    }

    @Operation(summary = "入库记录统计", description = "待入库单数 + 本月已入库的单数/件数/金额")
    @GetMapping("/summary")
    public Result<InboundSummaryVO> summary() {
        return Result.success(inboundService.summary());
    }

    @Operation(summary = "入库字典", description = "入库类型与状态，供前端下拉框使用")
    @GetMapping("/dicts")
    public Result<Map<String, Object>> dicts() {
        return Result.success(inboundService.dicts());
    }

    @Operation(summary = "新增入库单", description = "单号留空自动生成，状态为待入库，明细至少一条")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody InboundSaveRequest req) {
        return Result.success("新增成功", inboundService.create(req));
    }

    @Operation(summary = "编辑入库单", description = "仅待入库状态可编辑，明细整体替换")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody InboundSaveRequest req) {
        inboundService.update(id, req);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "删除入库单", description = "仅待入库状态可删；逻辑删除，明细一并删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        inboundService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "确认入库", description = "待入库 → 已入库")
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        inboundService.confirm(id);
        return Result.success("已确认入库", null);
    }

    @Operation(summary = "作废入库单", description = "待入库 / 已入库 → 已作废")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        inboundService.cancel(id);
        return Result.success("已作废", null);
    }
}
