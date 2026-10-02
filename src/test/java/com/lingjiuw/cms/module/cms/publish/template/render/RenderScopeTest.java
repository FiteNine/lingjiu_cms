package com.lingjiuw.cms.module.cms.publish.template.render;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.detail;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.field;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.foreach;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.item;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.render;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §5.1 的作用域栈与字段解析：**就近**（循环项 &gt; 当前条目）、**无前缀只在匿名栈帧里找**、
 * 以及 §5.2.1 的转义边界（模板文本永不转义）。
 */
class RenderScopeTest {

    private static ContentItem 产品(String title) {
        return item("id", 1L, "typeCode", RenderFixture.TYPE, "title", title,
                "images", List.of(Map.of("url", "/a.jpg", "title", "图1"),
                        Map.of("url", "/b.jpg", "title", "图2")));
    }

    @Test
    void 循环项优先于当前条目() {
        RenderContext ctx = detail(产品("页面标题"));

        // 循环外：栈底 = 当前条目
        assertEquals("页面标题", render(ctx, field("title")));

        // 循环里：栈顶 = 迭代项（§5.1 的"就近"，循环项永远优先于当前条目）
        assertEquals("图1图2", render(detail(产品("页面标题")),
                foreach("images", Map.of(), List.of(field("title")))));
    }

    @Test
    void 循环里用item前缀取页面级字段() {
        assertEquals("页面标题|/a.jpg;页面标题|/b.jpg;", render(detail(产品("页面标题")),
                foreach("images", Map.of(), List.of(
                        field("item.title"), text("|"), field("url"), text(";")))));
    }

    @Test
    void 无前缀字段不命中具名作用域() {
        RenderContext ctx = detail(产品("页面标题"));
        ctx.putPage("url", "/page-url");
        ctx.pushNamed("site", Map.of("url", "https://example.com"));
        ctx.pushNamed("channel", Map.of("url", "/category/news/"));

        // [field:url/] 只在匿名栈帧里找：page.url / site.url / channel.url 都不算数（§5.1 第（3）条）
        PublishException error = assertThrows(PublishException.class, () -> render(ctx, field("url")));
        assertEquals(PublishErrorCode.E1004, error.code());
        assertTrue(error.getMessage().contains("字段 url 不存在"), error.getMessage());
        assertTrue(error.actual().contains("title"), "报错要列出可用的字段：" + error.actual());

        // 写前缀才取得到
        assertEquals("/page-url", render(ctx, field("page.url")));
    }

    @Test
    void 字段输出转义五个字符() {
        RenderContext ctx = detail(产品("a&b<c>d\"e'f"));
        assertEquals("a&amp;b&lt;c&gt;d&quot;e&#39;f", render(ctx, field("title")));
    }

    @Test
    void 模板文本永不转义() {
        assertEquals("<div class=\"x\">&</div>", render(detail(产品("T")),
                text("<div class=\"x\">&</div>")));
    }

    @Test
    void 富文本按字段声明原样输出() {
        // content 是内置的 RICHTEXT 声明（raw=1）：模板作者无权关掉清洗，也无权打开转义（§5.2.1）
        RenderContext ctx = detail(item("id", 1L, "typeCode", RenderFixture.TYPE,
                "content", "<p>hi &amp; bye</p>"));
        assertEquals("<p>hi &amp; bye</p>", render(ctx, field("content")));
    }

    @Test
    void 多行文本默认输出转义并把换行变成br() {
        RenderContext ctx = detail(item("id", 1L, "typeCode", RenderFixture.TYPE,
                "summary", "a&b\nc"));
        assertEquals("a&amp;b<br>c", render(ctx, field("summary")));
    }

    @Test
    void 字段为空时输出空串不报错() {
        RenderContext ctx = detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "title", null));
        assertEquals("", render(ctx, field("title")));
    }

    @Test
    void 循环结束后循环项从栈上弹掉() {
        PublishException error = assertThrows(PublishException.class, () -> render(detail(产品("T")),
                foreach("images", Map.of(), List.of(field("url"))), field("url")));
        assertEquals(PublishErrorCode.E1004, error.code());
    }

    @Test
    void 具名作用域不存在时报E1004并说明该作用域() {
        // 首页没有 channel（§5.1 第（4）条：HOME / SEARCH / 404 都没有"浏览位置"）
        PublishException error = assertThrows(PublishException.class,
                () -> render(RenderFixture.home(), field("channel.label")));
        assertEquals(PublishErrorCode.E1004, error.code());
        assertTrue(error.actual().contains("channel"), error.actual());
    }
}
