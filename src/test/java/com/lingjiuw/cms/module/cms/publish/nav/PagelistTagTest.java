package com.lingjiuw.cms.module.cms.publish.nav;

import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.tag.PagelistTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.Recorder;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.args;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.bodyTag;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.listPage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code {cms:pagelist}}（static-publish.md §6.4）：页号窗口、当前页只发一次、{@code items} 过滤、
 * {@code label} 的来源、正文分页上 → E3003。
 */
class PagelistTagTest {

    private final PagelistTag tag = new PagelistTag(TestContentProvider.builder().build());

    @Test
    void 五页时第三页的窗口与当前项() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("pagelist", Map.of()), listPage(null, 3, 5), recorder, new StringBuilder());

        // first, prev, 页号窗口 1..5（当前页 3 由 current 承担）, next, last = 9 项
        assertEquals(9, recorder.size());
        assertEquals(List.of("首页", "上一页", "1", "2", "3", "4", "5", "下一页", "末页"),
                recorder.frames().stream().map(frame -> frame.get("label")).toList());
        assertEquals(List.of("first", "prev", "pageno", "pageno", "pageno", "pageno", "pageno",
                        "next", "last"),
                recorder.frames().stream().map(frame -> frame.get("type")).toList());
        assertEquals(List.of("", "prev", "", "", "", "", "", "next", ""),
                recorder.frames().stream().map(frame -> frame.get("rel")).toList());
        assertEquals(List.of("/news/", "/news/page-2/", "/news/", "/news/page-2/", "/news/page-3/",
                        "/news/page-4/", "/news/page-5/", "/news/page-4/", "/news/page-5/"),
                recorder.frames().stream().map(frame -> frame.get("url")).toList());
    }

    @Test
    void 当前页只出现一次() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("pagelist", Map.of()), listPage(null, 3, 5), recorder, new StringBuilder());

        long currentCount = recorder.frames().stream().filter(frame -> Boolean.TRUE.equals(frame.get("current")))
                .count();
        assertEquals(1, currentCount);
        assertEquals("current", recorder.frame(4).get("class"));
        assertEquals("", recorder.frame(3).get("class"));
        // 页号 3 只出现一次（由 current 那一项承担），且它带的是当前页 URL
        assertEquals(1, recorder.frames().stream().filter(frame -> "3".equals(frame.get("label"))).count());
    }

    @Test
    void items只给prevnext时只出两项() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("pagelist", args("items", "prev,next")), listPage(null, 3, 5), recorder,
                new StringBuilder());

        assertEquals(2, recorder.size());
        assertEquals("prev", recorder.frame(0).get("type"));
        assertEquals("next", recorder.frame(1).get("type"));
        assertEquals("/news/page-2/", recorder.frame(0).get("url"));
        assertEquals("/news/page-4/", recorder.frame(1).get("url"));
    }

    @Test
    void items同时含pageno与current时当前页不重复() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("pagelist", args("items", "pageno,current")), listPage(null, 2, 4), recorder,
                new StringBuilder());

        assertEquals(4, recorder.size());
        assertEquals(List.of("1", "2", "3", "4"),
                recorder.frames().stream().map(frame -> frame.get("label")).toList());
        assertEquals("pageno", recorder.frame(0).get("type"));
        assertEquals("current", recorder.frame(1).get("type"));
        assertTrue((Boolean) recorder.frame(1).get("current"));
    }

    @Test
    void 第一页时首页与上一页不出现() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("pagelist", Map.of()), listPage(null, 1, 3), recorder, new StringBuilder());

        assertEquals(List.of("1", "2", "3", "下一页", "末页"),
                recorder.frames().stream().map(frame -> frame.get("label")).toList());
    }

    @Test
    void listsize决定页号窗口大小() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("pagelist", args("listsize", "3", "items", "pageno")), listPage(null, 7, 10),
                recorder, new StringBuilder());

        assertEquals(List.of("6", "7", "8"), recorder.frames().stream().map(frame -> frame.get("label"))
                .toList());
    }

    @Test
    void label取自站点选项pager点labels() {
        PagelistTag custom = new PagelistTag(TestContentProvider.builder()
                .siteOption("pager.labels", "Top,Older,Newer,End").build());
        Recorder recorder = new Recorder();
        custom.render(bodyTag("pagelist", Map.of()), listPage(null, 2, 3), recorder, new StringBuilder());

        assertEquals("Top", recorder.frame(0).get("label"));
        assertEquals("Older", recorder.frame(1).get("label"));
        assertEquals("Newer", recorder.frame(5).get("label"));
        assertEquals("End", recorder.frame(6).get("label"));
    }

    @Test
    void 没有注入分页URL时url留空串() {
        RenderContext ctx = NavTestSupport.page(PageType.LIST, "article");
        ctx.putPage("pageNo", 2);
        ctx.putPage("totalPages", 3);
        ctx.putPage("paginationKind", "list");
        Recorder recorder = new Recorder();
        tag.render(bodyTag("pagelist", args("items", "prev")), ctx, recorder, new StringBuilder());

        assertEquals("", recorder.frame(0).get("url"));
    }

    @Test
    void 用在正文分页上报E3003() {
        RenderContext ctx = NavTestSupport.page(PageType.DETAIL, "article");
        ctx.putPage("pageNo", 2);
        ctx.putPage("totalPages", 3);
        ctx.putPage("paginationKind", "content");
        TagNode node = bodyTag("pagelist", Map.of());

        PublishException error = assertThrows(PublishException.class,
                () -> tag.render(node, ctx, new Recorder(), new StringBuilder()));
        assertEquals(PublishErrorCode.E3003, error.code());
        assertTrue(error.getMessage().contains("page.paginationKind = 'content'"), error.getMessage());
    }

    @Test
    void 只有一页时不渲染() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("pagelist", Map.of()), listPage(null, 1, 1), recorder, new StringBuilder());

        assertEquals(0, recorder.size());
    }

    @Test
    void 未声明的items取值报E1002并列出允许值() {
        TagNode node = bodyTag("pagelist", args("items", "pageno,next,foo"));

        PublishException error = assertThrows(PublishException.class,
                () -> tag.render(node, listPage(null, 1, 2), new Recorder(), new StringBuilder()));
        assertEquals(PublishErrorCode.E1002, error.code());
        assertTrue(error.getMessage().contains("期望 first / prev / pageno / next / last / current"),
                error.getMessage());
    }

    @Test
    void maxCount按矩阵填() {
        assertEquals(-1, tag.maxCount(PageType.HOME));
        assertEquals(-1, tag.maxCount(PageType.LIST));
        // TAGLIST（标签总览页，§7.2.1）没有分页主体：{cms:pagelist} 不合法
        assertEquals(0, tag.maxCount(PageType.TAGLIST));
        assertEquals(-1, tag.maxCount(PageType.TAGPAGE));
        assertEquals(-1, tag.maxCount(PageType.ARCHIVE));
        assertEquals(-1, tag.maxCount(PageType.DETAIL));
        assertEquals(-1, tag.maxCount(PageType.DPAGE));
        assertEquals(-1, tag.maxCount(PageType.FACET));
        assertEquals(1, tag.maxCount(PageType.STATIC));
        assertEquals(0, tag.maxCount(PageType.SINGLE));
        assertEquals(0, tag.maxCount(PageType.SEARCH));
        assertEquals(0, tag.maxCount(PageType.PAGE404));
        assertEquals(0, tag.maxCount(PageType.FEED));
    }

    @Test
    void 页面类型不合法时报E3012() {
        PublishException error = assertThrows(PublishException.class, () -> tag.render(
                bodyTag("pagelist", Map.of()), NavTestSupport.page(PageType.PAGE404, null), new Recorder(),
                new StringBuilder()));

        assertEquals(PublishErrorCode.E3012, error.code());
        assertTrue(error.getMessage().contains("当前页面类型是 PAGE404"), error.getMessage());
    }
}
