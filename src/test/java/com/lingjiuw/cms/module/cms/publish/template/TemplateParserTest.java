package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.Node;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TextNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板词法与解析的表驱动用例（static-publish.md §3.1–§3.5、§4.2、§4.3、§12.3 的 ★ 用例）。
 *
 * <p>对着契约逐条钉：五种词法元素、起始序列后非法名字报 E1001（不退回文本）、
 * 引号内的 {@code }} 与 {@code /} 不结束标签、重复参数 key 报 E1002、块配对与未闭合报 E1003、
 * {@code {cms:else/}} 只是一个普通的自闭合节点、§3.4 的转义只在紧邻起始序列时生效。
 */
class TemplateParserTest {

    private static final String PATH = "news/list.html";

    /* ---------------- 正常解析 ---------------- */

    static Stream<Arguments> 正常用例() {
        return Stream.of(
                Arguments.of("文本 + 块标签 + 文本",
                        "<h1>{cms:if field='hot'}热{/cms:if}</h1>",
                        List.of(new TextNode("<h1>", 1, PATH),
                                new TagNode("if", Map.of("field", "hot"),
                                        List.of(new TextNode("热", 1, PATH)), -1, 1, PATH),
                                new TextNode("</h1>", 1, PATH))),

                Arguments.of("自闭合标签的 body 是 null（§4.5 第 3 条要靠它区分写法）",
                        "{cms:list type='article' row='10'/}",
                        List.of(new TagNode("list", Map.of("type", "article", "row", "10"), null, -1, 1, PATH))),

                Arguments.of("块标签体为空时是空列表，不是 null",
                        "{cms:list}{/cms:list}",
                        List.of(new TagNode("list", Map.of(), List.of(), -1, 1, PATH))),

                Arguments.of("字段引用",
                        "[field:title/]",
                        List.of(new FieldNode(List.of("title"), Map.of(), 1, PATH))),

                Arguments.of("字段引用带 formatter 参数",
                        "[field:hot show='is-hot'/]",
                        List.of(new FieldNode(List.of("hot"), Map.of("show", "is-hot"), 1, PATH))),

                Arguments.of("多段字段路径：多值按下标 / 取个数 / JSON 取键",
                        "[field:images.0.url/][field:images.count/][field:specs.weight/]",
                        List.of(new FieldNode(List.of("images", "0", "url"), Map.of(), 1, PATH),
                                new FieldNode(List.of("images", "count"), Map.of(), 1, PATH),
                                new FieldNode(List.of("specs", "weight"), Map.of(), 1, PATH))),

                Arguments.of("首段是数字也合法（§3.2 的 segment 允许 [0-9]+，取不到是编译期 E1004 的事）",
                        "[field:0/]",
                        List.of(new FieldNode(List.of("0"), Map.of(), 1, PATH))),

                Arguments.of("{cms:if} 内 {cms:list} 内 {cms:foreach} 三层嵌套",
                        "{cms:if field='hasChildren'}\n"
                                + "{cms:list type='article'}\n"
                                + "{cms:foreach field='images'}[field:url/]{/cms:foreach}\n"
                                + "{/cms:list}\n"
                                + "{/cms:if}",
                        List.of(new TagNode("if", Map.of("field", "hasChildren"), List.of(
                                new TextNode("\n", 1, PATH),
                                new TagNode("list", Map.of("type", "article"), List.of(
                                        new TextNode("\n", 2, PATH),
                                        new TagNode("foreach", Map.of("field", "images"), List.of(
                                                new FieldNode(List.of("url"), Map.of(), 3, PATH)), -1, 3, PATH),
                                        new TextNode("\n", 3, PATH)), -1, 2, PATH),
                                new TextNode("\n", 4, PATH)), -1, 1, PATH))),

                Arguments.of("同名块标签可以嵌套，靠栈配对",
                        "{cms:if a='1'}{cms:if b='2'}x{/cms:if}{/cms:if}",
                        List.of(new TagNode("if", Map.of("a", "1"), List.of(
                                new TagNode("if", Map.of("b", "2"),
                                        List.of(new TextNode("x", 1, PATH)), -1, 1, PATH)), -1, 1, PATH))),

                Arguments.of("引号内的 } 不结束标签（§3.3 最容易写错的一处）",
                        "{cms:include file='a}b.html'/}",
                        List.of(new TagNode("include", Map.of("file", "a}b.html"), null, -1, 1, PATH))),

                Arguments.of("引号内的 / 与 {cms: 都只是字面量",
                        "{cms:include file='a/b}c{d}.html' title=\"x/y\"/}",
                        List.of(new TagNode("include",
                                Map.of("file", "a/b}c{d}.html", "title", "x/y"), null, -1, 1, PATH))),

                Arguments.of("引号内的 \\' \\\" 转义，其余反斜杠原样保留（\\| \\, 留给 WhereParser）",
                        "{cms:include title=\"it's\" alt='a\\'b' where='price\\|discount\\,sale'/}",
                        List.of(new TagNode("include",
                                Map.of("title", "it's", "alt", "a'b", "where", "price\\|discount\\,sale"),
                                null, -1, 1, PATH))),

                Arguments.of("裸值取到空白或自闭合符为止",
                        "{cms:list row=10 orderby=publishTime active=1/}",
                        List.of(new TagNode("list",
                                Map.of("row", "10", "orderby", "publishTime", "active", "1"), null, -1, 1, PATH))),

                Arguments.of("裸值里的 / 是普通字符，只在 /} 处停（§3.3：裸值到空白或 } / /> 为止）",
                        "{cms:include file=_partials/header.html/}",
                        List.of(new TagNode("include", Map.of("file", "_partials/header.html"), null, -1, 1, PATH))),

                Arguments.of("块标签的裸值同样保留中间的 /",
                        "{cms:if file=_partials/a/b.html}x{/cms:if}",
                        List.of(new TagNode("if", Map.of("file", "_partials/a/b.html"),
                                List.of(new TextNode("x", 1, PATH)), -1, 1, PATH))),

                Arguments.of("字段引用的裸值到 /] 为止",
                        "[field:x money=1/]",
                        List.of(new FieldNode(List.of("x"), Map.of("money", "1"), 1, PATH))),

                Arguments.of("参数之间允许跨行，行号仍指标签起始行",
                        "{cms:list\n  type='article'\n  row='10'/}",
                        List.of(new TagNode("list", Map.of("type", "article", "row", "10"), null, -1, 1, PATH))),

                Arguments.of("{cms:else/} 是普通的自闭合节点，解析器不绑定 elseIndex",
                        "{cms:if field='x'}A{cms:else/}B{/cms:if}",
                        List.of(new TagNode("if", Map.of("field", "x"), List.of(
                                new TextNode("A", 1, PATH),
                                new TagNode("else", Map.of(), null, -1, 1, PATH),
                                new TextNode("B", 1, PATH)), -1, 1, PATH))),

                Arguments.of("\\{cms: 与 \\[field: 输出字面量，反斜杠不输出（§3.4）",
                        "\\{cms:list} 与 \\[field:x/] 都是文本",
                        List.of(new TextNode("{cms:list} 与 [field:x/] 都是文本", 1, PATH))),

                Arguments.of("反斜杠只在紧邻起始序列时生效，Windows 路径与正则不被吃掉",
                        "C:\\www\\a.html  正则 \\d+\\s",
                        List.of(new TextNode("C:\\www\\a.html  正则 \\d+\\s", 1, PATH))),

                Arguments.of("每个节点带起始行号（对 \\n 计数，从 1 起）",
                        "line1\nline2 {cms:list/}\n[field:title/]",
                        List.of(new TextNode("line1\nline2 ", 1, PATH),
                                new TagNode("list", Map.of(), null, -1, 2, PATH),
                                new TextNode("\n", 2, PATH),
                                new FieldNode(List.of("title"), Map.of(), 3, PATH))),

                Arguments.of("{cms: 的 { 与 cms: 之间有空白就是普通文本（§4.2）",
                        "{ cms:list} 与 {cms :list}",
                        List.of(new TextNode("{ cms:list} 与 {cms :list}", 1, PATH))),

                Arguments.of("不构成起始序列的括号保持文本",
                        "a { b } c {cms d",
                        List.of(new TextNode("a { b } c {cms d", 1, PATH))),

                Arguments.of("空模板",
                        "",
                        List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("正常用例")
    void 解析结果(String name, String source, List<Node> expected) {
        assertEquals(expected, TemplateParser.parse(source, PATH));
    }

    /* ---------------- 报错 ---------------- */

    static Stream<Arguments> 报错用例() {
        return Stream.of(
                /* E1001：起始序列之后不是合法 name / field_path，一律报错，不退回文本（§3.2） */
                bad("标签名以数字开头 → E1001", "{cms:123}", PublishErrorCode.E1001,
                        "[E1001]", "news/list.html:1", "标签名不合法", "123"),
                bad("文末孤立的 {cms: → E1001", "hello\n{cms:", PublishErrorCode.E1001,
                        "[E1001]", "news/list.html:2", "文件结尾"),
                bad("{cms: 后面是空白 → E1001", "{cms: }", PublishErrorCode.E1001, "标签名不合法"),
                bad("{cms: 后面直接是 } → E1001", "{cms:}", PublishErrorCode.E1001, "标签名不合法"),
                bad("大写标签名 → E1001", "{cms:List/}", PublishErrorCode.E1001, "List"),
                bad("标签名里混进 - → E1001", "{cms:list-x/}", PublishErrorCode.E1001, "list-x/}"),
                bad("块结束的名字不合法 → E1001", "{/cms:123}", PublishErrorCode.E1001, "{/cms:"),
                bad("字段路径第一段非法 → E1001", "[field:-x/]", PublishErrorCode.E1001,
                        "字段路径不合法", "-x/]"),
                bad("字段路径在 . 后面断掉 → E1001", "[field:images./]", PublishErrorCode.E1001, "images./]"),
                bad("字段路径里混进 - → E1001", "[field:im-ages/]", PublishErrorCode.E1001, "im-ages"),
                bad("文末孤立的 [field: → E1001", "[field:", PublishErrorCode.E1001, "文件结尾"),

                /* E1003：块配对与结构 */
                bad("块标签没有闭合 → E1003，列出每个未闭合标签的行号", "{cms:if}\n{cms:list}",
                        PublishErrorCode.E1003, "2 个块标签没有闭合", "第 1 行的 {cms:if}", "第 2 行的 {cms:list}"),
                bad("错配 → E1003，给出两个标签各自的行号", "{cms:if}\n{cms:list}\n{/cms:if}",
                        PublishErrorCode.E1003, "{/cms:if} 与最近的 {cms:list} 不匹配",
                        "第 2 行", "第 3 行", "news/list.html:3"),
                bad("错配且外层有同名标签 → E1003 指出它在里面", "{cms:if}{cms:list}\n{/cms:if}",
                        PublishErrorCode.E1003, "先写 {/cms:list}"),
                bad("自闭合标签不需要块结束 → E1003", "{cms:list/}\n{/cms:list}", PublishErrorCode.E1003,
                        "没有可以配对的块标签", "news/list.html:2"),
                bad("孤立的 {/cms:if} → E1003", "{/cms:if}", PublishErrorCode.E1003, "没有可以配对的块标签"),
                bad("{cms:else} 带体 → E1003", "{cms:if a='1'}\n{cms:else}\n{/cms:else}\n{/cms:if}",
                        PublishErrorCode.E1003, "{cms:else} 不能带标签体", "news/list.html:2", "{cms:else/}"),
                bad("标签扫到文件结尾 → E1003", "{cms:list row='10'", PublishErrorCode.E1003,
                        "没有结束", "news/list.html:1"),
                bad("自闭合写成 /> → E1003", "{cms:list a='1'/>}", PublishErrorCode.E1003,
                        "结束符写错了", "{cms:list/}"),
                bad("字段引用少了 / → E1003", "[field:title]", PublishErrorCode.E1003, "少了 `/`", "/]"),
                bad("块结束里写参数 → E1003", "{cms:if a='1'}\n{/cms:if b='2'}", PublishErrorCode.E1003,
                        "不能写参数", "news/list.html:2"),
                bad("块结束没写完 → E1003", "{cms:if a='1'}{/cms:if", PublishErrorCode.E1003, "没有结束"),

                /* E1002：参数区 */
                bad("同一个 key 出现两次 → E1002，不做 last-wins", "{cms:list row='10' row='20'/}",
                        PublishErrorCode.E1002, "参数 row 出现了两次", "'10'", "'20'"),
                bad("重复参数跨行 → E1002，两个行号都给", "{cms:list row='10'\n  row='20'/}",
                        PublishErrorCode.E1002, "第 1 行", "第 2 行"),
                bad("字段引用的参数重复 → E1002", "[field:title a='1' a='2'/]",
                        PublishErrorCode.E1002, "参数 a 出现了两次"),
                bad("裸值为空 → E1002", "{cms:list row=}", PublishErrorCode.E1002, "值是空的"),
                bad("参数缺少 = → E1002", "{cms:list row 10/}", PublishErrorCode.E1002, "缺少 ="),
                bad("参数名以数字开头 → E1002", "{cms:list 8='x'/}", PublishErrorCode.E1002, "无法识别"),
                bad("引号没有闭合 → E1002", "{cms:include file='a.html/}", PublishErrorCode.E1002,
                        "引号没有闭合", "news/list.html:1"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("报错用例")
    void 报错文案(String name, String source, PublishErrorCode code, List<String> contains) {
        PublishException e = assertThrows(PublishException.class, () -> TemplateParser.parse(source, PATH));
        assertEquals(code, e.code());
        assertTrue(e.getMessage().contains(PATH), () -> "报错必须带模板路径：\n" + e.getMessage());
        for (String part : contains) {
            assertTrue(e.getMessage().contains(part), () -> "报错文案里缺少「" + part + "」：\n" + e.getMessage());
        }
    }

    @Test
    void 报错文案是三行格式() {
        PublishException e = assertThrows(PublishException.class, () -> TemplateParser.parse("{cms:123}", PATH));
        String[] lines = e.getMessage().split("\n", -1);
        assertEquals(3, lines.length, () -> "§10.1 要求三行：\n" + e.getMessage());
        assertTrue(lines[0].startsWith("[E1001] "), lines[0]);
        assertTrue(lines[0].endsWith(" · " + PATH + ":1"), lines[0]);
        assertTrue(lines[1].startsWith("  → 现状："), lines[1]);
        assertTrue(lines[2].startsWith("  → 建议："), lines[2]);
    }

    /** 真实片段的混排：include 自闭合 + if/list 嵌套 + 体内 else + 字段引用 + 行尾的字面量转义。 */
    @Test
    void 真实模板片段的结构与行号() {
        String src = "{cms:include file='_partials/head.html' title='列表'/}\n"
                + "{cms:if field='page.hasResults'}\n"
                + "{cms:list type='article' row='10'}\n"
                + "<a href=\"[field:url/]\">[field:title/]</a>{cms:else/}暂无{/cms:list}\n"
                + "{/cms:if}\n"
                + "\\{cms:list} 与 \\[field:x/] 在文档里就是字面量";

        assertEquals(List.of(
                new TagNode("include", Map.of("file", "_partials/head.html", "title", "列表"), null, -1, 1, PATH),
                new TextNode("\n", 1, PATH),
                new TagNode("if", Map.of("field", "page.hasResults"), List.of(
                        new TextNode("\n", 2, PATH),
                        new TagNode("list", Map.of("type", "article", "row", "10"), List.of(
                                new TextNode("\n<a href=\"", 3, PATH),
                                new FieldNode(List.of("url"), Map.of(), 4, PATH),
                                new TextNode("\">", 4, PATH),
                                new FieldNode(List.of("title"), Map.of(), 4, PATH),
                                new TextNode("</a>", 4, PATH),
                                new TagNode("else", Map.of(), null, -1, 4, PATH),
                                new TextNode("暂无", 4, PATH)), -1, 3, PATH),
                        new TextNode("\n", 4, PATH)), -1, 2, PATH),
                new TextNode("\n{cms:list} 与 [field:x/] 在文档里就是字面量", 5, PATH)),
                TemplateParser.parse(src, PATH));
    }

    private static Arguments bad(String name, String source, PublishErrorCode code, String... contains) {
        return Arguments.of(name, source, code, List.of(contains));
    }
}
