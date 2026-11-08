package com.figureerp.module.acg.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.common.storage.FileStorageService;
import com.figureerp.module.acg.converter.AcgEnums;
import com.figureerp.module.acg.dto.AcgWorkQuery;
import com.figureerp.module.acg.dto.AcgWorkSaveRequest;
import com.figureerp.module.acg.dto.HeatRecordRequest;
import com.figureerp.module.acg.service.AcgAnalysisService;
import com.figureerp.module.acg.service.AcgWorkService;
import com.figureerp.module.acg.vo.AcgAnalysisVO;
import com.figureerp.module.acg.vo.AcgHeatPointVO;
import com.figureerp.module.acg.vo.AcgOptionVO;
import com.figureerp.module.acg.vo.AcgWorkVO;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ACG 作品目录接口：档案维护 + 热度录入 + 选品分析 + 下拉选项。
 * <p>
 * 查询类对任何登录用户开放；增删改、热度录入需要管理员或主管，
 * 权限校验在 Service 内部完成（越权返回 403）。
 */
@Tag(name = "ACG 作品目录", description = "作品档案维护、热度趋势记录、选品分析")
@RestController
@RequestMapping("/api/acg")
@RequiredArgsConstructor
public class AcgWorkController {

    private final AcgWorkService acgWorkService;
    private final AcgAnalysisService acgAnalysisService;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "分页查询作品", description = "支持名称/原名/编号/标签模糊，类型/状态/年份/热度区间过滤，多种排序")
    @GetMapping("/page")
    public Result<PageResult<AcgWorkVO>> page(@Valid AcgWorkQuery q) {
        return Result.success(acgWorkService.page(q));
    }

    @Operation(summary = "作品类型与连载状态字典")
    @GetMapping("/types")
    public Result<Map<String, Map<String, String>>> types() {
        Map<String, Map<String, String>> dict = new LinkedHashMap<>();
        dict.put("workTypes", AcgEnums.typeDict());
        dict.put("releaseStatuses", AcgEnums.releaseStatusDict());
        return Result.success(dict);
    }

    @Operation(summary = "作品下拉选项", description = "供其他业务表选择作品，仅返回启用作品，按热度倒序")
    @GetMapping("/options")
    public Result<List<AcgOptionVO>> options(@RequestParam(required = false) String keyword) {
        return Result.success(acgWorkService.options(keyword));
    }

    @Operation(summary = "选品分析总览", description = "KPI + 潜力排行 + 热度销量四象限 + 类型分布 + 文字结论")
    @GetMapping("/analysis")
    public Result<AcgAnalysisVO> analysis() {
        return Result.success(acgAnalysisService.analysis());
    }

    @Operation(summary = "作品详情")
    @GetMapping("/{id}")
    public Result<AcgWorkVO> detail(@PathVariable Long id) {
        return Result.success(acgWorkService.detail(id));
    }

    @Operation(summary = "作品热度趋势", description = "返回指定天数内的热度快照，按日期正序")
    @GetMapping("/{id}/trend")
    public Result<List<AcgHeatPointVO>> trend(@PathVariable Long id,
                                              @RequestParam(required = false) Integer days) {
        return Result.success(acgWorkService.trend(id, days));
    }

    @Operation(summary = "新增作品")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AcgWorkSaveRequest req) {
        return Result.success("新增成功", acgWorkService.create(req));
    }

    @Operation(summary = "编辑作品")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AcgWorkSaveRequest req) {
        acgWorkService.update(id, req);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "删除作品", description = "逻辑删除，作品与其热度快照一并失效")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        acgWorkService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "启用 / 停用作品", description = "停用后不再出现在其他业务表的下拉选项中")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        acgWorkService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }

    @Operation(summary = "录入当期热度", description = "写一条热度快照（同日覆盖）并同步作品当前值")
    @PostMapping("/{id}/heat")
    public Result<Void> recordHeat(@PathVariable Long id, @Valid @RequestBody HeatRecordRequest req) {
        acgWorkService.recordHeat(id, req);
        return Result.success("热度已记录", null);
    }

    @Operation(summary = "上传封面图", description = "仅支持图片，返回可访问 URL，落盘到 uploads/acg")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        currentUserService.require();
        String url = fileStorageService.store(file, "acg");
        return Result.success("上传成功", url);
    }
}
