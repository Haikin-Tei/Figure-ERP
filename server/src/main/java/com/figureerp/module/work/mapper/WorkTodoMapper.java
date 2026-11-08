package com.figureerp.module.work.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.figureerp.module.work.entity.WorkTodo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待办 Mapper，只用到 BaseMapper 的通用 CRUD。
 */
@Mapper
public interface WorkTodoMapper extends BaseMapper<WorkTodo> {
}
