package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.PaginationKind;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

/**
 * §4.5 第 13 条：{@code {cms:pagelist}} 用在**正文分页**上 → E3003（§5.6）。
 *
 * <p>判定读的是 {@link TemplateAst#paginationKind()}——它是编译器在 include 展开之后按
 * §4.5 口径表算出来的"这一页到底怎么分页"，正是 {@code page.paginationKind} 的取值。
 * 长文拆几十页时页号条没有意义，导航只用"上一页 / 下一页 / 目录"（§5.6），所以这不是风格问题。
 *
 * <p>§6.7 矩阵的 ※2（{@code DETAIL} 上的 {@code {cms:pagelist}} 只在列表分页时合法）
 * 与本条是同一条规则，只在这里实现一次。
 */
@Component
public final class PaginationKindValidator implements TemplateValidator {

    public PaginationKindValidator() {
    }

    @Override
    public int order() {
        return 13;
    }

    @Override
    public String describe() {
        return "§4.5 第 13 条：{cms:pagelist} 用在正文分页上（E3003）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        if (ast.paginationKind() != PaginationKind.CONTENT) {
            return;
        }
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                if (!"pagelist".equals(node.name())) {
                    return;
                }
                throw PublishException.error(PublishErrorCode.E3003,
                        "正文分页不提供页号条", node.sourcePath(), node.lineNo(),
                        "第 " + node.lineNo() + " 行的 {cms:pagelist}；本页 page.paginationKind='content'",
                        "正文分页只用 page.prevUrl / page.nextUrl（§5.6）");
            }
        });
    }
}
