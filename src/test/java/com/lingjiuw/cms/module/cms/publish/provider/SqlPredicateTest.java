package com.lingjiuw.cms.module.cms.publish.provider;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 层面的两条硬约束（backend/AGENTS.md）：
 * <ol>
 *   <li>静态发布要用的语句确实存在于 {@code mapper/cms/*.xml} 里（语句 id 与 Mapper 方法名一一对应）；</li>
 *   <li><b>XML 里的语句不会自动带逻辑删除条件</b>——引用带 {@code @TableLogic deleted} 的表的语句，
 *       必须自己写 {@code deleted = 0}。这条最容易漏，所以用源码文本检查兜住。</li>
 * </ol>
 *
 * <p>检查是"展开 {@code <include>} 之后"做的：本包把 SQL 拆成了
 * {@code publishWindow} / {@code predicate} / {@code whereCondition} 等片段，只扫语句本身会漏判。
 */
class SqlPredicateTest {

    /** 静态发布用到的全部语句（id → 出处文件），一个都不能少。 */
    private static final Map<String, String> REQUIRED_STATEMENTS = Map.ofEntries(
            Map.entry("selectPublishRows", "CmsContentMapper.xml"),
            Map.entry("countPublishRows", "CmsContentMapper.xml"),
            Map.entry("selectPrevNeighbor", "CmsContentMapper.xml"),
            Map.entry("selectNextNeighbor", "CmsContentMapper.xml"),
            Map.entry("selectDescendants", "CmsContentMapper.xml"),
            Map.entry("selectContentAncestors", "CmsContentMapper.xml"),
            Map.entry("selectChildCounts", "CmsContentMapper.xml"),
            Map.entry("selectTypeCounts", "CmsContentMapper.xml"),
            Map.entry("selectArchiveCounts", "CmsContentMapper.xml"),
            Map.entry("selectFacetCounts", "CmsContentMapper.xml"),
            Map.entry("selectCategoryCounts", "CmsContentCategoryMapper.xml"),
            Map.entry("selectContentCategories", "CmsContentCategoryMapper.xml"),
            Map.entry("selectTagCounts", "CmsContentTagMapper.xml"),
            Map.entry("selectContentTags", "CmsContentTagMapper.xml"),
            Map.entry("selectPublishSite", "CmsSiteMapper.xml"));

    /** 带 {@code @TableLogic deleted} 的表（写错一个字母会让检查形同虚设）。 */
    private static final Set<String> LOGIC_DELETE_TABLES = Set.of(
            "cms_content", "cms_content_type", "cms_field", "cms_content_index",
            "cms_content_category", "cms_content_tag", "cms_menu", "cms_menu_item",
            "cms_site", "cms_site_publish_option", "cms_category", "cms_tag", "cms_media");

    @Test
    void 静态发布用的语句都在XML里() {
        List<Statement> statements = statements();
        for (Map.Entry<String, String> required : REQUIRED_STATEMENTS.entrySet()) {
            Statement statement = statements.stream()
                    .filter(candidate -> candidate.id().endsWith("." + required.getKey()))
                    .findFirst().orElse(null);
            assertTrue(statement != null, "缺少语句 " + required.getKey() + "（应在 "
                    + required.getValue() + " 里）");
            assertTrue(statement.file().equals(required.getValue()),
                    required.getKey() + " 应在 " + required.getValue() + " 里，实际在 " + statement.file());
            assertFalse(statement.sql().isBlank(), required.getKey() + " 的 SQL 是空的");
        }
    }

    @Test
    void 引用逻辑删除表的语句都自己写了deleted0() {
        List<String> violations = new ArrayList<>();
        for (Statement statement : statements()) {
            for (String table : tablesIn(statement.sql())) {
                if (!LOGIC_DELETE_TABLES.contains(table)) {
                    continue;
                }
                // insert 语句表达"逻辑删除列有确定取值"的方式与另外三种不同：SQL 里没有可写的
                // `deleted = 0` 条件，能把同一件事说明白的写法是**把 deleted 列显式列进列清单并给 0**。
                // （不给也能跑——列默认 0——但那就成了"靠建表默认值兜"，默认值一变语义静默变味。）
                if (!statement.sql().contains("deleted = 0") && !insertsDeletedZero(statement)) {
                    violations.add(statement.id() + " 引用了 " + table
                            + " 但没有 deleted = 0（insert 语句要显式写 deleted 列 = 0；"
                            + "backend/AGENTS.md：XML 语句不自动带逻辑删除）");
                    break;
                }
            }
        }
        assertTrue(violations.isEmpty(), "逻辑删除条件缺失：\n" + String.join("\n", violations));
    }

    /**
     * {@code insert} 语句是否显式写了 {@code deleted} 列并给 0。
     *
     * <p>只看两件事：**列清单**里有 {@code deleted}、值清单以 {@code 0} 收尾。本文档里 deleted 恒是
     * 最后一个非审计列，两条同时成立时它的取值就是 0。不追求语法级精确——这是一条抓"漏写"的 lint，
     * 不是 SQL 解析器；真要看语义，集成测试会直接连库跑。
     *
     * <p>列清单从 {@code insert into <表> (} 的那个左括号起算，不能拿"SQL 里第一个左括号"——
     * 语句前面可能还有 {@code <selectKey>} 展开出来的 {@code select nextval('…')}。
     */
    private static boolean insertsDeletedZero(Statement statement) {
        if (!"insert".equals(statement.kind())) {
            return false;
        }
        String sql = statement.sql();
        Matcher insert = INSERT_INTO.matcher(sql);
        if (!insert.find()) {
            return false;
        }
        int columnsStart = insert.end() - 1;
        int columnsEnd = sql.indexOf(')', columnsStart + 1);
        if (columnsEnd < 0) {
            return false;
        }
        String columns = sql.substring(columnsStart, columnsEnd).toLowerCase(Locale.ROOT);
        return COLUMN_DELETED.matcher(columns).find() && TRAILING_ZERO.matcher(sql).find();
    }

    /** {@code insert into <表> (}，用于定位列清单。 */
    private static final Pattern INSERT_INTO =
            Pattern.compile("(?is)\\binsert\\s+into\\s+[\\w.\"]+\\s*\\(");
    private static final Pattern COLUMN_DELETED =
            Pattern.compile("(^|[,(])\\s*deleted\\s*(,|$)");
    private static final Pattern TRAILING_ZERO = Pattern.compile(",\\s*0\\s*\\)");

    /* ---------------- 扫描与展开 ---------------- */

    private record Statement(String id, String file, String sql, String kind) {
    }

    /** 全部语句：展开 {@code <include>} 之后的 SQL 文本，{@code id} 是 {@code namespace.method}。 */
    private static List<Statement> statements() {
        List<Statement> statements = new ArrayList<>();
        for (Path file : mapperFiles()) {
            Document document = parse(file);
            String namespace = document.getDocumentElement().getAttribute("namespace");
            Map<String, String> fragments = fragments(document);
            for (Element element : elements(document, "select", "insert", "update", "delete")) {
                String id = namespace + "." + element.getAttribute("id");
                statements.add(new Statement(id, file.getFileName().toString(),
                        expand(text(element), fragments, namespace), element.getTagName()));
            }
        }
        return statements;
    }

    private static Map<String, String> fragments(Document document) {
        Map<String, String> fragments = new HashMap<>();
        for (Element element : elements(document, "sql")) {
            fragments.put(element.getAttribute("id"), text(element));
        }
        return fragments;
    }

    /** 递归展开 {@code <include refid="…"/>}：片段里还会 include 别的片段（predicate → publishWindow）。 */
    private static String expand(String sql, Map<String, String> fragments, String namespace) {
        String result = sql;
        for (int round = 0; round < 8 && result.contains("<include"); round++) {
            Matcher matcher = Pattern.compile("<include\\s+refid=\"([^\"]+)\"\\s*/>").matcher(result);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                String refid = matcher.group(1);
                int dot = refid.lastIndexOf('.');
                String name = dot < 0 ? refid : refid.substring(dot + 1);
                matcher.appendReplacement(out, Matcher.quoteReplacement(fragments.getOrDefault(name, " ")));
            }
            matcher.appendTail(out);
            result = out.toString();
        }
        return result;
    }

    /** SQL 里出现的表名（按词边界匹配，{@code cms_article_tag} 不会被当成 {@code cms_article}）。 */
    private static Set<String> tablesIn(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("(?<![\\w])(cms_[a-z_]+)(?![\\w])").matcher(sql);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    private static List<Path> mapperFiles() {
        Path dir = mapperDir();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.toString().endsWith(".xml")).sorted().toList();
        } catch (Exception e) {
            throw new IllegalStateException("读不到 " + dir.toAbsolutePath(), e);
        }
    }

    /** 从 maven 的工作目录（{@code backend/}）或仓库根目录都能找到映射文件。 */
    private static Path mapperDir() {
        for (Path candidate : List.of(
                Path.of("src", "main", "resources", "mapper", "cms"),
                Path.of("backend", "src", "main", "resources", "mapper", "cms"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到 mapper/cms 目录（工作目录 "
                + Path.of("").toAbsolutePath() + "）");
    }

    private static Document parse(Path file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 不联网取 DTD（XML 里写的是 mybatis.org 的公开 DTD，测试环境不该依赖网络）
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(Files.readString(file).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("解析 " + file + " 失败", e);
        }
    }

    private static List<Element> elements(Document document, String... names) {
        List<Element> found = new ArrayList<>();
        for (String name : names) {
            NodeList nodes = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element element && name.equals(element.getTagName())) {
                    found.add(element);
                }
            }
        }
        return found;
    }

    private static String text(Element element) {
        return element.getTextContent() == null ? "" : element.getTextContent();
    }
}
