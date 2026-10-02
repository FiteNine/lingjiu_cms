package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishErrors;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不带 ★ 的几条（§4.5 的第 13/14/15/16/17/18 条）+ §6.7 的三条脚注 + §10.3 第 2 条的多错聚合。
 *
 * <p>★ 的 12 条各有自己的表驱动类；这里放"契约要求实现、但 §12.3 没有指定用例"的那些，
 * 目的是证明代码路径真的通（不是只有 ★ 的路径通）。
 */
class OtherValidationsTest extends CompileCaseSupport {

    /* ---------------- 第 13 条：{cms:pagelist} 用在正文分页上 ---------------- */

    @Test
    void 正文分页上用pagelist报E3003() {
        CompileFixture fixture = new CompileFixture().type(product("content"))
                .page(PageType.DETAIL, "product");
        fixture.file("product_detail.html",
                "{cms:detail}x{/cms:detail}{cms:pagelist}a{/cms:pagelist}");

        CompileFixture.assertError(fixture.error("product_detail.html"), PublishErrorCode.E3003,
                "正文分页不提供页号条", "page.paginationKind='content'",
                "用 page.prevUrl / page.nextUrl");
    }

    @Test
    void 列表分页上用pagelist放行() {
        CompileFixture fixture = new CompileFixture().type(product())
                .page(PageType.DETAIL, "product");
        fixture.file("product_detail.html",
                "{cms:list type='product' category='all'}x{/cms:list}"
                        + "{cms:pagelist}a{/cms:pagelist}");

        fixture.compile("product_detail.html");
    }

    @Test
    void 正文分页与列表分页同现报E3001() {
        // §4.5 的两条死限之二：paginate_body 非空 + 模板里有 {cms:list}
        CompileFixture fixture = new CompileFixture().type(product("content"))
                .page(PageType.DETAIL, "product");
        fixture.file("product_detail.html",
                "{cms:detail}x{/cms:detail}{cms:list type='product' category='all'}y{/cms:list}");

        CompileFixture.assertError(fixture.error("product_detail.html"), PublishErrorCode.E3001,
                "两个分页主体", "正文分页与列表分页二选一");
    }

    /* ---------------- 第 14 / 15 条 ---------------- */

    @Test
    void 首页上没有当前条目时of不成立() {
        CompileFixture fixture = base(PageType.HOME, null);
        fixture.file("index.html", "{cms:list type='product' category='all' of='self'}x{/cms:list}");

        CompileFixture.assertError(fixture.error("index.html"), PublishErrorCode.E2009,
                "HOME 页没有当前条目", "of='self' 无法解析");
    }

