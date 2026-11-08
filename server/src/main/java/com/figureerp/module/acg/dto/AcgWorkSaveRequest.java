package com.figureerp.module.acg.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增 / 编辑 ACG 作品的入参。
 * <p>
 * 只有名称和类型必填；热度、销量允许在新增时直接给初值，
 * 后续日常更新建议走「录入热度」接口，这样才会留下趋势快照。
 */
@Data
public class AcgWorkSaveRequest {

    /** 作品编号，可空 */
    @Size(max = 50, message = "作品编号最多 50 个字符")
    private String workCode;

    @NotBlank(message = "作品名称不能为空")
    @Size(max = 120, message = "作品名称最多 120 个字符")
    private String name;

    @Size(max = 160, message = "原名最多 160 个字符")
    private String originalName;

    @NotBlank(message = "作品类型不能为空")
    private String workType;

    @Size(max = 100, message = "版权方最多 100 个字符")
    private String ipOwner;

    @Size(max = 100, message = "制作公司最多 100 个字符")
    private String studio;

    @Size(max = 100, message = "作者最多 100 个字符")
    private String author;

    @Min(value = 1950, message = "发行年份不能早于 1950")
    @Max(value = 2100, message = "发行年份不能晚于 2100")
    private Integer releaseYear;

    /** 连载状态：ONGOING / FINISHED / UPCOMING */
    private String releaseStatus;

    @Min(value = 0, message = "热度不能小于 0")
    @Max(value = 100, message = "热度不能大于 100")
    private Integer heatScore;

    @Min(value = 0, message = "月销量不能为负数")
    private Integer monthlySales;

    @Min(value = 0, message = "累计销量不能为负数")
    private Integer totalSales;

    /** 标签列表，落库时拼成逗号分隔字符串 */
    private List<String> tags;

    @Size(max = 255, message = "封面图地址过长")
    private String coverImage;

    @Size(max = 500, message = "备注最多 500 个字符")
    private String remark;

    /** 1 可选 / 0 停用，默认 1 */
    private Integer status;
}
