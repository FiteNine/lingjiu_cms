package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.BuiltinFields;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.DefaultTemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.Resolution;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code {cms:foreach}}（static-publish.md §3.5 裁定五、§6.2）：迭代**已经在手上的**多值字段
 * 或引擎预加载清单，**不迭代查询结果**（查询标签自带标签体），**不参与分页**（没有 {@code page}）。
 *
 * <pre>
 * {cms:foreach field='images' row='12' orderby='sort'}
 *   &lt;img src="[field:url/]" alt="[field:alt/]"&gt;
 * {/cms:foreach}
 * </pre>
 *
 * <p>每次迭代压入一个**新的匿名作用域**（永远在栈顶，所以 {@code [field:url/]} 在循环里取的是
 * 迭代项，而不是页面条目——§5.1 的"就近"），并给出 {@code index} / {@code index0} /
 * {@code isFirst} / {@code isLast} / {@code count}。
 *
 * <p>{@code row} / {@code offset} / {@code orderby} / {@code order} 是**截断与排序，不产生分页**
 * ——与查询标签的 {@code row} 同名不同义（§6.2 原文加粗标明）。
 */
@Component
public class ForeachTag implements TagHandler {

    @Override
    public String name() {
        return "foreach";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of(
                ParamSpec.required("field",
                        "要迭代的多值字段路径（IMAGES / FILES / RELATION / TAGS / JSON / ENUM_MULTI，"
                                + "以及引擎预加载的 children / toc / categories / ancestors / tags / site.alternates）"),
                ParamSpec.intOf("row", 0, "最多迭代条数（只是截断，不产生分页；0 = 全部）"),
                ParamSpec.intOf("offset", 0, "跳过 N 条"),
                ParamSpec.of("orderby", "迭代项内的字段 code（如 sort；toc 按层级、tags 按名称）"),
                ParamSpec.enumOf("order", "asc", "排序方向", "asc", "desc"));
    }

    /** §6.7 的矩阵第一行：任何页面类型上个数不限。 */
    @Override
    public int maxCount(PageType pageType) {
        return -1;
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        Resolution resolution = DefaultTemplateRenderer.resolveOrFail(ctx,
                DefaultTemplateRenderer.splitPath(node.arg("field")), node.sourcePath(), node.lineNo());
        List<Object> items = itemsOf(resolution.value(), node);

        String orderby = node.arg("orderby");
        if (orderby != null && !orderby.isBlank()) {
            sort(items, orderby.trim(), "desc".equalsIgnoreCase(node.arg("order")));
        }
        int offset = intArg(node, "offset");
        if (offset > 0) {
            items = offset >= items.size()
                    ? new ArrayList<>() : new ArrayList<>(items.subList(offset, items.size()));
        }
        int row = intArg(node, "row");
        if (row > 0 && row < items.size()) {
            items = new ArrayList<>(items.subList(0, row));
        }

        // count 是"这次真正迭代了几项"（截断之后）：否则 isLast 在截断时永远不为真
        int count = items.size();
        ctx.enterLoop();
        try {
            for (int index = 0; index < count; index++) {
                Object item = items.get(index);
                ctx.pushAnonymous(frameOf(item, index, count), frameType(item));
                try {
                    renderer.renderNodes(node.bodyNodes(), ctx, out);
                } finally {
                    ctx.popAnonymous();
                }
            }
        } finally {
            ctx.exitLoop();
        }
    }

