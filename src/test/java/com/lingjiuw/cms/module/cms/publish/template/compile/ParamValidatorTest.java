package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.model.PageType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * §4.5 第 2 条 ★：参数是否在该标签的 ParamSpec 内、类型是否可转换、必填是否缺失 → E1002。
 */
class ParamValidatorTest extends CompileCaseSupport {

    static Stream<Case> 参数用例() {
        return Stream.of(
                Case.fail("未声明的参数", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' channel='all'}x{/cms:list}",
                        PublishErrorCode.E1002,
                        "参数 channel 不存在", "{cms:list} 的参数有", "全部列出"),
                Case.fail("必填参数缺失", PageType.STATIC, null,
                        "{cms:form class='x'}x{/cms:form}",
                        PublishErrorCode.E1002,
                        "参数 code 是必填的", "可用参数有"),
                Case.fail("INT 转换失败", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' row='abc'}x{/cms:list}",
                        PublishErrorCode.E1002,
                        "参数 row 需要整数", "row='abc'"),
                Case.fail("BOOL 取值非法", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' includeChildren='yes'}x{/cms:list}",
                        PublishErrorCode.E1002,
                        "参数 includeChildren 需要 0 / 1 / true / false", "includeChildren='yes'"),
                Case.fail("ENUM 取值非法", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' order='up'}x{/cms:list}",
                        PublishErrorCode.E1002,
                        "参数 order 的取值不在允许范围", "允许值：asc / desc"),
                Case.fail("首页省略 type 是编译期错", PageType.HOME, null,
                        "{cms:list category='all' row='5'}x{/cms:list}",
                        PublishErrorCode.E1002,
                        "参数 type 是必填的", "没有「当前页面的类型」", "type='all'"),
                Case.fail("首页省略 category 是编译期错", PageType.HOME, null,
                        "{cms:list type='all' row='5'}x{/cms:list}",
                        PublishErrorCode.E1002,
                        "参数 category 是必填的", "没有「当前栏目」"),
                Case.fail("type='all' 时 row 必填", PageType.TAGPAGE, null,
                        "{cms:list type='all' category='all'}x{/cms:list}",
                        PublishErrorCode.E1002,
                        "type='all' 时参数 row 是必填的"),
                Case.fail("include 参数不得用保留名", PageType.DETAIL, "product",
                        "{cms:include file='_partials/x.html' page='1'/}",
                        PublishErrorCode.E1002,
                        "参数名 page 是保留名", "site channel page param query item"),
                Case.ok("参数齐备", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' row='10' includeChildren='0'}"
                                + "x{/cms:list}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("参数用例")
    void 表驱动(Case item) {
        run(item);
    }
}
