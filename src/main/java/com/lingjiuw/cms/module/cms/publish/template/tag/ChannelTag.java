package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code {cms:channel}} —— 导航树（static-publish.md §6.4）。
 *
 * <p>五个来源各解决一件事（§6.4 的表）：{@code category}（分类树）/ {@code content}（层级内容树）/
 * {@code type}（内容类型 = 导航项）/ {@code menu}（菜单是数据，能混排单页与外链）/ {@code facet}
 * （筛选取值条）。
 *
 * <p>{@code current} / {@code class} 严格按 §6.3 的口径表，全部经 {@link CurrentMarks} 算；
 * {@code source='menu'} 里 {@code kind='url'} 的项按"目标 URL = 当前页 URL"判定。
 *
 * <p>{@code count} 的口径由 {@code countScope} 决定（{@code node} / {@code tree}，默认 {@code tree}），
 * 计数本身由 {@link ContentProvider} 按 §6.3 的统一判定函数给出（标签不自己数）。
 */
@Component
@RequiredArgsConstructor
public class ChannelTag implements TagHandler {

    private static final List<String> SOURCES = List.of("category", "content", "type", "menu", "facet");

    @Nullable
    private final ContentProvider provider;

    @Override
    public String name() {
        return "channel";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of(
                ParamSpec.enumOf("source", "category", "导航来源：分类树 / 内容树 / 类型 / 菜单 / 筛选取值",
                        SOURCES.toArray(String[]::new)),
                ParamSpec.of("type", "source='content'/'facet' 时的类型 code"),
                ParamSpec.of("field", "source='facet' 时的 facet 字段 code"),
                ParamSpec.of("code", "main", "source='menu' 时的菜单 code（§2.4）"),
                ParamSpec.of("channel", "起始节点（分类 id/slug，或内容 id/slug）"),
                ParamSpec.of("parent", "起始节点（与 channel 同义，§6.4 的两种写法）"),
                ParamSpec.intOf("depth", 1, "向下展开层数；>1 时每项带 children"),
                ParamSpec.enumOf("countScope", "tree", "count 是否含子分类", "node", "tree"),
                ParamSpec.intOf("row", 0, "顶层条数上限（0 = 全部；source='type' 时取前 N 个）"));
    }

    /**
     * §6.7 的矩阵把 {@code {cms:channel}} 在 {@code HOME} / {@code SEARCH} / {@code STATIC} / {@code 404}
     * 上标成 ✗，与 §6.4 自己的旗舰用例冲突：官网主导航与页脚要放在所有页面共用的 header/footer
     * 片段里（§3.7 裁定六），照 ✗ 字面实现首页就编译不过。已裁定：**除 {@code feed} 外一律合法**，
     * 没有浏览位置时 {@code current=false}、{@code class=""}（不高亮，不报错）。
     */
    @Override
    public int maxCount(PageType pageType) {
        return pageType.matrixColumn() == PageType.FEED ? 0 : -1;
    }

    /**
     * 标签体内迭代项的字段：§6.3 的"统一产出字段契约" + §6.4 的 {@code channel} 产出清单。
     *
     * <p>五个来源的产出并成一份——{@code bodyKeys(PageType)} 拿不到模板里写的 {@code source}
     * （{@code type} 来源多 {@code typeCode}、{@code facet} 来源多 {@code facetPath} / {@code urlWith}），
     * 取并集是保守的：宁可漏报一个笔误，也不能把合法模板误报成 E1004。
     */
    @Override
    public Set<String> bodyKeys(PageType pageType) {
        return Set.of("id", "name", "label", "slug", "url", "current", "class", "children", "count",
                "target", "rel", "typeCode", "facetPath", "urlWith", "parentId");
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        CurrentMarks.requireAllowed(this, node, ctx);

        String source = CurrentMarks.enumOf(node, "source", "category", SOURCES.toArray(String[]::new));
        int depth = Math.max(1, CurrentMarks.intOf(node, "depth", 1));
        int row = Math.max(0, CurrentMarks.intOf(node, "row", 0));
        Here here = new Here(ctx);

        List<NavItem> items = switch (source) {
            case "content" -> contentTree(node, ctx, here, depth);
            case "type" -> typeNodes(here);
            case "menu" -> menu(node, ctx, here, depth);
            case "facet" -> facetValues(node, ctx, here);
            default -> categories(node, ctx, here, depth);
        };
        if (row > 0 && items.size() > row) {
            items = items.subList(0, row);
        }
        for (NavItem item : items) {
            CurrentMarks.renderEach(node, ctx, renderer, out, item.values());
        }
    }

