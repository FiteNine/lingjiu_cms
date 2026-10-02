package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code {cms:prenext}} —— 上一篇 / 下一篇（static-publish.md §6.4）。
 *
 * <p>迭代产出 {@code type}（{@code prev} / {@code next}）{@code title} {@code url} {@code publishTime}；
 * **不存在时不渲染该次迭代**（两个方向都不存在则整体不渲染），所以"已是第一篇"这类提示写在模板里：
 * {@code {cms:if field='item.hasPrev'}}（§5.5），标签内不做分支。
 *
 * <p>数据来自 {@link ContentProvider#neighbors}，因此"相邻"的判定与计数同一个口径（§6.3）；
 * {@code within='parent'} 只在同一父内容内相邻（小说章节必备），{@code category} 的默认值就是
 * {@code all}。
 */
@Component
@RequiredArgsConstructor
public class PrenextTag implements TagHandler {

    @Nullable
    private final ContentProvider provider;

    @Override
    public String name() {
        return "prenext";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of(
                ParamSpec.enumOf("type", "both", "要输出的方向", "prev", "next", "both"),
                ParamSpec.enumOf("within", "type", "相邻的范围：同类型 / 仅同一父内容内", "type", "parent"),
                ParamSpec.of("category", "all", "限定同一分类内相邻（all = 不限）"));
    }

    /** §6.7：上下篇只在详情页上成立（DPAGE 与 DETAIL 同模板、同上下文）。 */
    @Override
    public int maxCount(PageType pageType) {
        return switch (pageType.matrixColumn()) {
            case DETAIL -> -1;
            default -> 0;
        };
    }

    /** 标签体内迭代项的字段（§6.4 的"迭代产出"）：报 E1004 的字段名校验靠它。 */
    @Override
    public Set<String> bodyKeys(PageType pageType) {
        return Set.of("type", "title", "url", "publishTime");
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        CurrentMarks.requireAllowed(this, node, ctx);

        String direction = CurrentMarks.enumOf(node, "type", "both", "prev", "next", "both");
        String within = CurrentMarks.enumOf(node, "within", "type", "type", "parent");
        String category = CurrentMarks.str(node, "category", "all");

        ContentProvider.Neighbors neighbors = db().neighbors(ctx.typeCode(), ctx.currentEntryId(),
                within, category);
        if (neighbors == null) {
            return;
        }
        // type='both' 时先上一篇、后下一篇；不存在的那一侧不渲染该次迭代
        if (!"next".equals(direction)) {
            emit(node, ctx, renderer, out, "prev", neighbors.prev());
        }
        if (!"prev".equals(direction)) {
            emit(node, ctx, renderer, out, "next", neighbors.next());
        }
    }

    private static void emit(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out,
                             String type, ContentItem item) {
        if (item == null) {
            return;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("type", type);
        values.put("title", item.title());
        values.put("url", item.url());
        values.put("publishTime", item.get("publishTime"));
        CurrentMarks.renderEach(node, ctx, renderer, out, values);
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
