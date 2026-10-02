package com.lingjiuw.cms.module.cms.publish.model;

/**
 * {@code ENUM} / {@code ENUM_MULTI} 的一个选项（§2.2：字段定义的 {@code options} 是
 * {@code 值:标签} 逗号分隔的列表）。
 *
 * @param value 存储值（进索引表的就是它）
 * @param label 给人看的标签，{@code format='label'} 输出的就是它
 */
public record EnumOption(String value, String label) {
}
