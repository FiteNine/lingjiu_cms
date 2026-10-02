package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.model.PageType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * §4.5 第 3 条 ★：块配对、{@code {cms:else/}} 位置、写法与声明是否相符 → E1003。
 *
 * <p>块名不匹配（{@code {/cms:if}} 配 {@code {cms:list}}）在解析期报，不在本表里；
 * 本表覆盖编译器负责的两块：else 的绑定位置、以及"标签体写在自闭合标签上 / 自闭合写在块标签上"。
 */
class StructureValidatorTest extends CompileCaseSupport {

    static Stream<Case> 结构用例() {
        return Stream.of(
                Case.fail("else 不在 if 里", PageType.DETAIL, "product",
                        "{cms:foreach field='tags'}{cms:else/}{/cms:foreach}",
                        PublishErrorCode.E1003,
                        "{cms:else/} 位置错", "在 {cms:foreach} 内", "只能是 {cms:if} 的直接子节点"),
                Case.fail("同一个 if 里两个 else", PageType.DETAIL, "product",
                        "{cms:if field='title'}a{cms:else/}b{cms:else/}c{/cms:if}",
                        PublishErrorCode.E1003,
                        "两个 {cms:else/}", "最多一个"),
                Case.fail("else 写在模板根上", PageType.DETAIL, "product",
                        "a{cms:else/}b",
                        PublishErrorCode.E1003,
                        "不在任何 {cms:if} 里"),
                Case.fail("块标签写成了自闭合", PageType.DETAIL, "product",
                        "{cms:if field='title'/}",
                        PublishErrorCode.E1003,
                        "必须带标签体", "写的是自闭合形态"),
                Case.fail("include 写了标签体", PageType.DETAIL, "product",
                        "{cms:include file='card.html'}体{/cms:include}",
                        PublishErrorCode.E1003,
                        "{cms:include} 不接受标签体"),
                Case.ok("else 成对", PageType.DETAIL, "product",
                        "{cms:if field='title'}a{cms:else/}b{/cms:if}"),
                Case.ok("嵌套 if 各自的 else", PageType.DETAIL, "product",
                        "{cms:if field='title'}{cms:if field='summary'}a{cms:else/}b{/cms:if}"
                                + "{cms:else/}c{/cms:if}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("结构用例")
    void 表驱动(Case item) {
        run(item);
    }

    /**
     * 另一半：**自闭合标签被写成了块标签**。14 个契约标签里只有 {@code include} 是自闭合的，
     * 而它在展开期就被消费掉了，因此用一个额外的自闭合假标签覆盖这条分支。
     */
    @org.junit.jupiter.api.Test
    void 自闭合标签写了标签体报E1003() {
        CompileFixture fixture = base(PageType.DETAIL, "product").selfClosingTag("widget");
        fixture.file("product_detail.html", "{cms:widget}体{/cms:widget}");

        CompileFixture.assertError(fixture.error("product_detail.html"), PublishErrorCode.E1003,
                "{cms:widget} 不能写标签体", "是自闭合标签");
    }
}
