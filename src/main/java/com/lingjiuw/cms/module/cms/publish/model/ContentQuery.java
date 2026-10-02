package com.lingjiuw.cms.module.cms.publish.model;

import java.util.List;

/**
 * 一次内容查询的全部参数（static-publish.md §6.3 的"共用参数表"）。
 *
 * <p>模板里的参数一律是**字面量**（§3.3），因此这个对象是"模板参数被解析、校验、归一之后"的
 * 形态：{@code where} 已经解析成 {@link WhereCondition}，{@code orderby} 已经解析成
 * {@link OrderBy}，{@code type='all'} 与 {@code category='all'} 已经落到布尔标记上。
 *
 * <p>它是**可变**的：标签按"页面缺省 → 模板参数覆盖"的顺序逐项设置，读起来就是那张参数表。
 * 一次渲染里每个标签一个实例，不跨线程共享。
 *
 * <p>三条口径写死在字段注释里，因为它们是分页与计数的正确性前提：
 * <ul>
 *   <li>{@code status='PUBLISHED'} **隐含** {@code publish_time <= now()} 且
 *       {@code expire_time IS NULL OR expire_time > now()}（§6.3）；</li>
 *   <li>所有计数（{@code channel.count} / {@code tagnav.count} / {@code archive.count} /
 *       facet 的 {@code count>0} / 作者文章数 / {@code {cms:prenext}} 的邻接判定）
 *       **一律按同一个判定函数**，否则计数会把还没到点的内容算进去（§6.3）；</li>
 *   <li>排序末尾恒追加 {@code id desc}（§6.3）。</li>
 * </ul>
 */
public final class ContentQuery {

    private String typeCode;
    private boolean typeAll;
    private String category;
    private boolean includeChildren = true;
    private List<String> tags = List.of();
    private String author;
    private String of;
    private String relate;
    private boolean excludeSelf;
    private List<WhereCondition> where = List.of();
    private String keyword;
    private Boolean top;
    private Boolean recommend;
    private String status = "PUBLISHED";
    private List<OrderBy> orderby = List.of();
    private boolean relationOrder;
    private int row;
    private int offset;
    private int depth = 1;

    /** 锚定项坐标（§3.7 裁定三）：{@code of} 与 {@code relate} 都基于它，不在参数里出现。 */
    private long anchorId;
    private long anchorParentId;

    /* ---------------- 取值 ---------------- */

    public String typeCode() {
        return typeCode;
    }

    /** {@code type='all'}：跨类型，按 {@code cms_content} 公共列取数（§6.3）。 */
    public boolean typeAll() {
        return typeAll;
    }

    public String category() {
        return category;
    }

    /** {@code category='all'}：不限栏目。 */
    public boolean categoryAll() {
        return category == null || "all".equals(category);
    }

    public boolean includeChildren() {
        return includeChildren;
    }

    public List<String> tags() {
        return tags;
    }

    public String author() {
        return author;
    }

    /** {@code of='self'} / {@code of='parent'}；未给则为 null。 */
    public String of() {
        return of;
    }

    /** {@code relate='tag'} / {@code 'category'} / {@code 'field:<code>'}；未给则为 null。 */
    public String relate() {
        return relate;
    }

    public boolean excludeSelf() {
        return excludeSelf;
    }

    public List<WhereCondition> where() {
        return where;
    }

    public String keyword() {
        return keyword;
    }

    public Boolean top() {
        return top;
    }

    public Boolean recommend() {
        return recommend;
    }

    public String status() {
        return status;
    }

    public List<OrderBy> orderby() {
        return orderby;
    }

    /** {@code orderby='relationOrder'}：按 {@code RELATION} 字段的数组顺序排（§6.3）。 */
    public boolean relationOrder() {
        return relationOrder;
    }

    public int row() {
        return row;
    }

    public int offset() {
        return offset;
    }

    public int depth() {
        return depth;
    }

    public long anchorId() {
        return anchorId;
    }

    public long anchorParentId() {
        return anchorParentId;
    }

    /* ---------------- 归一后的判定 ---------------- */

    /** 是否要跨类型取数：{@code type='all'}。 */
    public boolean crossType() {
        return typeAll;
    }

    /** 是否按"锚定项的子内容"取数（{@code of='self'}）。 */
    public boolean childrenOfAnchor() {
        return "self".equals(of);
    }

