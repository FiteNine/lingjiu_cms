package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code {cms:else/}}（static-publish.md §3.5 裁定四）：**块内分隔，不是新词法元素**。
 *
 * <p>编译器把它从 {@code {cms:if}} 的体里摘掉，并把位置记在 {@link TagNode#elseIndex()} 上；
 * 渲染期只看 {@code if} 的 {@code bodyBeforeElse()} / {@code bodyAfterElse()}，永远不会走到这里。
 * 它仍然要注册——§6.1 的标签总表是封闭清单，少一个"可用标签"就少一条（§4.5 第 1 条）——
 * 但 {@link #render} 只报内部错误：静默什么都不做会把"编译器没绑定 else"这种 bug 藏起来。
 */
@Component
public class ElseTag implements TagHandler {

    @Override
    public String name() {
        return "else";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of();
    }

    /** 自闭合标签：{@code {cms:else/}}，写成块标签 → E1003（§4.5 第 3 条）。 */
    @Override
    public boolean needsBody() {
        return false;
    }

    /** §6.7 的矩阵第一行：任何页面类型上个数不限。 */
    @Override
    public int maxCount(PageType pageType) {
        return -1;
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        throw new IllegalStateException("{cms:else/} 应该在编译期被绑定成 {cms:if} 的 elseIndex"
                + "（§3.5 裁定四），渲染期不该遇到它。出现位置：" + node.sourcePath() + ":" + node.lineNo()
                + "。若看到这条，说明编译器没有把它从 {cms:if} 的体里摘掉，或有人拿未编译的 AST 直接渲染。");
    }
}
