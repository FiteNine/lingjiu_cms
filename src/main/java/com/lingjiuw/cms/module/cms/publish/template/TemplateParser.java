package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.Node;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TextNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模板解析器（static-publish.md §3 语法规格、§4.2 扫描算法、§4.3 嵌套配对）。
 *
 * <p>单次线性扫描，**不开正则**：只在遇到起始序列时才进标签解析，其余累积为 {@link TextNode}
 * （§4.2）。起始序列必须紧邻（{@code {cms:}} 的 {@code {} 与 {@code cms:} 之间不得有空白），
 * 且**一出现就进标签解析**：后面不是合法的 name / field_path 就报 E1001，不退回当作文本（§3.2）。
 *
 * <p>块配对用显式栈（§4.3）：{@code {/cms:x}} 与栈顶不同名报 E1003 并给出两个标签的行号，
 * 扫描结束栈非空也报 E1003 并列出每个未闭合标签的行号。**不做容错。**
 *
 * <p>三个刻意的"不在这里报"：
 * <ol>
 *   <li><b>标签名是否存在</b>：不在标签表里的名字是编译期校验 1 的 E1001（§4.5）；</li>
 *   <li><b>参数是否声明、类型是否可转换、必填是否缺失</b>：编译期校验 2 的 E1002（§4.5）；
 *       解析期只报"同一个 key 出现两次"（§3.3）；</li>
 *   <li><b>{@code {cms:else/}} 的位置</b>：解析期把它解析成一个普通的自闭合 {@code TagNode("else", …)}，
 *       {@code elseIndex} 留 -1，由编译器在 <b>include 展开之后</b>绑定到外层 {@code {cms:if}}
 *       （§5.3 v2.2 定死："位置类校验只在展开后的模板上做一次"，否则同一片段被 include 在
 *       {@code {cms:if}} 内时会一处能编译、一处报 E1003）。只有带体的 {@code {cms:else}…{/cms:else}}
 *       在这里就报 E1003——它连词法上都不是 §3.1 的元素 3。</li>
 * </ol>
 */
public final class TemplateParser {

    /** 块标签 / 自闭合标签的起始序列（§3.1 的元素 2、3）。 */
    private static final String TAG_OPEN = "{cms:";
    /** 块结束的起始序列（§3.1 的元素 4）。 */
    private static final String CLOSE_OPEN = "{/cms:";
    /** 字段引用的起始序列（§3.1 的元素 5）。 */
    private static final String FIELD_OPEN = "[field:";

    private TemplateParser() {
    }

    /**
     * 把一个模板源码解析成节点列表。
     *
     * @param source       模板源码
     * @param templatePath 模板相对路径，写进每个节点（§4.2）与每条报错（§10.1）；
     *                     include 在编译期展开后，片段节点仍保留片段自己的路径（§5.3）
     * @return 顶层节点，按源码顺序；解析产物不可变
     * @throws PublishException 词法与配对错误（E1001 / E1002 / E1003，§10.2）
     */
    public static List<Node> parse(String source, String templatePath) {
        Objects.requireNonNull(source, "source");
        TemplateLexer lexer = new TemplateLexer(source, templatePath);
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(null, Map.of(), 1));

        StringBuilder text = new StringBuilder();
        int textLine = 1;
        while (!lexer.eof()) {
            if (lexer.at(CLOSE_OPEN)) {
                flushText(stack.peek(), text, textLine, templatePath);
                parseClose(lexer, stack);
            } else if (lexer.at(TAG_OPEN)) {
                flushText(stack.peek(), text, textLine, templatePath);
                parseTag(lexer, stack);
            } else if (lexer.at(FIELD_OPEN)) {
                flushText(stack.peek(), text, textLine, templatePath);
                parseField(lexer, stack);
            } else {
                if (text.length() == 0) {
                    textLine = lexer.line();
                }
                if (lexer.peek() == '\\' && (lexer.at(1, TAG_OPEN) || lexer.at(1, FIELD_OPEN))) {
                    lexer.next();                            // §3.4：反斜杠本身不输出
                    text.append(lexer.take(lexer.peek() == '{' ? TAG_OPEN.length() : FIELD_OPEN.length()));
                } else {
                    text.append(lexer.next());
                }
            }
        }
        flushText(stack.peek(), text, textLine, templatePath);
        if (stack.size() > 1) {
            throw unclosed(lexer, stack);
        }
        return List.copyOf(stack.peek().body);
    }

    /* ---------------- 三种元素 ---------------- */

    /** 块标签 {@code {cms:NAME ARGS}…{/cms:NAME}} 与自闭合标签 {@code {cms:NAME ARGS/}}。 */
    private static void parseTag(TemplateLexer lexer, Deque<Frame> stack) {
        int line = lexer.line();
        lexer.advance(TAG_OPEN.length());
        String name = scanTagName(lexer, TAG_OPEN, line);
        String label = TAG_OPEN + name + "}";
        ParamParser.Args args = ParamParser.parse(lexer, label, line, '}');

        if (args.selfClosing()) {
            stack.peek().body.add(new TagNode(name, args.values(), null, -1, line, lexer.templatePath()));
            return;
        }
        if ("else".equals(name)) {
            throw lexer.error(PublishErrorCode.E1003, "{cms:else} 不能带标签体", line,
                    "第 " + line + " 行写的是 {cms:else}（块标签写法）",
                    "写成自闭合的 {cms:else/}：它是 {cms:if} 的块内分隔，不是块标签（§3.5 裁定四）");
        }
        stack.push(new Frame(name, args.values(), line));
    }

    /** 块结束 {@code {/cms:NAME}}：与栈顶配对，不匹配即报错（§4.3）。 */
    private static void parseClose(TemplateLexer lexer, Deque<Frame> stack) {
        int line = lexer.line();
        lexer.advance(CLOSE_OPEN.length());
        String name = scanTagName(lexer, CLOSE_OPEN, line);
        lexer.skipWhitespace();
        if (lexer.eof()) {
            throw lexer.error(PublishErrorCode.E1003, CLOSE_OPEN + name + " 没有结束", line,
                    "第 " + line + " 行的 " + CLOSE_OPEN + name + " 扫到文件结尾也没等到 `}`",
                    "块结束只写 {/cms:" + name + "}");
        }
        if (lexer.peek() != '}') {
            throw lexer.error(PublishErrorCode.E1003, "{/cms:" + name + "} 里不能写参数", line,
                    "第 " + line + " 行的 {/cms:" + name + "} 读到 `" + lexer.excerptAhead(lexer.pos(), 12) + "`",
                    "块结束只写 {/cms:" + name + "}；参数写在开始标签里");
        }
        lexer.next();

        Frame top = stack.peek();
        if (top.name == null) {
            throw lexer.error(PublishErrorCode.E1003, "{/cms:" + name + "} 没有可以配对的块标签", line,
                    "第 " + line + " 行的 {/cms:" + name + "} 之前没有任何还没闭合的 {cms:…}",
                    "删掉它，或补上它的开始标签；自闭合写法 {cms:" + name + "/} 不需要 {/cms:" + name + "}");
        }
        if (!top.name.equals(name)) {
            throw mismatch(lexer, line, name, top, stack);
        }
        stack.pop();
        stack.peek().body.add(new TagNode(top.name, top.args, top.body, -1, top.line, lexer.templatePath()));
    }

    /** 字段引用 {@code [field:PATH ARGS/]}：从上下文栈解析，任意位置可用（§3.1 的元素 5）。 */
    private static void parseField(TemplateLexer lexer, Deque<Frame> stack) {
        int line = lexer.line();
        lexer.advance(FIELD_OPEN.length());
        List<String> path = scanFieldPath(lexer, line);
        String label = FIELD_OPEN + String.join(".", path) + "/]";
        ParamParser.Args args = ParamParser.parse(lexer, label, line, ']');
        stack.peek().body.add(new FieldNode(path, args.values(), line, lexer.templatePath()));
    }

    /* ---------------- name 与 field_path（§3.2） ---------------- */

    /**
     * 扫标签名（{@code name = [a-z][a-z0-9_]*}）。§3.2 v2.2 定死：起始序列之后不是合法 name → E1001，
     * **不退回当作文本**（{@code {cms:123}}、{@code {cms: }}、文末孤立的 {@code {cms:} 都走这里）。
     */
    private static String scanTagName(TemplateLexer lexer, String prefix, int line) {
        int start = lexer.pos();
        if (!isTagNameStart(lexer.peek())) {
            throw illegalTagName(lexer, prefix, line, start);
        }
        StringBuilder name = new StringBuilder();
        name.append(lexer.next());
        while (isTagNamePart(lexer.peek())) {
            name.append(lexer.next());
        }
        char next = lexer.peek();
        if (!lexer.eof() && !Character.isWhitespace(next) && next != '/' && next != '}') {
            throw illegalTagName(lexer, prefix, line, start);   // 名字里混进了 `-` `:` 之类的字符
        }
        return name.toString();
    }

    private static PublishException illegalTagName(TemplateLexer lexer, String prefix, int line, int start) {
        String seen = lexer.excerptAhead(start, 24);
        if (seen.isEmpty()) {
            return lexer.error(PublishErrorCode.E1001, prefix + " 后面没有标签名", line,
                    "第 " + line + " 行的 " + prefix + " 后面就是文件结尾",
                    "补上标签名（小写字母开头，如 list）；只想输出字面量时写 \\{cms:（§3.4）");
        }
        return lexer.error(PublishErrorCode.E1001, "标签名不合法", line,
                "第 " + line + " 行的 " + prefix + " 后面读到 `" + seen + "`；标签名是 [a-z][a-z0-9_]*",
                "改成像 {cms:list} 这样的小写名字；要输出字面量请写 \\{cms:…（§3.4）");
    }

    /**
     * 扫字段路径（{@code field_path = segment { "." segment }}）：
     * {@code images.0.url}（多值按下标）、{@code images.count}（多值取个数）、{@code specs.weight}
     * （JSON 取键）都由此覆盖，无新增语法要素（§3.2）。
     */
    private static List<String> scanFieldPath(TemplateLexer lexer, int line) {
        int start = lexer.pos();
        List<String> path = new ArrayList<>();
        while (true) {
            String segment = scanSegment(lexer);
            if (segment.isEmpty()) {
                throw illegalFieldPath(lexer, line, start);
            }
            path.add(segment);
            if (lexer.peek() != '.') {
                break;
            }
            lexer.next();
        }
        char next = lexer.peek();
        if (!lexer.eof() && !Character.isWhitespace(next) && next != '/' && next != ']') {
            throw illegalFieldPath(lexer, line, start);         // 路径里混进了 `-` 之类的字符
        }
        return List.copyOf(path);
    }

    private static PublishException illegalFieldPath(TemplateLexer lexer, int line, int start) {
        String seen = lexer.excerptAhead(start, 24);
        if (seen.isEmpty()) {
            return lexer.error(PublishErrorCode.E1001, FIELD_OPEN + " 后面没有字段路径", line,
                    "第 " + line + " 行的 " + FIELD_OPEN + " 后面就是文件结尾",
                    "补上字段路径，如 [field:title/]、[field:images.0.url/]；要输出字面量请写 \\[field:（§3.4）");
        }
        return lexer.error(PublishErrorCode.E1001, "字段路径不合法", line,
                "第 " + line + " 行的 " + FIELD_OPEN + " 后面读到 `" + seen
                        + "`；路径的段是 [A-Za-z_][A-Za-z0-9_]* 或数字下标",
                "写成 [field:title/]、[field:images.0.url/]、[field:images.count/] 这样的路径；"
                        + "要输出字面量请写 \\[field:…（§3.4）");
    }

    /** 扫一个路径段（{@code segment = [A-Za-z_][A-Za-z0-9_]* | [0-9]+}）。 */
    private static String scanSegment(TemplateLexer lexer) {
        char c = lexer.peek();
        StringBuilder segment = new StringBuilder();
        if (isDigit(c)) {
            while (isDigit(lexer.peek())) {
                segment.append(lexer.next());
            }
            return segment.toString();
        }
        if (c == '_' || isLetter(c)) {
            segment.append(lexer.next());
            while (isLetter(lexer.peek()) || isDigit(lexer.peek()) || lexer.peek() == '_') {
                segment.append(lexer.next());
            }
        }
        return segment.toString();
    }

    /* ---------------- 配对报错（§4.3） ---------------- */

    /** {@code {/cms:x}} 与栈顶不同名：两个标签各自的行号都要给出来。 */
    private static PublishException mismatch(TemplateLexer lexer, int line, String closeName,
                                             Frame top, Deque<Frame> stack) {
        String advice = "把第 " + line + " 行改成 {/cms:" + top.name + "}，或删掉第 " + top.line
                + " 行多写的 {cms:" + top.name + "}";
        for (Frame frame : stack) {                 // 从头（最内层）往外找：它是不是里面某个标签的结束标签
            if (closeName.equals(frame.name)) {
                advice = "第 " + frame.line + " 行的 {cms:" + closeName + "} 在它里面：先写 {/cms:"
                        + top.name + "} 把 " + top.name + " 闭合";
                break;
            }
        }
        return lexer.error(PublishErrorCode.E1003,
                "{/cms:" + closeName + "} 与最近的 {cms:" + top.name + "} 不匹配", line,
                "第 " + top.line + " 行的 {cms:" + top.name + "} 还没有闭合，第 " + line
                        + " 行写的是 {/cms:" + closeName + "}",
                advice);
    }

    /** 扫描结束栈非空：每个未闭合标签的行号都要给出来。 */
    private static PublishException unclosed(TemplateLexer lexer, Deque<Frame> stack) {
        List<Frame> open = new ArrayList<>(stack);  // 头部是最内层，尾部是根
        open.remove(open.size() - 1);               // 丢掉根帧
        StringBuilder list = new StringBuilder();
        for (int i = open.size() - 1; i >= 0; i--) { // 从最外层往里列，跟模板的阅读顺序一致
            if (list.length() > 0) {
                list.append('、');
            }
            list.append("第 ").append(open.get(i).line).append(" 行的 {cms:").append(open.get(i).name).append('}');
        }
        Frame outermost = open.get(open.size() - 1);
        return lexer.error(PublishErrorCode.E1003, "有 " + open.size() + " 个块标签没有闭合", outermost.line,
                "未闭合的是「" + list + "」",
                "给每个块标签补上对应的 {/cms:名字}；自闭合写法 {cms:名字/} 不需要块结束（§4.3）");
    }

    /* ---------------- 小工具 ---------------- */

    private static void flushText(Frame frame, StringBuilder text, int textLine, String templatePath) {
        if (text.length() > 0) {
            frame.body.add(new TextNode(text.toString(), textLine, templatePath));
            text.setLength(0);
        }
    }

    private static boolean isTagNameStart(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isTagNamePart(char c) {
        return isTagNameStart(c) || isDigit(c) || c == '_';
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** 一个还没闭合的块标签；根帧的 {@code name} 为 null。 */
    private static final class Frame {

        private final String name;
        private final Map<String, String> args;
        private final int line;
        private final List<Node> body = new ArrayList<>();

        private Frame(String name, Map<String, String> args, int line) {
            this.name = name;
            this.args = args;
            this.line = line;
        }
    }
}
