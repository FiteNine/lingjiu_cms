package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.BodyKind;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * §6.7 的"标签 × 页面类型合法性矩阵" → E3012（**不是** §4.5 表格里的一行，是本类自己占的序号 17）。
 *
 * <p>矩阵的每一格由**标签作者**在 {@link TagHandler#maxCount(PageType)} 里声明（{@code -1} 不限、
 * {@code 0} 不允许、{@code n>0} 至多 n 个），本类只消费它——所以矩阵的取值只有一份来源，
 * 标签改了那一行，这里立刻跟着变，不需要两处修改。{@link PageType#matrixColumn()} 负责把
 * 矩阵之外的取值映射回它所属的列（{@code TAGLIST→LIST}、{@code DPAGE→DETAIL}）。
 *
 * <p>三条脚注也在这里（它们是矩阵的一部分，不是别的校验）：
 * <ul>
 *   <li><b>※1 {@code STATIC}</b>：只有**声明了 {@code query} 的列表页条目**能放 {@code {cms:list}}
 *       （恰 1 个）与 {@code {cms:pagelist}}。判定靠站点选项 {@code pages.static} 里 {@code template}
 *       与当前模板同名的条目；选项缺失或没有同名条目时**不判**（那是计划期的活，编译器不猜）；</li>
 *   <li><b>※2 {@code DETAIL} 上的 {@code {cms:pagelist}}</b>：只在列表分页时合法，正文分页 → E3003。
 *       与 §4.5 第 13 条是同一条规则，只在 {@link PaginationKindValidator} 里实现一次，这里不重复；</li>
 *   <li><b>※3 {@code feed}</b>：只允许一个顶层 {@code {cms:query}}。"顶层"按 §5.4 的口径取
 *       "不在循环体内"（预解析执行的就是"全部不在循环体内的查询标签"）。</li>
 * </ul>
 */
@Component
public final class TagPageTypeMatrixValidator implements TemplateValidator {

    /** 站点声明的静态页列表的选项键（§2.7）。 */
    private static final String STATIC_PAGES_OPTION = "pages.static";

    private final TagRegistry registry;

    public TagPageTypeMatrixValidator(TagRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int order() {
        return 17;
    }

    @Override
    public String describe() {
        return "§6.7 矩阵：标签 × 页面类型的合法性（E3012）";
    }

    /** 一个标签节点 + 它所在的位置。 */
    private record Hit(TagNode node, ScopeWalker.Scope scope) {
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        PageType column = ctx.pageType().matrixColumn();
        Map<String, List<Hit>> byName = new LinkedHashMap<>();
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                if (registry.handler(node.name()) == null) {
                    return; // 未知标签由第 1 条报 E1001
                }
                byName.computeIfAbsent(node.name(), key -> new ArrayList<>()).add(new Hit(node, scope));
            }
        });

        if (column == PageType.FEED) {
            checkFeed(byName);
        }
        if (column == PageType.STATIC) {
            checkStaticListPage(ast, ctx, byName);
        }
        for (Map.Entry<String, List<Hit>> entry : byName.entrySet()) {
            TagHandler handler = registry.handler(entry.getKey());
            int max = handler.maxCount(column);
            List<Hit> hits = entry.getValue();
            if (max == 0) {
                throw notAllowed(handler, column, hits.get(0).node);
            }
            // 数量上限只对"不是页面主体"的标签生效：{cms:list} / {cms:detail} 的数量口径是
            // §4.5 第 8 条（E3001），在这里再报一次 E3012 会让同一件事有两个码（§10.2 的 E3011
            // 就是为这个理由废弃的）。这里只判 ✗ 的格子（上面那条）与三条脚注。
            if (max > 0 && handler.bodyKind() == BodyKind.NONE && hits.size() > max) {
                throw tooMany(handler, column, hits, max);
            }
        }
    }

    /* ---------------- ※1 STATIC 的列表页 ---------------- */

    private void checkStaticListPage(TemplateAst ast, CompileContext ctx, Map<String, List<Hit>> byName) {
        boolean declaresQuery = staticPageDeclaresQuery(ast.path(), ctx);
        Hit list = first(byName, "list");
        Hit pagelist = first(byName, "pagelist");
        if (declaresQuery) {
            if (list == null) {
                throw PublishException.error(PublishErrorCode.E3012,
                        "站点声明的列表页必须恰有 1 个 {cms:list}", ast.path(), 0,
                        "站点选项 " + STATIC_PAGES_OPTION + " 里 " + ast.path() + " 的条目声明了 query，"
                                + "但模板里没有 {cms:list}",
                        "补一个 {cms:list}（它是这一页的分页主体，§7.2.1、§6.7 的 ※1）");
            }
            return;
        }
        if (list != null) {
            throw staticNotListPage(list.node, "{cms:list}");
        }
        if (pagelist != null) {
            throw staticNotListPage(pagelist.node, "{cms:pagelist}");
        }
    }

    private static PublishException staticNotListPage(TagNode node, String tag) {
        return PublishException.error(PublishErrorCode.E3012,
                tag + " 只能用站点声明的列表页上", node.sourcePath(), node.lineNo(),
                "第 " + node.lineNo() + " 行的 " + tag + "；这一页在 " + STATIC_PAGES_OPTION
                        + " 里没有声明 query",
                "只有声明了 query 的静态页条目才是列表页，才能放 " + tag + "（§6.7 的 ※1、§7.2.1）");
    }

    /**
     * 站点选项 {@code pages.static} 里与当前模板同名的条目是否声明了 {@code query}。
     *
     * <p><b>判不了时按"非列表页"处理</b>（返回 {@code false}）：§6.7 的 ※1 说"其他静态页
     * （感谢页、落地页）放它们 → E3012"，而"是不是列表页"的唯一凭据就是那个选项条目。
     * 选项没配、或没有同名条目时，默认按更严的一侧判——否则未声明的静态页里放
     * {@code {cms:list}} 会静默通过，与"报错优于静默"相反。
     */
    private static boolean staticPageDeclaresQuery(String templatePath, CompileContext ctx) {
        if (ctx.provider() == null || ctx.provider().site() == null || templatePath == null) {
            return false;
        }
        Object option = ctx.provider().site().options().get(STATIC_PAGES_OPTION);
        if (!(option instanceof List<?> entries)) {
            return false;
        }
        for (Object item : entries) {
            if (!(item instanceof Map<?, ?> entry)) {
                continue;
            }
            Object template = entry.get("template");
            if (template == null || !templatePath.equals(String.valueOf(template))) {
                continue;
            }
            Object query = entry.get("query");
            if (query instanceof Map<?, ?> map && !map.isEmpty()) {
                return true;
            }
            if (query instanceof String text && !text.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /* ---------------- ※3 feed ---------------- */

    private void checkFeed(Map<String, List<Hit>> byName) {
        List<Hit> queries = byName.get("query");
        if (queries == null || queries.isEmpty()) {
            return;
        }
        // 规则管的是**顶层**查询（§6.7 的 ※3、§7.3 第（4）条）：写在 foreach / list 体内的
        // query 不算数，否则"1 个顶层 + 1 个循环体内的"这种合法模板会被误报
        List<Hit> topLevel = queries.stream().filter(hit -> !hit.scope.inLoop()).toList();
        if (topLevel.size() > 1) {
            TagNode second = topLevel.get(1).node;
            throw PublishException.error(PublishErrorCode.E3012,
                    "feed 只允许一个顶层 {cms:query}", second.sourcePath(), second.lineNo(),
                    "共 " + topLevel.size() + " 个顶层 {cms:query}（第 " + topLevel.get(0).node.lineNo()
                            + " 行、第 " + second.lineNo() + " 行）",
                    "feed 的迭代项就是那一个查询的结果（§7.3 第（4）条、§6.7 的 ※3）");
        }
        if (topLevel.isEmpty()) {
            // 一个顶层查询都没有：feed 的迭代项无从产生，不能因为"有 query 但都在循环体内"就放行
            Hit hit = queries.get(0);
            throw PublishException.error(PublishErrorCode.E3012,
                    "feed 的 {cms:query} 必须是顶层的", hit.node.sourcePath(), hit.node.lineNo(),
                    "第 " + hit.node.lineNo() + " 行的 {cms:query} 在循环体内",
                    "把查询移到循环之外（§6.7 的 ※3）");
        }
    }

    /* ---------------- 矩阵单元格 ---------------- */

    private PublishException notAllowed(TagHandler handler, PageType column, TagNode node) {
        List<PageType> allowed = allowedOn(handler);
        String what = allowed.size() == 1
                ? "{cms:" + handler.name() + "} 只能用在 " + allowed.get(0) + " 上"
                : "{cms:" + handler.name() + "} 不能用在 " + column + " 上";
        return PublishException.error(PublishErrorCode.E3012, what, node.sourcePath(), node.lineNo(),
                "当前页面类型是 " + column + "；" + (allowed.isEmpty()
                        ? "{cms:" + handler.name() + "} 在任何页面类型上都不允许"
                        : "允许的页面类型：" + join(allowed)),
                "标签与页面类型的矩阵见 §6.7");
    }

    private PublishException tooMany(TagHandler handler, PageType column, List<Hit> hits, int max) {
        Hit first = hits.get(0);
        Hit second = hits.get(1);
        return PublishException.error(PublishErrorCode.E3012,
                "{cms:" + handler.name() + "} 在 " + column + " 页上至多 " + max + " 个",
                second.node.sourcePath(), second.node.lineNo(),
                "共 " + hits.size() + " 个（第 " + first.node.lineNo() + " 行、第 " + second.node.lineNo()
                        + " 行…）",
                "标签与页面类型的矩阵见 §6.7");
    }

    /** 该标签在哪些页面类型上允许出现（{@code maxCount != 0}）；报错文案用，按矩阵的列去重。 */
    private static List<PageType> allowedOn(TagHandler handler) {
        List<PageType> allowed = new ArrayList<>();
        for (PageType pageType : PageType.values()) {
            PageType column = pageType.matrixColumn();
            if (handler.maxCount(column) != 0 && !allowed.contains(column)) {
                allowed.add(column);
            }
        }
        return allowed;
    }

    private static String join(List<PageType> pageTypes) {
        List<String> names = new ArrayList<>();
        for (PageType pageType : new TreeSet<>(pageTypes)) {
            names.add(pageType.name());
        }
        return String.join(" ", names);
    }

    private static Hit first(Map<String, List<Hit>> byName, String tag) {
        List<Hit> hits = byName.get(tag);
        return hits == null || hits.isEmpty() ? null : hits.get(0);
    }
}
