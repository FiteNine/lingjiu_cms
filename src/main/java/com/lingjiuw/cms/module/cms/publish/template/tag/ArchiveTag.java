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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code {cms:archive}} —— 归档导航（年 / 月）（static-publish.md §6.4）。
 *
 * <p>迭代产出 {@code year} {@code month} {@code label}（{@code 2026 年 3 月}）{@code url}
 * {@code count} {@code current} {@code class}。{@code count} 是内容数（实时算），口径与其他计数一致。
 */
@Component
@RequiredArgsConstructor
public class ArchiveTag implements TagHandler {

    @Nullable
    private final ContentProvider provider;

    @Override
    public String name() {
        return "archive";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of(
                ParamSpec.of("type", "all = 跨类型的年月归档；缺省 = 当前页面的类型"),
                ParamSpec.enumOf("mode", "month", "归档粒度", "year", "month"),
                ParamSpec.intOf("row", 24, "条数"),
                ParamSpec.of("category", "限定栏目，all = 不限；缺省 = 当前栏目"));
    }

    /** §6.7：除 feed 外都合法。 */
    @Override
    public int maxCount(PageType pageType) {
        return pageType.matrixColumn() == PageType.FEED ? 0 : -1;
    }

    /** 标签体内迭代项的字段（§6.4 的"迭代产出"）：报 E1004 的字段名校验靠它。 */
    @Override
    public Set<String> bodyKeys(PageType pageType) {
        return Set.of("year", "month", "label", "url", "count", "current", "class");
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        CurrentMarks.requireAllowed(this, node, ctx);

        String typeCode = CurrentMarks.str(node, "type", ctx.typeCode());
        String mode = CurrentMarks.enumOf(node, "mode", "month", "year", "month");
        int row = CurrentMarks.intOf(node, "row", 24);
        String category = CurrentMarks.str(node, "category", null);
        if (category == null) {
            long categoryId = CurrentMarks.currentCategoryId(ctx);
            category = categoryId > 0 ? String.valueOf(categoryId) : null;
        }

        Map<String, Object> channel = ctx.namedValues("channel");
        boolean onArchive = ctx.pageType() == PageType.ARCHIVE;
        int currentYear = onArchive ? (int) RenderContext.asLong(channel.get("year")) : 0;
        int currentMonth = onArchive ? (int) RenderContext.asLong(channel.get("month")) : 0;

        // §6.4 没有 includeChildren 参数，按 §6.3 查询参数的默认口径含子分类
        for (NavItem item : db().archives(typeCode, mode, row, category, true)) {
            Map<String, Object> values = new LinkedHashMap<>(item.values());
            int year = yearOf(values);
            int month = monthOf(values);
            // §6.4 把 label 的形态写死了（`2026 年 3 月`），因此这里按 year/month 现算，
            // 不看数据层给的是什么（数据层可能给 `2026-03` 这种 URL 用的形态）
            values.put("label", label(year, month));
            // §5.5：「年月 = 当前页面」。年模式下虽然数据项的 month 恒为 0，但当前页可能是
            // 月归档页（/archive/2026/03/），此时 mode='year' 的 2026 项并不是当前页面，
            // 不能因为 mode 是 year 就短路（否则误高亮）
            boolean current = currentYear > 0 && currentYear == year && currentMonth == month;
            CurrentMarks.renderEach(node, ctx, renderer, out, CurrentMarks.equal(values, current));
        }
    }

    /** §6.4 的 {@code label} 形态：{@code 2026 年 3 月}（年模式只有前半段）。 */
    static String label(int year, int month) {
        return month > 0 ? year + " 年 " + month + " 月" : year + " 年";
    }

    private static int yearOf(Map<String, Object> values) {
        return (int) RenderContext.asLong(values.get("year"));
    }

    private static int monthOf(Map<String, Object> values) {
        return (int) RenderContext.asLong(values.get("month"));
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
