package com.figureerp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置，启动后访问 http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.version:dev}")
    private String version;

    @Bean
    public OpenAPI figureErpOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("手办管家 API")
                .description("Figure-ERP 手办管理平台后端接口")
                .version(version)
                .contact(new Contact().name("Figure ERP")));
    }
}
