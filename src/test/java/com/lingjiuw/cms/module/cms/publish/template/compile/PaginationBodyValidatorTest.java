package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.model.PageType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * §4.5 第 8 条 ★：分页主体的数量与位置 → E3001 / E3002。
 */
class PaginationBodyValidatorTest extends CompileCaseSupport {

    static Stream<Case> 分页主体用例() {
        return Stream.of(
                Case.fail("两个 {cms:list}", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all'}a{/cms:list}"
                                + "{cms:list type='book' category='all'}b{/cms:list}",
                        PublishErrorCode.E3001,
                        "同一模板中出现了多个分页主体标签", "请只保留一个"),
                Case.fail("循环体内的 {cms:list}", PageType.DETAIL, "product",
                        "{cms:foreach field='tags'}"
                                + "{cms:list type='product' category='all'}a{/cms:list}"
                                + "{/cms:foreach}",
                        PublishErrorCode.E3001,
                        "循环体内不能有 {cms:list}", "循环里的列表用 {cms:query}"),
                Case.fail("分页主体写在 if 体内", PageType.DETAIL, "product",
                        "{cms:if field='title'}"
                                + "{cms:list type='product' category='all'}a{/cms:list}"
                                + "{/cms:if}",
                        PublishErrorCode.E3002,
                        "是分页主体，不能写在 {cms:if} 内", "分页计划不能依赖运行期真假"),
                Case.ok("详情页一个列表", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all'}a{/cms:list}"),
                Case.ok("详情页只有正文分页", PageType.DETAIL, "product",
                        "{cms:detail}x{/cms:detail}"),
                Case.ok("列表页可以没有分页主体", PageType.LIST, "product",
                        "只有文字，没有列表"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("分页主体用例")
    void 表驱动(Case item) {
        run(item);
    }

    /** 正文分页的两条：{@code paginate_body} 非空时 {@code {cms:detail}} 才是分页主体。 */
    static Stream<Case> 正文分页用例() {
        return Stream.of(
                Case.fail("正文分页 + 两个 detail", PageType.DETAIL, "product",
                        "{cms:detail}a{/cms:detail}{cms:detail}b{/cms:detail}",
                        PublishErrorCode.E3001,
                        "同一模板中出现了多个分页主体标签"),
                Case.fail("正文分页主体写在 if 内", PageType.DETAIL, "product",
                        "{cms:if field='title'}{cms:detail}a{/cms:detail}{/cms:if}",
                        PublishErrorCode.E3002,
                        "是分页主体，不能写在 {cms:if} 内"),
                Case.fail("正文分页与列表分页同现", PageType.DETAIL, "product",
                        "{cms:detail}a{/cms:detail}{cms:list type='product' category='all'}b{/cms:list}",
                        PublishErrorCode.E3001,
                        "两个分页主体", "正文分页与列表分页二选一"),
                Case.ok("单页上的 detail 不是分页主体", PageType.SINGLE, "about",
                        "{cms:detail}a{/cms:detail}"));
    }

    @ParameterizedTest(name = "正文分页：{0}")
    @MethodSource("正文分页用例")
    void 表驱动_正文分页(Case item) {
        CompileFixture fixture = new CompileFixture()
                .type(product("content"))
                .type("about", com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef.Kind.SINGLE)
                .page(item.pageType(), item.typeCode());
        fixture.file(entry(item.pageType()), item.template());
        if (item.code() == null) {
            fixture.compile(entry(item.pageType()));
            return;
        }
        CompileFixture.assertError(fixture.error(entry(item.pageType())), item.code(), item.keywords());
    }
}
