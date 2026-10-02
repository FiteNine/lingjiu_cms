package com.lingjiuw.cms.module.cms.publish.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导航 / 迭代项的通用载体（{@code {cms:channel}} / {@code {cms:breadcrumb}} /
 * {@code {cms:tagnav}} / {@code {cms:archive}} / {@code {cms:pagelist}} 的迭代项）。
 *
 * <p>为什么和 {@link ContentItem} 一样用 Map：§6.4 的每个标签产出的字段集合都不同
 * （分类有 {@code slug} / {@code count}，归档有 {@code year} / {@code month}，菜单有
 * {@code target} / {@code rel}，筛选有 {@code facetPath} / {@code urlWith}），
 * 用六个 POJO 只会让"迭代项有哪些字段"散成六处。§6.3 已经给出了统一契约：
 * **迭代项自身的字段 + 引擎算好的 {@code current} / {@code class}**。
 */
public final class NavItem {

    private final Map<String, Object> values;

    private NavItem(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static NavItem of(Map<String, Object> values) {
        return new NavItem(values);
    }

    public static NavItem empty() {
        return new NavItem(new LinkedHashMap<>());
    }

    public Map<String, Object> values() {
        return values;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public long id() {
        Object value = values.get("id");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public String label() {
        Object value = values.get("label");
        return value == null ? null : String.valueOf(value);
    }

    public String url() {
        Object value = values.get("url");
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 下一层（只有 {@code depth>1} 时有值）；没有时返回空列表。
     *
     * <p>逐元素做 {@code instanceof} 校验而不是直接强转：泛型擦除后强转不会校验元素类型，
     * 上游往 {@code children} 里放了 {@code List<Map<String,Object>>} 时，
     * {@code for (NavItem c : item.children())} 会在迭代处抛 {@code ClassCastException}，
     * 而那个栈帧离出错的地方很远。
     */
    public List<NavItem> children() {
        Object value = values.get("children");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<NavItem> items = new java.util.ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof NavItem item) {
                items.add(item);
            }
        }
        return items;
    }

    /** 复制一份并覆盖若干字段；标签用它补 {@code current} / {@code class} / {@code urlWith}（§5.5）。 */
    public NavItem with(Map<String, Object> extra) {
        Map<String, Object> merged = new LinkedHashMap<>(values);
        merged.putAll(extra);
        return new NavItem(merged);
    }

    public NavItem with(String key, Object value) {
        Map<String, Object> merged = new LinkedHashMap<>(values);
        merged.put(key, value);
        return new NavItem(merged);
    }

    /** 用于拼 class 的形态：把 {@code current} / {@code class} 一次性算好（§5.5 的第（1）种手法）。 */
    public static Map<String, Object> marks(boolean current, String currentClass, String trailClass) {
        Map<String, Object> marks = new LinkedHashMap<>();
        marks.put("current", current);
        marks.put("class", currentClass);
        if (trailClass != null) {
            marks.put("trailClass", trailClass);
        }
        return marks;
    }
}
