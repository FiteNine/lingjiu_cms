package com.lingjiuw.cms.module.cms.publish.template;

import java.util.List;

/**
 * 一个标签的参数声明（§3.3："每个标签通过 ParamSpec 声明自己的参数表；**未声明的参数报错**，
 * 报错时列出全部可用参数"）。
 *
 * <p>默认值只在本记录里声明，**不回填进 {@code TagNode.args}**：模板里没写就用默认值，模板里写了
 * 就用字面量——这样"模板作者写了什么"与"引擎补了什么"始终可区分。取值由标签实现自己做
 * （{@code CurrentMarks} 的 {@code str} / {@code intOf} / {@code boolOf} / {@code enumOf}，
 * 或查询标签的 {@code QueryParams.accepted}），本记录只描述参数。
 */
public record ParamSpec(String key, ParamType type, boolean required, String defaultValue,
                        String desc, List<String> enumValues) {

    public ParamSpec {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }

    /** 可选字符串参数。 */
    public static ParamSpec of(String key, String desc) {
        return new ParamSpec(key, ParamType.STRING, false, null, desc, null);
    }

    /** 可选字符串参数，带默认值。 */
    public static ParamSpec of(String key, String defaultValue, String desc) {
        return new ParamSpec(key, ParamType.STRING, false, defaultValue, desc, null);
    }

    /** 必填字符串参数。 */
    public static ParamSpec required(String key, String desc) {
        return new ParamSpec(key, ParamType.STRING, true, null, desc, null);
    }

    /** 可选整数参数。 */
    public static ParamSpec intOf(String key, int defaultValue, String desc) {
        return new ParamSpec(key, ParamType.INT, false, String.valueOf(defaultValue), desc, null);
    }

    /** 可选布尔参数（{@code 0} / {@code 1} / {@code true} / {@code false}）。 */
    public static ParamSpec boolOf(String key, int defaultValue, String desc) {
        return new ParamSpec(key, ParamType.BOOL, false, String.valueOf(defaultValue), desc, null);
    }

    /**
     * 可选枚举参数。
     *
     * @param values 允许值，按书写顺序；报错文案会原样列出（§10.3 第 1 条）
     */
    public static ParamSpec enumOf(String key, String defaultValue, String desc, String... values) {
        return new ParamSpec(key, ParamType.ENUM, false, defaultValue, desc, List.of(values));
    }

    /** 该参数的类型与允许取值范围，用于报错文案。 */
    public String typeText() {
        return switch (type) {
            case STRING -> "文本";
            case INT -> "整数";
            case BOOL -> "0 / 1 / true / false";
            case ENUM -> String.join(" / ", enumValues);
        };
    }
}
