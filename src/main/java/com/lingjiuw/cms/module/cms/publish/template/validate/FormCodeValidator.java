package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * §4.5 第 12 条 ★：{@code {cms:form}} 的 {@code code} 是否指向存在的表单定义 → E2008。
 *
 * <p>**{@code hidden} 的 key 一律不校验**（§6.5 v2.2 统一）：key 任意，只有**值**命中 5 个引擎关键字
 * （{@code contentId:self} 这类）才替换，其余按字面量输出且不报错——来源标记（{@code utm_source:wechat}）
 * 恰恰是官网上最常见的隐藏域。v2.1 在 §0.2、§4.5、§10.2 三处写成"key 必须落在允许清单内"，
 * 与 §6.5 直接冲突，以 §6.5 为准。
 */
@Component
public final class FormCodeValidator implements TemplateValidator {

    public FormCodeValidator() {
    }

    @Override
    public int order() {
        return 12;
    }

    @Override
    public String describe() {
        return "§4.5 第 12 条：{cms:form} 的 code 是否存在（E2008）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                if (!"form".equals(node.name())) {
                    return;
                }
                String code = node.arg("code");
                // 空串同样按"必填缺失"处理，由第 2 条报 E1002（否则会报出"表单  不存在"这种双空格文案）
                if (code == null || code.isBlank() || ctx.provider() == null) {
                    return;
                }
                if (ctx.provider().form(code) != null) {
                    return;
                }
                List<String> codes = ctx.provider().formCodes();
                // 逐条写进报告后继续遍历：一页里几个 {cms:form} 的 code 都写错时一次全报
                report.error(PublishException.error(PublishErrorCode.E2008,
                        "表单 " + code + " 不存在", node.sourcePath(), node.lineNo(),
                        "{cms:form code='" + code + "'}",
                        "本站表单有 " + Suggest.join(codes) + Suggest.hint(code, codes)));
            }
        });
    }
}
