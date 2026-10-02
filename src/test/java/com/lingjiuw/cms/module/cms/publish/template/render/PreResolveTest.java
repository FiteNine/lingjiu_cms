package com.lingjiuw.cms.module.cms.publish.template.render;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.Node;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.tag.ElseTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.ForeachTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.IfTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.IncludeTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.detail;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.field;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.foreach;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.ifBody;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.ifElse;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.item;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.render;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.selfClosing;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §5.4 第 2 步的预解析，以及"编译期该干完的事"在渲染期的兜底。
 *
 * <p>预解析的**下钻规则**是内核负责人定死的口径：**只下钻 {@code {cms:if}} 的体**，
 * 其余标签体（{@code foreach} / {@code list} / {@code query}…）一律不下钻——它们的体会被
 * 重复渲染，把顶层查询算在它们里面会让行为依赖书写位置。这条规则在这里被写成用例钉住。
 */
class PreResolveTest {

    /** 一个"参与预解析"的假标签：查询类标签的替身（list / query / detail 是别的代理的文件）。 */
    static final class SpyTag implements TagHandler {

        private final String name;
        final List<String> prepared = new ArrayList<>();
        List<String> preparedAtFirstRender;

        SpyTag(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<ParamSpec> params() {
            return List.of(ParamSpec.of("id", "夹具用"));
        }

        @Override
        public boolean preResolve() {
            return true;
        }

        @Override
        public void prepare(TagNode node, RenderContext ctx) {
            prepared.add(node.arg("id"));
        }

        @Override
        public int maxCount(PageType pageType) {
            return -1;
        }

        @Override
        public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
            if (preparedAtFirstRender == null) {
                preparedAtFirstRender = List.copyOf(prepared);
            }
            out.append('<').append(node.arg("id")).append('>');
        }
    }

    private static ContentItem 条目() {
        return item("id", 1L, "typeCode", RenderFixture.TYPE, "title", "T",
                "images", List.of(Map.of("url", "/a.jpg")));
    }

    private static TagNode spy(String id) {
        return selfClosing("spy", Map.of("id", id));
    }

    @Test
    void 预解析只下钻if的体() {
        SpyTag spy = new SpyTag("spy");
        TemplateRenderer renderer = RenderFixture.renderer(spy);
        Node[] template = {
                spy("top"),
                ifBody("images", List.of(spy("if"))),
                ifElse("images", List.of(), List.of(spy("else"))),
                foreach("images", Map.of(), List.of(spy("foreach")))
        };

        String out = RenderFixture.render(renderer, detail(条目()), template);

        // if 的体（两段）都下钻；foreach 的体不下钻；顺序 = 文档顺序
        assertEquals(List.of("top", "if", "else"), spy.prepared);
        // 预解析必须发生在渲染之前
        assertEquals(List.of("top", "if", "else"), spy.preparedAtFirstRender);
        // foreach 的体照旧渲染（只是不参与预解析）
        assertEquals("<top><if><foreach>", out);
    }

    @Test
    void include在渲染期报内部错误() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> render(detail(条目()),
                        selfClosing("include", Map.of("file", "header.html"))));
        assertTrue(error.getMessage().contains("编译期展开"), error.getMessage());
        assertTrue(error.getMessage().contains("header.html"), error.getMessage());
    }

    @Test
    void else在渲染期报内部错误() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> render(detail(条目()), selfClosing("else", Map.of())));
        assertTrue(error.getMessage().contains("elseIndex"), error.getMessage());
    }

    @Test
    void 结构标签在任何页面类型上个数组不限() {
        List<TagHandler> handlers = List.of(new IncludeTag(), new IfTag(), new ElseTag(), new ForeachTag());
        for (PageType pageType : PageType.values()) {
            for (TagHandler handler : handlers) {
                assertEquals(-1, handler.maxCount(pageType),
                        handler.name() + " 在 " + pageType + " 上应当不限个数");
            }
        }
    }

    @Test
    void 结构标签的四种写法声明与写法相符() {
        assertTrue(new IfTag().needsBody());
        assertTrue(new ForeachTag().needsBody());
        // 自闭合：{cms:include …/} 与 {cms:else/}
        assertFalse(new IncludeTag().needsBody());
        assertFalse(new ElseTag().needsBody());
        // 只有 include 接受任意额外参数（§6.2）
        assertTrue(new IncludeTag().openParams());
        assertFalse(new IfTag().openParams());
        assertFalse(new ForeachTag().openParams());
        assertFalse(new ElseTag().openParams());
    }

    @Test
    void 未知标签渲染期报E1001() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(detail(条目()), selfClosing("arclist", Map.of())));
        assertEquals(PublishErrorCode.E1001, error.code());
        assertTrue(error.getMessage().contains("未知标签 {cms:arclist}"), error.getMessage());
        assertTrue(error.getMessage().contains("可用标签见 §6.1"), error.getMessage());
    }
}
