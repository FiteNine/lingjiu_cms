package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.model.DefVersion;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.TemplateCompiler;
import com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.Node;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * §4.4 编译缓存与失效：命中、`defVersion` 变、`mtime + size` 变、**同长度替换片段**（只有 sha256 看得见）。
 */
class CompileCacheTest extends CompileCaseSupport {

    @Test
    void 同路径同上下文命中缓存() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html", "[field:title/]");
        TemplateCompiler compiler = fixture.compiler();

        TemplateAst first = compiler.compile("product_detail.html", fixture.context());
        TemplateAst second = compiler.compile("product_detail.html", fixture.context());

        assertSame(first, second, "指纹一致时必须命中缓存，返回同一个不可变 AST");
        assertEquals(1, compiler.cachedCount());
    }

    @Test
    void defVersion变了要重编译() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html", "[field:title/]");
        TemplateCompiler compiler = fixture.compiler();

        TemplateAst first = compiler.compile("product_detail.html", fixture.context());
        fixture.defVersion(DefVersion.ZERO.bumpFields());
        TemplateAst second = compiler.compile("product_detail.html", fixture.context());

        assertNotSame(first, second, "改了字段定义必须用新定义重编译（§4.4 的 v2.1 修改）");
        assertEquals(1, compiler.cachedCount(), "同站点同路径同签名的旧 defVersion 记录被淘汰");
    }

    @Test
    void mtime变了要重编译() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html", "[field:title/]", 1_000L, 14L);
        TemplateCompiler compiler = fixture.compiler();
        TemplateAst first = compiler.compile("product_detail.html", fixture.context());

        fixture.file("product_detail.html", "[field:title/]", 2_000L, 14L);
        TemplateAst second = compiler.compile("product_detail.html", fixture.context());

        assertNotSame(first, second, "mtime 变（size 不变）也要重编译");
    }

    @Test
    void 片段同长度替换靠sha256失效() {
        // §4.4 v2.2 定死的场景：片段改一个词、字节数完全不变，只按 size 会静默陈旧
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("index.html", "{cms:include file='card.html'/}")
                .file("_partials/card.html", "[field:title/]", 1_000L, 14L);
        TemplateCompiler compiler = fixture.compiler();
        TemplateAst first = compiler.compile("index.html", fixture.context());
        assertEquals("title", firstField(first));

        // 同长度替换：mtime 与 size 都不变，只有内容（与 sha256）变了
        fixture.file("_partials/card.html", "[field:cover/]", 1_000L, 14L);
        TemplateAst second = compiler.compile("index.html", fixture.context());

        assertNotSame(first, second, "同长度替换必须重编译（astVersion 含 sha256）");
        assertNotEquals(first.astVersion(), second.astVersion(), "astVersion 必须变");
        assertEquals("cover", firstField(second), "重编译后用的是新片段内容");
    }

    @Test
    void 不同上下文签名各自缓存不互相驱逐() {
        // §4.4 把签名加进 key 的理由：同一个 list.html 会被 HOME / LIST 共用
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("list.html", "{cms:list type='product' category='all'}x{/cms:list}");
        TemplateCompiler compiler = fixture.compiler();

        fixture.page(PageType.DETAIL, "product");
        TemplateAst detail = compiler.compile("list.html", fixture.context());
        fixture.page(PageType.LIST, "product");
        TemplateAst list = compiler.compile("list.html", fixture.context());
        fixture.page(PageType.DETAIL, "product");
        TemplateAst again = compiler.compile("list.html", fixture.context());

        assertNotSame(detail, list, "不同上下文是两条记录");
        assertSame(detail, again, "回到原上下文必须仍然命中（淘汰不能跨签名）");
        assertEquals(2, compiler.cachedCount());
    }

    @Test
    void 缓存命中时警告会被重放() {
        CompileFixture fixture = base(PageType.ARCHIVE, null).option("page.archive", 0);
        fixture.file("archive_list.html", "{cms:list type='product' category='all'}x{/cms:list}");
        TemplateCompiler compiler = fixture.compiler();

        com.lingjiuw.cms.module.cms.publish.template.ValidationReport first =
                new com.lingjiuw.cms.module.cms.publish.template.ValidationReport();
        compiler.compile("archive_list.html", fixture.context(), first);
        com.lingjiuw.cms.module.cms.publish.template.ValidationReport second =
                new com.lingjiuw.cms.module.cms.publish.template.ValidationReport();
        compiler.compile("archive_list.html", fixture.context(), second);

        assertEquals(1, first.warnings().size());
        assertEquals(1, second.warnings().size(), "命中缓存也要把上次的 W5001 重放进报告");
        assertEquals(com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode.W5001,
                second.warnings().get(0).code());
    }

    private static String firstField(TemplateAst ast) {
        List<FieldNode> fields = new ArrayList<>();
        collect(ast.nodes(), fields);
        return fields.isEmpty() ? null : fields.get(0).head();
    }

    private static void collect(List<Node> nodes, List<FieldNode> out) {
        for (Node node : nodes) {
            if (node instanceof FieldNode field) {
                out.add(field);
            } else if (node instanceof TagNode tag && tag.body() != null) {
                collect(tag.body(), out);
            }
        }
    }
}
