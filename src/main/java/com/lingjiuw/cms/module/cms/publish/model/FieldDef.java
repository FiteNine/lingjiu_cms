package com.lingjiuw.cms.module.cms.publish.model;

import java.util.List;

/**
 * 自定义字段定义（static-publish.md §2.2 的 {@code cms_field}）。
 *
 * <p>引擎不认识业务，只认识字段：模板里能取到什么、能不能筛、要不要转义，
 * 全部由这份定义决定。
 */
public record FieldDef(String typeCode, String code, String label, FieldType fieldType,
                       boolean raw, boolean indexed, boolean searchable, boolean required,
                       String defaultValue, List<EnumOption> options, String formatter,
                       boolean crossSite, int sort) {

    public FieldDef {
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** 建一个字段定义；最常用的写法（自定义字段、默认不 raw / 不索引）。 */
    public static FieldDef of(String typeCode, String code, FieldType fieldType) {
        return new FieldDef(typeCode, code, code, fieldType, false, false, false, false,
                null, List.of(), null, false, 0);
    }

    /** 建一个可筛选的自定义字段（{@code indexed=1}）。 */
    public static FieldDef indexed(String typeCode, String code, FieldType fieldType) {
        return new FieldDef(typeCode, code, code, fieldType, false, true, false, false,
                null, List.of(), null, false, 0);
    }

    /**
     * 富文本字段：{@code raw=1}，模板作者无权改（§2.2、§5.2.1）。
     *
     * <p><b>前置条件（承重）</b>：{@code raw=1} 的输出**不转义**，因此该字段的值必须已经过
     * §5.2.4 白名单清洗。这个前提由**数据层**在入库时兑现（§5.2.2："Markdown 与富文本的入库处理"），
     * 只应由受信来源（后台编辑、清洗过的导入）写入；把未经清洗的用户提交接进这里就是存储型 XSS。
     * 引擎不提供"渲染前再清洗一次"的开关——清洗只做一处，在入库。
     */
    public static FieldDef richText(String typeCode, String code) {
        return new FieldDef(typeCode, code, code, FieldType.RICHTEXT, true, false, false, false,
                null, List.of(), null, false, 0);
    }

    /** 枚举字段，带选项。 */
    public static FieldDef enumOf(String typeCode, String code, EnumOption... options) {
        return new FieldDef(typeCode, code, code, FieldType.ENUM, false, true, false, false,
                null, List.of(options), null, false, 0);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder(String typeCode, String code, FieldType fieldType) {
        return new Builder(new FieldDef(typeCode, code, code, fieldType, false, false, false, false,
                null, List.of(), null, false, 0));
    }

    /** 定义数量不多但字段多，建造者只为让调用点读起来像定义本身。 */
    public static final class Builder {
        private String typeCode;
        private String code;
        private String label;
        private FieldType fieldType;
        private boolean raw;
        private boolean indexed;
        private boolean searchable;
        private boolean required;
        private String defaultValue;
        private List<EnumOption> options = List.of();
        private String formatter;
        private boolean crossSite;
        private int sort;

        private Builder(FieldDef source) {
            this.typeCode = source.typeCode;
            this.code = source.code;
            this.label = source.label;
            this.fieldType = source.fieldType;
            this.raw = source.raw;
            this.indexed = source.indexed;
            this.searchable = source.searchable;
            this.required = source.required;
            this.defaultValue = source.defaultValue;
            this.options = source.options;
            this.formatter = source.formatter;
            this.crossSite = source.crossSite;
            this.sort = source.sort;
        }

        public Builder label(String value) {
            this.label = value;
            return this;
        }

        public Builder raw(boolean value) {
            this.raw = value;
            return this;
        }

        public Builder indexed(boolean value) {
            this.indexed = value;
            return this;
        }

        public Builder searchable(boolean value) {
            this.searchable = value;
            return this;
        }

        public Builder required(boolean value) {
            this.required = value;
            return this;
        }

        public Builder defaultValue(String value) {
            this.defaultValue = value;
            return this;
        }

        public Builder options(List<EnumOption> value) {
            this.options = value == null ? List.of() : List.copyOf(value);
            return this;
        }

        public Builder formatter(String value) {
            this.formatter = value;
            return this;
        }

        public Builder crossSite(boolean value) {
            this.crossSite = value;
            return this;
        }

        public Builder sort(int value) {
            this.sort = value;
            return this;
        }

        public FieldDef build() {
            return new FieldDef(typeCode, code, label, fieldType, raw, indexed, searchable, required,
                    defaultValue, options, formatter, crossSite, sort);
        }
    }
}
