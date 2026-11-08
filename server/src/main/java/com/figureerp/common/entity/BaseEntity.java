package com.figureerp.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 所有业务实体的父类：统一主键、审计字段、逻辑删除。
 * <p>
 * 约定：数据库表必须有 id / create_time / update_time / deleted 四列。
 * 时间字段由 {@code MyMetaObjectHandler} 自动填充，业务代码不用手动 set。
 */
@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 未删除，1 已删除 */
    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
