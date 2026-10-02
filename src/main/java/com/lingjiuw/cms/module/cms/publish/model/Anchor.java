package com.lingjiuw.cms.module.cms.publish.model;

/**
 * "锚定项"（static-publish.md §3.7 裁定三）：当前渲染位置所依附的那一条内容。
 *
 * <p>判定顺序**先命中先算，没有"或"**：
 * <ol>
 *   <li>若当前位于 {@code {cms:list}} / {@code {cms:query}} / {@code {cms:foreach}} 的循环体内
 *       → 锚定项 = **栈顶迭代项**；</li>
 *   <li>否则 → 锚定项 = **本页面的当前条目**（§5.1，它恒在匿名栈底）。</li>
 * </ol>
 *
 * <p>{@code of='self'} 取"以锚定项为父"的子内容，{@code of='parent'} 取"锚定项的父"的子内容。
 * 因此锚定项只需要这几个字段；它不是一条完整内容，而是"定位用的坐标"。
 *
 * @param id       锚定项 id；{@code 0} 表示没有锚定项（首页 / 静态页）
 * @param parentId 锚定项的父 id；{@code 0} 表示没有父
 * @param typeCode 锚定项的内容类型 code；没有锚定项时为 null
 * @param title    锚定项标题，仅用于报错文案
 */
public record Anchor(long id, long parentId, String typeCode, String title) {

    /** 没有锚定项（首页 / 静态页 / 搜索页）。 */
    public static final Anchor NONE = new Anchor(0L, 0L, null, null);

    public boolean present() {
        return id > 0;
    }

    public boolean hasParent() {
        return parentId > 0;
    }

    /** 报错文案里怎么称呼这个锚定项（§10.1 要求把实际值原样贴出来）。 */
    public String label() {
        if (!present()) {
            return "（没有当前条目）";
        }
        String name = title == null || title.isBlank() ? "id=" + id : title;
        // typeCode 来自 values.get("typeCode")，不保证非空：别把 "null" 贴进报错文案
        String type = typeCode == null || typeCode.isBlank() ? "" : typeCode + " ";
        return name + "（" + type + "#" + id + "）";
    }
}
