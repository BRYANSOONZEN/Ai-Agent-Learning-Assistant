package com.se.platform.common.config;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.sql.init.SqlDataSourceScriptDatabaseInitializer;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 开发数据库重启后保留数据（platform.db.keep-data=true，application.yml 里打开；单元测试里关掉）。
 *
 * <p>原来用内存数据库，每次启动都重建，老师上传的资料、识别出的知识点、学生的记录一重启就没了。
 * 现在数据库存成文件（backend/data/），启动时：
 * <ul>
 *   <li>还没有库（第一次启动，或者删掉了 data 文件夹）：执行 schema.sql 建表、data.sql 放演示数据</li>
 *   <li>已经有库：不重建、不清空；只把 schema.sql 里新加的表建出来（例如以后新增的功能），已有的表不动</li>
 * </ul>
 * 已有的表加了新列：写在 schema-upgrade.sql 里（每条都能重复执行），库已存在时执行一遍。
 * 正式部署用 MySQL，本来就会保留数据。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SqlInitializationProperties.class)
public class KeepDataConfig {

    @Bean
    @ConditionalOnProperty(name = "platform.db.keep-data", havingValue = "true")
    SqlDataSourceScriptDatabaseInitializer dataSourceScriptDatabaseInitializer(DataSource dataSource,
                                                                              SqlInitializationProperties props) {
        return new SqlDataSourceScriptDatabaseInitializer(dataSource, props) {
            @Override
            public boolean initializeDatabase() {
                if (!tableExists(dataSource, "course")) {
                    log.info("数据库是空的：建表并放入演示数据");
                    return super.initializeDatabase();
                }
                addMissingTables(dataSource);
                upgradeColumns(dataSource);
                return false;
            }
        };
    }

    private static final Pattern CREATE_TABLE = Pattern.compile("(?i)^CREATE\\s+TABLE\\s+(\\w+)");
    private static final Pattern CREATE_INDEX = Pattern.compile("(?i)^CREATE\\s+INDEX\\s+\\w+\\s+ON\\s+(\\w+)");

    /** 已有的库：schema.sql 里有、库里还没有的表，建出来 */
    static void addMissingTables(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            List<String> created = new ArrayList<>();
            for (String sql : statements()) {
                Matcher t = CREATE_TABLE.matcher(sql);
                Matcher i = CREATE_INDEX.matcher(sql);
                if (t.find() && !tableExists(c, t.group(1))) {
                    st.execute(sql);
                    created.add(t.group(1));
                } else if (i.find() && created.contains(i.group(1))) {
                    st.execute(sql);
                }
            }
            // 选课表是后加的：第一次建出来时，把已有的学生加入已有的课程（以前不用选课就能学，升级后不能把他们关在门外）
            if (created.contains("enrollment")) {
                st.execute("INSERT INTO enrollment (course_id, student_id, created_at) "
                        + "SELECT c.id, u.id, NOW() FROM course c, `user` u WHERE u.role = 'student'");
            }
            log.info(created.isEmpty() ? "数据库已存在，保留原有数据" : "数据库已存在，保留原有数据；新建了表：{}", created);
        } catch (Exception e) {
            throw new IllegalStateException("检查数据库表失败：" + e.getMessage(), e);
        }
    }

    /** 加列语句：ALTER TABLE 表 ADD COLUMN IF NOT EXISTS 列 …（H2 支持这种写法，MySQL 不支持） */
    private static final Pattern ADD_COLUMN = Pattern.compile(
            "(?is)^ALTER\\s+TABLE\\s+`?(\\w+)`?\\s+ADD\\s+COLUMN\\s+IF\\s+NOT\\s+EXISTS\\s+(\\w+)\\s+(.*)$");

    /**
     * 已有的库：执行 schema-upgrade.sql（给已有的表加新列），每条语句都能重复执行。
     * MySQL 不认识 ADD COLUMN IF NOT EXISTS：先查这一列在不在，不在才加；H2 专用的其他语句（改列类型）在 MySQL 上跳过——
     * MySQL 的库是按最新的 schema.sql 建的，类型本来就对。
     */
    static void upgradeColumns(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            boolean mysql = c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
            int n = 0;
            for (String sql : statements("schema-upgrade.sql")) {
                if (!mysql) {
                    st.execute(sql);
                    n++;
                    continue;
                }
                Matcher m = ADD_COLUMN.matcher(sql);
                if (m.find()) {
                    if (!columnExists(c, m.group(1), m.group(2))) {
                        st.execute("ALTER TABLE `" + m.group(1) + "` ADD COLUMN " + m.group(2) + " " + m.group(3));
                        n++;
                    }
                }
            }
            log.info("数据库升级语句执行了 {} 条（已有的列不会重复加）", n);
        } catch (Exception e) {
            throw new IllegalStateException("升级数据库失败：" + e.getMessage(), e);
        }
    }

    /** 当前库里有没有这一列（只看当前库，不看 MySQL 自己的系统库） */
    static boolean columnExists(Connection c, String table, String column) throws SQLException {
        try (ResultSet rs = c.getMetaData().getColumns(c.getCatalog(), c.getSchema(), null, null)) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME")) && column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    static List<String> statements() throws java.io.IOException {
        return statements("schema.sql");
    }

    /** schema.sql 按分号拆成一条条语句，去掉注释 */
    static List<String> statements(String file) throws java.io.IOException {
        String text = new String(new ClassPathResource(file).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        StringBuilder clean = new StringBuilder();
        for (String line : text.split("\\R")) {
            int dash = line.indexOf("--");
            clean.append(dash >= 0 ? line.substring(0, dash) : line).append('\n');
        }
        List<String> out = new ArrayList<>();
        for (String s : clean.toString().split(";")) {
            if (!s.isBlank()) {
                out.add(s.strip());
            }
        }
        return out;
    }

    static boolean tableExists(DataSource ds, String table) {
        try (Connection c = ds.getConnection()) {
            return tableExists(c, table);
        } catch (SQLException e) {
            return false;
        }
    }

    /** 当前库里有没有这张表（只看当前库：MySQL 自己的系统库里也有一张 user 表，不能算） */
    static boolean tableExists(Connection c, String table) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), c.getSchema(), null, new String[] {"TABLE"})) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
