package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.model.PageType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * §4.5 第 6 条 ★：{@code where} / {@code orderby} / {@code relate} 的字段可筛选性与语法 → E2007。
 */
class FilterFieldValidatorTest extends CompileCaseSupport {

    static Stream<Case> 过滤用例() {
        return Stream.of(
                Case.fail("未索引的自定义字段不能 where", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' where='price:lte:100'}x{/cms:list}",
                        PublishErrorCode.E2007,
                        "字段 price 不能用于 where", "该字段没有勾选「可筛选」"),
                Case.fail("where 语法不可解析", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' where='brand'}x{/cms:list}",
                        PublishErrorCode.E2007,
                        "where 参数不可解析", "不是 code:op:value 三段式"),
                Case.fail("运算符不在白名单", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' where='brand:zz:acme'}x{/cms:list}",
                        PublishErrorCode.E2007,
                        "运算符 `zz` 不在白名单里", "可用运算符：eq ne gt gte lt lte like in has"),
                Case.fail("未索引字段不能 orderby", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' orderby='price desc'}x{/cms:list}",
                        PublishErrorCode.E2007,
                        "字段 price 不能用于 orderby", "可用排序字段"),
                Case.fail("relate 指向未索引字段", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' relate='field:price'}x{/cms:list}",
                        PublishErrorCode.E2007,
                        "字段 price 不能用于 relate"),
                Case.fail("relate 取值不对", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' relate='brand'}x{/cms:list}",
                        PublishErrorCode.E2007,
                        "relate 的取值不对", "允许 tag / category / field:<code> 三种"),
                Case.fail("type='all' 不能配 where", PageType.HOME, null,
                        "{cms:list type='all' category='all' row='5' where='brand:eq:acme'}x{/cms:list}",
                        PublishErrorCode.E2007,
                        "where 不能与 type='all' 一起用"),
                Case.fail("type='all' 不能配 of", PageType.DETAIL, "product",
                        "{cms:list type='all' row='5' of='self'}x{/cms:list}",
                        PublishErrorCode.E2007,
                        "of 不能与 type='all' 一起用"),
                Case.fail("relationOrder 需要 relate", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' orderby='relationOrder'}x{/cms:list}",
                        PublishErrorCode.E2007,
                        "orderby='relationOrder' 只能与 relate='field:<code>' 一起用"),
                Case.ok("索引字段 + 内置字段都能用", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' where='brand:eq:acme,publishTime:gte:2026-01-01'"
                                + " orderby='sort asc, publishTime desc'}x{/cms:list}"),
                Case.ok("转义与多值", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' where='brand:in:acme\\|globex'}x{/cms:list}"),
                Case.ok("relate 指向 indexed 的 RELATION 字段", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' relate='field:accessories'"
                                + " orderby='relationOrder'}x{/cms:list}"),
                Case.ok("relate 指向内置可筛字段", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all' relate='field:authorId'}x{/cms:list}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("过滤用例")
    void 表驱动(Case item) {
        run(item);
    }
}
