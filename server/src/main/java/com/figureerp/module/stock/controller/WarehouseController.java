package com.figureerp.module.stock.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.stock.dto.WarehouseQuery;
import com.figureerp.module.stock.dto.WarehouseSaveRequest;
import com.figureerp.module.stock.service.WarehouseService;
import com.figureerp.module.stock.vo.WarehouseVO;
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

/**
 * 仓库管理接口（库存管理 → 仓库设置 → 库位管理 页面使用）。
 * <p>
 * 查询对所有登录用户开放；新增/编辑/删除/改状态需要管理员或主管，
 * 权限校验在 {@link WarehouseService} 内部完成。
 */
@Tag(name = "仓库管理", description = "仓库档案：代码/名称/地址/类型/状态的查询与维护")
@RestController
@RequestMapping("/api/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @Operation(summary = "分页查询仓库", description = "支持按代码/名称模糊，按类型/状态过滤")
    @GetMapping("/page")
    public Result<PageResult<WarehouseVO>> page(@Valid WarehouseQuery q) {
        return Result.success(warehouseService.page(q));
    }

    @Operation(summary = "仓库详情")
    @GetMapping("/{id}")
    public Result<WarehouseVO> detail(@PathVariable Long id) {
        return Result.success(warehouseService.detail(id));
    }

    @Operation(summary = "新增仓库")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody WarehouseSaveRequest req) {
        return Result.success("新增成功", warehouseService.create(req));
    }

    @Operation(summary = "编辑仓库")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody WarehouseSaveRequest req) {
        warehouseService.update(id, req);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "删除仓库", description = "逻辑删除，数据仍在库中，删除后代码可重新使用")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "修改仓库状态", description = "A 使用中 / C 盘库中 / D 停用中")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        warehouseService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }
}
