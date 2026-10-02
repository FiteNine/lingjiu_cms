package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.model.PageType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * §4.5 第 1 条 ★：标签名是否存在（含编辑距离猜测）→ E1001。
 */
class TagNameValidatorTest extends CompileCaseSupport {

    static Stream<Case> 标签名用例() {
        return Stream.of(
                Case.fail("未知标签 + 笔误猜测", PageType.DETAIL, "product",
                        "{cms:lst type='product' category='all'}x{/cms:lst}",
                        PublishErrorCode.E1001,
                        "未知标签 {cms:lst}", "引擎不认识 lst", "你是不是想写 {cms:list}？"),
                Case.fail("未知的具名标签", PageType.HOME, null,
                        "{cms:sql/}",
                        PublishErrorCode.E1001,
                        "未知标签 {cms:sql}", "可用标签见 §6.1"),
                Case.ok("已知标签照常编译", PageType.DETAIL, "product",
                        "{cms:list type='product' category='all'}x{/cms:list}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("标签名用例")
    void 表驱动(Case item) {
        run(item);
    }
}
