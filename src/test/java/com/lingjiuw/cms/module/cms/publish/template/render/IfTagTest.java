package com.lingjiuw.cms.module.cms.publish.template.render;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.detail;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.ifBody;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.ifElse;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.item;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.render;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §3.7 裁定一：{@code {cms:if}} 的真假判定表（判定顺序写死在
 * {@link RenderContext#truthy}，标签只负责"取字段"与"选哪一段体"）。
 */
class IfTagTest {

    static Stream<Arguments> 判定表() {
        return Stream.of(
                // 假：null / trim 后空串 / "0" / "false"（忽略大小写）/ 数字 0 / 布尔 false /
                // 空列表与空 jsonb / 按数字字面量解析为 0 的字符串
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of("   ", false),
                Arguments.of("0", false),
                Arguments.of("0.0", false),
                Arguments.of("0.00", false),
                Arguments.of("00", false),
                Arguments.of("false", false),
                Arguments.of("FALSE", false),
                Arguments.of("False", false),
                Arguments.of(0, false),
                Arguments.of(0L, false),
                Arguments.of(0.0d, false),
                Arguments.of(new BigDecimal("0.00"), false),
                Arguments.of(false, false),
                Arguments.of(List.of(), false),
                Arguments.of(Map.of(), false),
                // 真：其余任何内容
                Arguments.of("1", true),
                Arguments.of("0.5", true),
                Arguments.of("-1", true),
                Arguments.of("about", true),
                Arguments.of(3, true),
                Arguments.of(true, true),
                Arguments.of(List.of("x"), true),
                Arguments.of(Map.of("k", "v"), true));
    }

    @ParameterizedTest(name = "值 {0} → {1}")
    @MethodSource("判定表")
    void 判定顺序写死的真假表(Object value, boolean expected) {
        RenderContext ctx = detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "x", value));
        String out = render(ctx, ifElse("x", List.of(text("T")), List.of(text("F"))));
        assertEquals(expected ? "T" : "F", out);
    }

    @Test
    void 没有else时判假就什么都不渲染() {
        RenderContext ctx = detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "x", "0"));
        assertEquals("", render(ctx, ifBody("x", List.of(text("T")))));
    }

    @Test
    void 字段名不存在是报错不是判假() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "title", "T")),
                        ifBody("nosuch", List.of(text("T")))));
        assertEquals(PublishErrorCode.E1004, error.code());
        assertTrue(error.getMessage().contains("字段 nosuch 不存在"), error.getMessage());
        assertTrue(error.actual().contains("title"), error.actual());
    }

    @Test
    void 具名作用域字段可用于if() {
        RenderContext ctx = detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "title", "T"));
        ctx.putPage("empty", true);
        assertEquals("暂无内容", render(ctx, ifBody("page.empty", List.of(text("暂无内容")))));
        ctx.putPage("empty", false);
        assertEquals("", render(ctx, ifBody("page.empty", List.of(text("暂无内容")))));
    }

    @Test
    void 空体渲染空串() {
        RenderContext ctx = detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "title", "T"));
        assertEquals("", render(ctx, ifBody("title", List.of())));
    }

    @Test
    void field参数为空报E1002() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "title", "T")),
                        ifBody("", List.of(text("T")))));
        assertEquals(PublishErrorCode.E1002, error.code());
    }

    @Test
    void 多段路径的中间段断掉报E1005() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "title", "T")),
                        ifBody("title.nosuch", List.of(text("T")))));
        assertEquals(PublishErrorCode.E1005, error.code());
        assertTrue(error.getMessage().contains("在第 2 段断掉"), error.getMessage());
    }
}
