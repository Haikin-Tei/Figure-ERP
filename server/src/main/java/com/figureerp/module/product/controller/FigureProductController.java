package com.figureerp.module.product.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.common.storage.FileStorageService;
import com.figureerp.module.product.dto.FigureProductQuery;
import com.figureerp.module.product.dto.FigureProductSaveRequest;
import com.figureerp.module.product.service.FigureProductService;
import com.figureerp.module.product.vo.FigureProductVO;
import com.figureerp.module.system.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 手办商品接口：档案维护 + 列表查询 + 图片上传。
 * <p>
 * 维护类接口需要管理员或主管，权限校验在 {@link FigureProductService} 内部完成；
 * 上传图片需要登录（任何角色），由 {@link CurrentUserService} 校验。
 */
@Tag(name = "手办商品", description = "商品档案：图片 / 长宽高 / 带盒重量录入与查询")
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class FigureProductController {

    private final FigureProductService productService;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "分页查询手办商品", description = "支持按名称/货号/品牌/系列/角色名模糊，按状态过滤")
    @GetMapping("/page")
    public Result<PageResult<FigureProductVO>> page(@Valid FigureProductQuery q) {
        return Result.success(productService.page(q));
    }

    @Operation(summary = "商品详情")
    @GetMapping("/{id}")
    public Result<FigureProductVO> detail(@PathVariable Long id) {
        return Result.success(productService.detail(id));
    }

    @Operation(summary = "新增商品")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody FigureProductSaveRequest req) {
        return Result.success("新增成功", productService.create(req));
    }

    @Operation(summary = "编辑商品")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody FigureProductSaveRequest req) {
        productService.update(id, req);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "删除商品", description = "逻辑删除，数据仍在库中")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "启用 / 停用商品")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }

    @Operation(summary = "上传商品图片", description = "仅支持图片，返回可访问的 URL，落盘到 uploads/products")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        // 上传需要登录，任何角色都可以（校验失败抛 401）
        currentUserService.require();
        String url = fileStorageService.store(file);
        return Result.success("上传成功", url);
    }
}
