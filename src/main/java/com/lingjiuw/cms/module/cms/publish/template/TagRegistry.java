package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 标签注册表（static-publish.md §4.1："标签名 → TagHandler（Spring 自动收集）"）。
 *
 * <p>§10.3 第 1 条要求"报错必须带可选项"，因此除了查找，这里还负责
 * **编辑距离猜测**（§10.1："能猜笔误就猜（编辑距离 ≤ 2 时给『你是不是想写 …』）"）。
 */
@Component
public final class TagRegistry {

    /** 编辑距离的上限：超过它就不猜，避免给出离谱建议（§10.1）。 */
    private static final int SUGGEST_DISTANCE = 2;

    private final Map<String, TagHandler> handlers;

    public TagRegistry(List<TagHandler> handlers) {
        Map<String, TagHandler> map = new TreeMap<>();
        for (TagHandler handler : handlers) {
            TagHandler previous = map.put(handler.name(), handler);
            if (previous != null) {
                throw new IllegalStateException("标签名重复注册：" + handler.name()
                        + "（" + previous.getClass().getName() + " 与 " + handler.getClass().getName() + "）");
            }
        }
        // 保序的只读视图：Map.copyOf 不保证迭代顺序，会让 names()（文档承诺按字典序）、
        // require() 报错里的可用标签清单、suggest() 的遍历顺序都随哈希变化
        this.handlers = java.util.Collections.unmodifiableMap(map);
    }

    /** 按名字取标签实现；未知标签返回 null。 */
    public TagHandler handler(String name) {
        return handlers.get(name);
    }

    /** 按名字取标签实现，未知则报 E1001（含可用标签清单与笔误猜测）。 */
    public TagHandler require(String name, String templatePath, int lineNo) {
        TagHandler handler = handlers.get(name);
        if (handler != null) {
            return handler;
        }
        String suggestion = suggest(name);
        throw PublishException.error(PublishErrorCode.E1001,
                "未知标签 {cms:" + name + "}",
                templatePath, lineNo,
                "引擎不认识 " + name,
                "可用标签见 §6.1：" + String.join(" ", names())
                        + (suggestion == null ? "" : "；你是不是想写 {cms:" + suggestion + "}？"));
    }

    /** 全部已注册的标签名，按字典序。 */
    public List<String> names() {
        return List.copyOf(handlers.keySet());
    }

    /** 编辑距离 ≤ 2 的最近候选；没有则返回 null。 */
    public String suggest(String name) {
        if (name == null) {
            return null;
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : handlers.keySet()) {
            int distance = editDistance(name, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return bestDistance <= SUGGEST_DISTANCE ? best : null;
    }

    /** 标准 Levenshtein；标签名很短，直接两行数组实现，不引依赖。 */
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

    /** 全部标签实现，供 §4.5 第 8/13/14/16 条的跨节点校验遍历使用。 */
    public List<TagHandler> all() {
        List<TagHandler> list = new ArrayList<>(handlers.values());
        list.sort(Comparator.comparing(TagHandler::name));
        return list;
    }
}
