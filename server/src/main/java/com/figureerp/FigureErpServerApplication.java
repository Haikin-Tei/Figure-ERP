package com.figureerp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类。
 * <p>
 * Mapper 统一放在各模块的 mapper 包下，用 @MapperScan 一次性扫描，
 * 这样新增 Mapper 接口不用每个都加 @Mapper 注解。
 */
@SpringBootApplication
@MapperScan("com.figureerp.**.mapper")
public class FigureErpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(FigureErpServerApplication.class, args);
	}

}
