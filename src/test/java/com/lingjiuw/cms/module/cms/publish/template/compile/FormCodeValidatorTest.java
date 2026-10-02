package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.model.PageType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * §4.5 第 12 条 ★：{@code {cms:form}} 的 {@code code} 是否指向存在的表单定义 → E2008。
 *
 * <p>同时钉住 §6.5 v2.2 的口径：**{@code hidden} 的 key 不受校验**（key 任意，只有值命中 5 个引擎
 * 关键字才替换），因此 {@code hidden='utm_source:wechat'} 必须能编译通过。
 */
class FormCodeValidatorTest extends CompileCaseSupport {

    static Stream<Case> 表单用例() {
        return Stream.of(
                Case.fail("表单不存在", PageType.STATIC, null,
                        "{cms:form code='nope'}x{/cms:form}",
                        PublishErrorCode.E2008,
                        "表单 nope 不存在", "本站表单有 inquiry、subscribe"),
                Case.fail("第二个表单也不存在", PageType.STATIC, null,
                        "{cms:form code='inquiry'}x{/cms:form}{cms:form code='nope2'}y{/cms:form}",
                        PublishErrorCode.E2008,
                        "表单 nope2 不存在"),
                Case.ok("表单存在", PageType.STATIC, null,
                        "{cms:form code='inquiry'}x{/cms:form}"),
                Case.ok("hidden 的 key 任意（只有值命中 5 个关键字才替换）", PageType.DETAIL, "product",
                        "{cms:form code='inquiry' hidden='utm_source:wechat,pid:contentId:self'}"
                                + "x{/cms:form}"),
                Case.ok("hidden 里写任意 key 都不报错", PageType.DETAIL, "product",
                        "{cms:form code='subscribe' hidden='a:b,c:d'}x{/cms:form}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("表单用例")
    void 表驱动(Case item) {
        run(item);
    }
}
