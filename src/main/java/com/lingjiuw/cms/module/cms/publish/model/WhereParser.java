package com.lingjiuw.cms.module.cms.publish.model;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code where} 参数的解析（static-publish.md §2.5、§4.5 第 6 条 ★）。
 *
 * <p>转义规则是这一处最容易写错的地方，因此写死在代码里：
 * <ul>
 *   <li>条件之间用 {@code ,} 分隔（AND）；</li>
 *   <li>条件体是 {@code code:op:value}，**value 按"第一个 {@code :} 之后到下一个未转义的 {@code ,} 为止"整段取**，
 *       所以 value 里允许出现 {@code :}；</li>
 *   <li>{@code op=in} 时多值用未转义的 {@code |} 分隔；</li>
 *   <li>值里要真的写 {@code ,} 或 {@code |} 时用 {@code \,} / {@code \|}。</li>
 * </ul>
 *
 * <p>参数层的转义（§3.3 的 {@code \'} / {@code \"} / {@code \\}）先发生，且**不吞掉** {@code \,} 与
 * {@code \|}（§3.3 规定"其余反斜杠原样保留"），所以 {@code \,} 会原样传到这里，由本类负责还原。
 */
public final class WhereParser {

    private WhereParser() {
    }

    /**
     * 解析 {@code where} 的字面量。
     *
     * @param raw          模板里写的 {@code where} 值；null / 空白返回空列表
     * @param templatePath 报错时用的模板路径，可为 null
     * @param lineNo       报错时用的行号，0 表示无
     * @throws PublishException 语法不可解析 → E2007（§10.2："语法不可解析"，文案要贴出实际值）
     */
    public static List<WhereCondition> parse(String raw, String templatePath, int lineNo) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<WhereCondition> conditions = new ArrayList<>();
        for (String piece : splitUnescaped(raw, ',')) {
            String condition = piece.trim();
            if (condition.isEmpty()) {
                throw E2007(raw, "第 " + (conditions.size() + 1) + " 个条件是空的", templatePath, lineNo);
            }
            int first = indexOfUnescaped(condition, ':');
            if (first < 0) {
                throw E2007(raw, "条件 `" + condition + "` 不是 code:op:value 三段式", templatePath, lineNo);
            }
            int second = indexOfUnescaped(condition, ':', first + 1);
            if (second < 0) {
                throw E2007(raw, "条件 `" + condition + "` 缺少运算符（写法是 code:op:value）",
                        templatePath, lineNo);
            }
            String fieldCode = condition.substring(0, first).trim();
            String opText = condition.substring(first + 1, second).trim();
            String valueText = condition.substring(second + 1);
            if (fieldCode.isEmpty()) {
                throw E2007(raw, "条件 `" + condition + "` 的字段 code 是空的", templatePath, lineNo);
            }
            WhereCondition.Op op = WhereCondition.Op.of(opText);
            if (op == null) {
                throw E2007(raw, "运算符 `" + opText + "` 不在白名单里",
                        templatePath, lineNo, "可用运算符：" + WhereCondition.Op.names());
            }
            List<String> values = op == WhereCondition.Op.in
                    ? unescapeEach(splitUnescaped(valueText, '|'))
                    : List.of(unescape(valueText).trim());
            for (String value : values) {
                // `code:eq:` / `code:in:` / `code:in:a|` 都会产出空取值；空值条件在两种实现
                // 下的语义不同（可能匹配空值，也可能匹配一切），宁可在解析期报 E2007。
                if (value.isEmpty()) {
                    throw E2007(raw, "条件 `" + condition + "` 的取值是空的", templatePath, lineNo);
                }
            }
            conditions.add(new WhereCondition(fieldCode, op, values));
        }
        return conditions;
    }

    /** 按未转义的分隔符切分，保留 {@code \} 与分隔符本身，交给 {@link #unescape} 还原。 */
    static List<String> splitUnescaped(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                current.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            if (c == delimiter) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        parts.add(current.toString());
        return parts;
    }

    static int indexOfUnescaped(String text, char target) {
        return indexOfUnescaped(text, target, 0);
    }

    static int indexOfUnescaped(String text, char target, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == target) {
                return i;
            }
        }
        return -1;
    }

    /** 还原 {@code \,} 与 {@code \|}；其余反斜杠原样保留（与 §3.3 的口径一致）。 */
    static String unescape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == ',' || next == '|') {
                    sb.append(next);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 还原 {@code \,} 与 {@code \|}，并去掉每个取值首尾的空白。
     *
     * <p>取值两侧的空格一律不算有效字符：{@code region:in:huadong | huanan}、
     * {@code price:lte: 9999} 是最常见的写法，保留空格会匹配不到任何东西。
     */
    private static List<String> unescapeEach(List<String> parts) {
        List<String> result = new ArrayList<>(parts.size());
        for (String part : parts) {
            result.add(unescape(part).trim());
        }
        return result;
    }

    private static PublishException E2007(String raw, String actual, String templatePath, int lineNo) {
        return E2007(raw, actual, templatePath, lineNo,
                "写法是 code:op:value，条件之间用逗号分隔（AND），in 的多值用 | 分隔，"
                        + "值里写 , 或 | 时用 \\, 与 \\| 转义");
    }

    private static PublishException E2007(String raw, String actual, String templatePath, int lineNo,
                                          String advice) {
        return PublishException.error(PublishErrorCode.E2007,
                "where 参数不可解析", templatePath, lineNo,
                actual + "；where='" + forMessage(raw) + "'", advice);
    }

    /**
     * 把原始 {@code where} 贴进报错文案前先压平：raw 是模板字面量，含回车/换行时会把
     * §10.1 的三行格式撑散，超长则让日志失去可读性。截断只影响文案，不影响解析。
     */
    private static String forMessage(String raw) {
        String text = raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ').trim();
        return text.length() <= 120 ? text : text.substring(0, 120) + "…";
    }
}
