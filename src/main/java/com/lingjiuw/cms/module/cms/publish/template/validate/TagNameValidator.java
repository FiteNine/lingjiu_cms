package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

/**
 * §4.5 第 1 条 ★：标签名是否存在（含编辑距离猜测）→ E1001。
 *
 * <p>文案（可用标签清单 + "你是不是想写 {cms:list}？"）由 {@link TagRegistry#require} 生成——
 * §10.3 第 1、3 条的两条硬要求都在那一处，这里只负责"每个标签节点过一遍"。
 */
@Component
public final class TagNameValidator implements TemplateValidator {

    /**
     * 必须与 {@code TemplateCompiler.ABORT_ORDER}（= 1）保持一致：标签名不认识时这个节点的
     * 参数表、字段集合、页面类型合法性全都无从谈起，本条失败会短路后续校验（见编译器常量注释）。
     */
    private static final int ORDER = 1;

    private final TagRegistry registry;

    public TagNameValidator(TagRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public String describe() {
        return "§4.5 第 1 条：标签名是否存在（E1001）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                registry.require(node.name(), node.sourcePath(), node.lineNo());
            }
        });
    }
}
