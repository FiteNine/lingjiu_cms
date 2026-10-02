package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.model.PageType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * §4.5 第 5 条 ★：字段路径的中间段是否可解析 → E1005（下标越界只能在渲染期查，见类注释）。
 */
class FieldPathValidatorTest extends CompileCaseSupport {

    static Stream<Case> 路径用例() {
        return Stream.of(
                Case.fail("单值字段取 .count", PageType.DETAIL, "product",
                        "[field:title.count/]",
                        PublishErrorCode.E1005,
                        "路径 title.count 在第 2 段断掉", "单值字段", "不能取下标 / 键"),
                Case.fail("单值字段取下标", PageType.DETAIL, "product",
                        "[field:price.0/]",
                        PublishErrorCode.E1005,
                        "路径 price.0 在第 2 段断掉", "price 是 DECIMAL（单值字段）",
                        "多值字段才能写 .count 或下标"),
                Case.fail("迭代项里没有的 key", PageType.DETAIL, "product",
                        "[field:images.0.href/]",
                        PublishErrorCode.E1005,
                        "路径 images.0.href 在第 3 段断掉", "迭代项里没有 href 这个 key",
                        "迭代项可用的 key 有"),
                Case.fail("多值字段的第二段不是下标", PageType.DETAIL, "product",
                        "[field:images.url/]",
                        PublishErrorCode.E1005,
                        "路径 images.url 在第 2 段断掉", "第二段只能是下标或 count"),
                Case.fail("count 之后还取段", PageType.DETAIL, "product",
                        "[field:images.count.url/]",
                        PublishErrorCode.E1005,
                        "路径 images.count.url 在第 3 段断掉", "count 之后不能再取段"),
                Case.fail("foreach 迭代单值字段", PageType.DETAIL, "product",
                        "{cms:foreach field='title'}x{/cms:foreach}",
                        PublishErrorCode.E1005,
                        "路径 title 在第 1 段断掉", "只能迭代多值字段"),
                Case.fail("路径段不合法（解析期就拦下）", PageType.DETAIL, "product",
                        "[field:images..url/]",
                        PublishErrorCode.E1001,
                        "字段路径不合法"),
                Case.ok("多值下标 + 迭代项 key", PageType.DETAIL, "product",
                        "[field:images.0.url/][field:images.count/][field:tags.0.name/]"),
                Case.ok("JSON 字段的键是数据", PageType.DETAIL, "product",
                        "[field:specs.weight/][field:specs.anything/]"),
                Case.ok("if 与 foreach 的 field 走同一套规则", PageType.DETAIL, "product",
                        "{cms:if field='images.0.url'}x{/cms:if}"
                                + "{cms:foreach field='images'}[field:url/]{/cms:foreach}"
                                + "{cms:foreach field='site.alternates'}[field:lang/]{/cms:foreach}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("路径用例")
    void 表驱动(Case item) {
        run(item);
    }
}
