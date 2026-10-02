package com.lingjiuw.cms.module.cms.publish.template.render;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.EnumOption;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import com.lingjiuw.cms.module.cms.publish.template.FieldOutput;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.detail;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.field;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.item;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.render;
import static com.lingjiuw.cms.module.cms.publish.template.render.RenderFixture.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §2.2 的 formatter：调用语法（规范形式 / 快捷形式 / 单 formatter / 组合合法性）与逐个 formatter
 * 的输出。字段类型取自字段声明（内置表 + 用例注入的自定义字段）。
 */
class FieldOutputTest {

    /** 自定义 DECIMAL 字段：文档里的 `price` 就是它（内置表里没有 price）。 */
    private static RenderContext 商品(Object... keyValues) {
        RenderContext ctx = detail(item(keyValues));
        FieldDef price = FieldDef.builder(RenderFixture.TYPE, "price", FieldType.DECIMAL).build();
        FieldDef state = FieldDef.builder(RenderFixture.TYPE, "state", FieldType.ENUM)
                .options(List.of(new EnumOption("1", "已发布"), new EnumOption("0", "草稿")))
                .build();
        FieldDef seconds = FieldDef.builder(RenderFixture.TYPE, "seconds", FieldType.INT).build();
        ctx.fieldDefLookup((type, code) -> switch (code) {
            case "price" -> price;
            case "state" -> state;
            case "seconds" -> seconds;
            default -> null;
        });
        return ctx;
    }

