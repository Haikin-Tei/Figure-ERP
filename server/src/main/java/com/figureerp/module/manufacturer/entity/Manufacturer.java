package com.figureerp.module.manufacturer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 厂商目录（手办 / 景品公司主数据）。
 * <p>
 * 一家厂商 = 一条记录，维护厂牌 Logo、公司名称、创立时间、国家地区。
 * 停用后不再出现在其他业务表的「厂商」下拉选项中，但列表仍可查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("manufacturer")
public class Manufacturer extends BaseEntity {

    /** 公司 / 厂牌名称，未删除记录内唯一 */
    private String name;

    /** 公司厂牌 Logo 图片 URL */
    private String logo;

    /** 创立时间（年月日） */
    private LocalDate foundedDate;

    /** 国家 / 地区，如 日本、中国、中国香港、美国 */
    private String country;

    /** 备注 */
    private String remark;

    /** 1 启用（出现在下拉选项）/ 0 停用 */
    private Integer status;
}
