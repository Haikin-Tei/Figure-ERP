package com.figureerp.module.logistics.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.logistics.dto.BatchPriceRequest;
import com.figureerp.module.logistics.dto.LogisticsPriceQuery;
import com.figureerp.module.logistics.dto.LogisticsPriceSaveRequest;
import com.figureerp.module.logistics.dto.QuoteQuery;
import com.figureerp.module.logistics.service.LogisticsPriceService;
import com.figureerp.module.logistics.vo.LogisticsPriceVO;
import com.figureerp.module.logistics.vo.QuoteResultVO;
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

import java.util.Map;

/**
 * 物流管理接口：价格维护 + 运费查询。
 * <p>
 * 维护类接口需要管理员或主管，权限校验在 {@link LogisticsPriceService} 内部完成。
 */
@Tag(name = "物流管理", description = "物流价格维护、批量全国价、运费比价查询")
@RestController
@RequestMapping("/api/logistics")
@RequiredArgsConstructor
public class LogisticsPriceController {

    private final LogisticsPriceService logisticsPriceService;

    @Operation(summary = "分页查询物流价格")
    @GetMapping("/prices")
    public Result<PageResult<LogisticsPriceVO>> page(@Valid LogisticsPriceQuery query) {
        return Result.success(logisticsPriceService.page(query));
    }

    @Operation(summary = "价格详情")
    @GetMapping("/prices/{id}")
    public Result<LogisticsPriceVO> detail(@PathVariable Long id) {
        return Result.success(logisticsPriceService.detail(id));
    }

    @Operation(summary = "新增价格")
    @PostMapping("/prices")
    public Result<Long> create(@Valid @RequestBody LogisticsPriceSaveRequest request) {
        return Result.success("新增成功", logisticsPriceService.create(request));
    }

    @Operation(summary = "编辑价格")
    @PutMapping("/prices/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody LogisticsPriceSaveRequest request) {
        logisticsPriceService.update(id, request);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "删除价格", description = "逻辑删除，数据仍在库中")
    @DeleteMapping("/prices/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        logisticsPriceService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "启用 / 停用价格")
    @PutMapping("/prices/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        logisticsPriceService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }

    @Operation(summary = "批量保存全国价格", description = "按供应商 + 出发地批量 upsert，用于一次填完 34 个省份")
    @PostMapping("/prices/batch")
    public Result<Integer> batchSave(@Valid @RequestBody BatchPriceRequest request) {
        int affected = logisticsPriceService.batchSave(request);
        return Result.success("已保存 " + affected + " 条价格", affected);
    }

    @Operation(summary = "运费报价查询", description = "给定出发地、目的地和重量，返回各供应商价格对比（按总价升序）")
    @GetMapping("/quote")
    public Result<QuoteResultVO> quote(@Valid QuoteQuery query) {
        return Result.success(logisticsPriceService.quote(query));
    }

    @Operation(summary = "物流字典", description = "供应商、省份（含城市与运费区域）、已配置的出发地，供前端下拉使用")
    @GetMapping("/dicts")
    public Result<Map<String, Object>> dicts() {
        return Result.success(logisticsPriceService.dicts());
    }
}
