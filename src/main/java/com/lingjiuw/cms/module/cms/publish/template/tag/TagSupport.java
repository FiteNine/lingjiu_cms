package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.Anchor;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询类标签的几件小事：锚定项、当前页号、迭代项的 {@code current} / {@code class} 与默认封面。
 *
 * <p>三件事在契约里各有一处口径，都只写这一遍：
 * <ul>
 *   <li><b>锚定项</b>（§3.7 裁定三）：循环体内 = 栈顶迭代项，否则 = 本页面的当前条目；
 *       §5.4 约束三还要求"锚定项根本不存在"的页面类型（{@code HOME} / {@code STATIC}）
 *       上直接报 E2009——那是编译期的事，渲染期只在**锚定项不存在**时报；</li>
 *   <li><b>{@code current} / {@code class}</b>（§5.5、§6.3 的口径表）：内容查询的迭代项
 *       {@code current} = 该项 id == 当前条目的 id，{@code class} = {@code "current"} / 空串；</li>
 *   <li><b>默认封面</b>（§5.5 第（1）条）：{@code cover} 为空时给 {@code site.defaultCover}，
 *       不是空串——模板据此写 {@code <img src="[field:cover/]">} 而不用判空。</li>
 * </ul>
 */
final class TagSupport {

    private TagSupport() {
    }

    /**
     * 取数出口。标签由 Spring 注入实例字段，不放 {@link RenderContext}——
     * 上下文是"这一页的状态"，取数出口是"进程级依赖"，两者混在一起会让派生页无法复用上下文。
     * 本方法只是把"没注入"这件事在渲染期明确地报出来，而不是让它变成一个 NPE。
     *
     * <p><b>优先用"正在渲染的那一页"的出口</b>（{@link CurrentProvider}，由页面计划显式设置）：
     * Spring 注入进来的那个是请求作用域的代理，在发布线程池里一调用就抛
     * {@code ScopeNotActiveException}。发布路径的出口只能由页面计划给出——
     * 它才知道这一批渲染的是哪个站点。
     */
    static ContentProvider provider(ContentProvider injected, TagNode node, RenderContext ctx) {
        ContentProvider current = CurrentProvider.current();
        if (current != null) {
            return current;
        }
        if (injected == null) {
            throw PublishException.error(PublishErrorCode.E3012,
                    "{cms:" + node.name() + "} 没有取数出口", node.sourcePath(), node.lineNo(),
                    "标签实例没有可用的 ContentProvider（本线程不是发布线程，也没有请求作用域）",
                    "发布路径由页面计划提供出口；请求内的渲染由 ContentProviderConfig 提供（§4.1）");
        }
        return injected;
    }

    /**
     * 锚定项坐标（§3.7 裁定三）。{@code of} / {@code relate} / {@code author='self'} 都基于它。
     * 没有锚定项时返回 {@link Anchor#NONE}（{@code of} 用在 {@code HOME} 上的报错由编译期给，§5.4 约束三）。
     */
    static Anchor anchor(RenderContext ctx, ContentProvider provider, TagNode node) {
        Anchor anchor = ctx.anchor();
        if (anchor.present()) {
            return anchor;
        }
        if (requiresAnchor(node) && !ctx.pageType().hasCurrentEntry()) {
            throw PublishException.error(PublishErrorCode.E2009,
                    "{cms:" + node.name() + "} 的 " + anchorParam(node) + " 无法解析",
                    node.sourcePath(), node.lineNo(),
                    "当前页面类型 " + ctx.pageType() + " 没有当前条目（锚定项）",
                    "of='self' 用在详情页 / 单页上；首页要子内容请用 where 条件");
        }
        return anchor;
    }

    private static boolean requiresAnchor(TagNode node) {
        return node.arg("of") != null || anchorRelatesToField(node);
    }

    private static boolean anchorRelatesToField(TagNode node) {
        String relate = node.arg("relate");
        // relate 的三个取值（tag / category / field:<code>）在 SQL 里都依赖锚定项 id：
        // 缺锚定项时应与 of 一样报 E2009，而不是按 anchorId=0 静默查出空集（§5.4 约束三、§6.3）
        return relate != null && !relate.isBlank();
    }

    private static String anchorParam(TagNode node) {
        return node.arg("of") != null ? "of" : "relate";
    }

    /** 当前页号：派生页由页面计划覆盖 {@code page.pageNo} 后重渲染，标签只读它（§5.4 第 5 步）。 */
    static int pageNo(RenderContext ctx) {
        Object value = ctx.pageVar("pageNo");
        int pageNo = (int) RenderContext.asLong(value);
        return pageNo < 1 ? 1 : pageNo;
    }

    /** 这一页自己的 URL（{@code page.currentUrl}）；没注入 {@link com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder} 时是空串。 */
    static String currentUrl(RenderContext ctx, int pageNo) {
        if (ctx.pageUrls() == null) {
            return "";
        }
        String url = pageNo <= 1 ? ctx.pageUrls().firstPageUrl() : ctx.pageUrls().pageUrl(pageNo);
        return url == null ? "" : url;
    }

    /**
     * 一个迭代项：内容字段 + {@code current} / {@code class}（§5.5）+ 默认封面（§5.5 第（1）条）。
     *
     * @param item      内容项
     * @param currentId 当前条目的 id；0 表示本页没有当前条目
     */
    static Map<String, Object> iterationItem(ContentItem item, long currentId, RenderContext ctx) {
        Map<String, Object> values = item.mutableValues();
        boolean current = currentId > 0 && item.id() == currentId;
        values.put("current", current);
        values.put("class", current ? "current" : "");
        Object cover = values.get("cover");
        if (cover == null || String.valueOf(cover).isBlank()) {
            values.put("cover", defaultCover(ctx));
        }
        return values;
    }

    /** {@code site.defaultCover}；站点作用域还没注入时返回空串。 */
    static String defaultCover(RenderContext ctx) {
        Object value = ctx.namedValues("site").get("defaultCover");
        return value == null ? "" : String.valueOf(value);
    }

    /** {@code query.<name>.rows}：具名查询的结果集（{@code {cms:query}} 渲染时复用它，§6.3）。 */
    @SuppressWarnings("unchecked")
    static List<ContentItem> namedRows(RenderContext ctx, String name) {
        Object meta = ctx.namedValues("query").get(name);
        if (!(meta instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object rows = ((Map<String, Object>) map).get("rows");
        if (!(rows instanceof List<?> list)) {
            return List.of();
        }
        List<ContentItem> items = new ArrayList<>(list.size());
        for (Object row : list) {
            items.add(row instanceof ContentItem item ? item : ContentItem.of(Map.of()));
        }
        return items;
    }
}
