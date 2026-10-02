package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

/**
 * §4.5 第 15 条：{@code {cms:detail}} 的 {@code type} 与页面类型的 kind 不匹配 → E3010。
 *
 * <p>规则只有一条：**单页取单页类型、非单页取非单页类型**（§6.3 的 {@code {cms:detail}} 表：
 * "显式给定时必须与页面类型的 kind 相容（{@code SINGLE} 页面上只能取 {@code SINGLE} 类型）"）。
 * 不写 {@code type} 时缺省就是当前页面的类型，天然相容。
 *
 * <p>两处不在这里报：
 * <ul>
 *   <li>{@code {cms:detail}} 出现在不允许它的页面类型上（首页 / 列表页 / 404…）→ §6.7 矩阵的
 *       E3012，本类跳过（避免同一件事两个错误码）；</li>
 *   <li>{@code type} 指向不存在的类型、或写 {@code type='all'} → 第 7 条的 E2006，本类跳过。</li>
 * </ul>
 */
@Component
public final class DetailTypeValidator implements TemplateValidator {

    private final TagRegistry registry;

    public DetailTypeValidator(TagRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public String describe() {
        return "§4.5 第 15 条：{cms:detail} 的 type 与页面 kind（E3010）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        TagHandler detail = registry.handler("detail");
        if (detail != null && detail.maxCount(ctx.pageType().matrixColumn()) == 0) {
            return; // 这个页面类型上根本没有 {cms:detail}，交给 §6.7 的 E3012
        }
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                if (!"detail".equals(node.name()) || ctx.provider() == null) {
                    return;
                }
                String typeCode = node.arg("type") != null ? node.arg("type") : ctx.typeCode();
                if (typeCode == null || "all".equals(typeCode)) {
                    return; // 不存在 / all 由第 7 条报 E2006
                }
                ContentTypeDef def = ctx.provider().type(typeCode);
                if (def == null) {
                    return;
                }
                boolean singlePage = ctx.pageType() == PageType.SINGLE;
                boolean singleType = def.kind() == ContentTypeDef.Kind.SINGLE;
                if (singlePage == singleType) {
                    return;
                }
                String what = singlePage
                        ? "single 页面上不能取 " + typeCode + "（" + def.kind() + "）类型的内容"
                        : ctx.pageType() + " 页面上不能取单页类型 " + typeCode + " 的内容";
                String advice = singlePage
                        ? "{cms:detail} 在单页上只能取 SINGLE 类型；要展示别的类型请用 {cms:query}（§6.3）"
                        : "单页类型只在它自己的页面上出现；要取一条内容请去掉 type，用当前页面的类型";
                throw PublishException.error(PublishErrorCode.E3010, what, node.sourcePath(),
                        node.lineNo(), "{cms:detail type='" + typeCode + "'}；页面类型 " + ctx.pageType(),
                        advice);
            }
        });
    }
}
