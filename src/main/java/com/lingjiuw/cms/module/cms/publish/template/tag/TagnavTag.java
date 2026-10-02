package com.lingjiuw.cms.module.cms.publish.template.tag;

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

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code {cms:tagnav}} —— 标签总览 / 标签云（static-publish.md §6.4）。
 *
 * <p>迭代产出 {@code id} {@code name} {@code slug} {@code url} {@code count} {@code current}
 * {@code class}。{@code count} 的来源是**内容数**（实时算，不是 {@code viewCount}），口径与其他计数
 * 一致（§6.3："一律按同一个判定函数"）——因此它由 {@link ContentProvider#tags} 给出，标签不自己数。
 *
 * <p>{@code type='all'} 是全类型合并（跨类型同名标签按 slug 合并、{@code count} 为合计）；
 * 不写 {@code type} 时按 §6.4 的默认值交给数据层处理合并口径。
 */
@Component
@RequiredArgsConstructor
public class TagnavTag implements TagHandler {

    @Nullable
    private final ContentProvider provider;

    @Override
    public String name() {
        return "tagnav";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of(
                ParamSpec.of("type", "只统计该类型下的标签；all = 全类型合并"),
                ParamSpec.intOf("row", 50, "条数"),
                ParamSpec.enumOf("orderby", "count", "排序", "count", "name", "sort"),
                ParamSpec.intOf("minCount", 1, "只输出内容数 ≥ N 的标签"));
    }

    /** §6.7：除 feed 外都合法。 */
    @Override
    public int maxCount(PageType pageType) {
        return pageType.matrixColumn() == PageType.FEED ? 0 : -1;
    }

    /** 标签体内迭代项的字段（§6.4 的"迭代产出"）：报 E1004 的字段名校验靠它。 */
    @Override
    public Set<String> bodyKeys(PageType pageType) {
        return Set.of("id", "name", "slug", "url", "count", "current", "class");
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        CurrentMarks.requireAllowed(this, node, ctx);

        String typeCode = CurrentMarks.str(node, "type", null);
        int row = CurrentMarks.intOf(node, "row", 50);
        String orderby = CurrentMarks.enumOf(node, "orderby", "count", "count", "name", "sort");
        int minCount = CurrentMarks.intOf(node, "minCount", 1);

        Map<String, Object> channel = ctx.namedValues("channel");
        // channel 只有在 TAGPAGE 上才是"当前标签"；其他页面类型下 channel.id 是分类 / 内容类型 id，
        // 与标签 id 不同源（不同序列表，极易碰撞），直接比对会把 id 恰好相等的标签误标 current
        boolean onTagPage = ctx.pageType() == PageType.TAGPAGE;
        long currentId = onTagPage ? RenderContext.asLong(channel.get("id")) : 0L;
        String currentSlug = onTagPage ? RenderContext.asString(channel.get("slug")) : null;

        for (NavItem item : db().tags(typeCode, row, orderby, minCount)) {
            boolean current = currentSlug != null && currentSlug.equals(RenderContext.asString(item.get("slug")))
                    || currentId > 0 && currentId == item.id();
            CurrentMarks.renderEach(node, ctx, renderer, out, CurrentMarks.equal(item, current).values());
        }
    }


    /**
     * 本页真正该用的取数出口：优先 {@link CurrentProvider}（页面计划显式设置的那个），
     * 没有被设置（请求内的预览 / 模板体检）时退回 Spring 注入的实例。
     *
     * <p>Spring 注入进来的是**请求作用域代理**，发布线程池里没有请求，一调用就抛
     * {@code ScopeNotActiveException}——发布路径必须走 {@code CurrentProvider}。
     */
    private ContentProvider db() {
        ContentProvider current = CurrentProvider.current();
        return current != null ? current : provider;
    }

}
