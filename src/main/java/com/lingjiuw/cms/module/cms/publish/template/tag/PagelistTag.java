package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.PageUrlBuilder;
import com.lingjiuw.cms.module.cms.publish.template.PaginationKind;
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
 * {@code {cms:pagelist}} —— 分页条（static-publish.md §6.4）。
 *
 * <p><b>迭代渲染，不是直接输出 HTML</b>：标签自己产出的是 {@code type} / {@code label} / {@code url} /
 * {@code current} / {@code class} / {@code rel} 六个字段，HTML 由标签体写（§6.4 的例子）。
 *
 * <p>三条口径写死在代码里：
 * <ul>
 *   <li>{@code label} 的来源：{@code first} / {@code prev} / {@code next} / {@code last} 取站点选项
 *       {@code pager.labels}（默认 {@code 首页,上一页,下一页,末页}）；{@code pageno} / {@code current}
 *       是页码数字本身；</li>
 *   <li><b>当前页只发一次</b>：{@code items} 同时含 {@code pageno} 与 {@code current} 时，页号窗口里
 *       跳过当前页，由 {@code current} 那一项承担；</li>
 *   <li>URL 一律走 {@link RenderContext#pageUrls()}（第 1 页是不含 {@code {n}} 的形态，§5.6）；
 *       没注入时留空串。</li>
 * </ul>
 *
 * <p>用在 {@code page.paginationKind='content'}（正文分页）上 → E3003（§5.6、§6.7 的 ※2）。
 */
@Component
@RequiredArgsConstructor
public class PagelistTag implements TagHandler {

    private static final String DEFAULT_ITEMS = "first,prev,pageno,next,last";
    private static final List<String> ITEM_TYPES =
            List.of("first", "prev", "pageno", "next", "last", "current");
    private static final String DEFAULT_LABELS = "首页,上一页,下一页,末页";

    @Nullable
    private final ContentProvider provider;

    @Override
    public String name() {
        return "pagelist";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of(
                ParamSpec.intOf("listsize", 5, "数字页码窗口大小"),
                ParamSpec.of("items", DEFAULT_ITEMS, "要输出的分页项类型"));
    }

    @Override
    public int maxCount(PageType pageType) {
        // §7.2.1：TAGLIST（标签总览页）没有分页主体，因此 {cms:pagelist} 不合法。
        // 必须在下面 matrixColumn() 的 switch 之外判：matrixColumn() 只做 DPAGE→DETAIL，
        // TAGLIST 不映射，落到 switch 的 default 会得到 -1（= 不限，合法），与 §7.2.1 要求的
        // 0（不允许）不符；而写在 switch 里的 `case TAGLIST` 永远不会命中
        if (pageType == PageType.TAGLIST) {
            return 0;
        }
        return switch (pageType.matrixColumn()) {
            // ※2：DETAIL 上只在列表分页时合法，正文分页由 render 报 E3003
            case SINGLE, SEARCH, PAGE404, FEED -> 0;
            // ※1：STATIC 只有"声明了 query 的列表页条目"能放，且恰 1 个。
            // maxCount(PageType) 拿不到"这一页是不是声明的列表页"，因此这里只能给上限 1，
            // 剩下的一半条件留给校验器（见报告）。
            case STATIC -> 1;
            default -> -1;
        };
    }

    /** 标签体内迭代项的字段（§6.4 的"迭代产出"）：报 E1004 的字段名校验靠它。 */
    @Override
    public Set<String> bodyKeys(PageType pageType) {
        return Set.of("type", "label", "url", "current", "class", "rel");
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        CurrentMarks.requireAllowed(this, node, ctx);

        if (PaginationKind.CONTENT.code().equals(RenderContext.asString(ctx.pageVar("paginationKind")))) {
            throw PublishException.error(PublishErrorCode.E3003, "正文分页不提供页号条",
                    node.sourcePath(), node.lineNo(),
                    "page.paginationKind = 'content'（本页由 <!--cms:page--> 切正文）",
                    "用 page.prevUrl / page.nextUrl");
        }

        int pageNo = (int) RenderContext.asLong(ctx.pageVar("pageNo"));
        int totalPages = (int) RenderContext.asLong(ctx.pageVar("totalPages"));
        if (pageNo <= 0 || totalPages <= 1) {
            // 没有分页主体（pageNo 缺省）或只有一页：页号条没有可点的目标，不渲染
            return;
        }

        Set<String> items = CurrentMarks.enumList(node, "items", DEFAULT_ITEMS, ITEM_TYPES);
        boolean withPageno = items.contains("pageno");
        boolean withCurrent = items.contains("current");
        int listsize = Math.max(1, CurrentMarks.intOf(node, "listsize", 5));
        PageUrlBuilder urls = ctx.pageUrls();
        String[] labels = pagerLabels();

        if (items.contains("first") && pageNo > 1) {
            emit(node, ctx, renderer, out, "first", labels[0], url(urls, 1), false, "");
        }
        if (items.contains("prev") && pageNo > 1) {
            emit(node, ctx, renderer, out, "prev", labels[1], url(urls, pageNo - 1), false, "prev");
        }
        if (withPageno || withCurrent) {
            int[] window = window(pageNo, totalPages, listsize);
            for (int page = window[0]; page <= window[1]; page++) {
                if (page != pageNo) {
                    if (withPageno) {
                        emit(node, ctx, renderer, out, "pageno", String.valueOf(page),
                                url(urls, page), false, "");
                    }
                } else if (withCurrent) {
                    emit(node, ctx, renderer, out, "current", String.valueOf(page),
                            url(urls, page), true, "");
                } else {
                    emit(node, ctx, renderer, out, "pageno", String.valueOf(page),
                            url(urls, page), true, "");
                }
            }
        }
        if (items.contains("next") && pageNo < totalPages) {
            emit(node, ctx, renderer, out, "next", labels[2], url(urls, pageNo + 1), false, "next");
        }
        if (items.contains("last") && pageNo < totalPages) {
            emit(node, ctx, renderer, out, "last", labels[3], url(urls, totalPages), false, "");
        }
    }

    private static void emit(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out,
                             String type, String label, String url, boolean current, String rel) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("label", label);
        item.put("url", url);
        item.put("rel", rel);
        CurrentMarks.renderEach(node, ctx, renderer, out, CurrentMarks.equal(item, current));
    }

    /** 页号窗口：以当前页居中、向两侧取 {@code listsize} 个，两端夹在 {@code [1, totalPages]} 里。 */
    static int[] window(int pageNo, int totalPages, int listsize) {
        int start = Math.max(1, pageNo - listsize / 2);
        int end = Math.min(totalPages, start + listsize - 1);
        start = Math.max(1, end - listsize + 1);
        return new int[]{start, end};
    }

    private static String url(PageUrlBuilder urls, int pageNo) {
        if (urls == null) {
            return "";
        }
        return pageNo <= 1 ? urls.firstPageUrl() : urls.pageUrl(pageNo);
    }

    /** 站点选项 {@code pager.labels}（§2.7）；缺项或空项退回默认文案。 */
    private String[] pagerLabels() {
        String[] labels = DEFAULT_LABELS.split(",");
        String[] configured = db().site().option("pager.labels", DEFAULT_LABELS).split(",", -1);
        for (int i = 0; i < labels.length && i < configured.length; i++) {
            if (!configured[i].isBlank()) {
                labels[i] = configured[i].trim();
            }
        }
        return labels;
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
