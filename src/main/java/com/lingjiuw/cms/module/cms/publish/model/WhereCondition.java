package com.lingjiuw.cms.module.cms.publish.model;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code where} 的一个条件（static-publish.md §2.5）。
 *
 * <pre>
 * where='brand:eq:acme,price:lte:9999,tagId:has:3'
 *       └─ 条件之间用 , 分隔（全部 AND，没有 OR）
 *       └─ 条件体是 code:op:value 三段，value 里允许出现 :（按第一个 : 之后到下一个 , 为止整段取值）
 *       └─ op=in 时，多值用 | 分隔：where='region:in:huadong|huanan'
 * </pre>
 *
 * @param fieldCode 字段 code；只接受 §2.2 白名单里的 code（内置）或 {@code indexed=1} 的自定义字段
 * @param op        运算符，白名单见 {@link Op}
 * @param values    取值：{@code in} 可能有多个，其余恰好一个；**值一律用 id**（标签 {@code tagId}、
 *                  分类 {@code categoryId} 是数字 id，{@code typeCode} / {@code slug} 是字符串）
 */
public record WhereCondition(String fieldCode, Op op, List<String> values) {

    public WhereCondition {
        // 这是发布引擎的公开模型（测试与业务代码会直接 new），非法状态一旦进来会静默流到
        // 下游的索引查询构造里。在这里把 javadoc 写的不变式钉住，报错要能定位到是哪个字段。
        if (fieldCode == null || fieldCode.isBlank()) {
            throw new IllegalArgumentException("where 条件的 fieldCode 不能为空");
        }
        if (op == null) {
            throw new IllegalArgumentException("where 条件 " + fieldCode + " 缺少运算符");
        }
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("where 条件 " + fieldCode + " 缺少取值");
        }
        if (op != Op.in && values.size() != 1) {
            throw new IllegalArgumentException("运算符 " + op + " 只接受一个取值（" + fieldCode
                    + " 给了 " + values.size() + " 个）");
        }
        values = List.copyOf(values);
    }

    /** §2.5 的运算符白名单；表外的运算符 → E2007。 */
    public enum Op {
        eq, ne, gt, gte, lt, lte, like, in, has;

        public static Op of(String text) {
            for (Op op : values()) {
                if (op.name().equals(text)) {
                    return op;
                }
            }
            return null;
        }

        public static String names() {
            List<String> names = new ArrayList<>();
            for (Op op : values()) {
                names.add(op.name());
            }
            return String.join(" ", names);
        }
    }
}