    /** 是否按"锚定项的父的子内容"取数（{@code of='parent'}）。 */
    public boolean siblingsOfAnchor() {
        return "parent".equals(of);
    }

    /** {@code where} / {@code orderby} 用到的全部自定义字段 code，供 §4.5 第 6 条查 {@code indexed}。 */
    public List<String> referencedFieldCodes() {
        List<String> codes = new java.util.ArrayList<>();
        for (WhereCondition condition : where) {
            codes.add(condition.fieldCode());
        }
        for (OrderBy order : orderby) {
            codes.add(order.fieldCode());
        }
        if (relate != null && relate.startsWith("field:")) {
            codes.add(relate.substring("field:".length()));
        }
        return codes;
    }

    /* ---------------- 设置（读起来就是 §6.3 的参数表） ---------------- */

    public ContentQuery type(String value) {
        // null / 空串必须显式拒绝：null 会被下游（DbContentProvider#params 的
        // `crossType() || typeCode() == null`）当成**跨类型**查询，空串又退化成"按空类型码过滤
        // （无结果）"——同一件事两种相反的语义，而且都是静默的范围放大。
        // "不限类型"的写法是显式的 type='all'（§6.3）；完全不调本方法也仍是跨类型，那是首页的口径。
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("type 不能为空：跨类型请显式传 \"all\"");
        }
        if ("all".equals(value)) {
            this.typeAll = true;
            this.typeCode = null;
        } else {
            this.typeAll = false;
            this.typeCode = value;
        }
        return this;
    }

    public ContentQuery category(String value) {
        this.category = value;
        return this;
    }

    public ContentQuery includeChildren(boolean value) {
        this.includeChildren = value;
        return this;
    }

    public ContentQuery tags(List<String> value) {
        this.tags = value == null ? List.of() : List.copyOf(value);
        return this;
    }

    public ContentQuery author(String value) {
        this.author = value;
        return this;
    }

    public ContentQuery of(String value) {
        this.of = value;
        return this;
    }

    public ContentQuery relate(String value) {
        this.relate = value;
        return this;
    }

    public ContentQuery excludeSelf(boolean value) {
        this.excludeSelf = value;
        return this;
    }

    public ContentQuery where(List<WhereCondition> value) {
        this.where = value == null ? List.of() : List.copyOf(value);
        return this;
    }

    public ContentQuery keyword(String value) {
        this.keyword = value;
        return this;
    }

    public ContentQuery top(Boolean value) {
        this.top = value;
        return this;
    }

    public ContentQuery recommend(Boolean value) {
        this.recommend = value;
        return this;
    }

    public ContentQuery status(String value) {
        this.status = value == null ? "PUBLISHED" : value;
        return this;
    }

    public ContentQuery orderby(List<OrderBy> value) {
        this.orderby = value == null ? List.of() : List.copyOf(value);
        return this;
    }

    public ContentQuery relationOrder(boolean value) {
        this.relationOrder = value;
        return this;
    }

    public ContentQuery row(int value) {
        this.row = value;
        return this;
    }

    public ContentQuery offset(int value) {
        this.offset = value;
        return this;
    }

    public ContentQuery depth(int value) {
        this.depth = value;
        return this;
    }

    public ContentQuery anchor(long id, long parentId) {
        this.anchorId = id;
        this.anchorParentId = parentId;
        return this;
    }

    /**
     * 浅拷贝：派生页之间要复用同一份查询参数再改 {@code offset} / {@code row}。
     * 三个集合字段（{@code tags} / {@code where} / {@code orderby}）与元素都是不可变的
     * （setter 一律 {@code List.copyOf}，元素是 record），因此共享引用是安全的。
     */
    public ContentQuery copy() {
        ContentQuery copy = new ContentQuery();
        copy.typeCode = typeCode;
        copy.typeAll = typeAll;
        copy.category = category;
        copy.includeChildren = includeChildren;
        copy.tags = tags;
        copy.author = author;
        copy.of = of;
        copy.relate = relate;
        copy.excludeSelf = excludeSelf;
        copy.where = where;
        copy.keyword = keyword;
        copy.top = top;
        copy.recommend = recommend;
        copy.status = status;
        copy.orderby = orderby;
        copy.relationOrder = relationOrder;
        copy.row = row;
        copy.offset = offset;
        copy.depth = depth;
        copy.anchorId = anchorId;
        copy.anchorParentId = anchorParentId;
        return copy;
    }
}
