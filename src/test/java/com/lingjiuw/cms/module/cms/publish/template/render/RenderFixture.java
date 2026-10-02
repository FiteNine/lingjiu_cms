package com.lingjiuw.cms.module.cms.publish.template.render;

import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.DefaultTemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.Node;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;
import com.lingjiuw.cms.module.cms.publish.template.ast.TextNode;
import com.lingjiuw.cms.module.cms.publish.template.tag.ElseTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.ForeachTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.IfTag;
import com.lingjiuw.cms.module.cms.publish.template.tag.IncludeTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 渲染用例的最小夹具：**手写 AST**，不经过编译器。理由有两条——渲染器的输入本来就是一整棵
 * 已编译的树（include 已展开、{@code else} 已绑成 {@code elseIndex}），而编译期那半边是
 * 别人的文件；手写节点让每个用例只表达"渲染期该怎样"。
 */
final class RenderFixture {

    static final String PATH = "news/list.html";
    static final String TYPE = "product";

    private RenderFixture() {
    }

    /** §6.1 的结构标签全在这里（查询 / 导航 / 交互类是别的代理的文件）。 */
    static TemplateRenderer renderer(TagHandler... extra) {
        List<TagHandler> handlers = new ArrayList<>(
                List.of(new IncludeTag(), new IfTag(), new ElseTag(), new ForeachTag()));
        handlers.addAll(List.of(extra));
        return new DefaultTemplateRenderer(new TagRegistry(handlers));
    }

    static String render(RenderContext ctx, Node... nodes) {
        return render(renderer(), ctx, nodes);
    }

    static String render(TemplateRenderer renderer, RenderContext ctx, Node... nodes) {
        StringBuilder out = new StringBuilder();
        renderer.render(new TemplateAst(PATH, List.of(nodes), List.of(), null, null, "ast-1"), ctx, out);
        return out.toString();
    }

    /** 详情页的当前条目（§5.1 第（1）条：栈底 = 当前条目，`item` 作用域也存在）。 */
    static RenderContext detail(ContentItem entry) {
        return RenderContext.forPage(PageType.DETAIL, TYPE, entry);
    }

    /** 首页：没有当前条目，栈底为空（§5.1 那张表）。 */
    static RenderContext home() {
        return RenderContext.forPage(PageType.HOME, null, null);
    }

    static ContentItem item(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            values.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return ContentItem.of(values);
    }

    static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            values.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return values;
    }

    static TextNode text(String text) {
        return new TextNode(text, 1, PATH);
    }

    static FieldNode field(String path) {
        return field(path, Map.of());
    }

    static FieldNode field(String path, Map<String, String> args) {
        return new FieldNode(List.of(path.split("\\.")), args, 7, PATH);
    }

    /** 块标签（体可能为空，但不是自闭合）。 */
    static TagNode tag(String name, Map<String, String> args, List<Node> body) {
        return new TagNode(name, args, body, -1, 9, PATH);
    }

    /** 自闭合标签：{@code body == null}（TagNode 用 null 表示自闭合写法）。 */
    static TagNode selfClosing(String name, Map<String, String> args) {
        return new TagNode(name, args, null, -1, 9, PATH);
    }

    static TagNode ifBody(String fieldPath, List<Node> body) {
        return new TagNode("if", Map.of("field", fieldPath), body, -1, 9, PATH);
    }

    /** {@code {cms:if}…{cms:else/}…{/cms:if}}：两段体拼成一个 body，位置记在 elseIndex 上。 */
    static TagNode ifElse(String fieldPath, List<Node> before, List<Node> after) {
        List<Node> body = new ArrayList<>(before);
        body.addAll(after);
        return new TagNode("if", Map.of("field", fieldPath), body, before.size(), 9, PATH);
    }

    static TagNode foreach(String fieldPath, Map<String, String> args, List<Node> body) {
        Map<String, String> all = new LinkedHashMap<>(args);
        all.put("field", fieldPath);
        return new TagNode("foreach", all, body, -1, 11, PATH);
    }
}
