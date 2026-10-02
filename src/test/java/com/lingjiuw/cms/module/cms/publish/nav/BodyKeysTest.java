package com.lingjiuw.cms.module.cms.publish.nav;

import com.lingjiuw.cms.module.cms.publish.TestContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.tag.ArchiveTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.BreadcrumbTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.ChannelTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.FormTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.PagelistTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.PrenextTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.TagnavTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 七个标签的 {@code bodyKeys}（§6.3 的"统一产出字段契约" + §6.4 各标签的"迭代产出"）。
 *
 * <p>这是 §4.5 第 4 条（字段名是否存在于该模板上下文可用的字段集合 → E1004）在**这七个标签体内**
 * 唯一的输入：不给它，校验器只能把标签体的那一帧当开放帧，体内写错字段名就静默通过。
 */
class BodyKeysTest {

    private final TestContentProvider provider = TestContentProvider.builder().build();

    @Test
    void channel体内字段来自统一产出契约与菜单列() {
        Set<String> keys = new ChannelTag(provider).bodyKeys(PageType.LIST);

        assertTrue(keys.containsAll(Set.of("id", "name", "label", "slug", "url", "current", "class",
                "children", "count")), keys.toString());
        // source='menu' 的两列（§2.4）与 source='type'/'facet' 各自多出来的字段
        assertTrue(keys.containsAll(Set.of("target", "rel", "typeCode", "facetPath", "urlWith")),
                keys.toString());
        // 写错的字段名必须不在集合里，否则 E1004 报不出来
        assertFalse(keys.contains("nmae"));
        assertFalse(keys.contains("ur"));
    }

    @Test
    void pagelist体内字段是六项() {
        Set<String> keys = new PagelistTag(provider).bodyKeys(PageType.LIST);

        assertEquals(Set.of("type", "label", "url", "current", "class", "rel"), keys);
    }

    @Test
    void breadcrumb体内字段含level() {
        Set<String> keys = new BreadcrumbTag(provider).bodyKeys(PageType.DETAIL);

        assertEquals(Set.of("name", "url", "current", "class", "level"), keys);
    }

    @Test
    void prenext体内字段是四项() {
        Set<String> keys = new PrenextTag(provider).bodyKeys(PageType.DETAIL);

        assertEquals(Set.of("type", "title", "url", "publishTime"), keys);
    }

    @Test
    void tagnav与archive体内字段() {
        assertEquals(Set.of("id", "name", "slug", "url", "count", "current", "class"),
                new TagnavTag(provider).bodyKeys(PageType.LIST));
        assertEquals(Set.of("year", "month", "label", "url", "count", "current", "class"),
                new ArchiveTag(provider).bodyKeys(PageType.LIST));
    }

    @Test
    void form没有标签体因此声明不了字段() {
        assertNull(new FormTag(provider).bodyKeys(PageType.DETAIL));
    }
}
