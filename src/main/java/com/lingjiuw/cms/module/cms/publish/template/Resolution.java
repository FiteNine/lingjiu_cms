package com.lingjiuw.cms.module.cms.publish.template;

/**
 * 字段路径的解析结果（§5.1）。解析失败**不抛异常**，而是把"在哪一段断掉、那一段上有什么"
 * 原样带回来——§10.3 要求报错必须能列清单，而只有解析器知道该列哪一份清单。
 *
 * @param found         是否解析成功
 * @param value         解析成功时的值；失败时为 null
 * @param brokenAt      断掉在第几段（0 起）；成功时为 -1
 * @param availableKeys 断掉处的可用 key（逗号分隔，已排序）；供"→ 现状 / → 建议"使用
 * @param scopeLabel    这次解析是在哪儿找的（"当前条目与循环项"、"具名作用域 site"…）
 */
public record Resolution(boolean found, Object value, int brokenAt, String availableKeys, String scopeLabel) {

    public static Resolution ok(Object value, String scopeLabel) {
        return new Resolution(true, value, -1, null, scopeLabel);
    }

    public static Resolution missing(int brokenAt, String availableKeys, String scopeLabel) {
        return new Resolution(false, null, brokenAt, availableKeys, scopeLabel);
    }
}
