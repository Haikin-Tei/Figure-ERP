package com.figureerp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Paths;

/**
 * 拦截器注册 + 静态资源映射。
 * <p>
 * 只拦 /api/**，白名单里放登录接口和自检接口（自检需要未登录也能访问，方便部署后探测）。
 * Swagger 相关路径不在 /api 下，天然不受影响。
 * <p>
 * 商品图片上传后通过 {@code /uploads/**} 对外提供访问，映射到底层文件系统的上传目录。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Value("${app.upload.base-dir:uploads}")
    private String uploadBaseDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/system/health",
                        "/api/system/db"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // user.dir 在 spring-boot:run 下就是 server/ 目录，完整根目录 = {user.dir}/{uploadBaseDir}
        String root = Paths.get(System.getProperty("user.dir"), uploadBaseDir).toFile().getAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + root + "/");
    }
}
