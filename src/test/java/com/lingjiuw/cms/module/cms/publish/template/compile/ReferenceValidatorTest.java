package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.model.PageType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * §4.5 第 7 条 ★：{@code type} / {@code category} / {@code tag} / {@code author} / {@code code}
 * 是否指向存在的对象 → E2006。
 */
class ReferenceValidatorTest extends CompileCaseSupport {

    static Stream<Case> 引用用例() {
        return Stream.of(
                Case.fail("类型不存在 + 笔误猜测", PageType.DETAIL, "product",
                        "{cms:list type='prodct' category='all'}x{/cms:list}",
                        PublishErrorCode.E2006,
                        "内容类型 prodct 不存在", "本站类型有", "你是不是想写 product？"),
                Case.fail("分类不存在", PageType.DETAIL, "product",
                        "{cms:list type='product' category='nope'}x{/cms:list}",
                        PublishErrorCode.E2006,
                        "分类 nope 不存在", "category='all'"),
                Case.fail("标签不存在", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' tag='nope'}x{/cms:list}",
                        PublishErrorCode.E2006,
                        "标签 nope 不存在", "多值用 | 分隔"),
                Case.fail("标签多值里有一个不存在", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' tag='java|nope'}x{/cms:list}",
                        PublishErrorCode.E2006,
                        "标签 nope 不存在"),
                Case.fail("作者不存在", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' author='nope'}x{/cms:list}",
                        PublishErrorCode.E2006,
                        "作者 nope 不存在", "author='self'"),
                Case.fail("菜单 code 不存在", PageType.DETAIL, "product",
                        "{cms:channel source='menu' code='nope'}x{/cms:channel}",
                        PublishErrorCode.E2006,
                        "菜单 nope 不存在", "本站菜单有 footer、main"),
                Case.fail("detail 上不能 type='all'", PageType.DETAIL, "product",
                        "{cms:detail type='all'}x{/cms:detail}",
                        PublishErrorCode.E2006,
                        "type='all' 不能用在 {cms:detail} 上"),
                Case.ok("引用都成立", PageType.DETAIL, "product",
                        "{cms:list type='product' category='news' tag='java' author='zhang'}"
                                + "x{/cms:list}"
                                + "{cms:channel source='menu' code='main'}x{/cms:channel}"),
                Case.ok("all 与 self 是关键字", PageType.HOME, null,
                        "{cms:query type='all' category='all' author='self' row='5'}x{/cms:query}"
                                + "{cms:tagnav type='all'}x{/cms:tagnav}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("引用用例")
    void 表驱动(Case item) {
        run(item);
    }
}
