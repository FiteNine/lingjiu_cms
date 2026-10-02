package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * §6.3 那张 {@code current} / {@code class} 取值口径表的**唯一实现**：{@code {cms:channel}} /
 * {@code {cms:breadcrumb}} / {@code {cms:tagnav}} / {@code {cms:archive}} / {@code {cms:pagelist}}
 * 全部经它算"这一项是不是当前项"。
 *
 * <p>契约里的两条口径落在这里：
 * <ul>
 *   <li><b>分类项 / 菜单项</b>：{@code current} 为真 = 该项是当前页面的分类（或它的祖先）；
 *       {@code class} = {@code active}（是自身）/ {@code active-trail}（是祖先）/ 空串；</li>
 *   <li><b>其余迭代项</b>（类型项 / facet 取值 / 页号 / 面包屑最后一级 / 标签 / 年月）：
 *       相等即 {@code current}，{@code class} = {@code current} / 空串。</li>
 * </ul>
 *
 * <p>§5.5 的结论是"把判断挪到引擎侧"，因此模板里永远不写比较，只写
 * {@code class="card [field:class/]"}——同一套取值在本类里只有一份实现。
 *
 * <p>本类另外放着 tag 包共用的三件小事：**参数读取**（§3.3）、**§6.7 矩阵的渲染期兜底**（E3012）、
 * 以及**一次迭代渲染**（压栈 → 渲染标签体 → 出栈，§5.1）。它们没有别的落点：标签实现只允许
 * 落在 {@code template/tag/} 下的这几个类里。
 */
public final class CurrentMarks {

    /** §6.3：分类项是自身。 */
    public static final String ACTIVE = "active";

    /** §6.3：分类项是当前分类的祖先。 */
    public static final String ACTIVE_TRAIL = "active-trail";

    /** §6.3：其余迭代项相等时的 class（也用于面包屑最后一级）。 */
    public static final String CURRENT = "current";

    /** §6.3：不相等、或页面没有浏览位置时的 class。 */
    public static final String NONE = "";

    private CurrentMarks() {
    }

    /* ---------------- §6.3 的两条口径 ---------------- */

    /**
     * 分类项 / 菜单项的口径（§6.3 第 2、3 行）：自身 → {@code active}，祖先 → {@code active-trail}，
     * 无关 → 空串。{@code current} 在"自身或祖先"时为真（表头写的是"= 当前页面的分类**或它的祖先**"，
     * 自身与祖先的区别由 {@code class} 承担）。
     */
    public static NavItem selfOrAncestor(NavItem item, boolean self, boolean ancestor) {
        return item.with("current", self || ancestor)
                .with("class", self ? ACTIVE : ancestor ? ACTIVE_TRAIL : NONE);
    }

    /** {@link #selfOrAncestor(NavItem, boolean, boolean)} 的裸 Map 形态（内容树节点）。 */
    public static Map<String, Object> selfOrAncestor(Map<String, Object> values, boolean self,
                                                     boolean ancestor) {
        return mark(values, self || ancestor, self ? ACTIVE : ancestor ? ACTIVE_TRAIL : NONE);
    }

    /** 单一相等的口径（§6.3 第 1、4、5、6、7 行）：相等 → {@code current}，否则空串。 */
    public static NavItem equal(NavItem item, boolean current) {
        return item.with("current", current).with("class", current ? CURRENT : NONE);
    }

    /** {@link #equal(NavItem, boolean)} 的裸 Map 形态（面包屑等级项、分页项）。 */
    public static Map<String, Object> equal(Map<String, Object> values, boolean current) {
        return mark(values, current, current ? CURRENT : NONE);
    }

    private static Map<String, Object> mark(Map<String, Object> values, boolean current, String className) {
        Map<String, Object> marked = new LinkedHashMap<>(values);
        marked.put("current", current);
        marked.put("class", className);
        return marked;
    }

    /* ---------------- 当前浏览位置（§5.1 第（4）条） ---------------- */

    /**
     * 当前页面的分类 id。只有"分类来源的列表页 / 详情页 / 详情分页"的 {@code channel} 是分类
     * （§5.1 第（4）条那张表）；类型来源的列表页、标签页、归档页、筛选页、单页都没有可比的分类，
     * 因此这里返回 {@code 0}——调用方据此把 {@code current} 一律算成假（**不高亮，不报错**）。
     */
    public static long currentCategoryId(RenderContext ctx) {
        PageType pageType = ctx.pageType();
        if (pageType != PageType.LIST && pageType != PageType.DETAIL && pageType != PageType.DPAGE) {
            return 0L;
        }
        Map<String, Object> channel = ctx.namedValues("channel");
        if (channel.isEmpty() || channel.containsKey("typeCode")) {
            // 类型来源的列表页：channel 是内容类型本身，不是分类
            return 0L;
        }
        return RenderContext.asLong(channel.get("id"));
    }

    /** 当前分类的祖先 id 集合（从顶到下），不含自身；没有当前分类时为空集。 */
    public static Set<Long> currentCategoryAncestors(RenderContext ctx, ContentProvider provider) {
        long categoryId = currentCategoryId(ctx);
        if (categoryId <= 0) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (NavItem ancestor : provider.categoryAncestors(categoryId)) {
            ids.add(ancestor.id());
        }
        return ids;
    }

