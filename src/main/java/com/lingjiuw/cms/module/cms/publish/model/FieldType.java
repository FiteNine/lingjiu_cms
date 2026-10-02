package com.lingjiuw.cms.module.cms.publish.model;

/**
 * 字段类型（static-publish.md §2.2 的 19 种，逐行落地）。
 *
 * <p>"不放开表达式"的补偿全在这里：能算的、能格式化的，都在**字段层**算完，
 * 模板作者不需要在模板里做运算（§5.5 第（3）条）。
 */
public enum FieldType {

    /** 单行文本。 */
    TEXT,
    /** 多行文本，输出时把换行转成 {@code <br>}。 */
    TEXTAREA,
    /** 富文本：入库时按 §5.2.4 的白名单清洗，字段标为 {@code raw=1}，原样输出。 */
    RICHTEXT,
    /** Markdown：入库时预渲染，模板用 {@code [field:contentHtml/]} 取渲染结果（§5.2.2）。 */
    MARKDOWN,
    /** 整数（bigint）。 */
    INT,
    /** 小数（numeric）。 */
    DECIMAL,
    /** 布尔（smallint，1 / 0）。 */
    BOOL,
    /** 日期。 */
    DATE,
    /** 日期时间。 */
    DATETIME,
    /** 单选枚举：存储值，{@code format='label'} 输出选项标签。 */
    ENUM,
    /** 多选枚举：每个取值一行进索引表，可被 {@code where} 的 {@code in} / {@code has} 过滤（§2.5）。 */
    ENUM_MULTI,
    /** 颜色（{@code #rrggbb}）。 */
    COLOR,
    /** 单张图片（媒体 id）。 */
    IMAGE,
    /** 图集（媒体 id 数组）。 */
    IMAGES,
    /** 单个附件。 */
    FILE,
    /** 多个附件。 */
    FILES,
    /** 关联内容 id 数组，数组顺序 = 录入顺序（可配 {@code orderby='relationOrder'}）。 */
    RELATION,
    /** 标签 id 数组。 */
    TAGS,
    /** 任意 jsonb：多段字段名访问，或迭代键值对做规格参数表。 */
    JSON;

    /** 是否是多值字段（可用 {@code {cms:foreach}} 迭代、可取 {@code .count}）。 */
    public boolean multiValued() {
        return switch (this) {
            case IMAGES, FILES, RELATION, TAGS, JSON, ENUM_MULTI -> true;
            default -> false;
        };
    }

    /**
     * 是否文本类（{@code maxlen} / {@code mask} / {@code upper} / {@code lower} 只对它们合法，§2.2）。
     *
     * <p>判据是 {@code FieldOutput.legal()} 那张合法性表：只有 {@code TEXT} / {@code TEXTAREA}
     * 拿到文本类的 formatter。{@code RICHTEXT} / {@code MARKDOWN} / {@code COLOR} 一个 formatter
     * 都不接受，{@code ENUM} 只接受 {@code label}——把它们算作文本类会放行
     * {@code [field:richtext maxlen='20'/]} 这类非法组合。
     */
    public boolean textual() {
        return switch (this) {
            case TEXT, TEXTAREA -> true;
            default -> false;
        };
    }

    /** 是否数值类（{@code number} / {@code compact} / {@code percent} / {@code money} / {@code fixed} 等）。 */
    public boolean numeric() {
        return this == INT || this == DECIMAL;
    }

    /** 是否日期类（日期模式串 / {@code relative} / {@code iso} / {@code weekday}）。 */
    public boolean temporal() {
        return this == DATE || this == DATETIME;
    }

    /** 是否媒体类（{@code size='thumb|medium|large'} 只对它们合法，§7.7）。 */
    public boolean media() {
        return this == IMAGE || this == IMAGES;
    }
}
