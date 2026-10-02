package com.lingjiuw.cms.module.cms.publish.nav;

import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.TestContentProvider.ContentSpec;
import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.tag.ArchiveTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.TagnavTag;
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
 * {@code {cms:tagnav}} / {@code {cms:archive}}（static-publish.md §6.4）：
 * {@code minCount} / {@code row} / {@code orderby} 生效、{@code count} 是内容数、{@code current} 口径。
 */
class TagnavArchiveTagTest {

    /** 标签：热点(1) / 推荐(2) / 最新(3)，内容数分别是 1 / 2 / 3。 */
    private final TestContentProvider tagProvider = TestContentProvider.builder()
            .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
            .tag(1, "hot", "热点")
            .tag(2, "rec", "推荐")
            .tag(3, "new", "最新")
            .content(ContentSpec.of(11, "article", "a", "文章A")
                    .publishTime("2026-05-01T10:00").tag("hot").tag("rec"))
            .content(ContentSpec.of(12, "article", "b", "文章B")
                    .publishTime("2026-05-02T10:00").tag("rec").tag("new"))
            .content(ContentSpec.of(13, "article", "c", "文章C")
                    .publishTime("2026-05-03T10:00").tag("new"))
            .content(ContentSpec.of(14, "article", "d", "文章D")
                    .publishTime("2026-05-04T10:00").tag("new"))
            .build();

    private final TagnavTag tagnav = new TagnavTag(tagProvider);
    private final ArchiveTag archive = new ArchiveTag(archiveProvider());

    private static TestContentProvider archiveProvider() {
        return TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .category(1, 0, "news", "新闻")
                .content(ContentSpec.of(11, "article", "a", "文章A")
                        .publishTime("2026-03-12T10:00").category("news"))
                .content(ContentSpec.of(12, "article", "b", "文章B")
                        .publishTime("2026-03-20T10:00").category("news"))
                .content(ContentSpec.of(13, "article", "c", "文章C")
                        .publishTime("2026-02-05T10:00").category("news"))
                .content(ContentSpec.of(14, "article", "d", "文章D").publishTime("2025-12-31T10:00"))
                .build();
    }

    /* ---------------- tagnav ---------------- */

    @Test
    void tagnav默认按内容数倒序() {
        Recorder recorder = new Recorder();
        tagnav.render(bodyTag("tagnav", Map.of()), page(PageType.TAGLIST, "article"), recorder,
                new StringBuilder());

        assertEquals(List.of("最新", "推荐", "热点"),
                recorder.frames().stream().map(frame -> frame.get("name")).toList());
        assertEquals(List.of(3L, 2L, 1L),
                recorder.frames().stream().map(frame -> frame.get("count")).toList());
        assertEquals(List.of("/tag/new/", "/tag/rec/", "/tag/hot/"),
                recorder.frames().stream().map(frame -> frame.get("url")).toList());
    }

    @Test
    void tagnav的minCount与row生效() {
        Recorder minCount = new Recorder();
        tagnav.render(bodyTag("tagnav", args("minCount", "2")), page(PageType.TAGLIST, "article"), minCount,
                new StringBuilder());
        Recorder row = new Recorder();
        tagnav.render(bodyTag("tagnav", args("row", "1")), page(PageType.TAGLIST, "article"), row,
                new StringBuilder());

        assertEquals(List.of("最新", "推荐"),
                minCount.frames().stream().map(frame -> frame.get("name")).toList());
        assertEquals(List.of("最新"), row.frames().stream().map(frame -> frame.get("name")).toList());
    }

    @Test
    void tagnav的orderby为name时按名称排() {
        Recorder recorder = new Recorder();
        tagnav.render(bodyTag("tagnav", args("orderby", "name")), page(PageType.TAGLIST, "article"), recorder,
                new StringBuilder());

        assertEquals(List.of("推荐", "最新", "热点"),
                recorder.frames().stream().map(frame -> frame.get("name")).toList());
    }

    @Test
    void tagnav在标签页上把当前标签标成current() {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("id", 3);
        channel.put("name", "最新");
        channel.put("label", "最新");
        channel.put("slug", "new");
        channel.put("url", "/tag/new/");
        Recorder recorder = new Recorder();
        tagnav.render(bodyTag("tagnav", Map.of()), page(PageType.TAGPAGE, "article", null, channel), recorder,
                new StringBuilder());

        assertEquals("current", recorder.frame(0).get("class"));
        assertEquals(true, recorder.frame(0).get("current"));
        assertEquals("", recorder.frame(1).get("class"));
        assertEquals(false, recorder.frame(1).get("current"));
    }

