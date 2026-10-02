package com.lingjiuw.cms.module.cms.publish.model;

import java.util.List;

/**
 * 表单定义（static-publish.md §6.5 渲染、§9.5 提交）。
 *
 * <p>期 1 只需要"渲染出静态 HTML + 校验 {@code code} 是否存在（E2008）"这两件事，
 * 因此这里只有渲染要用的字段；提交、附件、限流、审核属于动态层（期 4）。
 */
public record FormDef(String code, String name, String submitLabel, String successUrl,
                      List<FormField> fields) {

    public FormDef {
        // 过滤 null 元素：fields 来自 DB 行映射，某一行映射成 null 不该让整个页面渲染失败
        fields = fields == null ? List.of()
                : List.copyOf(fields.stream().filter(java.util.Objects::nonNull).toList());
    }

    /**
     * §6.5：无 JS 时提交成功 302 到 {@code success_url}，默认 {@code /thanks/}。
     *
     * <p>{@code success_url} 来自表单配置（DB），下游会拿它做 302 的 {@code Location}，
     * 因此这里做**站内相对路径**的白名单校验：只放行以单个 {@code /} 开头、不含 {@code :}
     * 的取值（挡掉 {@code https://evil.com}、{@code //evil.com}、{@code javascript:}），
     * 其余一律回退默认值，避免开放重定向 / 协议注入。
     */
    public String successUrlOrDefault() {
        if (successUrl == null || successUrl.isBlank()) {
            return "/thanks/";
        }
        String url = successUrl.trim();
        if (url.startsWith("/") && !url.startsWith("//") && url.indexOf(':') < 0) {
            return url;
        }
        return "/thanks/";
    }

    public String submitLabelOrDefault() {
        return submitLabel == null || submitLabel.isBlank() ? "提交" : submitLabel;
    }

    /**
     * 一个表单字段。
     *
     * @param code        提交时的参数名
     * @param label       给人看的标签
     * @param type        控件类型：{@code text} / {@code textarea} / {@code select} / {@code email}
     *                    / {@code tel} / {@code number} / {@code date} / {@code file} / {@code checkbox}
     * @param required    是否必填
     * @param placeholder 占位文字
     * @param options     {@code select} / {@code checkbox} 的选项
     */
    public record FormField(String code, String label, String type, boolean required,
                            String placeholder, List<EnumOption> options) {

        public FormField {
            options = options == null ? List.of() : List.copyOf(options);
        }

        /** 控件是不是"多行文本"。 */
        public boolean textarea() {
            return "textarea".equals(type);
        }

        /** 控件是不是下拉框。 */
        public boolean select() {
            return "select".equals(type);
        }

        /** 对应的 HTML {@code input type}（textarea / select 不适用）。 */
        public String inputType() {
            return switch (type == null ? "text" : type) {
                case "email", "tel", "number", "date", "file", "checkbox", "url", "hidden" -> type;
                default -> "text";
            };
        }
    }
}