    /**
     * 迭代来源：多值字段（列表 / 数组）、JSON 对象（迭代键值对）、以及引擎预加载清单。
     * {@code null} 与空集**不是错误**，渲染空内容（§5.4："这不是错误"）。
     */
    private static List<Object> itemsOf(Object value, TagNode node) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof Map<?, ?> map) {
            // JSON 字段：每项提供 key / value（§6.2 的规格参数表例子）
            List<Object> pairs = new ArrayList<>();
            map.forEach((key, item) -> {
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("key", String.valueOf(key));
                pair.put("value", item == null ? "" : item);
                pairs.add(pair);
            });
            return pairs;
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray()) {
            List<Object> items = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                items.add(java.lang.reflect.Array.get(value, i));
            }
            return items;
        }
        throw PublishException.error(PublishErrorCode.E1002,
                "foreach 的 field 指向的不是多值字段", node.sourcePath(), node.lineNo(),
                "field='" + node.arg("field") + "' 的值是 " + value.getClass().getSimpleName()
                        + "，不是列表 / 数组 / JSON 对象",
                "foreach 只能迭代多值字段（IMAGES / FILES / RELATION / TAGS / JSON / ENUM_MULTI）"
                        + "与引擎预加载清单：" + String.join(" ", BuiltinFields.FOREACH_SOURCES));
    }

    /** 迭代项自身的字段 + 引擎给的 5 个计数/位置字段（§6.2）。 */
    private static Map<String, Object> frameOf(Object item, int index, int count) {
        Map<String, Object> frame = new LinkedHashMap<>(valuesOf(item));
        frame.put("index", index + 1);
        frame.put("index0", index);
        frame.put("isFirst", index == 0);
        frame.put("isLast", index == count - 1);
        frame.put("count", count);
        return frame;
    }

    /**
     * 这一帧的字段属于哪个内容类型：迭代项上有 {@code typeCode}（§2.2 的派生字段，任何内容项都有）
     * 就用它，这样跨类型列表的字段声明才取得对；没有（图片、规格对这类非内容项）返回 {@code null}
     * = 继承页面类型（{@link RenderContext#pushAnonymous(Map, String)} 的语义）。
     */
    private static String frameType(Object item) {
        Object typeCode = valuesOf(item).get("typeCode");
        return typeCode == null ? null : String.valueOf(typeCode);
    }

    private static Map<String, Object> valuesOf(Object item) {
        if (item instanceof ContentItem content) {
            return content.values();
        }
        if (item instanceof NavItem nav) {
            return nav.values();
        }
        if (item instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        // 标量项（ENUM_MULTI 的取值数组等）：迭代项只有 value（§2.2）
        Map<String, Object> single = new LinkedHashMap<>();
        single.put("value", item);
        return single;
    }

    /** 排序在截断**之前**（先 ORDER BY 再 LIMIT/OFFSET），缺失的排序键按 null 处理，排序稳定。 */
    private static void sort(List<Object> items, String orderby, boolean desc) {
        Comparator<Object> comparator =
                (left, right) -> compare(sortKey(left, orderby), sortKey(right, orderby));
        items.sort(desc ? comparator.reversed() : comparator);
    }

    private static Object sortKey(Object item, String orderby) {
        Map<String, Object> values = valuesOf(item);
        return values.containsKey(orderby) ? values.get(orderby) : null;
    }

    private static int compare(Object left, Object right) {
        if (left == null) {
            return right == null ? 0 : -1;
        }
        if (right == null) {
            return 1;
        }
        Double a = asNumber(left);
        Double b = asNumber(right);
        // 必须是**全序**：数值一律排在文本之前，同类内才用统一口径比。
        // 若"数值对之间比数值、混合对之间比字符串"，就会同时出现 9<10、10<"2b"、9>"2b"
        // 这类自相矛盾的顺序，TimSort 在元素数 ≥32 时直接抛
        // "Comparison method violates its general contract!"，打断整页发布
        if (a != null && b != null) {
            return Double.compare(a, b);
        }
        if (a != null) {
            return -1;
        }
        if (b != null) {
            return 1;
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private static Double asNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof CharSequence text) {
            try {
                return Double.valueOf(text.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static int intArg(TagNode node, String key) {
        String text = node.arg(key);
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(text.trim());
            if (value < 0) {
                throw new NumberFormatException(text);
            }
            return value;
        } catch (NumberFormatException e) {
            throw PublishException.error(PublishErrorCode.E1002,
                    "参数 " + key + " 不是非负整数", node.sourcePath(), node.lineNo(),
                    "写了 " + key + "='" + text + "'",
                    "写一个非负整数，例如 " + key + "='10'（0 = 全部）");
        }
    }
}