    @Test
    void tagnav的type限定只统计该类型() {
        TestContentProvider provider = TestContentProvider.builder()
                .type(1, "article", "文章", ContentTypeDef.Kind.CONTENT)
                .type(2, "product", "产品", ContentTypeDef.Kind.CONTENT)
                .tag(1, "hot", "热点")
                .content(ContentSpec.of(11, "article", "a", "文章A")
                        .publishTime("2026-05-01T10:00").tag("hot"))
                .content(ContentSpec.of(21, "product", "p", "产品P")
                        .publishTime("2026-05-01T10:00").tag("hot"))
                .build();
        Recorder recorder = new Recorder();
        new TagnavTag(provider).render(bodyTag("tagnav", args("type", "product")),
                page(PageType.TAGLIST, "product"), recorder, new StringBuilder());

        assertEquals(1, recorder.size());
        assertEquals(1L, recorder.frame(0).get("count"));
    }

    /* ---------------- archive ---------------- */

    @Test
    void archive按月给出label与内容数() {
        Recorder recorder = new Recorder();
        archive.render(bodyTag("archive", args("type", "article")), page(PageType.LIST, "article"), recorder,
                new StringBuilder());

        assertEquals(List.of("2026 年 3 月", "2026 年 2 月", "2025 年 12 月"),
                recorder.frames().stream().map(frame -> frame.get("label")).toList());
        assertEquals(List.of(2L, 1L, 1L),
                recorder.frames().stream().map(frame -> frame.get("count")).toList());
        assertEquals(List.of(3, 2, 12), recorder.frames().stream().map(frame -> frame.get("month")).toList());
    }

    @Test
    void archive的row与mode生效() {
        Recorder row = new Recorder();
        archive.render(bodyTag("archive", args("type", "article", "row", "1")),
                page(PageType.LIST, "article"), row, new StringBuilder());
        Recorder year = new Recorder();
        archive.render(bodyTag("archive", args("type", "article", "mode", "year")),
                page(PageType.LIST, "article"), year, new StringBuilder());

        assertEquals(List.of("2026 年 3 月"), row.frames().stream().map(frame -> frame.get("label")).toList());
        assertEquals(List.of("2026 年", "2025 年"),
                year.frames().stream().map(frame -> frame.get("label")).toList());
        assertEquals(List.of(3L, 1L), year.frames().stream().map(frame -> frame.get("count")).toList());
        assertEquals(List.of(0, 0), year.frames().stream().map(frame -> frame.get("month")).toList());
    }

    @Test
    void archive在归档页上把当前年月标成current() {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("year", 2026);
        channel.put("month", 2);
        channel.put("label", "2026 年 2 月");
        channel.put("url", "/archive/2026-02/");
        Recorder recorder = new Recorder();
        archive.render(bodyTag("archive", args("type", "article")), page(PageType.ARCHIVE, "article", null,
                channel), recorder, new StringBuilder());

        assertEquals("", recorder.frame(0).get("class"));
        assertEquals("current", recorder.frame(1).get("class"));
        assertEquals(true, recorder.frame(1).get("current"));
    }

    @Test
    void archive的category缺省取当前栏目() {
        Recorder onCategory = new Recorder();
        archive.render(bodyTag("archive", Map.of()),
                page(PageType.LIST, "article", null, categoryChannel(1, "新闻", "news")), onCategory,
                new StringBuilder());
        Recorder onHome = new Recorder();
        archive.render(bodyTag("archive", args("type", "article")), page(PageType.HOME, null), onHome,
                new StringBuilder());

        // 当前栏目下只有 3 月与 2 月；首页没有"当前栏目"，因此不限栏目（多出 2025 年 12 月）
        assertEquals(List.of("2026 年 3 月", "2026 年 2 月"),
                onCategory.frames().stream().map(frame -> frame.get("label")).toList());
        assertEquals(3, onHome.size());
    }

    /* ---------------- 矩阵 ---------------- */

    @Test
    void 两个标签的maxCount都只在feed上不合法() {
        for (PageType pageType : PageType.values()) {
            int expected = pageType == PageType.FEED ? 0 : -1;
            assertEquals(expected, tagnav.maxCount(pageType), "tagnav " + pageType);
            assertEquals(expected, archive.maxCount(pageType), "archive " + pageType);
        }
    }

    @Test
    void feed上报E3012() {
        PublishException tagnavError = assertThrows(PublishException.class, () -> tagnav.render(
                bodyTag("tagnav", Map.of()), page(PageType.FEED, null), new Recorder(), new StringBuilder()));
        PublishException archiveError = assertThrows(PublishException.class, () -> archive.render(
                bodyTag("archive", Map.of()), page(PageType.FEED, null), new Recorder(), new StringBuilder()));

        assertEquals(PublishErrorCode.E3012, tagnavError.code());
        assertEquals(PublishErrorCode.E3012, archiveError.code());
    }
}
