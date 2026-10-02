package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

/**
 * §4.5 第 18 条：站点发布选项关掉了某类页面，却存在该类型的模板 → **W5001 警告**（不报错）。
 *
 * <p>为什么是警告而不是错误：主题包可能被多个站点共用（§2.7、§10.2 的 W5001 触发原文），
 * 一个站点关掉归档页不该让另一个站点的主题编译失败。
 *
 * <p>能判定的方向只有一个，另一个方向留给期 2：本类看到的是"**正在编译的这个模板**属于被关掉的
 * 页面类型"（模板路径、页面类型、类型 code 都在手上）。反过来"主题里躺着某类页面的模板而选项关着"
 * 需要遍历主题目录的候选模板（§7.3 的模板查找 + {@code TemplateSource.exists}），那是页面计划期
 * 的输入——期 1 的编译器没有 {@code TemplateSource} 之外的目录视图，因此不做（记在报告里）。
 *
 * <p>选项 → 页面类型的映射在 {@link PageTypeOptions}，与 §7.3 第（3）条共用一份。
 */
@Component
public final class DisabledPageTypeWarner implements TemplateValidator {

    public DisabledPageTypeWarner() {
    }

    @Override
    public int order() {
        return 18;
    }

    @Override
    public String describe() {
        return "§4.5 第 18 条：选项关掉了这类页面但模板存在（W5001，警告）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        if (report == null || ctx == null || ast == null
                || ctx.provider() == null || ctx.provider().site() == null) {
            return;
        }
        String option = PageTypeOptions.optionCode(ctx.pageType(), ctx.typeCode());
        if (option == null || !PageTypeOptions.disabled(ctx.pageType(), ctx.typeCode(), ctx.provider().site())) {
            return;
        }
        report.warn(PublishErrorCode.W5001,
                "[W5001] 站点发布选项 " + option + "=0，但存在该页面类型的模板 " + ast.path()
                        + "（页面类型 " + ctx.pageType() + "）"
                        + "\n  → 建议：主题包被多个站点共用时属正常；本站要出这类页面就把 " + option + " 设为 1");
    }
}
