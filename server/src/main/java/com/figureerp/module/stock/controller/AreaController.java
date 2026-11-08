package com.figureerp.module.stock.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.stock.dto.AreaQuery;
import com.figureerp.module.stock.dto.AreaSaveRequest;
import com.figureerp.module.stock.service.AreaService;
import com.figureerp.module.stock.vo.AreaVO;
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
 * 区域配置接口（库存管理 → 仓库设置 → 区域配置 页面使用）。
 * <p>
 * 查询对所有登录用户开放；新增/编辑/删除/改状态需要管理员或主管，
 * 权限校验在 {@link AreaService} 内部完成。
 */
@Tag(name = "区域配置", description = "区域档案：编号/名称/所属仓库/容量/状态的查询与维护")
@RestController
@RequestMapping("/api/area")
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    @Operation(summary = "分页查询区域", description = "支持按区域编号或名称模糊，按仓库/状态过滤")
    @GetMapping("/page")
    public Result<PageResult<AreaVO>> page(@Valid AreaQuery q) {
        return Result.success(areaService.page(q));
    }

    @Operation(summary = "区域详情")
    @GetMapping("/{id}")
    public Result<AreaVO> detail(@PathVariable Long id) {
        return Result.success(areaService.detail(id));
    }

    @Operation(summary = "新增区域")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AreaSaveRequest req) {
        return Result.success("新增成功", areaService.create(req));
    }

    @Operation(summary = "编辑区域")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AreaSaveRequest req) {
        areaService.update(id, req);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "删除区域", description = "逻辑删除；区域下还有货架时返回 1000 拦住")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        areaService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "修改区域状态", description = "A 使用中 / D 停用")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        areaService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }
}
