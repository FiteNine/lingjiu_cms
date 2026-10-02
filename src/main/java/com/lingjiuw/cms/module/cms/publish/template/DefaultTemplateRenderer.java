package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.Node;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;
import com.lingjiuw.cms.module.cms.publish.template.ast.TextNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模板渲染器（static-publish.md §5）。**无状态**：一次渲染的全部可变状态都在
 * {@link RenderContext} 里，因此同一个实例可以被 4 个发布线程共享（§12.5）。
 *
 * <p>渲染分两步，顺序不能改（§5.4）：
 * <ol>
 *   <li>预解析：对"不在循环体内"的 {@link TagHandler#preResolve()} 标签调 {@link TagHandler#prepare}；
 *       这样写在列表**上方**的 {@code {cms:if field='page.empty'}} 才有确定行为；</li>
 *   <li>渲染：节点树按顺序输出。</li>
 * </ol>
 *
 * <p>预解析的**下钻规则**（口径由内核负责人定死）：**只下钻 {@code {cms:if}} 的体**，
 * 其余标签体（{@code foreach} / {@code list} / {@code query}…）一律不下钻——它们的体会被
 * 重复渲染，把"顶层查询"算在它们里面会让行为依赖书写位置，而 §5.4 存在的理由正是消灭
 * 位置相关的行为。
 */
@Component
public class DefaultTemplateRenderer implements TemplateRenderer {

    private static final String IF = "if";

    private final TagRegistry tags;

    public DefaultTemplateRenderer(TagRegistry tags) {
        this.tags = tags;
    }

    @Override
    public void render(TemplateAst ast, RenderContext ctx, StringBuilder out) {
        preResolve(ast.nodes(), ctx);
        renderNodes(ast.nodes(), ctx, out);
    }

    @Override
    public void renderNodes(List<Node> nodes, RenderContext ctx, StringBuilder out) {
        for (Node node : nodes) {
            switch (node) {
                // 模板文本永不转义（§5.2.1）：<div> 要原样出去
                case TextNode text -> out.append(text.text());
                case FieldNode field -> renderField(field, ctx, out);
                case TagNode tag -> tags.require(tag.name(), tag.sourcePath(), tag.lineNo())
                        .render(tag, ctx, this, out);
            }
        }
    }

    @Override
    public void renderField(FieldNode node, RenderContext ctx, StringBuilder out) {
        Resolution resolution = resolveOrFail(ctx, node.path(), node.sourcePath(), node.lineNo());
        // 字段声明从"含这个字段的那一帧"取（RenderContext.fieldDef）；取不到 = 声明未知，
        // FieldOutput 会退化成"只做语法校验 + 按需要转义"。
        FieldDef def = ctx.fieldDef(node.path());
        out.append(FieldOutput.render(resolution.value(), def, node));
    }

    @Override
    public String format(Object value, FieldDef def, Map<String, String> formatArgs) {
        return FieldOutput.format(value, def, formatArgs);
    }

    /* ---------------- §5.4 第 2 步：预解析 ---------------- */

    private void preResolve(List<Node> nodes, RenderContext ctx) {
        for (Node node : nodes) {
            if (!(node instanceof TagNode tag)) {
                continue;
            }
            TagHandler handler = tags.handler(tag.name());
            if (handler != null && handler.preResolve()) {
                handler.prepare(tag, ctx);
            }
            if (IF.equals(tag.name())) {
                preResolve(tag.bodyBeforeElse(), ctx);
                preResolve(tag.bodyAfterElse(), ctx);
            }
        }
    }

    /* ---------------- 字段路径：解析与报错口径（§5.1、§10.2） ---------------- */

    /**
     * 解析一条字段路径，失败就报错。{@code {cms:if}} 与 {@code {cms:foreach}} 的 {@code field}
     * 参数走的也是这里——**报错文案只有一份**，否则同一个写错的字段名在不同标签上会给出
     * 三种不同的提示。
     */
    public static Resolution resolveOrFail(RenderContext ctx, List<String> path, String sourcePath,
                                           int lineNo) {
        Resolution resolution = ctx.resolve(path);
        if (resolution.found()) {
            return resolution;
        }
        String pathText = String.join(".", path);
        if (path.isEmpty()) {
            throw PublishException.error(PublishErrorCode.E1002, "field 参数是空的", sourcePath, lineNo,
                    "field='' 解析不出任何字段路径",
                    "写一个字段路径，写法与 [field:xxx/] 完全一致，例如 field='title' / field='page.empty'");
        }
        // 无前缀（匿名层）找不到，或具名作用域里没有这个 key：都是"字段名不在可用集合"（E1004）
        if (resolution.brokenAt() == 0
                || (resolution.brokenAt() == 1 && RenderContext.RESERVED_NAMES.contains(path.get(0)))) {
            throw PublishException.error(PublishErrorCode.E1004, "字段 " + pathText + " 不存在",
                    sourcePath, lineNo,
                    "引擎在" + resolution.scopeLabel() + "里找过它；可用的字段有："
                            + resolution.availableKeys(),
                    "字段 code 区分大小写；具名作用域要写前缀（site. / channel. / page. / param. / "
                            + "query. / item.），无前缀只在当前条目与循环项里找（§5.1）");
        }
        // 中间段断掉 / .count 用错（§5.1 的表格、E1005）
        int brokenAt = resolution.brokenAt();
        String broken = path.size() > brokenAt ? path.get(brokenAt) : "";
        String prefix = String.join(".", path.subList(0, brokenAt));
        String advice = "count".equals(broken)
                ? "count 只对多值字段有效（IMAGES / FILES / RELATION / TAGS / JSON / ENUM_MULTI，"
                        + "以及引擎预加载的 children / toc / categories / ancestors / tags / site.alternates）"
                : "先 {cms:if field='" + prefix + "'} 判空，或核对这一段的名字";
        throw PublishException.error(PublishErrorCode.E1005,
                "路径 " + pathText + " 在第 " + (brokenAt + 1) + " 段断掉",
                sourcePath, lineNo,
                "在 " + prefix + " 上取不到 " + broken + "；此处可用的 key："
                        + resolution.availableKeys(),
                advice);
    }

    /**
     * 把 {@code field} 参数（{@code {cms:if}} / {@code {cms:foreach}}）拆成路径段，
     * 写法与 {@code [field:xxx/]} 完全一致（§6.2）：点号分段，段是标识符或数字下标。
     */
    public static List<String> splitPath(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return List.of();
        }
        List<String> path = new ArrayList<>();
        for (String segment : pathText.trim().split("\\.")) {
            if (!segment.isEmpty()) {
                path.add(segment);
            }
        }
        return List.copyOf(path);
    }
}