    @Test
    void 非层级类型上ofparent不成立() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html",
                "{cms:list type='product' category='all' of='parent'}x{/cms:list}");

        CompileFixture.assertError(fixture.error("product_detail.html"), PublishErrorCode.E2009,
                "不是层级类型", "of='parent' 不成立");
    }

    @Test
    void 循环体内的of放行() {
        // §3.7 裁定三：循环里锚定项 = 栈顶迭代项，父是否存在是数据问题，不是编译期错误
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html",
                "{cms:foreach field='tags'}"
                        + "{cms:query type='product' category='all' of='parent'}x{/cms:query}"
                        + "{/cms:foreach}");

        fixture.compile("product_detail.html");
    }

    @Test
    void 单页上取内容类型报E3010() {
        CompileFixture fixture = base(PageType.SINGLE, "about");
        fixture.file("about.html", "{cms:detail type='product'}x{/cms:detail}");

        CompileFixture.assertError(fixture.error("about.html"), PublishErrorCode.E3010,
                "single 页面上不能取 product（CONTENT）类型的内容");
    }

    @Test
    void 详情页上取单页类型报E3010() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html", "{cms:detail type='about'}x{/cms:detail}");

        CompileFixture.assertError(fixture.error("product_detail.html"), PublishErrorCode.E3010,
                "不能取单页类型 about 的内容");
    }

    @Test
    void 单页上取单页类型放行() {
        CompileFixture fixture = base(PageType.SINGLE, "about");
        fixture.file("about.html", "{cms:detail type='about'}x{/cms:detail}");

        fixture.compile("about.html");
    }

    /* ---------------- 第 16 条 ---------------- */

    @Test
    void 查询名重复报E2010() {
        CompileFixture fixture = base(PageType.HOME, null);
        fixture.file("index.html", "第一行\n{cms:query type='all' category='all' row='5' name='latest'}"
                + "x{/cms:query}\n{cms:query type='all' category='all' row='5' name='latest'}"
                + "y{/cms:query}");

        PublishException error = fixture.error("index.html");

        CompileFixture.assertError(error, PublishErrorCode.E2010, "查询名 latest 重复使用",
                "index.html:2 与 index.html:3");
    }

    @Test
    void 循环体内命名报E2011() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html",
                "{cms:foreach field='tags'}"
                        + "{cms:query type='product' category='all' name='inner'}x{/cms:query}"
                        + "{/cms:foreach}");

        CompileFixture.assertError(fixture.error("product_detail.html"), PublishErrorCode.E2011,
                "循环内的查询不能命名", "在循环体内");
    }

    /* ---------------- 第 17 条：编码 ---------------- */

    @Test
    void 带BOM的模板报E1007() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html", "\uFEFF[field:title/]");

        CompileFixture.assertError(fixture.error("product_detail.html"), PublishErrorCode.E1007,
                "模板带 UTF-8 BOM", "U+FEFF");
    }

    @Test
    void 非UTF8解码残留报E1007() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html", "第一行\n中文\uFFFD乱码");

        CompileFixture.assertError(fixture.error("product_detail.html"), PublishErrorCode.E1007,
                "模板编码不是 UTF-8", "U+FFFD");
    }

    @Test
    void 片段里的编码问题也报E1007并带包含链() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html", "{cms:include file='card.html'/}")
                .file("_partials/card.html", "\uFEFF片段");

        PublishException error = fixture.error("product_detail.html");

        CompileFixture.assertError(error, PublishErrorCode.E1007, "模板带 UTF-8 BOM");
        assertEquals("_partials/card.html", error.templatePath());
        assertEquals(List.of("product_detail.html", "_partials/card.html"), error.includeChain());
    }

    /* ---------------- 第 18 条：W5001 是警告 ---------------- */

    @Test
    void 选项关掉页面类型时给W5001警告且不报错() {
        CompileFixture fixture = base(PageType.ARCHIVE, null).option("page.archive", 0);
        fixture.file("archive_list.html", "{cms:list type='product' category='all'}x{/cms:list}");
        ValidationReport report = new ValidationReport();

        fixture.compiler().compile("archive_list.html", fixture.context(), report);

        assertEquals(1, report.warnings().size());
        assertEquals(PublishErrorCode.W5001, report.warnings().get(0).code());
        assertTrue(report.warnings().get(0).message().contains("page.archive=0"),
                report.warnings().get(0).message());
    }

    @Test
    void 选项打开时没有警告() {
        CompileFixture fixture = base(PageType.ARCHIVE, null).option("page.archive", 1);
        fixture.file("archive_list.html", "{cms:list type='product' category='all'}x{/cms:list}");
        ValidationReport report = new ValidationReport();

        fixture.compiler().compile("archive_list.html", fixture.context(), report);

        assertTrue(report.empty());
    }

    /* ---------------- §6.7 矩阵与三条脚注 ---------------- */

    @Test
    void 标签用在不允许的页面类型上报E3012() {
        CompileFixture fixture = base(PageType.LIST, "product");
        fixture.file("list.html", "{cms:prenext}a{/cms:prenext}");

        CompileFixture.assertError(fixture.error("list.html"), PublishErrorCode.E3012,
                "{cms:prenext} 只能用在 DETAIL 上", "当前页面类型是 LIST", "矩阵见 §6.7");
    }

    @Test
    void 面包屑在404上报E3012() {
        CompileFixture fixture = base(PageType.PAGE404, null);
        fixture.file("404.html", "{cms:breadcrumb}a{/cms:breadcrumb}");

        CompileFixture.assertError(fixture.error("404.html"), PublishErrorCode.E3012,
                "{cms:breadcrumb} 不能用在 PAGE404 上");
    }

    @Test
    void 未声明query的静态页放list报E3012() {
        CompileFixture fixture = base(PageType.STATIC, null);
        fixture.file("thanks.html", "{cms:list type='product' category='all'}x{/cms:list}");

        CompileFixture.assertError(fixture.error("thanks.html"), PublishErrorCode.E3012,
                "{cms:list} 只能用站点声明的列表页上", "没有声明 query");
    }

    @Test
    void 声明了query的静态页必须恰有一个list() {
        CompileFixture fixture = staticListPage();
        fixture.file("rank.html", "没有列表");

        CompileFixture.assertError(fixture.error("rank.html"), PublishErrorCode.E3012,
                "站点声明的列表页必须恰有 1 个 {cms:list}");
    }

    @Test
    void 声明了query的静态页放一个list就通过() {
        CompileFixture fixture = staticListPage();
        fixture.file("rank.html",
                "{cms:list type='product' category='all'}x{/cms:list}{cms:pagelist}a{/cms:pagelist}");

        fixture.compile("rank.html");
    }

    private static CompileFixture staticListPage() {
        return base(PageType.STATIC, null).option("pages.static", List.of(Map.of(
                "code", "rank", "url", "/rank/page-{n}/", "template", "rank.html",
                "query", Map.of("type", "product", "orderby", "viewCountWeek desc", "row", 20))));
    }

    @Test
    void feed只允许一个顶层query() {
        CompileFixture fixture = base(PageType.FEED, null);
        fixture.file("feed.xml", "{cms:query type='product' row='20'}a{/cms:query}"
                + "{cms:query type='product' row='20'}b{/cms:query}");

        CompileFixture.assertError(fixture.error("feed.xml"), PublishErrorCode.E3012,
                "feed 只允许一个顶层 {cms:query}", "共 2 个");
    }

    @Test
    void feed的query不能在循环体内() {
        CompileFixture fixture = base(PageType.FEED, null);
        fixture.file("feed.xml", "{cms:foreach field='site.alternates'}"
                + "{cms:query type='product' row='20'}a{/cms:query}{/cms:foreach}");

        CompileFixture.assertError(fixture.error("feed.xml"), PublishErrorCode.E3012,
                "feed 的 {cms:query} 必须是顶层的");
    }

    @Test
    void feed上一个query通过() {
        CompileFixture fixture = base(PageType.FEED, null);
        fixture.file("feed.xml", "{cms:query type='product' row='20'}"
                + "[field:title/]{/cms:query}");

        fixture.compile("feed.xml");
    }

    /* ---------------- §10.3 第 2 条：一次返回多条错误 ---------------- */

    @Test
    void 多个字段名写错时一次全报() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html", "第一行\n[field:foo/]\n[field:bar/]\n[field:title/]");

        PublishErrors errors = assertThrows(PublishErrors.class, () -> fixture.compile("product_detail.html"));

        assertEquals(2, errors.totalCount(), errors.getMessage());
        assertEquals(List.of(PublishErrorCode.E1004, PublishErrorCode.E1004),
                errors.errors().stream().map(PublishException::code).toList());
        assertTrue(errors.getMessage().contains("(1/2)"), errors.getMessage());
    }

    @Test
    void 单个错误仍然抛裸PublishException() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html", "[field:foo/]");

        PublishException error = fixture.error("product_detail.html");

        assertEquals(PublishErrorCode.E1004, error.code());
    }

    @Test
    void 跨校验器的多个错误也会聚合() {
        // 一个字段名写错（第 4 条）+ 一个表单 code 不存在（第 12 条）
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html", "[field:foo/]{cms:form code='nope'}x{/cms:form}");

        PublishErrors errors = assertThrows(PublishErrors.class, () -> fixture.compile("product_detail.html"));

        assertEquals(2, errors.totalCount(), errors.getMessage());
        assertEquals(2, errors.countByCode().size(), "两个错误码各一条：" + errors.countByCode());
    }

    @Test
    void 标签名未知时短路不产生级联噪音() {
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html", "{cms:arclist}[field:foo/]{/cms:arclist}");

        // 第 1 条失败即停止：不会连带报 E1004（级联噪音）
        CompileFixture.assertError(fixture.error("product_detail.html"), PublishErrorCode.E1001,
                "未知标签 {cms:arclist}");
    }

    @Test
    void 两个list只报一个码() {
        // §10.2 的 E3011 废弃理由：同一件事两个码会让统计口径分裂
        CompileFixture fixture = base(PageType.DETAIL, "product");
        fixture.file("product_detail.html",
                "{cms:list type='product' category='all'}a{/cms:list}"
                        + "{cms:list type='product' category='all'}b{/cms:list}");

        PublishException error = fixture.error("product_detail.html");

        assertEquals(PublishErrorCode.E3001, error.code(), error.getMessage());
    }
}