    /* ---------------- source='category' ---------------- */

    private List<NavItem> categories(TagNode node, RenderContext ctx, Here here, int depth) {
        String start = startNode(node);
        Long parentId = null;
        if (start != null) {
            NavItem root = db().category(start);
            if (root == null) {
                throw missingCategory(node, start);
            }
            parentId = root.id();
        }
        ContentProvider.CountScope scope =
                "node".equals(CurrentMarks.enumOf(node, "countScope", "tree", "node", "tree"))
                        ? ContentProvider.CountScope.node : ContentProvider.CountScope.tree;
        List<NavItem> items = new ArrayList<>();
        for (NavItem item : level(parentId, depth, scope)) {
            items.add(categoryNode(item, here, depth));
        }
        return items;
    }

    /**
     * 取一层分类树。{@code ContentProvider.categories} 的注释写"parentId 为 null 表示从顶级起"，
     * 但把顶级写成 {@code 0}（父 id 为 0）也是同一件事的另一种编码，而接口给的是包装类型
     * {@code Long}——两种编码都在用。因此：**先按注释传 null，空结果再按 0 试一次**；
     * 只有"顶级"这一层会走两次，指定了起始节点时不走。
     */
    private List<NavItem> level(Long parentId, int depth, ContentProvider.CountScope scope) {
        List<NavItem> items = db().categories(parentId, depth, scope);
        if (parentId == null && items.isEmpty()) {
            items = db().categories(0L, depth, scope);
        }
        return items;
    }

    private NavItem categoryNode(NavItem item, Here here, int depth) {
        long id = item.id();
        NavItem marked = CurrentMarks.selfOrAncestor(item, id > 0 && id == here.categoryId,
                id > 0 && here.categoryAncestors.contains(id));
        List<NavItem> children = item.children();
        if (children.isEmpty()) {
            // depth>1 时**恒写 children（空则空列表）**：§6.4 的官方示例就是
            // {cms:if field='children'} / {cms:foreach field='children'}，而字段解析
            // "找不到就报错"（§5.1）——叶子节点上缺这个 key 会让每一页的导航都渲染失败。
            // 数据层给不给空列表是它的实现细节，标签的产出契约由标签自己保证。
            return depth > 1 ? marked.with("children", List.of()) : withoutChildren(marked);
        }
        List<NavItem> nested = new ArrayList<>(children.size());
        for (NavItem child : children) {
            nested.add(categoryNode(child, here, depth));
        }
        return depth > 1 ? marked.with("children", nested) : withoutChildren(marked);
    }

    /* ---------------- source='content' ---------------- */

    private List<NavItem> contentTree(TagNode node, RenderContext ctx, Here here, int depth) {
        String typeCode = CurrentMarks.str(node, "type", ctx.typeCode());
        if (typeCode == null) {
            throw CurrentMarks.missingParam(this, node, "type");
        }
        long startId = 0L;
        String start = startNode(node);
        if (start != null) {
            ContentItem root = content(typeCode, start);
            if (root == null) {
                throw PublishException.error(PublishErrorCode.E2006, "内容 " + start + " 不存在",
                        node.sourcePath(), node.lineNo(),
                        "{cms:channel source='content'} 的 channel/parent = '" + start + "'（类型 " + typeCode + "）",
                        "取内容的 id 或 slug");
            }
            startId = root.id();
        }
        List<NavItem> items = new ArrayList<>();
        List<ContentItem> flat = db().children(startId, typeCode, depth);
        // 数据层给的是"到 depth 层为止的全部后代"（扁平），depth>1 时这里按 parentId 组成树，
        // 这样"每项带 children"才是标签自己的契约（§6.4），不依赖数据层返回什么形状
        Map<Long, List<ContentItem>> byParent = new LinkedHashMap<>();
        for (ContentItem child : flat) {
            byParent.computeIfAbsent(child.parentId(), key -> new ArrayList<>()).add(child);
        }
        for (ContentItem child : flat) {
            if (child.parentId() == startId) {
                items.add(contentNode(child, here, depth, byParent));
            }
        }
        return items;
    }

