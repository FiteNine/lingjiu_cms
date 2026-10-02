package com.lingjiuw.cms.module.cms.publish.provider;

import com.lingjiuw.cms.module.cms.publish.model.EnumOption;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DbContentProvider} 里那批纯函数的语义：站点选项的类型解析（§2.7）、字段定义的两个字符串
 * 形态（§2.2 的 {@code field_type} 与 {@code options}）、{@code where} 取值归类（§2.5）、
 * 正文计数与 jsonb 的容错读取（§2.3）。
 *
 * <p>它们不碰数据库，因此是本包最便宜的一层回归网：真库测试跑不了时（数据库不可用），它们仍然证明
 * "库里存的那点文本"能被正确解释。
 */
class ProviderHelpersTest {

    /* ---------------- §2.7：站点选项 ---------------- */

    @Test
    void 布尔选项解析成Boolean_空串视为未设置() {
        assertEquals(Boolean.TRUE, DbContentProvider.parseOption("page.category", "1"));
        assertEquals(Boolean.FALSE, DbContentProvider.parseOption("page.archive", "0"));
        assertEquals(Boolean.TRUE, DbContentProvider.parseOption("publish.preview", "true"));
        assertNull(DbContentProvider.parseOption("page.tag", ""));
        assertNull(DbContentProvider.parseOption("page.tag", null));
        // 表外的取值形态原样保留：不猜，也不丢
        assertEquals("maybe", DbContentProvider.parseOption("page.tag", "maybe"));
    }

    @Test
    void JSON选项解析成List_其余保持字符串() {
        Object types = DbContentProvider.parseOption("feed.types", "[\"article\"]");
        assertTrue(types instanceof List<?>);
        assertEquals(List.of("article"), types);

        Object combos = DbContentProvider.parseOption("facets.combos", "[\"brand-acme+region-huadong\"]");
        assertEquals(List.of("brand-acme+region-huadong"), combos);
        // 坏 JSON：保留原字符串（配置脏不该打挂发布）
        assertEquals("[\"article\"", DbContentProvider.parseOption("feed.types", "[\"article\""));

        // 不是 JSON 选项的键保持字符串
        assertEquals("rss", DbContentProvider.parseOption("feed.format", "rss"));
    }

    @Test
    void 计数选项解析成Integer() {
        assertEquals(3, DbContentProvider.parseOption("publish.keepReleases", "3"));
        assertEquals(50, DbContentProvider.parseOption("facets.cardinality", "50"));
        assertEquals(400, DbContentProvider.parseOption("reading.speed", "400"));
        assertEquals("abc", DbContentProvider.parseOption("reading.speed", "abc"));
    }

    /* ---------------- §2.2：字段定义的两个字符串形态 ---------------- */

    @Test
    void 字段类型按名字解析_表外取值退化成TEXT() {
        assertEquals(FieldType.IMAGE, DbContentProvider.fieldType("image"));
        assertEquals(FieldType.ENUM_MULTI, DbContentProvider.fieldType(" ENUM_MULTI "));
        assertEquals(FieldType.TEXT, DbContentProvider.fieldType("VIDEO"));
        assertEquals(FieldType.TEXT, DbContentProvider.fieldType(null));
    }

    @Test
    void 枚举选项是值冒号标签的逗号串() {
        assertEquals(List.of(new EnumOption("huadong", "华东"), new EnumOption("huanan", "华南")),
                DbContentProvider.enumOptions("huadong:华东,huanan:华南"));
        // 没有冒号时标签 = 值；空段丢掉
        assertEquals(List.of(new EnumOption("a", "a")),
                DbContentProvider.enumOptions(" a , ,"));
        assertEquals(List.of(), DbContentProvider.enumOptions(null));
    }

    /* ---------------- §2.5：where 取值的归类 ---------------- */

    @Test
    void where取值按数字时间文本归类() {
        assertEquals("num", DbContentProvider.valueKind("9999"));
        assertEquals("num", DbContentProvider.valueKind("-1.5"));
        assertEquals("time", DbContentProvider.valueKind("2026-05-01T10:00"));
        assertEquals("time", DbContentProvider.valueKind("2026-05-01"));
        assertEquals("str", DbContentProvider.valueKind("acme"));
        assertEquals("str", DbContentProvider.valueKind(""));
    }

    @Test
    void 时间字符串的三种形态都能读() {
        assertEquals(LocalDateTime.of(2026, 5, 1, 10, 0),
                DbContentProvider.parseTime("2026-05-01T10:00"));
        assertEquals(LocalDateTime.of(2026, 5, 1, 10, 0),
                DbContentProvider.parseTime("2026-05-01 10:00:00"));
        assertEquals(LocalDateTime.of(2026, 5, 1, 0, 0),
                DbContentProvider.parseTime("2026-05-01"));
        assertNull(DbContentProvider.parseTime("不是时间"));
    }

    /* ---------------- §2.2 / §2.3：多值字段与正文计量 ---------------- */

    @Test
    void 多值字段的存储形态全都归一成字符串列表() {
        assertEquals(List.of("1", "2"), DbContentProvider.storedValues(List.of(1, 2)));
        assertEquals(List.of("1", "2"), DbContentProvider.storedValues("[1,2]"));
        assertEquals(List.of("a", "b"), DbContentProvider.storedValues("a,b"));
        assertEquals(List.of("hot"), DbContentProvider.storedValues("hot"));
        assertEquals(List.of(), DbContentProvider.storedValues(null));
        assertEquals(List.of(), DbContentProvider.storedValues(""));
    }

    @Test
    void 正文字数与图片计数按纯文本口径() {
        assertEquals(4, DbContentProvider.plainLength("<p>正文内容</p>"));
        assertEquals(0, DbContentProvider.plainLength("<p></p>"));
        assertEquals(2, DbContentProvider.plainLength("图 <img src=\"a.png\"> 文"));
        assertEquals(2, DbContentProvider.countOccurrences("<img a><img b>", "<img"));
        assertEquals(0, DbContentProvider.countOccurrences("", "<img"));
    }

    @Test
    void toc读数组也读包了一层的对象_坏数据不抛() {
        assertEquals(List.of(Map.of("level", 2, "text", "标题")),
                DbContentProvider.tocOf("[{\"level\":2,\"text\":\"标题\"}]"));
        assertEquals(1, DbContentProvider.tocOf("{\"items\":[{\"level\":2}]}").size());
        assertEquals(List.of(), DbContentProvider.tocOf("{\"nope\":1}"));
        assertEquals(List.of(), DbContentProvider.tocOf("不是 JSON"));
        assertEquals(List.of(), DbContentProvider.tocOf(null));
    }
}
