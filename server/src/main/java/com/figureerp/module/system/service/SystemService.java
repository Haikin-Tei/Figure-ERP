package com.figureerp.module.system.service;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统层服务：目前只提供数据库连通性自检。
 * <p>
 * 之所以单独做一个 service，是因为它需要真实拿一次连接，属于「有副作用 / 有 IO」的代码，
 * 不适合直接塞在 Controller 里。后续业务模块的 Service 也照这个分层写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final DataSource dataSource;

    /**
     * 真实查一次库，返回数据库版本、当前库名、表数量与连接池情况。
     * 连接失败不抛异常，而是把错误信息放进返回值，方便前端直接展示。
     */
    public Map<String, Object> dbHealth() {
        Map<String, Object> info = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection()) {
            info.put("connected", true);
            info.put("latencyMs", System.currentTimeMillis() - start);

            DatabaseMetaData meta = conn.getMetaData();
            info.put("product", meta.getDatabaseProductName());
            info.put("productVersion", meta.getDatabaseProductVersion());
            info.put("driverVersion", meta.getDriverVersion());

            // 当前库名 + 库里的表数量（用来确认 figure_erp 是否连对了）
            try (Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
                    if (rs.next()) {
                        info.put("database", rs.getString(1));
                    }
                }
                try (ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                    if (rs.next()) {
                        info.put("tableCount", rs.getInt(1));
                    }
                }
            }

            // 连接池状态：排查「连上了但池子被打满」这类问题时很有用
            if (dataSource instanceof HikariDataSource hikari) {
                Map<String, Object> pool = new LinkedHashMap<>();
                pool.put("active", hikari.getHikariPoolMXBean() == null ? -1 : hikari.getHikariPoolMXBean().getActiveConnections());
                pool.put("idle", hikari.getHikariPoolMXBean() == null ? -1 : hikari.getHikariPoolMXBean().getIdleConnections());
                pool.put("total", hikari.getHikariPoolMXBean() == null ? -1 : hikari.getHikariPoolMXBean().getTotalConnections());
                pool.put("maxPoolSize", hikari.getMaximumPoolSize());
                info.put("pool", pool);
            }
        } catch (Exception e) {
            log.error("数据库连通性检查失败", e);
            info.put("connected", false);
            info.put("latencyMs", System.currentTimeMillis() - start);
            info.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        return info;
    }
}