    @Test
    void 日期模式串直接写() {
        RenderContext ctx = 商品("id", 1L, "typeCode", RenderFixture.TYPE,
                "publishTime", LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        assertEquals("2026-01-02", render(ctx, field("publishTime", Map.of("format", "Y-m-d"))));
        assertEquals("2026-01-02 03:04:05",
                render(ctx, field("publishTime", Map.of("format", "Y-m-d H:i:s"))));
        assertEquals("2026年01月02日",
                render(ctx, field("publishTime", Map.of("format", "Y年m月d日"))));
        assertEquals("2026-01-02T03:04:05", render(ctx, field("publishTime", Map.of("format", "iso"))));
        assertEquals("星期五", render(ctx, field("publishTime", Map.of("format", "weekday"))));
    }

    @Test
    void 相对时间() {
        RenderContext ctx = 商品("id", 1L, "typeCode", RenderFixture.TYPE,
                "publishTime", LocalDateTime.now().minusDays(3));
        assertEquals("3 天前", render(ctx, field("publishTime", Map.of("format", "relative"))));
    }

    @Test
    void json格式化把文案转成能放进JSON字符串的形态() {
        // 结构化数据（<script type=ld+json>）里的文案必须转义，否则一个引号就把整块 JSON 弄断。
        // 引号与 HTML 转义表管得到的字符一律走 Unicode 转义：产出里不再出现 & < > " '，
        // 于是紧接着那次 HTML 转义（§5.2.1）原样放行，而 JSON 解析器会把它们还原成本来的字符。
        RenderContext ctx = 商品("id", 1L, "typeCode", RenderFixture.TYPE,
                "title", "a\"b<c>&d'e");
        assertEquals("a\\u0022b\\u003cc\\u003e\\u0026d\\u0027e",
                render(ctx, field("title", Map.of("format", "json"))));
    }

    @Test
    void json格式化把换行转成转义序列() {
        // TEXTAREA 的默认输出会把换行变成 <br>（§2.2），而 JSON 里需要的是 \n：
        // json 这一支在换行被处理之前就把它们转掉，所以两个 formatter 不会互相打架。
        RenderContext ctx = 商品("id", 1L, "typeCode", RenderFixture.TYPE,
                "title", "第一行\n第二行\tTAB");
        assertEquals("第一行\\n第二行\\tTAB",
                render(ctx, field("title", Map.of("format", "json"))));
    }

    @Test
    void maxlen按字符截断并加省略号() {
        RenderContext ctx = 商品("id", 1L, "typeCode", RenderFixture.TYPE,
                "title", "0123456789012345678901234");
        assertEquals("01234567890123456789…", render(ctx, field("title", Map.of("maxlen", "20"))));
        // 规范形式与快捷形式等价
        assertEquals("01234567890123456789…",
                render(ctx, field("title", Map.of("format", "maxlen", "maxlen", "20"))));
    }

    @Test
    void 数值类的formatter() {
        RenderContext ctx = 商品("id", 1L, "typeCode", RenderFixture.TYPE,
                "price", new BigDecimal("9.9"), "viewCount", 1234567, "seconds", 3725);
        assertEquals("¥9.90", render(ctx, field("price", Map.of("money", "1"))));
        assertEquals("$9.90", render(ctx, field("price", Map.of("symbol", "$"))));
        assertEquals("9.90", render(ctx, field("price", Map.of("fixed", "2"))));
        assertEquals("9.9%", render(ctx, field("price", Map.of("format", "percent"))));
        assertEquals("1,234,567", render(ctx, field("viewCount", Map.of("format", "number"))));
        assertEquals("123.5万", render(ctx, field("viewCount", Map.of("format", "compact"))));
        assertEquals("01:02:05", render(ctx, field("seconds", Map.of("format", "duration"))));
        assertEquals("3.6 KB", render(ctx, field("seconds", Map.of("format", "filesize"))));
    }

    @Test
    void 布尔字段用show拼class() {
        assertEquals("card is-hot", render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "recommend", 1),
                text("card "), field("recommend", Map.of("show", "is-hot"))));
        assertEquals("card ", render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "recommend", 0),
                text("card "), field("recommend", Map.of("show", "is-hot"))));
        assertEquals("有", render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "recommend", 1),
                field("recommend", Map.of("format", "yesno", "yes", "有", "no", "无"))));
    }

    @Test
    void 枚举输出选项标签() {
        RenderContext ctx = 商品("id", 1L, "typeCode", RenderFixture.TYPE, "state", "1");
        assertEquals("已发布", render(ctx, field("state", Map.of("format", "label"))));
        assertEquals("1", render(ctx, field("state")));
    }

    @Test
    void 图片派生尺寸() {
        Map<String, Object> cover = Map.of(
                "url", "/uploads/2026/a.jpg",
                "thumb", "/uploads/derive/9f2a1c33bb04e7d1-thumb.webp");
        assertEquals("/uploads/derive/9f2a1c33bb04e7d1-thumb.webp",
                render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "cover", cover),
                        field("cover", Map.of("size", "thumb"))));
        // 默认输出就是 url（§2.2 的 IMAGE 行）
        assertEquals("/uploads/2026/a.jpg", render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "cover", cover),
                field("cover")));
        // 派生 URL 含内容哈希，只有数据层知道；值已经是 URL 时原样输出，不在渲染期重拼
        assertEquals("/uploads/2026/a.jpg",
                render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "cover", "/uploads/2026/a.jpg"),
                        field("cover", Map.of("size", "thumb"))));
    }

    @Test
    void 图集默认输出首图() {
        List<Map<String, Object>> images = List.of(Map.of("url", "/1.jpg"), Map.of("url", "/2.jpg"));
        assertEquals("/1.jpg", render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "images", images),
                field("images")));
    }

    @Test
    void 一个字段只能有一个formatter() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "price", 9),
                        field("price", Map.of("format", "number", "money", "1"))));
        assertEquals(PublishErrorCode.E1002, error.code());
        assertTrue(error.getMessage().contains("只能有一个 formatter"), error.getMessage());
        assertTrue(error.getMessage().contains("news/list.html:7"), error.getMessage());
    }

    @Test
    void 组合非法时报E1002并列出该类型可用的formatter() {
        // title 是内置的 TEXT 声明；size 只对 IMAGE / IMAGES 合法（§2.2 第 4 条）
        PublishException error = assertThrows(PublishException.class,
                () -> render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "title", "T"),
                        field("title", Map.of("size", "thumb"))));
        assertEquals(PublishErrorCode.E1002, error.code());
        assertTrue(error.getMessage().contains("formatter size 不能用在 TEXT 字段上"), error.getMessage());
        assertTrue(error.getMessage().contains("maxlen mask upper lower"), error.getMessage());
        // json 也是 TEXT 上合法的 formatter：把文案转成能安全放进 <script type=ld+json> 的形态
        // （结构化数据用；引号用 Unicode 转义表示，过一遍 HTML 转义也不会被拆坏）
        assertEquals(List.of("maxlen", "mask", "upper", "lower", "json"),
                FieldOutput.legalFormatterNames(FieldType.TEXT));
    }

    @Test
    void 富文本字段没有任何formatter() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "content", "<p>x</p>"),
                        field("content", Map.of("format", "maxlen", "maxlen", "3"))));
        assertEquals(PublishErrorCode.E1002, error.code());
        assertTrue(error.getMessage().contains("不能用在 RICHTEXT 字段上"), error.getMessage());
    }

    @Test
    void 未知formatter给笔误建议() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "publishTime", LocalDateTime.now()),
                        field("publishTime", Map.of("format", "relativ"))));
        assertEquals(PublishErrorCode.E1002, error.code());
        assertTrue(error.getMessage().contains("formatter relativ 不存在"), error.getMessage());
        assertTrue(error.getMessage().contains("你是不是想写 relative"), error.getMessage());
    }

    @Test
    void 参数名不在专属清单里报E1002() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "title", "T"),
                        field("title", Map.of("upper", "1"))));
        assertEquals(PublishErrorCode.E1002, error.code());
        assertTrue(error.getMessage().contains("formatter 参数 upper 不存在"), error.getMessage());
        assertTrue(error.getMessage().contains("maxlen mask size show money fixed symbol"), error.getMessage());
    }

    @Test
    void 日期模式串不能用在文本字段上() {
        PublishException error = assertThrows(PublishException.class,
                () -> render(商品("id", 1L, "typeCode", RenderFixture.TYPE, "title", "T"),
                        field("title", Map.of("format", "Y-m-d"))));
        assertEquals(PublishErrorCode.E1002, error.code());
        assertTrue(error.getMessage().contains("不能用在 TEXT 字段上"), error.getMessage());
    }

    @Test
    void 声明未知时只做语法校验并转义() {
        // price 在内置表里没有声明，也没有注入 lookup → 声明未知：只校验语法，按 raw=false 输出
        RenderContext ctx = detail(item("id", 1L, "typeCode", RenderFixture.TYPE, "price", "<b>9.9</b>"));
        assertEquals("&lt;b&gt;9.9&lt;/b&gt;", render(ctx, field("price")));
        // 类型未知时组合合法性跳过（没有字段类型可依据），但表达式语法仍然拦得住
        PublishException error = assertThrows(PublishException.class,
                () -> render(ctx, field("price", Map.of("format", "number", "money", "1"))));
        assertEquals(PublishErrorCode.E1002, error.code());
    }

    @Test
    void 标签产出属性的值也走同一处转义() {
        // TemplateRenderer.format 是"转义与 formatter 只有一处实现"的对外出口
        assertEquals("a&amp;b", RenderFixture.renderer().format("a&b", null, Map.of()));
        assertEquals("9.90", RenderFixture.renderer().format(new BigDecimal("9.9"), null,
                Map.of("format", "fixed", "fixed", "2")));
    }
}
