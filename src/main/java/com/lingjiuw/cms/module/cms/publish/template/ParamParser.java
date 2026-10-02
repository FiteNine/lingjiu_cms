package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标签参数区的扫描（static-publish.md §3.3、§4.2）。
 *
 * <p>这里唯一容易写错的一条是"**引号内的 `}` 与 `/` 不结束标签**"（§3.3 原话）：
 * {@code {cms:include file='a}b.html'/}} 合法，所以参数区不能靠"找第一个 `}`"结束，
 * 必须逐字符推进并维护引号状态。
 *
 * <p>参数值**永远是字面量**（§3.3）：引号内只认 {@code \'} {@code \"} {@code \\} 三个转义，
 * 其余反斜杠原样保留——{@code \,} 与 {@code \|} 要留给后面的 {@code WhereParser}（§2.5）。
 *
 * <p>本类只报参数区自身的写法错误与"**同一个 key 出现两次**"（§3.3，E1002）；
 * "这个参数没声明 / 类型不符 / 必填缺失"是编译期校验 2 的事（§4.5），不在这里报。
 */
public final class ParamParser {

    /**
     * 一次参数区扫描的结果。
     *
     * @param values      参数表，保持模板里的书写顺序
     * @param selfClosing 模板里写的是自闭合形态（cms 标签的 {@code /}}、字段引用的 {@code /]}）
     */
    public record Args(Map<String, String> values, boolean selfClosing) {

        public Args {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    private ParamParser() {
    }

    /**
     * 从标签名 / 字段路径之后扫到元素的结束符。
     *
     * @param lexer       扫描游标，调用时停在 name / field_path 之后
     * @param label       报错文案里的元素写法，如 {@code {cms:list}}、{@code [field:images.0.url/]}
     * @param elementLine 元素起始行（§10.1 的位置指向元素本身，不是参数所在行）
     * @param close       元素结束字符：{@code {cms:…}} 是 {@code '}'}，{@code [field:…]} 是 {@code ']'}
     * @return 参数表与是不是自闭合写法；{@code close == ']'} 时只有 {@code /]} 合法，
     *         因此 {@code selfClosing} 恒为 true（写成裸 {@code ]} 会报 E1003）
     */
    public static Args parse(TemplateLexer lexer, String label, int elementLine, char close) {
        int argsStart = lexer.pos();
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, Integer> firstLines = new LinkedHashMap<>();
        Map<String, String> firstRaw = new LinkedHashMap<>();

        while (true) {
            lexer.skipWhitespace();
            if (lexer.eof()) {
                throw unterminated(lexer, label, elementLine, argsStart, close);
            }
            char c = lexer.peek();
            if (c == '/' && lexer.peek(1) == close) {
                lexer.take(2);
                return new Args(values, true);
            }
            if (c == close) {
                if (close == ']') {
                    throw missingSlash(lexer, label, elementLine);
                }
                lexer.next();
                return new Args(values, false);
            }
            if (c == '/') {
                throw badSlash(lexer, label, elementLine, close);
            }
            readArg(lexer, label, elementLine, close, values, firstLines, firstRaw);
        }
    }

    /* ---------------- 参数与值 ---------------- */

    private static void readArg(TemplateLexer lexer, String label, int elementLine, char close,
                                Map<String, String> values, Map<String, Integer> firstLines,
                                Map<String, String> firstRaw) {
        int keyLine = lexer.line();
        if (!isKeyStart(lexer.peek())) {
            throw lexer.error(PublishErrorCode.E1002, "参数区里有无法识别的内容", elementLine,
                    "第 " + elementLine + " 行的 " + label + " 读到 `" + lexer.excerptAhead(lexer.pos(), 16) + "`",
                    "参数写成 key='值' / key=\"值\" / key=值，多个参数之间用空白分隔");
        }
        StringBuilder key = new StringBuilder();
        key.append(lexer.next());
        while (isKeyPart(lexer.peek())) {
            key.append(lexer.next());
        }
        String name = key.toString();
        if (lexer.peek() != '=') {
            throw lexer.error(PublishErrorCode.E1002, "参数 " + name + " 后面缺少 =", elementLine,
                    "第 " + elementLine + " 行的 " + label + " 读到 `" + name + lexer.excerptAhead(lexer.pos(), 12) + "`",
                    "写成 " + name + "='值'（文本要有引号，数字与 0 / 1 可以不加）");
        }
        lexer.next();

        int valueStart = lexer.pos();
        String value = readValue(lexer, label, elementLine, name, close);
        String raw = lexer.excerpt(valueStart, lexer.pos());
        if (values.containsKey(name)) {
            throw lexer.error(PublishErrorCode.E1002, "参数 " + name + " 出现了两次", keyLine,
                    "第 " + firstLines.get(name) + " 行是 " + name + "=" + firstRaw.get(name)
                            + "，第 " + keyLine + " 行又是 " + name + "=" + raw,
                    "删掉其中一个：同一个标签里重复参数一律报错，引擎不做 last-wins（§3.3）");
        }
        firstLines.put(name, keyLine);
        firstRaw.put(name, raw);
        values.put(name, value);
    }

    /** 三种值形式：{@code '…'}、{@code "…"}、裸值（§3.3）。 */
    private static String readValue(TemplateLexer lexer, String label, int elementLine, String key, char close) {
        char c = lexer.peek();
        if (c == '\'' || c == '"') {
            return readQuoted(lexer, label, key, c);
        }
        StringBuilder value = new StringBuilder();
        while (!lexer.eof()) {
            char ch = lexer.peek();
            // 裸值取到空白或**本元素的结束符**为止（§3.3）：`{cms:…}` 是 `}`、`[field:…]` 是 `/]`。
            // 不按 close 区分会让另一侧元素的结束符把值提前截断，报错位置与真实成因错位
            if (Character.isWhitespace(ch) || ch == close) {
                break;
            }
            // 结束符组合 `/}` `/]` `/>`；裸值里的 `/` 是普通字符——
            // 否则契约允许的 `{cms:include file=_partials/header.html/}` 会在 `_partials` 处断掉
            if (ch == '/' && (lexer.peek(1) == close || lexer.peek(1) == '>')) {
                break;
            }
            value.append(lexer.next());
        }
        if (value.length() == 0) {
            throw lexer.error(PublishErrorCode.E1002, "参数 " + key + " 的值是空的", elementLine,
                    "第 " + elementLine + " 行的 " + label + " 里，`" + key + "=` 后面直接就是空白或结束符",
                    "写成 " + key + "='值'；确实要空串请写 " + key + "=''");
        }
        return value.toString();
    }

    private static String readQuoted(TemplateLexer lexer, String label, String key, char quote) {
        int openPos = lexer.pos();
        int openLine = lexer.line();
        lexer.next();
        StringBuilder value = new StringBuilder();
        while (true) {
            if (lexer.eof()) {
                throw lexer.error(PublishErrorCode.E1002, "参数 " + key + " 的引号没有闭合", openLine,
                        "第 " + openLine + " 行的 " + label + " 里 `" + lexer.excerptAhead(openPos, 24)
                                + "` 一路扫到文件结尾也没等到配对的 " + quote,
                        "补上配对的 " + quote + "；引号内要写引号请用 \\' 或 \\\"（§3.3）");
            }
            char c = lexer.next();
            if (c == quote) {
                return value.toString();
            }
            if (c == '\\') {
                char escaped = lexer.peek();
                if (escaped == '\'' || escaped == '"' || escaped == '\\') {
                    value.append(lexer.next());
                    continue;
                }
                value.append(c);                             // `\,` `\|` 原样保留给 WhereParser（§2.5）
                continue;
            }
            value.append(c);
        }
    }

    /* ---------------- 结束符 ---------------- */

    private static PublishException unterminated(TemplateLexer lexer, String label, int elementLine,
                                                 int argsStart, char close) {
        String seen = lexer.excerptAhead(argsStart, 24);
        String actual = seen.isEmpty()
                ? "第 " + elementLine + " 行的 " + label + " 后面就是文件结尾"
                : "第 " + elementLine + " 行的 " + label + " 一路扫到文件结尾也没等到 `" + close + "`，读到 `" + seen + "`";
        String advice = close == '}'
                ? "块标签以 `}` 结束，自闭合标签以 `/}` 结束；先确认模板文件没有被截断"
                : "字段引用以 `/]` 结束，例如 [field:title/]";
        return lexer.error(PublishErrorCode.E1003, label + " 没有结束", elementLine, actual, advice);
    }

    private static PublishException badSlash(TemplateLexer lexer, String label, int elementLine, char close) {
        String seen = lexer.excerptAhead(lexer.pos(), 12);
        if (close == ']') {
            return lexer.error(PublishErrorCode.E1003, "字段引用的结束符写错了", elementLine,
                    "第 " + elementLine + " 行的 " + label + " 读到 `" + seen + "`；字段引用必须以 `/]` 结束",
                    "写成 " + label + " 这样：`/` 后面紧跟 `]`");
        }
        return lexer.error(PublishErrorCode.E1003, label + " 的结束符写错了", elementLine,
                "第 " + elementLine + " 行的 " + label + " 读到 `" + seen + "`；"
                        + (seen.startsWith("/>") ? "本引擎没有 `/>` 这个写法，" : "") + "`/` 必须紧跟 `}`",
                "自闭合标签写成 " + label.substring(0, label.length() - 1) + "/}，块标签写成 " + label);
    }

    private static PublishException missingSlash(TemplateLexer lexer, String label, int elementLine) {
        return lexer.error(PublishErrorCode.E1003, "字段引用少了 `/`", elementLine,
                "第 " + elementLine + " 行的 " + label + " 遇到 `]` 就结束了；字段引用必须以 `/]` 结束",
                "把 `]` 改成 `/]`，例如 [field:title/]");
    }

    /* ---------------- 字符判定（§3.2 的 key = [A-Za-z][A-Za-z0-9_]*） ---------------- */

    private static boolean isKeyStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isKeyPart(char c) {
        return isKeyStart(c) || (c >= '0' && c <= '9') || c == '_';
    }
}
