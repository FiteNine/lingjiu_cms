package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.template.validate.UrlPatternRules;
import com.lingjiuw.cms.module.cms.publish.template.validate.UrlPatternRules.PageKind;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4.5 第 9 条 ★：{@code url_pattern} 的占位符白名单与必需占位符 → E4001 / E4003。
 *
 * <p>期 1 只到**判定函数**这一层（§7.1.1 的白名单 + §7.1.3 的五条判定），因此这里是纯函数用例：
 * 进 pattern 与几个标志，出错误码与文案关键字。
 */
class UrlPatternRulesTest {

    record Case(String name, Runnable action, PublishErrorCode code, String... keywords) {

        static Case ok(String name, Runnable action) {
            return new Case(name, action, null);
        }

        static Case fail(String name, Runnable action, PublishErrorCode code, String... keywords) {
            return new Case(name, action, code, keywords);
        }
    }

    static Stream<Case> 用例() {
        return Stream.of(
                Case.fail("白名单外的占位符", () -> UrlPatternRules.checkWhitelist(
                                "/product/{brand}.html", "类型 product 的 detail_url_pattern"),
                        PublishErrorCode.E4001,
                        "占位符 {brand} 不在白名单里", "可用占位符只有这些（§7.1.1）"),
                Case.fail("不以 / 开头", () -> UrlPatternRules.checkWhitelist(
                                "product/{slug}.html", "类型 product 的 detail_url_pattern"),
                        PublishErrorCode.E4001,
                        "必须以 / 开头"),
                Case.fail("以 // 结尾", () -> UrlPatternRules.checkWhitelist(
                                "/product//", "类型 product 的 list_url_pattern"),
                        PublishErrorCode.E4001,
                        "不能以 // 结尾", "会产出空段"),
                Case.fail("含查询串", () -> UrlPatternRules.checkWhitelist(
                                "/product/{slug}.html?x=1", "类型 product 的 detail_url_pattern"),
                        PublishErrorCode.E4001,
                        "不能含 ? 或 #", "静态产物不做查询串"),
                Case.fail("详情页缺 {slug} 与 {id}", () -> UrlPatternRules.checkRequired(
                                "/product/", UrlPatternRules.requiredGroups(PageKind.DETAIL, false, 0),
                                "类型 product 的 detail_url_pattern"),
                        PublishErrorCode.E4001,
                        "缺少必需占位符 {id} 或 {slug}", "该页面类型的必需占位符见 §7.1.3"),
                Case.fail("分类页缺 {categoryPath} / {categorySlug}", () -> UrlPatternRules.checkRequired(
                                "/list/", UrlPatternRules.requiredGroups(PageKind.CATEGORY_LIST, false, 0),
                                "站点选项 url.list"),
                        PublishErrorCode.E4001,
                        "缺少必需占位符 {categoryPath} 或 {categorySlug}"),
                Case.fail("归档页缺 {year}", () -> UrlPatternRules.checkRequired(
                                "/archive/", UrlPatternRules.requiredGroups(PageKind.ARCHIVE_YEAR, false, 0),
                                "站点选项 url.archive"),
                        PublishErrorCode.E4001,
                        "缺少必需占位符 {year}"),
                Case.fail("{n} 用在不分页的页面", () -> UrlPatternRules.checkPagination(
                                "/product/{slug}/page-{n}/", false,
                                "类型 product 的 detail_url_pattern"),
                        PublishErrorCode.E4001,
                        "占位符 {n} 用在不分页的页面上", "去掉 {n}"),
                Case.fail("会分页却没有 {n}", () -> UrlPatternRules.checkTotalPages(
                                "/news/", 3, "分类 news 的 list_url_pattern"),
                        PublishErrorCode.E4003,
                        "共 3 页，但 url_pattern 没有 {n}"),
                Case.fail("正文分页却没有 {n}", () -> UrlPatternRules.checkContentPagination(
                                "/book/{slug}.html", true, "类型 book 的 detail_url_pattern"),
                        PublishErrorCode.E4001,
                        "paginate_body 非空，但 url_pattern 里没有 {n}",
                        "会被静默丢掉"),
                Case.ok("合法模式串", () -> {
                    UrlPatternRules.checkWhitelist("/book/{parentSlug}/{slug}-{n}.html",
                            "类型 book 的 detail_url_pattern");
                    UrlPatternRules.checkRequired("/book/{parentSlug}/{slug}-{n}.html",
                            UrlPatternRules.requiredGroups(PageKind.DETAIL, true, 5),
                            "类型 book 的 detail_url_pattern");
                    UrlPatternRules.checkPagination("/book/{parentSlug}/{slug}-{n}.html", true,
                            "类型 book 的 detail_url_pattern");
                    UrlPatternRules.checkContentPagination("/book/{slug}-{n}.html", true,
                            "类型 book 的 detail_url_pattern");
                    UrlPatternRules.checkTotalPages("/book/{slug}-{n}.html", 12,
                            "类型 book 的 detail_url_pattern");
                }),
                Case.ok("首页只有 1 页时不要求 {n}", () -> UrlPatternRules.checkRequired(
                        "/page-{n}/", UrlPatternRules.requiredGroups(PageKind.HOME, true, 1),
                        "站点选项 url.home")),
                Case.ok("单页与类型列表页没有必需占位符", () -> {
                    UrlPatternRules.checkRequired("/about/",
                            UrlPatternRules.requiredGroups(PageKind.SINGLE, false, 0), "类型 about");
                    UrlPatternRules.checkRequired("/product/page-{n}/",
                            UrlPatternRules.requiredGroups(PageKind.TYPE_LIST, true, 0),
                            "类型 product 的 list_url_pattern");
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("用例")
    void 表驱动(Case item) {
        if (item.code() == null) {
            item.action().run();
            return;
        }
        PublishException error = org.junit.jupiter.api.Assertions.assertThrows(PublishException.class,
                item.action()::run);
        CompileFixture.assertError(error, item.code(), item.keywords());
    }

    /** {@code {n}} 与 {@code {pageNo}} 是同一个占位符（§7.1.1），{@code {n}} 是简写。 */
    @org.junit.jupiter.api.Test
    void 分页号的两种写法等价() {
        assertTrue(UrlPatternRules.hasPageNo("/news/page-{n}/"));
        assertTrue(UrlPatternRules.hasPageNo("/news/page-{pageNo}/"));
        assertFalse(UrlPatternRules.hasPageNo("/news/"));
    }
}
