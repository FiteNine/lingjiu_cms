package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * §4.5 第 16 条：命名查询的 {@code name} 重复、或在循环体内命名 → E2010 / E2011（§5.4 约束三）。
 *
 * <p>为什么循环体内禁止命名：命名查询的结果进 {@code query.<name>} 作用域供**标签外**读取，
 * 而循环体内的查询会执行 N 次，"第几次迭代的结果"无法在标签外访问——位置相关的行为不可靠，
 * 因此宁可编译期报错（§5.4 约束三原话）。需要占位文案时把查询提到顶层并命名。
 *
 * <p>本类与 {@link TemplateAst#namedQueries()} 的分工：那个字段是编译器算出来的"注册顺序清单"
 * （供 {@code query.<name>} 的清单与文案），本类要的是"**哪两行**重复了"（§10.2 的 E2010 文案要
 * 同时给出两个位置），因此自己记一份"名字 → 首个位置"。
 */
@Component
public final class NamedQueryValidator implements TemplateValidator {

    private final TagRegistry registry;

    public NamedQueryValidator(TagRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int order() {
        return 16;
    }

    @Override
    public String describe() {
        return "§4.5 第 16 条：命名查询重复 / 循环体内命名（E2010 / E2011）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        Map<String, TagNode> firstBy = new LinkedHashMap<>();
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                TagHandler handler = registry.handler(node.name());
                if (handler == null || !handler.queryTag()) {
                    return;
                }
                String name = node.arg("name");
                if (name == null || name.isBlank()) {
                    return;
                }
                if (scope.inLoop()) {
                    // 最近的那个循环标签：ancestors 是"从外到内"的全部祖先，最后一个只是直接父标签，
                    // 直接取它会把 {cms:if}/{cms:detail} 里的命名误报成"在 {cms:if} 内"
                    TagNode loop = null;
                    for (int i = scope.ancestors().size() - 1; i >= 0; i--) {
                        TagNode ancestor = scope.ancestors().get(i);
                        if ("foreach".equals(ancestor.name()) || "list".equals(ancestor.name())
                                || "query".equals(ancestor.name())) {
                            loop = ancestor;
                            break;
                        }
                    }
                    if (loop != null) {
                        report.error(PublishException.error(PublishErrorCode.E2011,
                                "循环内的查询不能命名", node.sourcePath(), node.lineNo(),
                                "{cms:" + node.name() + " name='" + name + "'} 在循环体内（第 "
                                        + loop.lineNo() + " 行的 {cms:" + loop.name() + "} 内）",
                                "把查询提到顶层再命名，或去掉 name（§5.4 约束三）"));
                        return;
                    }
                }
                TagNode previous = firstBy.putIfAbsent(name, node);
                if (previous != null) {
                    // 逐条写进报告后继续遍历（§10.3 第 2 条）：同一模板里其它重名也要一次全报
                    report.error(PublishException.error(PublishErrorCode.E2010,
                            "查询名 " + name + " 重复使用", node.sourcePath(), node.lineNo(),
                            previous.sourcePath() + ":" + previous.lineNo() + " 与 "
                                    + node.sourcePath() + ":" + node.lineNo(),
                            "同一个模板里查询名唯一；两个结果都要用就起两个名字"));
                }
            }
        });
    }
}
