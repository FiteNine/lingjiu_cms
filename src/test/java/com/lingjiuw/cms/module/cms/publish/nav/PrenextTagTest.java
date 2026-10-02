package com.lingjiuw.cms.module.cms.publish.nav;

import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.TestContentProvider.ContentSpec;
import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.tag.PrenextTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.Recorder;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.args;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.bodyTag;
import static com.lingjiuw.cms.module.cms.publish.nav.NavTestSupport.page;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code {cms:prenext}}（static-publish.md §6.4）：{@code within='parent'} 只在同一父内容内相邻、
 * 不存在时不渲染该次迭代、{@code category} 的默认值就是 {@code all}。
 *
 * <p>书 A（章 1/2/3）与书 B（章 1/2）用类型默认排序 {@code sort asc} 排，且 {@code sort} 在全类型内
 * 唯一（1..5），所以"上一篇/下一篇"的顺序是确定的：{@code 甲一 → 甲二 → 甲三 → 乙一 → 乙二}。
 */
class PrenextTagTest {

    private final TestContentProvider provider = TestContentProvider.builder()
            .type(1, "book", "书", ContentTypeDef.Kind.TREE)
            .type(2, "chapter", "章", ContentTypeDef.Kind.TREE)
            .sort("chapter", "sort", "asc")
            .content(ContentSpec.of(100, "book", "book-a", "书A").publishTime("2026-05-01T10:00"))
            .content(ContentSpec.of(200, "book", "book-b", "书B").publishTime("2026-05-01T10:00"))
            .content(ContentSpec.of(101, "chapter", "a-1", "甲章一").parent(100).sort(1)
                    .publishTime("2026-05-02T10:00"))
            .content(ContentSpec.of(102, "chapter", "a-2", "甲章二").parent(100).sort(2)
                    .publishTime("2026-05-03T10:00"))
            .content(ContentSpec.of(103, "chapter", "a-3", "甲章三").parent(100).sort(3)
                    .publishTime("2026-05-04T10:00"))
            .content(ContentSpec.of(201, "chapter", "b-1", "乙章一").parent(200).sort(4)
                    .publishTime("2026-05-05T10:00"))
            .content(ContentSpec.of(202, "chapter", "b-2", "乙章二").parent(200).sort(5)
                    .publishTime("2026-05-06T10:00"))
            .build();

    private final PrenextTag tag = new PrenextTag(provider);

    private RenderContext chapterPage(long contentId) {
        ContentItem entry = provider.contentById(contentId);
        return page(PageType.DETAIL, "chapter", entry, null);
    }

    @Test
    void 中间章的上一篇与下一篇都在同一本书内() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("prenext", args("within", "parent")), chapterPage(102), recorder,
                new StringBuilder());

        assertEquals(2, recorder.size());
        assertEquals("prev", recorder.frame(0).get("type"));
        assertEquals("甲章一", recorder.frame(0).get("title"));
        assertEquals("/chapter/a-1.html", recorder.frame(0).get("url"));
        assertEquals("next", recorder.frame(1).get("type"));
        assertEquals("甲章三", recorder.frame(1).get("title"));
        assertEquals("2026-05-04T10:00", recorder.frame(1).get("publishTime").toString());
    }

    @Test
    void 首章时prev不渲染() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("prenext", args("within", "parent")), chapterPage(101), recorder,
                new StringBuilder());

        assertEquals(1, recorder.size());
        assertEquals("next", recorder.frame(0).get("type"));
        assertEquals("甲章二", recorder.frame(0).get("title"));
    }

    @Test
    void 末章时next不渲染且不串到下一本书() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("prenext", args("within", "parent")), chapterPage(103), recorder,
                new StringBuilder());

        assertEquals(1, recorder.size());
        assertEquals("prev", recorder.frame(0).get("type"));
        assertEquals("甲章二", recorder.frame(0).get("title"));
    }

    @Test
    void within为type时会跨书而parent不会() {
        Recorder across = new Recorder();
        tag.render(bodyTag("prenext", args("within", "type")), chapterPage(103), across, new StringBuilder());
        Recorder withinParent = new Recorder();
        tag.render(bodyTag("prenext", args("within", "parent")), chapterPage(103), withinParent,
                new StringBuilder());

        assertEquals(2, across.size());
        assertEquals("乙章一", across.frame(1).get("title"));
        assertEquals(1, withinParent.size());
    }

    @Test
    void type只给prev时只出一个方向() {
        Recorder recorder = new Recorder();
        tag.render(bodyTag("prenext", args("type", "prev")), chapterPage(102), recorder, new StringBuilder());

        assertEquals(1, recorder.size());
        assertEquals("prev", recorder.frame(0).get("type"));
    }

    @Test
    void category的默认值就是all() {
        Recorder implicit = new Recorder();
        tag.render(bodyTag("prenext", Map.of()), chapterPage(102), implicit, new StringBuilder());
        Recorder explicit = new Recorder();
        tag.render(bodyTag("prenext", args("category", "all")), chapterPage(102), explicit, new StringBuilder());

        assertEquals(implicit.frames().stream().map(frame -> frame.get("title")).toList(),
                explicit.frames().stream().map(frame -> frame.get("title")).toList());
        assertEquals(2, implicit.size());
    }

    @Test
    void maxCount只在详情页合法() {
        for (PageType pageType : PageType.values()) {
            int expected = pageType == PageType.DETAIL || pageType == PageType.DPAGE ? -1 : 0;
            assertEquals(expected, tag.maxCount(pageType), pageType.name());
        }
    }

    @Test
    void 列表页上报E3012() {
        PublishException error = assertThrows(PublishException.class, () -> tag.render(
                bodyTag("prenext", Map.of()), page(PageType.LIST, "chapter"), new Recorder(),
                new StringBuilder()));

        assertEquals(PublishErrorCode.E3012, error.code());
    }
}
