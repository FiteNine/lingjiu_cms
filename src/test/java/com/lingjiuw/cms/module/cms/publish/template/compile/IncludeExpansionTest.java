package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.Node;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;
import com.lingjiuw.cms.module.cms.publish.template.ast.TextNode;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4.5 第 10 条 ★：include 越界 / 循环 / 超深 → E1006；顺带证明**展开**本身（§5.3）：
 * 报错位置指向片段内的真实行号、带包含链、参数在编译期变成字面量。
 */
class IncludeExpansionTest extends CompileCaseSupport {

    @Test
    void 循环包含报E1006并给包含链() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("index.html", "{cms:include file='a.html'/}")
                .file("_partials/a.html", "{cms:include file='b.html'/}")
                .file("_partials/b.html", "{cms:include file='a.html'/}");

        PublishException error = fixture.error("index.html");

        CompileFixture.assertError(error, PublishErrorCode.E1006, "include 循环",
                "_partials/a.html → _partials/b.html → _partials/a.html");
        assertEquals(List.of("index.html", "_partials/a.html", "_partials/b.html", "_partials/a.html"),
                error.includeChain(), "包含链要从入口模板一路列到重复的那一段");
    }

    @Test
    void 超过10层报E1006() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("index.html", "{cms:include file='f0.html'/}");
        for (int i = 0; i < 12; i++) {
            fixture.file("_partials/f" + i + ".html", "{cms:include file='f" + (i + 1) + ".html'/}");
        }
        fixture.file("_partials/f12.html", "底");

        PublishException error = fixture.error("index.html");

        CompileFixture.assertError(error, PublishErrorCode.E1006, "include 超过 10 层", "10");
    }

    @Test
    void 片段不存在与路径越界() {
        CompileFixture missing = base(PageType.DETAIL, "product");
        missing.file("index.html", "第一行\n{cms:include file='nope.html'/}");
        PublishException error = missing.error("index.html");
        CompileFixture.assertError(error, PublishErrorCode.E1006, "include 的片段不存在",
                "_partials/nope.html");
        // §10.1：位置必须指向**写出这个 include 的那一行**，不是链深度
        assertEquals("index.html", error.templatePath());
        assertEquals(2, error.lineNo());

        CompileFixture outside = base(PageType.DETAIL, "product");
        outside.file("index.html", "{cms:include file='../secret.html'/}");
        CompileFixture.assertError(outside.error("index.html"), PublishErrorCode.E1006,
                "include 路径越界", "../secret.html");
    }

    @Test
    void 展开后报错指向片段内的真实行号并带包含链() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("index.html", "第一行\n{cms:include file='card.html'/}")
                .file("_partials/card.html", "片段第一行\n[field:proce/]");

        PublishException error = fixture.error("index.html");

        CompileFixture.assertError(error, PublishErrorCode.E1004, "字段 proce 不存在");
        assertEquals("_partials/card.html", error.templatePath(), "报错位置必须是片段自己");
        assertEquals(2, error.lineNo(), "行号必须是片段内的行号");
        assertEquals(List.of("index.html", "_partials/card.html"), error.includeChain());
    }

    @Test
    void 展开把片段节点并进整棵树并保留片段路径() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("index.html", "A{cms:include file='card.html'/}B")
                .file("_partials/card.html", "[field:title/]");

        TemplateAst ast = fixture.compile("index.html");

        List<FieldNode> fields = new ArrayList<>();
        collectFields(ast.nodes(), fields);
        assertEquals(1, fields.size(), "片段里的字段引用应该出现在展开后的树上");
        assertEquals("_partials/card.html", fields.get(0).sourcePath());
        assertEquals(1, fields.get(0).lineNo());
    }

    @Test
    void 展开后的分页主体只算一次() {
        // 同一个片段被 include 两次 → 两个 {cms:list} → 展开后的整体判定报 E3001（§5.3）
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("index.html",
                        "{cms:include file='card.html'/}{cms:include file='card.html'/}")
                .file("_partials/card.html", "{cms:list type='product' category='all'}x{/cms:list}");

        PublishException error = fixture.error("index.html");

        CompileFixture.assertError(error, PublishErrorCode.E3001, "多个分页主体标签",
                "请只保留一个");
    }

    @Test
    void include参数在编译期变成字面量且内层覆盖外层() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("index.html", "{cms:include file='outer.html' title='外层'/}")
                .file("_partials/outer.html",
                        "[field:param.title/]{cms:include file='inner.html' title='内层'/}")
                .file("_partials/inner.html", "[field:param.title/]");

        TemplateAst ast = fixture.compile("index.html");

        List<String> texts = new ArrayList<>();
        collectTexts(ast.nodes(), texts);
        assertTrue(texts.contains("外层"), "外层片段里的 param.title 应该是 外层：" + texts);
        assertTrue(texts.contains("内层"), "内层片段里的 param.title 应该是 内层（就近解析）：" + texts);
    }

    @Test
    void include参数可替换标签属性里的字段引用() {
        // 参数化片段的前提：标签属性在**解析期**就固定下来（模板参数一律是字面量，§3.3），
        // 想让"一栏服务"这类片段靠 where 区分栏目，属性里的 [field:param.x/] 也必须被替换——
        // 否则同一段标记要按栏目复制若干份。
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("index.html", "{cms:include file='col.html' group='acme'/}")
                .file("_partials/col.html",
                        "{cms:query type='product' category='all' where='brand:eq:[field:param.group/]' row='5'}{/cms:query}");

        TemplateAst ast = fixture.compile("index.html");

        List<TagNode> queries = new ArrayList<>();
        collectTags(ast.nodes(), "query", queries);
        assertEquals(1, queries.size(), "展开后应该只有一个 query 标签");
        assertEquals("brand:eq:acme", queries.get(0).arg("where"),
                "属性里的 [field:param.group/] 要换成实参的字面量");
    }

    @Test
    void 没传到实参的param标记原样保留() {
        // 只替换"这次 include 真的传了"的名字：片段被别的模板复用时漏传参数，
        // 应当留下能一眼认出的原文，而不是被替换成空串悄悄改了查询条件。
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("index.html", "{cms:include file='col.html' kind='acme'/}")
                .file("_partials/col.html",
                        "{cms:query type='product' category='all' keyword='[field:param.other/]' row='5'}{/cms:query}");

        TemplateAst ast = fixture.compile("index.html");

        List<TagNode> queries = new ArrayList<>();
        collectTags(ast.nodes(), "query", queries);
        assertEquals("[field:param.other/]", queries.get(0).arg("keyword"));
    }

    /** 收集某一类标签（含嵌在标签体里的）。 */
    private static void collectTags(List<Node> nodes, String name, List<TagNode> out) {
        for (Node node : nodes) {
            if (!(node instanceof TagNode tag)) {
                continue;
            }
            if (name.equals(tag.name())) {
                out.add(tag);
            }
            if (tag.body() != null) {
                collectTags(tag.body(), name, out);
            }
        }
    }

    @Test
    void 片段里的else能绑到包含者的if() {
        // §5.3：位置类校验在展开之后做一次——片段里的 {cms:else/} 允许绑到包含它的 {cms:if}
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("index.html", "{cms:if field='title'}A{cms:include file='else.html'/}B{/cms:if}")
                .file("_partials/else.html", "{cms:else/}");

        TemplateAst ast = fixture.compile("index.html");

        TagNode ifNode = (TagNode) ast.nodes().get(0);
        assertEquals(1, ifNode.elseIndex(), "else 被摘掉后，elseIndex 指向 else 分支的第一个节点");
        assertEquals(1, ifNode.bodyBeforeElse().size());
        assertEquals(1, ifNode.bodyAfterElse().size());
    }

    @Test
    void 片段里的else绑不到任何if时报E1003() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("index.html", "{cms:include file='else.html'/}")
                .file("_partials/else.html", "{cms:else/}");

        assertThrows(PublishException.class, () -> fixture.compile("index.html"));
        CompileFixture.assertError(fixture.error("index.html"), PublishErrorCode.E1003,
                "{cms:else/} 位置错");
    }

    private static void collectFields(List<Node> nodes, List<FieldNode> out) {
        for (Node node : nodes) {
            if (node instanceof FieldNode field) {
                out.add(field);
            } else if (node instanceof TagNode tag && tag.body() != null) {
                collectFields(tag.body(), out);
            }
        }
    }

    private static void collectTexts(List<Node> nodes, List<String> out) {
        for (Node node : nodes) {
            if (node instanceof TextNode text) {
                out.add(text.text());
            } else if (node instanceof TagNode tag && tag.body() != null) {
                collectTexts(tag.body(), out);
            }
        }
    }
}
