package com.lingjiuw.cms.module.cms.publish.template.validate;

import java.util.Collection;
import java.util.TreeSet;

/**
 * 笔误猜测（static-publish.md §10.1 / §10.3 第 3 条）：名字与已知名字的编辑距离 ≤ 2 时给
 * "你是不是想写 X？"，模板作者少翻一次文档。
 *
 * <p>与 {@code TagRegistry.suggest} 是同一个算法：那一份是**包级私有**（{@code template} 包），
 * 本包（{@code template.validate}）取不到，标签名之外的参数名 / 字段名 / 类型 code 又都需要它，
 * 因此这里独立实现一份（20 行，两处都只服务报错文案）。
 */
final class Suggest {

    /** 编辑距离上限：超过它就不猜，避免给出离谱建议（§10.1）。 */
    private static final int MAX_DISTANCE = 2;

    private Suggest() {
    }

    /**
     * 最接近的候选。
     *
     * @param name       实际写的名字
     * @param candidates 已知名字
     * @return 距离 ≤ 2 的最近候选；没有则返回 null
     */
    static String closest(String name, Collection<String> candidates) {
        if (name == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        // TreeSet 不收 null：候选可能来自 YAML / DB，先剔掉空值再排序
        TreeSet<String> ordered = new TreeSet<>();
        for (String candidate : candidates) {
            if (candidate != null) {
                ordered.add(candidate);
            }
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : ordered) {
            // 只接受距离 ≤ 2 的候选，长度差超过上限的不可能命中，先剪枝（不影响结果与并列规则）
            if (Math.abs(name.length() - candidate.length()) > MAX_DISTANCE) {
                continue;
            }
            int distance = editDistance(name, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return bestDistance <= MAX_DISTANCE ? best : null;
    }

    /** "；你是不是想写 X？"；没有候选时返回空串。 */
    static String hint(String name, Collection<String> candidates) {
        String best = closest(name, candidates);
        return best == null ? "" : "；你是不是想写 " + best + "？";
    }

    /** 标准 Levenshtein；名字都很短，直接两行数组实现，不引依赖。 */
    static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    /** 逗号分隔的清单，给报错文案用。 */
    static String join(Collection<String> names) {
        return names == null || names.isEmpty() ? "（一个都没有）" : String.join("、", new TreeSet<>(names));
    }
}
