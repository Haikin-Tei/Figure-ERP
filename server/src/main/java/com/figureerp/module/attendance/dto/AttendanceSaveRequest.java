package com.figureerp.module.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 新增 / 编辑考勤记录。
 * <p>
 * 只传时间，状态与工时全部由后端按规则算：
 * <ul>
 *   <li>上班晚于 09:00 记迟到；</li>
 *   <li>下班早于 18:00 记早退，没填记未打卡；</li>
 *   <li>销售岗（{@code UserRole.isSales}）强制清空下班时间，记免打卡。</li>
 * </ul>
 * 前端就算传了 checkInStatus 之类的字段也会被忽略。
 */
@Data
@Schema(description = "考勤记录保存参数")
public class AttendanceSaveRequest {

    @Schema(description = "员工 ID，留空表示当前登录用户；只有主管 / 人事可以录别人的")
    private Long userId;

    @Schema(description = "考勤日期，不能晚于今天", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-09")
    @NotNull(message = "请选择考勤日期")
    private LocalDate attendDate;

    @Schema(description = "上班时间，日期必须与考勤日期一致", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "2026-11-09T09:00:00")
    @NotNull(message = "请填写上班时间")
    private LocalDateTime checkInTime;

    @Schema(description = "下班时间，销售岗不填；日期必须与考勤日期一致", example = "2026-11-09T18:10:00")
    private LocalDateTime checkOutTime;

    @Schema(description = "备注，如「外勤」「忘记打卡后补录」")
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;
}
