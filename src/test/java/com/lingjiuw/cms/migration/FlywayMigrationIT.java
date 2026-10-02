package com.lingjiuw.cms.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 期 0 的迁移验收（static-publish.md §12.1 的迁移清单、§12.2 期 0 的判据）。
 *
 * <p>为什么是一个"手动跑"的 IT 而不是默认的 {@code mvn test} 用例：它需要一个真实的
 * PostgreSQL，而 §12.3 的第一批测试（编译期校验的表驱动用例、黄金文件）是纯函数式的，
 * 不该被数据库拖成必跑项。类名以 {@code IT} 结尾，Surefire 的默认包含规则
 * （{@code *Test} / {@code Test*} / {@code *Tests} / {@code *TestCase}）不会收集它，
 * 因此 {@code mvn test} 不受影响；要跑它用：
 *
 * <pre>
 * mvn test -Dtest=FlywayMigrationIT
 * </pre>
 *
 * <p>它做三件事，每一件都是"文档承诺了但没人验证过"的地方：
 * <ol>
 *   <li>在一个**全新 schema** 里从零执行全部迁移（而不是复用已有的 {@code public}），
 *       这样"迁移能不能从空库跑起来"才有判据；</li>
 *   <li>断言 9 个新迁移文件都真的执行成功（§12.1 第 1/2/4/5/10/11/12/13/14 条）；</li>
 *   <li>断言播种的效果：**每个站点都有 3 个内置类型与 {@code main} 菜单**、发布选项非空
 *       （§12.2 期 0："新建站点后自动有 3 个内置类型与默认菜单"——这里验的是存量站点的补齐，
 *       新建站点走 {@code SiteBootstrapServiceImpl.seed}）。</li>
 * </ol>
 *
 * <p>跑完把临时 schema 删掉，不留垃圾。连接参数与 {@code application.yml} 一致，可用
 * 系统属性覆盖：{@code -Dcms.test.db.url=... -Dcms.test.db.user=... -Dcms.test.db.password=...}
 */
class FlywayMigrationIT {

    private static final String URL = System.getProperty("cms.test.db.url",
            "jdbc:postgresql://localhost:5432/lingjiuw_cms");
    private static final String USER = System.getProperty("cms.test.db.user", "cms");
    private static final String PASSWORD = System.getProperty("cms.test.db.password", "cms123456");

    /** §12.1 的 9 个新迁移（期 0 范围）。版本号与文件名里的时间戳一一对应。 */
    private static final List<String> EXPECTED_VERSIONS = List.of(
            "20261002090001", "20261002090002", "20261002090004", "20261002090005",
            "20261002090010", "20261002090011", "20261002090012", "20261002090013",
            "20261002090014");

    @Test
    void migrationsApplyFromScratchAndSeedEverySite() throws SQLException {
        String schema = "verify_pub_" + System.currentTimeMillis();
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(URL, USER, PASSWORD)
                    .schemas(schema)
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .load();

            MigrateResult result = flyway.migrate();

            // ① 全部迁移成功，且 9 个新迁移都在其中
            assertThat(result.success).as("迁移必须全部成功").isTrue();
            Set<String> applied = new TreeSet<>();
            result.migrations.forEach(m -> applied.add(m.version));
            for (String version : EXPECTED_VERSIONS) {
                assertThat(applied)
                        .as("迁移 %s 必须被执行（§12.1）", version)
                        .contains(version);
            }

            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
                conn.setSchema(schema);

                // ② §12.1 第 1/2/4/5/10 条建的表都在
                assertThat(tableNames(conn))
                        .as("§12.1 的迁移必须建出这些表")
                        .contains("cms_content_type", "cms_field", "cms_content", "cms_content_index",
                                "cms_content_category", "cms_content_tag",
                                "cms_menu", "cms_menu_item",
                                "cms_site_publish_option",
                                "cms_publish_task", "cms_publish_lock");

                // ③ §12.1 第 12/13 条的增列（v2.2 补的两批列）
                assertThat(columnNames(conn, "cms_media"))
                        .as("§12.1 第 12 条：cms_media 增列")
                        .contains("alt", "width", "height", "derive_status");
                assertThat(columnNames(conn, "cms_content"))
                        .as("§12.1 第 13 条：cms_content 增列")
                        .contains("expire_time", "view_count_day", "view_count_week",
                                "comment_count", "rating_avg", "rating_count");

                // ④ §12.1 第 14 条：明细表补齐 id 主键与 deleted 逻辑删除列
                for (String detail : List.of("cms_redirect", "cms_menu_item", "cms_content_index",
                        "cms_content_category", "cms_content_tag", "cms_form_field")) {
                    if (!tableNames(conn).contains(detail)) {
                        continue;
                    }
                    assertThat(columnNames(conn, detail))
                            .as("§12.1 第 14 条：%s 必须有 id 主键与 deleted 列", detail)
                            .contains("id", "deleted");
                }

                // ⑤ 播种：每个站点 3 个内置类型 + main 菜单 + 发布选项非空（§12.2 期 0 的判据）
                List<Long> siteIds = longColumn(conn, "select id from cms_site order by id");
                assertThat(siteIds).as("库里至少要有一个站点，否则播种无从验证").isNotEmpty();
                for (Long siteId : siteIds) {
                    Set<String> codes = stringColumn(conn,
                            "select code from cms_content_type where site_id = " + siteId
                                    + " and deleted = 0 and code in ('article','single','author')");
                    assertThat(codes)
                            .as("站点 %s 必须有 3 个内置内容类型（§2.1）", siteId)
                            .containsExactlyInAnyOrder("article", "single", "author");

                    assertThat(stringColumn(conn,
                            "select code from cms_menu where site_id = " + siteId + " and deleted = 0"))
                            .as("站点 %s 必须有默认菜单 main（§2.4）", siteId)
                            .contains("main");

                    long options = longColumn(conn,
                            "select count(*) from cms_site_publish_option where site_id = " + siteId)
                            .get(0);
                    assertThat(options)
                            .as("站点 %s 的发布选项必须被播种（§2.7）", siteId)
                            .isGreaterThan(0);
                }
            }
        } finally {
            dropSchema(schema);
        }
    }

    /* ---------------- 元数据查询与清理 ---------------- */

    private static Set<String> tableNames(Connection conn) throws SQLException {
        return stringColumn(conn, "select table_name from information_schema.tables"
                + " where table_schema = current_schema()");
    }

    private static Set<String> columnNames(Connection conn, String table) throws SQLException {
        return stringColumn(conn, "select column_name from information_schema.columns"
                + " where table_schema = current_schema() and table_name = '" + table + "'");
    }

    private static Set<String> stringColumn(Connection conn, String sql) throws SQLException {
        Set<String> values = new TreeSet<>();
        try (Statement statement = conn.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    private static List<Long> longColumn(Connection conn, String sql) throws SQLException {
        List<Long> values = new ArrayList<>();
        try (Statement statement = conn.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getLong(1));
            }
        }
        return values;
    }

    private static void dropSchema(String schema) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = conn.createStatement()) {
            statement.execute("drop schema if exists " + schema + " cascade");
        } catch (SQLException e) {
            System.out.println("[FlywayMigrationIT] 临时 schema 清理失败，请手工删除：" + schema
                    + "（" + e.getMessage() + "）");
        }
    }
}
