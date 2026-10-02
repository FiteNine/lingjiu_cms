package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.model.PageType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * §4.5 第 4 条 ★：字段名是否在"该模板上下文可用的字段集合"里 → E1004。
 */
class FieldNameValidatorTest extends CompileCaseSupport {

    static Stream<Case> 字段名用例() {
        return Stream.of(
                Case.fail("字段不存在 + 笔误猜测", PageType.DETAIL, "product",
                        "[field:proce/]",
                        PublishErrorCode.E1004,
                        "字段 proce 不存在", "可用字段有", "你是不是想写 price？"),
                Case.fail("首页没有 channel 作用域", PageType.HOME, null,
                        "[field:channel.name/]",
                        PublishErrorCode.E1004,
                        "没有 channel 作用域", "具名作用域与页面类型的对应关系见 §5.1 第（4）条"),
                Case.fail("page 作用域上没有的 key", PageType.DETAIL, "product",
                        "[field:page.empty2/]",
                        PublishErrorCode.E1004,
                        "具名作用域 page 上没有 key empty2", "可用的 key 有"),
                Case.fail("type='all' 时自定义字段不可用", PageType.HOME, null,
                        "{cms:list type='all' category='all' row='5'}[field:price/]{/cms:list}",
                        PublishErrorCode.E1004,
                        "字段 price 不存在", "迭代项（类型 all）"),
                Case.fail("命名查询不存在", PageType.HOME, null,
                        "{cms:if field='query.latest.empty'}x{/cms:if}",
                        PublishErrorCode.E1004,
                        "查询名 latest 不存在", "写 name='…' 才有"),
                Case.fail("循环里字段名写错", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all'}[field:titel/]{/cms:list}",
                        PublishErrorCode.E1004,
                        "字段 titel 不存在", "你是不是想写 title？"),
                Case.ok("当前条目与循环项都能取", PageType.DETAIL, "product",
                        "[field:title/]{cms:list type='product' category='all'}"
                                + "[field:title/][field:class/][field:price/]{/cms:list}"),
                Case.ok("具名作用域照常可用", PageType.DETAIL, "product",
                        "[field:site.name/][field:page.pageNo/][field:channel.name/]"
                                + "[field:param.q/][field:item.title/]"),
                Case.ok("命名查询可用", PageType.HOME, null,
                        "{cms:query type='all' category='all' row='5' name='latest'}x{/cms:query}"
                                + "{cms:if field='query.latest.empty'}空{/cms:if}"
                                + "[field:query.latest.rows.0.title/]"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("字段名用例")
    void 表驱动(Case item) {
        run(item);
    }
}
