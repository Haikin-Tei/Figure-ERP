package com.figureerp.module.stock.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.stock.dto.StockQuery;
import com.figureerp.module.stock.service.StockService;
import com.figureerp.module.stock.vo.StockLocationVO;
import com.figureerp.module.stock.vo.StockSummaryVO;
import com.figureerp.module.stock.vo.StockVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存查询接口（库存管理 → 手办列表 页面使用）。
 * <p>
 * 只读：库存由入库单确认 / 作废驱动，不提供手工维护接口。
 * 查询对所有登录用户开放。
 */
@Tag(name = "库存查询", description = "在库库存：按手办汇总、库位明细、顶部统计")
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @Operation(summary = "分页查询库存", description = "按手办汇总，支持名称/货号/品牌关键字、按仓库过滤、按件数/金额/入库时间排序")
    @GetMapping("/page")
    public Result<PageResult<StockVO>> page(@Valid StockQuery q) {
        return Result.success(stockService.page(q));
    }

    @Operation(summary = "库存统计", description = "在库款式数 / 总件数 / 总金额 / 涉及仓库数 / 占用库位数")
    @GetMapping("/summary")
    public Result<StockSummaryVO> summary() {
        return Result.success(stockService.summary());
    }

    @Operation(summary = "手办的库位明细", description = "某个手办在各仓库 / 区域 / 货架上分别有多少件")
    @GetMapping("/{productId}/locations")
    public Result<List<StockLocationVO>> locations(@PathVariable Long productId) {
        return Result.success(stockService.locations(productId));
    }
}
