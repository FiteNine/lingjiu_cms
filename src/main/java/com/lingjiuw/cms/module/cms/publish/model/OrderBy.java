package com.lingjiuw.cms.module.cms.publish.model;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code orderby} 的一个排序字段（static-publish.md §6.3）：
 * 可多字段（{@code orderby='sort asc, publishTime desc'}）。
 *
 * <p>**任何排序都在末尾追加 {@code id desc} 兜底**（§6.3 v2.2 定死）：没有 tie-break 时，
 * 同一 {@code publishTime} 的多条内容在分页里会重复出现或整条丢失，{@code {cms:prenext}} 的
 * "上一篇"也会漂移。这条兜底不在本记录里表达——它是查询执行时的固定后缀，只写一处。
 *
 * @param fieldCode 字段 code
 * @param desc      是否倒序；单字段排序简写 {@code order} 也归一到这里
 */
public record OrderBy(String fieldCode, boolean desc) {

    /**
     * 解析 {@code orderby} 的字面量。每段形如 {@code sort}、{@code sort asc}、{@code publishTime desc}；
     * 省略方向时按 {@code asc}。
     */
    public static List<OrderBy> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<OrderBy> list = new ArrayList<>();
        for (String piece : raw.split(",")) {
            String text = piece.trim();
            if (text.isEmpty()) {
                continue;
            }
            String[] parts = text.split("\\s+");
            String code = parts[0];
            boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
            list.add(new OrderBy(code, desc));
        }
        return list;
    }

    /**
     * 拼进 SQL 的片段；{@code id desc} 由执行方统一追加，这里不管。
     *
     * <p><b>只接受标识符形态的 code</b>：本方法把 {@code fieldCode} 原样拼进 SQL，而它的来源是
     * 模板标签 / 站点配置里的字面量（不可信输入）。字符白名单（字母数字下划线、不以数字开头）
     * 已经排除空格、引号、括号与 {@code --} 注释，{@code id) desc --} 这类串进不来。
     */
    public String sqlFragment() {
        if (fieldCode == null || !fieldCode.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("orderby 的字段 code 不是合法标识符：" + fieldCode);
        }
        return fieldCode + (desc ? " desc" : " asc");
    }
}
