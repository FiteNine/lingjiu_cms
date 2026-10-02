package com.lingjiuw.cms.module.cms.publish.template.ast;

import java.util.List;
import java.util.Map;

/**
 * 字段引用 {@code [field:NAME ARGS/]}（§3.1 的第 5 种词法元素）。
 *
 * <p>{@code path} 是 §3.2 的 {@code field_path}：一段或多段，段可以是标识符或数字下标，
 * 例如 {@code [images.0.url]}、{@code [specs.weight]}、{@code [images.count]}。
 * 首段是 6 个保留名之一时按**具名作用域**解析（§5.1），否则只在匿名栈帧里就近查找。
 *
 * <p>{@code args} 是 formatter 参数（§2.2 的调用语法），永远是字面量。
 */
public record FieldNode(List<String> path, Map<String, String> args, int lineNo, String sourcePath) implements Node {

    public FieldNode {
        path = List.copyOf(path);
        // 与 TagNode 同理：Map.copyOf 的迭代顺序在 JDK 规范里是 "unspecified"，而报错要"贴出第一个
        // 非法的 formatter 参数"（§10.3 第 1 条），顺序不确定会让同一个模板每次报不同的参数。
        args = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(args));
    }

    /** 点号连接的完整路径，用于报错文案（§10.1 要求把实际值原样贴出来）。 */
    public String pathText() {
        return String.join(".", path);
    }

    /** 路径首段。 */
    public String head() {
        if (path.isEmpty()) {
            throw new IllegalStateException("字段路径不能为空（" + sourcePath + ":" + lineNo + "）");
        }
        return path.get(0);
    }
}
