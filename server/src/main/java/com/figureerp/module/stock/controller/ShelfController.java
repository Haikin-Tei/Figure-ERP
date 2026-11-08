package com.figureerp.module.stock.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.stock.dto.ShelfQuery;
import com.figureerp.module.stock.dto.ShelfSaveRequest;
import com.figureerp.module.stock.service.ShelfService;
import com.figureerp.module.stock.vo.ShelfVO;
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
 * 货架管理接口（库存管理 → 仓库设置 → 货架管理 页面使用）。
 * <p>
 * 查询对所有登录用户开放；新增/编辑/删除/改状态需要管理员或主管，
 * 权限校验在 {@link ShelfService} 内部完成。
 */
@Tag(name = "货架管理", description = "货架档案：编号/所属仓库/库位信息/状态的查询与维护")
@RestController
@RequestMapping("/api/shelf")
@RequiredArgsConstructor
public class ShelfController {

    private final ShelfService shelfService;

    @Operation(summary = "分页查询货架", description = "支持按货架编号或库位信息模糊，按仓库/状态过滤")
    @GetMapping("/page")
    public Result<PageResult<ShelfVO>> page(@Valid ShelfQuery q) {
        return Result.success(shelfService.page(q));
    }

    @Operation(summary = "货架详情")
    @GetMapping("/{id}")
    public Result<ShelfVO> detail(@PathVariable Long id) {
        return Result.success(shelfService.detail(id));
    }

    @Operation(summary = "新增货架")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ShelfSaveRequest req) {
        return Result.success("新增成功", shelfService.create(req));
    }

    @Operation(summary = "编辑货架")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ShelfSaveRequest req) {
        shelfService.update(id, req);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "删除货架", description = "逻辑删除，数据仍在库中，删除后同仓库下编号可重新使用")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        shelfService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "修改货架状态", description = "A 正常 / B 维护中 / D 停用")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        shelfService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }
}