    /* ---------------- 参数读取（§3.3：参数值永远是字面量） ---------------- */

    /** 字符串参数；模板里没写（或写空）时用默认值。 */
    public static String str(TagNode node, String key, String defaultValue) {
        String value = node.arg(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** 整数参数；不可解析 → E1002。 */
    public static int intOf(TagNode node, String key, int defaultValue) {
        String value = str(node, key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw badParam(node, key, value, "整数");
        }
    }

    /** 布尔参数（{@code 0} / {@code 1} / {@code true} / {@code false}，忽略大小写）；其余 → E1002。 */
    public static boolean boolOf(TagNode node, String key, boolean defaultValue) {
        String value = str(node, key, null);
        if (value == null) {
            return defaultValue;
        }
        if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("0".equals(value) || "false".equalsIgnoreCase(value)) {
            return false;
        }
        throw badParam(node, key, value, "0 / 1 / true / false");
    }

    /** 枚举参数；越界 → E1002，并把允许值原样列出来（§10.1 的"能列清单就列清单"）。 */
    public static String enumOf(TagNode node, String key, String defaultValue, String... allowed) {
        String value = str(node, key, defaultValue);
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return value;
            }
        }
        throw badParam(node, key, value, String.join(" / ", allowed));
    }

    /** 逗号分隔的枚举列表参数（{@code {cms:pagelist}} 的 {@code items}）：逐项校验、保持书写顺序。 */
    public static Set<String> enumList(TagNode node, String key, String defaultValue, List<String> allowed) {
        String raw = str(node, key, defaultValue);
        if (raw == null) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!allowed.contains(value)) {
                throw badParam(node, key, value, String.join(" / ", allowed));
            }
            values.add(value);
        }
        return values;
    }

    /** 必填参数缺失 → E1002，并把该标签的全部参数列出来（§10.2 的文案模板）。 */
    public static PublishException missingParam(TagHandler handler, TagNode node, String key) {
        List<String> params = new ArrayList<>();
        for (ParamSpec spec : handler.params()) {
            params.add(spec.key() + "（" + spec.typeText() + "）");
        }
        return PublishException.error(PublishErrorCode.E1002, "参数 " + key + " 不存在",
                node.sourcePath(), node.lineNo(),
                "{cms:" + handler.name() + "} 的参数有 " + String.join(" ", params),
                "补上参数 " + key + "（参数值一律是字面量，见 §3.3）");
    }

    private static PublishException badParam(TagNode node, String key, String actual, String expects) {
        return PublishException.error(PublishErrorCode.E1002, "参数 " + key + " 的取值不可用",
                node.sourcePath(), node.lineNo(),
                "参数 " + key + " = '" + actual + "'，期望 " + expects,
                "参数表见 §6.4；参数值一律是字面量，不做字段插值（§3.3）");
    }

    /* ---------------- §6.7 矩阵（渲染期兜底） ---------------- */

    /**
     * 标签用在该页面类型上不允许的位置 → E3012（{@code maxCount} 为 0）。
     *
     * <p>编译期由校验器按同一个 {@link TagHandler#maxCount(PageType)} 报同一条；这里兜底是因为
     * 渲染期也要能独立工作（预览、单测），而且"报错优于静默渲染空值"（§10.3）。
     */
    public static void requireAllowed(TagHandler handler, TagNode node, RenderContext ctx) {
        if (handler.maxCount(ctx.pageType()) != 0) {
            return;
        }
        throw PublishException.error(PublishErrorCode.E3012,
                "{cms:" + handler.name() + "} 不能用在 " + ctx.pageType() + " 页面上",
                node.sourcePath(), node.lineNo(),
                "当前页面类型是 " + ctx.pageType(),
                "标签与页面类型的矩阵见 §6.7；本标签可用的页面类型：" + allowedTypes(handler));
    }

    /** 该标签合法的页面类型清单（由 {@code maxCount} 现算，矩阵只有一处）。 */
    private static String allowedTypes(TagHandler handler) {
        List<String> names = new ArrayList<>();
        for (PageType pageType : PageType.values()) {
            // maxCount 的契约是 -1 = 不限个数（见 TagHandler），0 才是"不允许"；
            // 按 > 0 判会让"不限"的标签被列成"（一个都没有）"，与 requireAllowed 的口径也不一致
            if (handler.maxCount(pageType) != 0) {
                names.add(pageType.name());
            }
        }
        return names.isEmpty() ? "（一个都没有）" : String.join(" ", names);
    }

    /* ---------------- 一次迭代渲染 ---------------- */

    /**
     * 把迭代项压进匿名栈、渲染标签体、再出栈。循环项**永远匿名、永远在栈顶**（§5.1），
     * 因此模板里直接写 {@code [field:url/]} 取到的就是当前这一项。
     *
     * @param item 迭代项的值表（{@link NavItem#values()} 或裸 Map）
     */
    public static void renderEach(TagNode node, RenderContext ctx, TemplateRenderer renderer,
                                  StringBuilder out, Map<String, Object> item) {
        ctx.pushAnonymous(item);
        ctx.enterLoop();
        try {
            renderer.renderNodes(node.bodyNodes(), ctx, out);
        } finally {
            ctx.exitLoop();
            ctx.popAnonymous();
        }
    }
}
