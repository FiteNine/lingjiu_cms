package com.lingjiuw.cms.module.cms.publish.template.render;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.detail;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.field;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.item;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.render;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字段路径的报错路径（§10.2）：E1004"字段名不在可用集合"与 E1005"路径中间段断掉 / {@code .count} 用错"。
 * 文案按 §10.1 的三行格式，由 {@link PublishException} 统一拼出。
 */
class RenderErrorTest {

    private static RenderContext 条目() {
        return detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "title", "页面标题",
                "images", List.of(Map.of("url", "/a.jpg"))));
    }

    @Test
    void 字段名不存在报E1004并列出可用字段() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(条目(), field("nosuch")));
        assertEquals(PublishErrorCode.E1004, error.code());
        assertTrue(error.getMessage().contains("字段 nosuch 不存在"), error.getMessage());
        // §10.3：能列清单就列清单——现状里必须是"可用的字段有：…"
        assertTrue(error.actual().contains("可用的字段有"), error.actual());
        assertTrue(error.actual().contains("title"), error.actual());
        // §10.1：模板错误必须带 模板相对路径:行号
        assertEquals(RenderFixture.PATH, error.templatePath());
        assertEquals(7, error.lineNo());
    }

    @Test
    void 空图集上按下标取子字段报E1005() {
        RenderContext ctx = detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "title", "T",
                "images", List.of()));
        PublishException error = assertThrows(PublishException.class,
                () -> render(ctx, field("images.0.url")));
        assertEquals(PublishErrorCode.E1005, error.code());
        // §10.2 的原例：路径 images.0.url 在第 2 段断掉
        assertTrue(error.getMessage().contains("路径 images.0.url 在第 2 段断掉"), error.getMessage());
        assertTrue(error.actual().contains("count"), error.actual());
    }

    @Test
    void 下标越界报E1005() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(条目(), field("images.5.url")));
        assertEquals(PublishErrorCode.E1005, error.code());
        assertTrue(error.getMessage().contains("在第 2 段断掉"), error.getMessage());
    }

    @Test
    void count用在标量字段上报E1005并说明理由() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(条目(), field("title.count")));
        assertEquals(PublishErrorCode.E1005, error.code());
        assertTrue(error.getMessage().contains("路径 title.count 在第 2 段断掉"), error.getMessage());
        assertTrue(error.advice().contains("count 只对多值字段有效"), error.advice());
    }

    @Test
    void count用在多值字段上合法() {
        assertEquals("1", render(条目(), field("images.count")));
    }
}
