package com.lingjiuw.cms.module.cms.publish.template.render;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.detail;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.field;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.foreach;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.item;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.ordered;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.render;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §6.2 的 {@code {cms:foreach}}：迭代手上的多值字段、每项给
 * {@code index} / {@code index0} / {@code isFirst} / {@code isLast} / {@code count}，
 * {@code row} 截断、{@code offset} 跳过、{@code orderby} 排序（**都不是分页**）。
 */
class ForeachTagTest {

    private static ContentItem 图集() {
        return item("id", 1L, "typeCode", RenderFixture.TYPE,
                "images", List.of(Map.of("url", "/a.jpg"), Map.of("url", "/b.jpg"), Map.of("url", "/c.jpg")));
    }

    @Test
    void 每项提供索引与位置字段() {
        String out = render(detail(图集()), foreach("images", Map.of(), List.of(
                field("index"), text(":"), field("url"), text(":"),
                field("isFirst"), field("isLast"), text(":"), field("count"), text(";"))));
        assertEquals("1:/a.jpg:10:3;2:/b.jpg:00:3;3:/c.jpg:01:3;", out);
    }

    @Test
    void index0从0起() {
        assertEquals("0,1,2,", render(detail(图集()), foreach("images", Map.of(), List.of(
                field("index0"), text(",")))));
    }

    @Test
    void row截断且count跟着变() {
        // count 是"这次真正迭代了几项"：否则截断之后 isLast 永远不为真
        assertEquals("/a.jpg1/2;/b.jpg2/2;", render(detail(图集()), foreach("images", Map.of("row", "2"),
                List.of(field("url"), field("index"), text("/"), field("count"), text(";")))));
    }

    @Test
    void offset跳过前N条() {
        assertEquals("/b.jpg1/2;/c.jpg2/2;", render(detail(图集()), foreach("images", Map.of("offset", "1"),
                List.of(field("url"), field("index"), text("/"), field("count"), text(";")))));
    }

    @Test
    void row与offset一起用时先跳过再截断() {
        assertEquals("/b.jpg;", render(detail(图集()), foreach("images", Map.of("offset", "1", "row", "1"),
                List.of(field("url"), text(";")))));
    }

    @Test
    void orderby按迭代项内的字段排序() {
        ContentItem entry = item("id", 1L, "typeCode", RenderFixture.TYPE, "images", List.of(
                ordered("url", "/a.jpg", "sort", 2),
                ordered("url", "/b.jpg", "sort", 1)));
        assertEquals("/b.jpg/a.jpg", render(detail(entry),
                foreach("images", Map.of("orderby", "sort"), List.of(field("url")))));
        assertEquals("/a.jpg/b.jpg", render(detail(entry),
                foreach("images", Map.of("orderby", "sort", "order", "desc"), List.of(field("url")))));
    }

    @Test
    void 嵌套两层时内层取内层项() {
        ContentItem entry = item("id", 1L, "typeCode", RenderFixture.TYPE, "images", List.of(
                ordered("url", "/a.jpg", "specs", ordered("width", "800")),
                ordered("url", "/b.jpg", "specs", ordered("height", "600"))));
        String out = render(detail(entry), foreach("images", Map.of(), List.of(
                field("url"), text(":"),
                foreach("specs", Map.of(), List.of(
                        field("key"), text("="), field("value"), text(";"))),
                text("|"))));
        assertEquals("/a.jpg:width=800;|/b.jpg:height=600;|", out);
    }

    @Test
    void 标量项提供value() {
        ContentItem entry = item("id", 1L, "typeCode", RenderFixture.TYPE, "tags", List.of("标签一", "标签二"));
        assertEquals("标签一,标签二,", render(detail(entry),
                foreach("tags", Map.of(), List.of(field("value"), text(",")))));
    }

    @Test
    void 空集与null都不渲染也不报错() {
        assertEquals("", render(detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "files", null)),
                foreach("files", Map.of(), List.of(text("X")))));
        assertEquals("", render(detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "images", List.of())),
                foreach("images", Map.of(), List.of(text("X")))));
    }

    @Test
    void 非多值字段报E1002() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "title", "T")),
                        foreach("title", Map.of(), List.of(text("X")))));
        assertEquals(PublishErrorCode.E1002, error.code());
        assertTrue(error.getMessage().contains("不是多值字段"), error.getMessage());
        assertTrue(error.getMessage().contains("images"), error.getMessage());
    }

    @Test
    void 字段名不存在时报E1004() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(detail(图集()), foreach("nosuch", Map.of(), List.of(text("X")))));
        assertEquals(PublishErrorCode.E1004, error.code());
    }

    @Test
    void 循环结束后项从栈上弹掉() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(detail(图集()), foreach("images", Map.of(), List.of(field("url"))),
                        field("url")));
        assertEquals(PublishErrorCode.E1004, error.code());
    }

    @Test
    void 嵌套两层时内层用完后外层仍然可见() {
        ContentItem entry = item("id", 1L, "typeCode", RenderFixture.TYPE, "images", List.of(
                ordered("url", "/a.jpg", "specs", ordered("width", "800"))));
        // 内层循环压栈后弹掉，[field:url/] 又回到外层的迭代项
        assertEquals("800|/a.jpg", render(detail(entry), foreach("images", Map.of(), List.of(
                foreach("specs", Map.of(), List.of(field("value"))),
                text("|"), field("url")))));
    }

    @Test
    void 迭代页面的其他多值字段时不污染页面级字段() {
        ContentItem entry = item("id", 1L, "typeCode", RenderFixture.TYPE, "title", "页面标题",
                "images", List.of(Map.of("url", "/a.jpg")));
        assertEquals("页面标题/a.jpg", render(detail(entry), foreach("images", Map.of(), List.of(
                field("item.title"), field("url")))));
    }

    @Test
    void orderby缺失的排序键按空值排且顺序稳定() {
        List<Map<String, Object>> images = new ArrayList<>();
        images.add(ordered("url", "/a.jpg"));
        images.add(ordered("url", "/b.jpg"));
        assertEquals("/a.jpg/b.jpg", render(detail(item("id", 1L, "typeCode", RenderFixture.TYPE,
                "images", images)), foreach("images", Map.of("orderby", "sort"), List.of(field("url")))));
    }
}
