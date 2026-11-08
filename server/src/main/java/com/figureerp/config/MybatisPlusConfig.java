package com.figureerp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * <p>
 * 分页插件必须在拦截器里注册，否则 Page 对象不会自动 limit。
 */
@Configuration
public class MybatisPlusConfig {

    /** 单页最大条数，防止前端传个 pageSize=100000 把库拖死 */
    private static final long MAX_PAGE_SIZE = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        // 页码超过总页数时回到第一页，而不是返回空列表
        pagination.setOverflow(true);

        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
