package com.lingjiuw.cms.module.cms.publish.provider;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 一次取数的全部条件，MyBatis 侧只认这个对象（{@code mapper/cms/CmsContentMapper.xml} 里的
 * {@code #{p.xxx}} 与 {@code <if test="p.xxx">}）。
 *
 * <p>为什么不让 XML 直接吃 {@code ContentQuery}：契约里的 {@code category} 是"id 或 slug"、
 * {@code tag} 是"slug 列表"、{@code where} 的字段 code 要判"内置列 / 分类 / 标签 / 索引表"，
 * 这些**都要先落成 id 与列名**才写得出 SQL。翻译发生在 Java（这里），SQL 里只做比较，
 * 于是"值一律用 id"（§2.5）这条口径只有一处实现。
 *
 * <p>同样地，XML 里的语句**不会自动带逻辑删除条件**（backend/AGENTS.md），所以每条语句都自己写
 * {@code deleted = 0}；本类只负责把条件翻译好。
 */
@Getter
@Setter
public class ProviderParams {

    /** 站点隔离：所有语句都从它开始（数据层不跨站点取数）。 */
    private long siteId;

    /** 具体类型；{@code type='all'}（跨类型）时为 null，语义由 {@link #typeAll} 决定。 */
    private String typeCode;

    /** {@code type='all'}：跨类型取数，SQL 里不许出现 {@code type_code} 条件。 */
    private boolean typeAll;

    /** 栏目过滤的分类 id 集合；{@code null} = 不限栏目（{@code category='all'}）。 */
    private List<Long> categoryIds;

    /** 标签过滤的标签 id 集合（多值之间 AND）。 */
    private List<Long> tagIds;

    /** 模板写了 {@code tag} 参数（即便解析出来是空集合，也要按"查不到"处理，不能退化成"不限"）。 */
    private boolean tagFilterActive;

    /** 有标签 slug 在库里不存在 ⇒ 一条都不匹配（宁可空，不可放宽）。 */
    private boolean tagMissing;

    /** 作者 id；{@code author='self'} 在标签层已经换成当前条目的 authorId（§6.3）。 */
    private Long authorId;

    /** 作者解析不出来 ⇒ 一条都不匹配。 */
    private boolean authorMissing;

    /** {@code of='self'} / {@code 'parent'}；null = 不按锚定项取子内容。 */
    private String of;

    /** {@code relate='tag'} / {@code 'category'}；{@code 'field:<code>'} 落在 {@link #relateField}。 */
    private String relate;

    /** {@code relate='field:<code>'} 的字段 code；候选 id 在锚定项或本行的该字段数组里。 */
    private String relateField;

    /** 锚定项 id（§3.7 裁定三）。 */
    private long anchorId;

    /** 锚定项的父 id；0 = 根（{@code of='parent'} 时取顶级）。 */
    private long anchorParentId;

    /** {@code exclude='self'}：排除锚定项自身。 */
    private boolean excludeSelf;

    /** {@code orderby='relationOrder'}：按 RELATION 数组顺序排（§6.3 v2.2）。 */
    private boolean relationOrder;

    /** {@code where} 的每个条件，全部 AND（§2.5）。 */
    private List<Condition> conditions = List.of();

    /** {@code keyword}：标题模糊匹配。 */
    private String keyword;

    /** 仅置顶 / 仅推荐；null = 不管。 */
    private Boolean top;
    private Boolean recommend;

    /** 时间窗模式：{@code published}（默认）/ {@code any} / {@code exact}。 */
    private String statusMode = "published";

    /**
     * 时间窗模式的白名单校验（本方法覆盖 Lombok 为该字段生成的 setter）。
     *
     * <p>XML 侧的 {@code publishWindow} 用 {@code <choose>} 精确匹配这三个字面量，其余一律落到
     * {@code <otherwise>} = **最宽松的 any 分支**：拼错大小写（{@code "Published"}）就会让草稿与
     * 未到点的内容静默出现在静态站里（内容泄漏，不是简单降级），所以非法值必须在赋值处就炸。
     */
    public void setStatusMode(String value) {
        if (value != null && !"published".equals(value) && !"any".equals(value)
                && !"exact".equals(value)) {
            throw new IllegalArgumentException(
                    "statusMode 只接受 published / any / exact，收到：" + value);
        }
        this.statusMode = value == null ? "published" : value;
    }

    /** {@code statusMode='exact'} 时的状态值（DRAFT / OFFLINE…）。 */
    private String statusValue;

    /** 排序项（末尾恒追加 {@code id desc} 兜底，§6.3 v2.2）；{@link #relationOrder} 优先。 */
    private List<OrderItem> order = List.of();

    /** 当页条数；0 = 不分页（取全部）。 */
    private int limit;

    /** 跳过的条数。 */
    private int offset;

    /** 层级深度（{@code children} / {@code query(depth>1)}）。 */
    private int depth = 1;

    /** 层级查询的起始父 id 集合（{@code children} / 预加载 {@code children} 用）。 */
    private List<Long> parentIds;

    /* ---------------- 条件与排序项 ---------------- */

    /**
     * 一个 {@code where} 条件（{@link com.lingjiuw.cms.module.cms.publish.model.WhereCondition} 的
     * SQL 侧形态）。{@code kind} / {@code valueKind} 是**翻译的结果**，XML 只按它们选列与类型。
     */
    @Getter
    @Setter
    public static class Condition {

        private String fieldCode;

        /** 运算符名：{@code eq ne gt gte lt lte like in has}（§2.5 白名单）。 */
        private String op;

        /** 取值（{@code in} 可能多个）。 */
        private List<String> values = List.of();

        /**
         * 字段落在哪里：
         * {@code category} / {@code tag}（关联表）、{@code text} / {@code number} / {@code time}
         * （{@code cms_content} 的列）、{@code custom}（{@code cms_content_index}）。
         */
        private String kind;

        /** {@code custom} 条件比哪一列：{@code num} / {@code time} / {@code str}。 */
        private String valueKind;

        /**
         * 第一个取值；没有取值时返回 {@code null}（**不是空串**）。
         *
         * <p>方向必须是"朝严"：XML 里 {@code like} 会拼出 {@code '%' || #{w.first} || '%'}，
         * 给 {@code null} 得到 {@code NULL}（一行都不匹配），给空串则得到 {@code '%%'}（**匹配所有行**）；
         * {@code cast(null as numeric)} 也合法，空串会直接抛 {@code invalid input syntax}。
         * §2.5 的口径是"宁可空，不可放宽"。
         */
        public String getFirst() {
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }

    /** 一个排序项：内置列或 {@code indexed} 的自定义字段（§6.3 的白名单）。 */
    @Getter
    @Setter
    public static class OrderItem {

        private String fieldCode;

        private boolean desc;

        /** {@code builtin}（{@code cms_content} 的列）/ {@code custom}（{@code cms_content_index}）。 */
        private String kind;

        /** {@code custom} 排序按哪一列排：{@code num} / {@code time} / {@code str}。 */
        private String valueKind;
    }
}
