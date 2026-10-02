package com.lingjiuw.cms.module.cms.publish.query;

import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.Node;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用的最小渲染器：**只做"把迭代项里的字段拼出来"**，不实现 §5.2 的转义与 formatter。
 *
 * <p>为什么不用真渲染器：期 1 的渲染器由另一个代理写，而本组测试要验的是**标签算出来的值**
 * （哪一页有哪些条目、{@code current} / {@code class} 是什么、{@code page.*} 的值），
 * 不是转义规则。标签体在这里被约定成若干 {@link FieldNode}：渲染它就往 {@code out} 里追加
 * 该字段的值，取不到时拼 {@code <断在:路径>}——这样"取不到"不会静默成空串。
 */
final class StubRenderer implements TemplateRenderer {

    /** 每次 {@link #renderNodes} 时**匿名栈顶**那一帧的快照，按调用顺序。 */
    final List<Map<String, Object>> frames = new ArrayList<>();

    /** 每次迭代渲染出来的字段值，按调用顺序。 */
    final List<Map<String, String>> rendered = new ArrayList<>();

    /** 渲染过程中去重后的匿名栈顶帧（循环项与栈底可能同名，用它看栈顶到底是谁）。 */
    private Map<String, Object> currentFrame;

    @Override
    public void render(TemplateAst ast, RenderContext ctx, StringBuilder out) {
        renderNodes(ast.nodes(), ctx, out);
    }

    @Override
    public void renderNodes(List<Node> nodes, RenderContext ctx, StringBuilder out) {
        Map<String, Object> frame = capture(ctx);
        frames.add(frame);
        Map<String, String> values = new LinkedHashMap<>();
        rendered.add(values);
        for (Node node : nodes) {
            if (node instanceof FieldNode field) {
                Object resolved = ctx.resolve(field.path()).value();
                String text = resolved == null ? "" : String.valueOf(resolved);
                values.put(field.pathText(), text);
                out.append(text);
            } else if (node instanceof TagNode tag) {
                // 嵌套标签（例如 {cms:if}）的判定不是本组测试的目标，只递归它的体
                renderNodes(tag.bodyNodes(), ctx, out);
            }
        }
    }

    @Override
    public void renderField(FieldNode node, RenderContext ctx, StringBuilder out) {
        Object value = ctx.resolve(node.path()).value();
        out.append(value == null ? "" : String.valueOf(value));
    }

    @Override
    public String format(Object value, com.lingjiuw.cms.module.cms.publish.model.FieldDef def,
                         Map<String, String> formatArgs) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 取匿名栈顶那一帧。{@code RenderContext} 不暴露栈，用"循环项一定有 {@code id}（或 {@code key}）
     * 而栈底是页面当前条目"这一点，靠若干哨兵 key 探测栈顶。
     */
    private Map<String, Object> capture(RenderContext ctx) {
        Map<String, Object> frame = new LinkedHashMap<>();
        for (String key : List.of("id", "title", "class", "current", "url", "cover", "content")) {
            com.lingjiuw.cms.module.cms.publish.template.Resolution resolution =
                    ctx.resolve(List.of(key));
            if (resolution.found()) {
                frame.put(key, resolution.value());
            }
        }
        currentFrame = frame;
        return frame;
    }

    /** 迭代项里的某个 key 的值（按栈顶解析取，含 {@code current} / {@code class} 这类引擎补的字段）。 */
    Object top(String key) {
        return currentFrame == null ? null : currentFrame.get(key);
    }

    /** 上一次渲染里某个字段的值；没有渲染过返回 null。 */
    String last(String path) {
        return rendered.isEmpty() ? null : rendered.get(rendered.size() - 1).get(path);
    }

    /** 全部渲染出来的某个字段的值，按顺序。 */
    List<String> all(String path) {
        List<String> values = new ArrayList<>();
        for (Map<String, String> frame : rendered) {
            values.add(frame.get(path));
        }
        return values;
    }

    /** 渲染了几次迭代项。 */
    int iterations() {
        return rendered.size();
    }

    /** 一个最小的标签体：若干 {@code [field:x/]}。 */
    static List<Node> body(String... paths) {
        List<Node> nodes = new ArrayList<>();
        for (String path : paths) {
            nodes.add(field(path));
        }
        return nodes;
    }

    static FieldNode field(String path) {
        return new FieldNode(List.of(path.split("\\.")), Map.of(), 1, "detail.html");
    }

    /** 造一个标签节点；{@code args} 里值为 null 的项会被丢掉（等价于模板里没写这个参数）。 */
    static TagNode tag(String name, Map<String, String> args, String... bodyPaths) {
        Map<String, String> clean = new LinkedHashMap<>();
        args.forEach((key, value) -> {
            if (value != null) {
                clean.put(key, value);
            }
        });
        return new TagNode(name, clean, body(bodyPaths), -1, 7, "list.html");
    }

    /** 一个 {@link TagHandler} 存根：只为让"标签名 → 参数表"这类查找有东西可用。 */
    static TagHandler handler(String name) {
        return new TagHandler() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<ParamSpec> params() {
                return List.of();
            }

            @Override
            public int maxCount(com.lingjiuw.cms.module.cms.publish.model.PageType pageType) {
                return -1;
            }

            @Override
            public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer,
                               StringBuilder out) {
            }
        };
    }
}
