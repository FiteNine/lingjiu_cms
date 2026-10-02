package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code {cms:breadcrumb}} —— 面包屑（static-publish.md §6.4）。
 *
 * <p><b>层级构成写死</b>：{@code 起点 → 祖先分类（从顶到下）→ [父内容链（withContent=1 且当前条目是
 * 层级内容）] → 当前项}。
 *
 * <p>因此章节页（书 → 章）上 {@code withContent=1} 得到 `首页 / 主分类 / 书 / 章`，最后一级是章
 * （{@code current=1}）、倒数第二级是书；{@code withContent=0} 只到分类层。承载了可分页目录的
 * 详情页（§5.6）与普通详情页走的是同一条路径——{@code channel} 仍是主分类（§5.1 第（4）条）。
 *
 * <p>{@code from='channel'} 时起点就是当前栏目（不再出首页，也不再出它的祖先分类）。
 */
@Component
@RequiredArgsConstructor
public class BreadcrumbTag implements TagHandler {

    @Nullable
    private final ContentProvider provider;

    @Override
    public String name() {
        return "breadcrumb";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of(
                ParamSpec.enumOf("from", "home", "起点：站点名 / 从当前 channel 起", "home", "channel"),
                ParamSpec.boolOf("withContent", 1, "是否包含内容层级（书 → 章、系列 → 篇）"),
                ParamSpec.enumOf("contentLabel", "title", "内容层显示什么", "title", "categoryName"));
    }

    /** §6.7：404 / SEARCH / HOME / STATIC / feed 上没有"浏览位置"，面包屑没有意义。 */
    @Override
    public int maxCount(PageType pageType) {
        return switch (pageType.matrixColumn()) {
            case HOME, SEARCH, STATIC, PAGE404, FEED -> 0;
            default -> -1;
        };
    }

    /** 标签体内迭代项的字段（§6.4 的"迭代产出"）：报 E1004 的字段名校验靠它。 */
    @Override
    public Set<String> bodyKeys(PageType pageType) {
        return Set.of("name", "url", "current", "class", "level");
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        CurrentMarks.requireAllowed(this, node, ctx);

        boolean fromHome = "home".equals(CurrentMarks.enumOf(node, "from", "home", "home", "channel"));
        boolean withContent = CurrentMarks.boolOf(node, "withContent", true);
        String contentLabel = CurrentMarks.enumOf(node, "contentLabel", "title", "title", "categoryName");
        Map<String, Object> channel = ctx.namedValues("channel");

        List<Map<String, Object>> levels = new ArrayList<>();
        if (fromHome) {
            levels.add(level(db().site().name(), "/"));
        }

        long categoryId = CurrentMarks.currentCategoryId(ctx);
        if (categoryId > 0) {
            if (fromHome) {
                for (NavItem ancestor : db().categoryAncestors(categoryId)) {
                    // 数据层的"祖先链"是否含自身没有写死（ContentProvider 的注释只说"从顶到下"），
                    // 这里按 id 排掉自身，链上那一级统一由下面的 channel 承担
                    if (ancestor.id() != categoryId) {
                        levels.add(level(displayName(ancestor), ancestor.url()));
                    }
                }
            }
            // 分类页上它同时也是"当前项"，由下面的去重收口
            levels.add(level(channelName(channel), RenderContext.asString(channel.get("url"))));
        }

        ContentItem entry = ctx.currentItem();
        if (withContent && entry != null) {
            for (ContentItem ancestor : db().contentAncestors(entry.id())) {
                levels.add(level(contentName(ancestor, contentLabel), ancestor.url()));
            }
        }

        Map<String, Object> current = currentObject(ctx, channel, contentLabel);
        // withContent=0 是"只到分类层"（§6.4）：内容层连同当前条目一起不出
        boolean withCurrent = withContent || !ctx.pageType().hasCurrentEntry();
        if (current != null && withCurrent && !sameUrl(levels, current)) {
            levels.add(current);
        }

        for (int i = 0; i < levels.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>(levels.get(i));
            item.put("level", i + 1);
            CurrentMarks.renderEach(node, ctx, renderer, out,
                    CurrentMarks.equal(item, i == levels.size() - 1));
        }
    }

    /** 当前页对象：详情页 / 单页是当前条目本身；列表类页面是 {@code channel}（§5.1 第（4）条）。 */
    private Map<String, Object> currentObject(RenderContext ctx, Map<String, Object> channel,
                                              String contentLabel) {
        ContentItem entry = ctx.currentItem();
        if (entry != null && ctx.pageType().hasCurrentEntry()) {
            return level(contentName(entry, contentLabel), entry.url());
        }
        return channel.isEmpty() || channelName(channel) == null
                ? null
                : level(channelName(channel), RenderContext.asString(channel.get("url")));
    }

    /** {@code channel} 的显示名：{@code label} 是给人看的（§5.1 第（4）条），没有才退回 {@code name}。 */
    private static String channelName(Map<String, Object> channel) {
        String label = RenderContext.asString(channel.get("label"));
        return label != null ? label : RenderContext.asString(channel.get("name"));
    }

    /** 末级去重：当前项已经在链上（分类页）时不再追加一级。 */
    private static boolean sameUrl(List<Map<String, Object>> levels, Map<String, Object> candidate) {
        if (levels.isEmpty()) {
            return false;
        }
        Object last = levels.get(levels.size() - 1).get("url");
        // 空安全比较：两级的 url 都为 null（channel 没给 url）时也要判为同一项，
        // 否则分类页上"当前分类"会被追加两次
        return java.util.Objects.equals(last, candidate.get("url"));
    }

    private static Map<String, Object> level(String name, String url) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("url", url);
        return values;
    }

    private static String displayName(NavItem item) {
        return item.label() != null ? item.label() : RenderContext.asString(item.get("name"));
    }

    /** {@code contentLabel}：内容层显示 {@code title} 还是 {@code categoryName}（§6.4）。 */
    private static String contentName(ContentItem item, String contentLabel) {
        if ("categoryName".equals(contentLabel)) {
            return RenderContext.asString(item.get("categoryName"));
        }
        return item.title();
    }


    /**
     * 本页真正该用的取数出口：优先 {@link CurrentProvider}（页面计划显式设置的那个），
     * 没有被设置（请求内的预览 / 模板体检）时退回 Spring 注入的实例。
     *
     * <p>注入进来的是单例 {@code RequestAwareProvider}（{@code ContentProviderConfig}，已放弃
     * 请求作用域代理）：它在非请求线程上回落到 siteId=0 的空出口，**不会抛异常**。
     * 因此发布路径必须优先走 {@code CurrentProvider}，否则会静默产出空数据（与预演不一致）。
     */
    private ContentProvider db() {
        ContentProvider current = CurrentProvider.current();
        return current != null ? current : provider;
    }

}
