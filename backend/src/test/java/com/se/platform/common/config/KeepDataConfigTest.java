package com.se.platform.common.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * 开发库启动时靠“course 表在不在”判断是不是空库（空库才建表、放演示数据）。
 * 判断错了会把已有的开发库当成空库重建，所以用和开发库一样的 H2 设置测一遍；MySQL 上另有实机验证（文档 07）。
 */
class KeepDataConfigTest {

    @Test
    void tableAndColumnChecks_onH2WithDevSettings() throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:mem:keepdata;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER", "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE course (id BIGINT PRIMARY KEY, name VARCHAR(10))");
            st.execute("CREATE TABLE `user` (id BIGINT PRIMARY KEY)");
            assertTrue(KeepDataConfig.tableExists(c, "course"));
            assertTrue(KeepDataConfig.tableExists(c, "user"));
            assertFalse(KeepDataConfig.tableExists(c, "enrollment"));
            assertTrue(KeepDataConfig.columnExists(c, "course", "name"));
            assertFalse(KeepDataConfig.columnExists(c, "course", "status"));
        }
    }
}