    private NavItem contentNode(ContentItem item, Here here, int depth, Map<Long, List<ContentItem>> byParent) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", item.id());
        values.put("name", item.title());
        values.put("label", item.title());
        values.put("slug", item.slug());
        values.put("url", item.url());
        values.put("typeCode", item.typeCode());
        values.put("parentId", item.parentId());
        values.put("count", item.has("childCount") ? item.get("childCount") : 0);
        if (depth > 1) {
            List<ContentItem> children = contentItems(item.get("children"));
            if (children.isEmpty()) {
                children = byParent.getOrDefault(item.id(), List.of());
            }
            List<NavItem> nested = new ArrayList<>(children.size());
            for (ContentItem child : children) {
                nested.add(contentNode(child, here, depth - 1, byParent));
            }
            // 与 categoryNode / menuNode 对齐：depth>1 时**恒写** children（空则空列表）。
            // bodyKeys 声明了 children，缺 key 会让层级内容树的叶子在渲染期报字段解析错误
            values.put("children", nested);
        }
        return NavItem.of(CurrentMarks.selfOrAncestor(values, item.id() > 0 && item.id() == here.entryId,
                item.id() > 0 && here.contentAncestors.contains(item.id())));
    }

    private ContentItem content(String typeCode, String idOrSlug) {
        long id = RenderContext.asLong(idOrSlug);
        return id > 0 ? db().contentById(id) : db().contentBySlug(typeCode, idOrSlug);
    }

    /** 层级内容项里的 {@code children}：内容树的下一层（§6.3 的 {@code depth} 语义）。 */
    private static List<ContentItem> contentItems(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<ContentItem> items = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof ContentItem item) {
                items.add(item);
            } else if (element instanceof Map<?, ?> map) {
                Map<String, Object> values = new LinkedHashMap<>();
                map.forEach((key, value) -> values.put(String.valueOf(key), value));
                items.add(ContentItem.of(values));
            }
        }
        return items;
    }

    /* ---------------- source='type' ---------------- */

    private List<NavItem> typeNodes(Here here) {
        List<NavItem> items = new ArrayList<>();
        for (NavItem item : db().typeNodes()) {
            String code = RenderContext.asString(item.get("typeCode"));
            if (code == null) {
                code = RenderContext.asString(item.get("slug"));
            }
            items.add(CurrentMarks.equal(item,
                    here.typeCode != null && here.typeCode.equals(code)));
        }
        // §6.4：source='type' 时 row 是"按类型的 sort 排序取前 N 个"。数据层给了 sort 就按它排，
        // 没给（只有它自己知道的顺序）就保持原样——标签不发明排序依据。
        if (!items.isEmpty() && items.stream().allMatch(item -> item.has("sort"))) {
            items.sort(Comparator.comparingLong(item -> RenderContext.asLong(item.get("sort"))));
        }
        return items;
    }

    /* ---------------- source='menu' ---------------- */

    private List<NavItem> menu(TagNode node, RenderContext ctx, Here here, int depth) {
        String code = CurrentMarks.str(node, "code", "main");
        List<NavItem> items = db().menu(code);
        if (items.isEmpty() && !db().menuCodes().contains(code)) {
            throw PublishException.error(PublishErrorCode.E2006, "菜单 " + code + " 不存在",
                    node.sourcePath(), node.lineNo(),
                    "{cms:channel source='menu'} 的 code = '" + code + "'；本站菜单有 "
                            + (db().menuCodes().isEmpty() ? "（一个都没有）"
                            : String.join("、", db().menuCodes())),
                    "菜单 code 见后台「内容 → 导航菜单」（§2.4）");
        }
        List<NavItem> marked = new ArrayList<>(items.size());
        for (NavItem item : items) {
            marked.add(menuNode(item, here, depth));
        }
        return marked;
    }

    /**
     * §6.3 第 3 行：菜单项指向的对象 = 当前页面对象（含祖先）；{@code kind='url'} 按"目标 URL = 当前页
     * URL"判定。{@code kind} 与 {@code refId} / {@code refCode} / {@code url} 就是 §2.4 的列名。
     */
    private NavItem menuNode(NavItem item, Here here, int depth) {
        String kind = RenderContext.asString(item.get("kind"));
        long refId = RenderContext.asLong(item.get("refId"));
        String refCode = RenderContext.asString(item.get("refCode"));
        String target = RenderContext.asString(item.get("url"));

        boolean self = false;
        boolean trail = false;
        switch (kind == null ? "custom" : kind) {
            case "category" -> {
                self = refId > 0 && refId == here.categoryId;
                trail = !self && refId > 0 && here.categoryAncestors.contains(refId);
            }
            case "content" -> {
                self = refId > 0 && refId == here.entryId;
                trail = !self && refId > 0 && here.contentAncestors.contains(refId);
            }
            case "url" -> self = here.pageUrl != null && !here.pageUrl.isBlank()
                    && here.pageUrl.equals(target);
            case "type" -> self = here.typeCode != null && here.typeCode.equals(refCode);
            case "tag" -> self = here.tagSlug != null && here.tagSlug.equals(refCode);
            case "archive" -> self = archiveMatches(here, refId, refCode);
            // custom（纯占位父项）/ author（静态站没有作者页）→ 没有"当前项"可言
            default -> self = false;
        }

        NavItem marked = CurrentMarks.selfOrAncestor(item, self, trail);
        List<NavItem> children = item.children();
        if (children.isEmpty()) {
            // depth>1 时**恒写 children（空则空列表）**：§6.4 的官方示例就是
            // {cms:if field='children'} / {cms:foreach field='children'}，而字段解析
            // "找不到就报错"（§5.1）——叶子节点上缺这个 key 会让每一页的导航都渲染失败。
            // 数据层给不给空列表是它的实现细节，标签的产出契约由标签自己保证。
            return depth > 1 ? marked.with("children", List.of()) : withoutChildren(marked);
        }
        List<NavItem> nested = new ArrayList<>(children.size());
        for (NavItem child : children) {
            nested.add(menuNode(child, here, depth));
        }
        return depth > 1 ? marked.with("children", nested) : withoutChildren(marked);
    }

    /** 归档菜单项的 ref 取 {@code 2026-03}（月）或 {@code 2026}（年）两种写法。 */
    private static boolean archiveMatches(Here here, long refId, String refCode) {
        if (here.year <= 0) {
            return false;
        }
        String month = here.year + "-" + (here.month < 10 ? "0" + here.month : String.valueOf(here.month));
        String ref = refCode != null && !refCode.isBlank() ? refCode : String.valueOf(refId);
        return ref.equals(month) || ref.equals(String.valueOf(here.year));
    }

    /* ---------------- source='facet' ---------------- */

    private List<NavItem> facetValues(TagNode node, RenderContext ctx, Here here) {
        String typeCode = CurrentMarks.str(node, "type", ctx.typeCode());
        if (typeCode == null) {
            throw CurrentMarks.missingParam(this, node, "type");
        }
        String field = CurrentMarks.str(node, "field", null);
        if (field == null) {
            throw CurrentMarks.missingParam(this, node, "field");
        }
        List<String> combos = combos(db().site().options().get("facets.combos"));
        String pattern = db().site().option("url.facet", "/f/{facetPath}/");
        List<String> currentSegments = segments(here.facetPath);

        List<NavItem> items = new ArrayList<>();
        for (NavItem value : db().facetValues(typeCode, field)) {
            String path = RenderContext.asString(value.get("facetPath"));
            if (path == null || path.isBlank()) {
                path = field + "-" + RenderContext.asString(value.get("slug"));
            }
            NavItem marked = CurrentMarks.equal(value, currentSegments.contains(path));
            items.add(marked.with("urlWith", urlWith(path, field, currentSegments, combos, pattern)));
        }
        return items;
    }

    /**
     * §6.3 的 {@code urlWith}："当前页面的筛选组合 + 该取值"的 URL；**只有该组合在
     * {@code facets.combos} 里声明过时才输出，否则空串**（与 §7.4"不做全排列"的死限一致）。
     */
    private static String urlWith(String path, String field, List<String> currentSegments,
                                  List<String> combos, String pattern) {
        List<String> segments = new ArrayList<>(currentSegments);
        String prefix = field + "-";
        int at = -1;
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).startsWith(prefix)) {
                at = i;
            }
        }
        if (at >= 0) {
            segments.set(at, path);
        } else {
            segments.add(path);
        }
        String combo = String.join("+", segments);
        return combos.contains(combo) ? pattern.replace("{facetPath}", combo) : "";
    }

    private static List<String> segments(String facetPath) {
        return facetPath == null || facetPath.isBlank() ? List.of() : List.of(facetPath.split("\\+"));
    }

    /** 站点选项 {@code facets.combos}：契约写的是数组，配置表里也可能是逗号串，两种都收。 */
    private static List<String> combos(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> combos = new ArrayList<>();
            for (Object element : list) {
                combos.add(String.valueOf(element).trim());
            }
            return combos;
        }
        if (raw instanceof CharSequence text && !text.toString().isBlank()) {
            List<String> combos = new ArrayList<>();
            for (String element : text.toString().split(",")) {
                if (!element.isBlank()) {
                    combos.add(element.trim());
                }
            }
            return combos;
        }
        return List.of();
    }

    /* ---------------- 共用 ---------------- */

    private static String startNode(TagNode node) {
        return CurrentMarks.str(node, "channel", CurrentMarks.str(node, "parent", null));
    }

    private static NavItem withoutChildren(NavItem item) {
        Map<String, Object> values = new LinkedHashMap<>(item.values());
        values.remove("children");
        return NavItem.of(values);
    }

    private static PublishException missingCategory(TagNode node, String start) {
        return PublishException.error(PublishErrorCode.E2006, "分类 " + start + " 不存在",
                node.sourcePath(), node.lineNo(),
                "{cms:channel} 的 channel/parent = '" + start + "'",
                "取分类的 id 或 slug（后台「内容 → 分类」）");
    }

    /**
     * 一次渲染里"当前页是谁"的坐标。菜单项 / 分类项 / 内容项的 {@code current} 判定都读它，
     * 因此每个渲染只查一次祖先链，而不是逐项查。
     */
    private final class Here {

        private final long categoryId;
        private final Set<Long> categoryAncestors;
        private final Set<Long> contentAncestors;
        private final long entryId;
        private final String typeCode;
        private final String pageUrl;
        private final String tagSlug;
        private final String facetPath;
        private final int year;
        private final int month;

        private Here(RenderContext ctx) {
            this.categoryId = CurrentMarks.currentCategoryId(ctx);
            this.categoryAncestors = CurrentMarks.currentCategoryAncestors(ctx, db());
            this.entryId = ctx.currentEntryId();
            this.contentAncestors = entryId > 0 ? ancestorIds(entryId) : Set.of();
            this.typeCode = ctx.typeCode();
            this.pageUrl = RenderContext.asString(ctx.pageVar("url"));
            Map<String, Object> channel = ctx.namedValues("channel");
            this.tagSlug = ctx.pageType() == PageType.TAGPAGE
                    ? RenderContext.asString(channel.get("slug")) : null;
            this.facetPath = ctx.pageType() == PageType.FACET
                    ? RenderContext.asString(channel.get("facetPath")) : null;
            this.year = ctx.pageType() == PageType.ARCHIVE
                    ? (int) RenderContext.asLong(channel.get("year")) : 0;
            this.month = ctx.pageType() == PageType.ARCHIVE
                    ? (int) RenderContext.asLong(channel.get("month")) : 0;
        }

        private Set<Long> ancestorIds(long contentId) {
            Set<Long> ids = new LinkedHashSet<>();
            for (ContentItem ancestor : db().contentAncestors(contentId)) {
                ids.add(ancestor.id());
            }
            return ids;
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
