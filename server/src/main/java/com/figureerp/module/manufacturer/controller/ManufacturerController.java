package com.figureerp.module.manufacturer.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.common.storage.FileStorageService;
import com.figureerp.module.manufacturer.dto.ManufacturerQuery;
import com.figureerp.module.manufacturer.dto.ManufacturerSaveRequest;
import com.figureerp.module.manufacturer.service.ManufacturerService;
import com.figureerp.module.manufacturer.vo.ManufacturerOptionVO;
import com.figureerp.module.manufacturer.vo.ManufacturerSummaryVO;
import com.figureerp.module.manufacturer.vo.ManufacturerVO;
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

import java.util.List;

/**
 * 厂商目录接口：厂商档案维护 + 启用/停用 + 下拉选项 + Logo 上传。
 * <p>
 * 查询类对任何登录用户开放；增删改、启停需要管理员或主管，权限校验在 Service 内部完成（越权返回 403）。
 */
@Tag(name = "厂商目录", description = "厂商档案维护、启用/停用、下拉选项")
@RestController
@RequestMapping("/api/manufacturer")
@RequiredArgsConstructor
public class ManufacturerController {

    private final ManufacturerService manufacturerService;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "分页查询厂商", description = "支持关键字（名称/国家地区）、国家地区、状态过滤，多种排序")
    @GetMapping("/page")
    public Result<PageResult<ManufacturerVO>> page(@Valid ManufacturerQuery q) {
        return Result.success(manufacturerService.page(q));
    }

    @Operation(summary = "国家地区筛选项", description = "返回已录入厂商出现过的国家地区，去重排序")
    @GetMapping("/countries")
    public Result<List<String>> countries() {
        return Result.success(manufacturerService.countries());
    }

    @Operation(summary = "厂商下拉选项", description = "供其他业务表选择厂商，仅返回启用厂商")
    @GetMapping("/options")
    public Result<List<ManufacturerOptionVO>> options(@RequestParam(required = false) String keyword) {
        return Result.success(manufacturerService.options(keyword));
    }

    @Operation(summary = "厂商目录统计", description = "总数 / 启用 / 停用 / 覆盖国家地区数")
    @GetMapping("/summary")
    public Result<ManufacturerSummaryVO> summary() {
        return Result.success(manufacturerService.summary());
    }

    @Operation(summary = "厂商详情")
    @GetMapping("/{id}")
    public Result<ManufacturerVO> detail(@PathVariable Long id) {
        return Result.success(manufacturerService.detail(id));
    }

    @Operation(summary = "新增厂商")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ManufacturerSaveRequest req) {
        return Result.success("新增成功", manufacturerService.create(req));
    }

    @Operation(summary = "编辑厂商")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ManufacturerSaveRequest req) {
        manufacturerService.update(id, req);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "删除厂商", description = "逻辑删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        manufacturerService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "启用 / 停用厂商", description = "停用后不再出现在其他业务表的下拉选项中")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        manufacturerService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }

    @Operation(summary = "上传厂商 Logo", description = "仅支持图片，返回可访问 URL，落盘到 uploads/manufacturer")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        currentUserService.require();
        String url = fileStorageService.store(file, "manufacturer");
        return Result.success("上传成功", url);
    }
}
