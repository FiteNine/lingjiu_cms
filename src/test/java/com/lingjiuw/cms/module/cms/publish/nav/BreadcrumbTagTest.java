package com.lingjiuw.cms.module.cms.publish.nav;

import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.TestContentProvider.ContentSpec;
import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.tag.BreadcrumbTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.Recorder;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.args;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.bodyTag;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.categoryChannel;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.page;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code {cms:breadcrumb}}（static-publish.md §6.4）：层级构成写死为
 * {@code 起点 → 祖先分类 → [父内容链] → 当前项}；章节页最后一级是章、倒数第二级是书。
 */
class BreadcrumbTagTest {

    private final TestContentProvider provider = TestContentProvider.builder()
            .type(1, "book", "书", ContentTypeDef.Kind.TREE)
            .type(2, "chapter", "章", ContentTypeDef.Kind.TREE)
            .category(1, 0, "novel", "小说")
            .content(ContentSpec.of(100, "book", "book-a", "书A")
                    .publishTime("2026-05-01T10:00").category("novel"))
            .content(ContentSpec.of(101, "chapter", "ch-1", "第一章")
                    .parent(100).publishTime("2026-05-02T10:00").category("novel"))
            .build();

    private final BreadcrumbTag tag = new BreadcrumbTag(provider);

    private RenderContext chapterPage(PageType pageType) {
        ContentItem entry = provider.contentById(101);
        return page(pageType, "chapter", entry, categoryChannel(1, "小说", "novel"));
    }

    @Test
    void 章节页withContent为1时四级且书在倒数第二级() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("breadcrumb", Map.of()), chapterPage(PageType.DETAIL), recorder, new StringBuilder());

        assertEquals(4, recorder.size());
        assertEquals(List.of("测试站", "小说", "书A", "第一章"),
                recorder.frames().stream().map(frame -> frame.get("name")).toList());
        assertEquals(List.of("/", "/novel/", "/book/book-a.html", "/chapter/ch-1.html"),
                recorder.frames().stream().map(frame -> frame.get("url")).toList());
        assertEquals(List.of(1, 2, 3, 4), recorder.frames().stream().map(frame -> frame.get("level")).toList());
        assertEquals(true, recorder.frame(3).get("current"));
        assertEquals("current", recorder.frame(3).get("class"));
        assertEquals(false, recorder.frame(2).get("current"));
        assertEquals("", recorder.frame(2).get("class"));
    }

    @Test
    void 承载了可分页目录的详情页也一样() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("breadcrumb", Map.of()), chapterPage(PageType.DPAGE), recorder, new StringBuilder());

        assertEquals(4, recorder.size());
        assertEquals("书A", recorder.frame(2).get("name"));
        assertEquals("第一章", recorder.frame(3).get("name"));
        assertEquals(true, recorder.frame(3).get("current"));
    }

    @Test
    void withContent为0时只到分类层() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("breadcrumb", args("withContent", "0")), chapterPage(PageType.DETAIL), recorder,
                new StringBuilder());

        assertEquals(List.of("测试站", "小说"),
                recorder.frames().stream().map(frame -> frame.get("name")).toList());
        assertEquals(true, recorder.frame(1).get("current"));
    }

    @Test
    void from为channel时从当前栏目起() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("breadcrumb", args("from", "channel")), chapterPage(PageType.DETAIL), recorder,
                new StringBuilder());

        assertEquals(List.of("小说", "书A", "第一章"),
                recorder.frames().stream().map(frame -> frame.get("name")).toList());
    }

    @Test
    void contentLabel决定内容层显示什么() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("breadcrumb", args("contentLabel", "categoryName")), chapterPage(PageType.DETAIL),
                recorder, new StringBuilder());

        assertEquals(List.of("测试站", "小说", "小说", "小说"),
                recorder.frames().stream().map(frame -> frame.get("name")).toList());
    }

    @Test
    void 分类页上当前分类只出现一次() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("breadcrumb", Map.of()),
                page(PageType.LIST, "article", null, categoryChannel(1, "小说", "novel")), recorder,
                new StringBuilder());

        assertEquals(List.of("测试站", "小说"),
                recorder.frames().stream().map(frame -> frame.get("name")).toList());
        assertEquals(List.of(1, 2), recorder.frames().stream().map(frame -> frame.get("level")).toList());
        assertEquals(true, recorder.frame(1).get("current"));
    }

    @Test
    void 标签页与归档页的当前项来自channel() {
        Recorder tagPage = new Recorder();
        Map<String, Object> tagChannel = new LinkedHashMap<>();
        tagChannel.put("id", 3);
        tagChannel.put("name", "最新");
        tagChannel.put("label", "最新");
        tagChannel.put("slug", "new");
        tagChannel.put("url", "/tag/new/");
        tagPage(tagPage, tagChannel);

        assertEquals(List.of("测试站", "最新"),
                tagPage.frames().stream().map(frame -> frame.get("name")).toList());

        Recorder archivePage = new Recorder();
        Map<String, Object> archiveChannel = new LinkedHashMap<>();
        archiveChannel.put("year", 2026);
        archiveChannel.put("month", 3);
        archiveChannel.put("label", "2026 年 3 月");
        archiveChannel.put("url", "/archive/2026-03/");
        tag.render(bodyTag("breadcrumb", Map.of()), page(PageType.ARCHIVE, "article", null, archiveChannel),
                archivePage, new StringBuilder());

        assertEquals(List.of("测试站", "2026 年 3 月"),
                archivePage.frames().stream().map(frame -> frame.get("name")).toList());
    }

    private void tagPage(Recorder recorder, Map<String, Object> channel) {
        tag.render(bodyTag("breadcrumb", Map.of()), page(PageType.TAGPAGE, "article", null, channel), recorder,
                new StringBuilder());
    }

    @Test
    void maxCount按矩阵填() {
        assertEquals(0, tag.maxCount(PageType.HOME));
        assertEquals(0, tag.maxCount(PageType.SEARCH));
        assertEquals(0, tag.maxCount(PageType.STATIC));
        assertEquals(0, tag.maxCount(PageType.PAGE404));
        assertEquals(0, tag.maxCount(PageType.FEED));
        assertEquals(-1, tag.maxCount(PageType.LIST));
        assertEquals(-1, tag.maxCount(PageType.TAGLIST));
        assertEquals(-1, tag.maxCount(PageType.TAGPAGE));
        assertEquals(-1, tag.maxCount(PageType.ARCHIVE));
        assertEquals(-1, tag.maxCount(PageType.DETAIL));
        assertEquals(-1, tag.maxCount(PageType.DPAGE));
        assertEquals(-1, tag.maxCount(PageType.SINGLE));
        assertEquals(-1, tag.maxCount(PageType.FACET));
    }

    @Test
    void 首页上报E3012() {
        PublishException error = assertThrows(PublishException.class, () -> tag.render(
                bodyTag("breadcrumb", Map.of()), page(PageType.HOME, null), new Recorder(),
                new StringBuilder()));

        assertEquals(PublishErrorCode.E3012, error.code());
    }
}
