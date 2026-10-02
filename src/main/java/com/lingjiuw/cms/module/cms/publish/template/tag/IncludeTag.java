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
 * {@code {cms:include}}（static-publish.md §6.2、§5.3）：片段包含。
 *
 * <pre>
 * {cms:include file='header.html' title='最新'/}
 * </pre>
 *
 * <p><b>include 在编译期展开</b>：{@code file} 与其余参数都是纯字面量，可以完全静态展开，
 * 展开后片段共享同一作用域栈、节点保留片段内的真实路径与行号。因此**渲染期不会遇到 include
 * 节点**，{@link #render} 只报内部错误——静默什么都不做会把"编译器没展开"这种 bug 藏起来。
 *
 * <p>它仍然要注册：§6.1 的标签总表是封闭清单，而且 {@code {cms:include}} 是唯一接受任意额外
 * 参数的标签（{@link #openParams()}），编译期校验"参数名不得与 6 个保留名同名"要用到它。
 */
@Component
public class IncludeTag implements TagHandler {

    @Override
    public String name() {
        return "include";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of(ParamSpec.required("file", "相对当前主题片段目录的路径（§5.3），纯字面量"));
    }

    /** 其余参数都是片段内的 {@code [field:param.<key>/]}，但不得与 6 个保留名同名（§6.2）。 */
    @Override
    public boolean openParams() {
        return true;
    }

    /** 自闭合标签：{@code {cms:include …/}}（§6.2）。 */
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
        throw new IllegalStateException("{cms:include} 应该在编译期展开（§5.3）：file 与参数都是字面量，"
                + "渲染期不该遇到 include 节点。出现位置：" + node.sourcePath() + ":" + node.lineNo()
                + "，file='" + node.arg("file") + "'。若看到这条，说明编译器没有展开 include，"
                + "或有人拿未编译的 AST 直接渲染。");
    }
}
