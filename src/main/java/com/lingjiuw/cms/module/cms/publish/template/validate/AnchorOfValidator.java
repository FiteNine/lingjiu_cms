package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

/**
 * §4.5 第 14 条：{@code of='self'} / {@code of='parent'} 用在不成立的位置 → E2009。
 *
 * <p>锚定项的判定顺序（§3.7 裁定三，**先命中先算**）：
 * <ol>
 *   <li>在 {@code {cms:list}} / {@code {cms:query}} / {@code {cms:foreach}} 的循环体内 →
 *       锚定项 = 栈顶迭代项，**永远成立**，因此循环体内的 {@code of} 一律放行（父是否存在是数据问题，
 *       契约明确写了"得到空集 / 不渲染"不是错误，§5.4 约束三）；</li>
 *   <li>否则锚定项 = 本页面的当前条目。页面没有当前条目（首页 / 列表页 / 标签页 / 归档页 /
 *       筛选页 / 搜索页 / 静态页 / 404 / feed）→ 编译期 E2009；</li>
 *   <li>{@code of='parent'} 还要"锚定项本身有父"才可能成立：当前类型不是层级类型时，
 *       锚定项永远不会有父 → 编译期 E2009（{@link CompileContext#currentTypeHierarchical()}）。</li>
 * </ol>
 */
@Component
public final class AnchorOfValidator implements TemplateValidator {

    public AnchorOfValidator() {
    }

    @Override
    public int order() {
        return 14;
    }

    @Override
    public String describe() {
        return "§4.5 第 14 条：of='self' / of='parent' 的位置（E2009）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                String of = node.arg("of");
                if (of == null || (!"self".equals(of) && !"parent".equals(of))) {
                    return; // 取值非法由第 2 条的 ENUM 检查报 E1002
                }
                if (scope.inLoop()) {
                    return; // 锚定项 = 栈顶迭代项，成立
                }
                if (!ctx.hasCurrentEntry()) {
                    // 逐条写进报告后继续遍历（§10.3 第 2 条）：同一模板里其它标签的 E2009
                    // 不能被这一条吞掉；return 保证同一标签不叠加"不是层级类型"的第二条
                    report.error(PublishException.error(PublishErrorCode.E2009,
                            ctx.pageType() + " 页没有当前条目，of='" + of + "' 无法解析",
                            node.sourcePath(), node.lineNo(),
                            "{cms:" + node.name() + "} of='" + of + "'；本页的锚定项不存在",
                            "of 只在有当前条目的页面（详情页 / 单页）或循环体内成立；"
                                    + "首页这类页面请显式写 type 与 category（§3.7 裁定三）"));
                    return;
                }
                if ("parent".equals(of) && !ctx.currentTypeHierarchical()) {
                    report.error(PublishException.error(PublishErrorCode.E2009,
                            "当前类型 " + ctx.typeCode() + " 不是层级类型，of='parent' 不成立",
                            node.sourcePath(), node.lineNo(),
                            "{cms:" + node.name() + "} of='parent'；锚定项永远没有父",
                            "层级内容（书→章、系列→篇）才能用 of='parent'；取自己的子内容用 of='self'（§2.1、§3.7）"));
                }
            }
        });
    }
}
