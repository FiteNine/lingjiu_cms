package com.lingjiuw.cms.module.cms.publish.nav;

import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.Resolution;
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
 * 导航标签测试的夹具（static-publish.md §6.4）：
 * <ul>
 *   <li>造 {@link TagNode}（标签体只放一个占位字段，标签实现自己决定渲染几次）；</li>
 *   <li>造页面上下文（页面类型 / 当前条目 / {@code channel} / {@code page} 字段 / 分页 URL）；</li>
 *   <li>{@link Recorder}：把每次迭代渲染时**标签体看到的字段值**记下来——它读的就是
 *       {@code [field:x/]} 会读到的东西（{@link RenderContext#resolve}），因此断言的是模板作者
 *       真正能用的取值，而不是标签内部的结构。</li>
 * </ul>
 */
final class NavTestSupport {

    private NavTestSupport() {
    }

    static TagNode tag(String name, Map<String, String> args) {
        return new TagNode(name, args, null, -1, 1, "nav_test.html");
    }

    static TagNode bodyTag(String name, Map<String, String> args) {
        return new TagNode(name, args,
                List.of(new FieldNode(List.of("label"), Map.of(), 1, "nav_test.html")), -1, 1, "nav_test.html");
    }

    static Map<String, String> args(String... keyValues) {
        Map<String, String> args = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            args.put(keyValues[i], keyValues[i + 1]);
        }
        return args;
    }

    /** 一个页面上下文：当前条目压栈底（{@code DETAIL}/{@code SINGLE}），并放进 {@code channel}。 */
    static RenderContext page(PageType pageType, String typeCode, ContentItem entry,
                              Map<String, Object> channel) {
        RenderContext ctx = RenderContext.forPage(pageType, typeCode, entry);
        if (channel != null && !channel.isEmpty()) {
            ctx.pushNamed("channel", channel);
        }
        return ctx;
    }

    static RenderContext page(PageType pageType, String typeCode) {
        return page(pageType, typeCode, null, null);
    }

    /** 列表页：{@code channel} 是当前栏目（分类来源），{@code page} 作用域带上分页字段。 */
    static RenderContext listPage(Map<String, Object> channel, int pageNo, int totalPages) {
        RenderContext ctx = page(PageType.LIST, "article", null, channel);
        ctx.putPage("url", pageUrl(pageNo));
        ctx.putPage("pageNo", pageNo);
        ctx.putPage("totalPages", totalPages);
        ctx.putPage("paginationKind", "list");
        ctx.pageUrls(new PageUrlBuilder() {
            @Override
            public String firstPageUrl() {
                return "/news/";
            }

            @Override
            public String pageUrl(int page) {
                return "/news/page-" + page + "/";
            }
        });
        return ctx;
    }

    static String pageUrl(int pageNo) {
        return pageNo <= 1 ? "/news/" : "/news/page-" + pageNo + "/";
    }

    /** 分类页的 {@code channel}（§5.1 第（4）条第一行）。 */
    static Map<String, Object> categoryChannel(long id, String name, String slug) {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("id", id);
        channel.put("name", name);
        channel.put("label", name);
        channel.put("slug", slug);
        channel.put("url", "/" + slug + "/");
        channel.put("path", "/" + slug);
        channel.put("count", 0);
        return channel;
    }

    /** 把一次迭代里标签体看到的字段值记下来。 */
    static final class Recorder implements TemplateRenderer {

        private final List<Map<String, Object>> frames = new ArrayList<>();

        List<Map<String, Object>> frames() {
            return frames;
        }

        Map<String, Object> frame(int index) {
            return frames.get(index);
        }

        int size() {
            return frames.size();
        }

        @Override
        public void render(TemplateAst ast, RenderContext ctx, StringBuilder out) {
            throw new UnsupportedOperationException("导航标签不渲染整棵模板");
        }

        @Override
        public void renderNodes(List<Node> nodes, RenderContext ctx, StringBuilder out) {
            Map<String, Object> frame = new LinkedHashMap<>();
            for (String key : ctx.anonymousKeys().split(", ")) {
                if (key.isEmpty()) {
                    continue;
                }
                Resolution resolution = ctx.resolve(List.of(key));
                if (resolution.found()) {
                    frame.put(key, resolution.value());
                }
            }
            frames.add(frame);
            out.append('|');
        }

        @Override
        public void renderField(FieldNode node, RenderContext ctx, StringBuilder out) {
            throw new UnsupportedOperationException("导航标签不渲染字段");
        }

        @Override
        public String format(Object value, FieldDef def, Map<String, String> formatArgs) {
            if (value == null) {
                return "";
            }
            String text = String.valueOf(value);
            if (def != null && def.raw()) {
                return text;
            }
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
        }
    }
}
