package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;

/**
 * 模板的字符游标（static-publish.md §3.1、§3.4、§4.2）。
 *
 * <p>三种词法元素（块标签 / 自闭合标签 / 字段引用）共用这一个游标，**单次线性扫描、不开正则**
 * （§4.2）：{@link TemplateParser} 负责结构与配对，{@link ParamParser} 负责参数区，
 * 两者都只通过本类前进与取字。游标只往前，因此"当前行号"永远等于已消费的 {@code \n} 个数 + 1。
 *
 * <p>本类只做两件有副作用的事：推进 {@code pos}（并对 {@code \n} 计行）、拼报错（§10.1 的三行格式）。
 */
public final class TemplateLexer {

    private final String source;
    private final String templatePath;
    private int pos;
    private int line = 1;

    /**
     * @param source       模板源码（§4.5 第 17 条已校验过编码）
     * @param templatePath 模板相对路径，进报错位置；为 null 时报错不带路径
     */
    public TemplateLexer(String source, String templatePath) {
        this.source = source;
        this.templatePath = templatePath;
    }

    public String source() {
        return source;
    }

    public String templatePath() {
        return templatePath;
    }

    public int pos() {
        return pos;
    }

    /** 当前位置的行号，从 1 开始（§4.2：对 {@code \n} 计数）。 */
    public int line() {
        return line;
    }

    public boolean eof() {
        return pos >= source.length();
    }

    /** 当前字符；已到结尾返回 {@code '\0'}（因此所有判断都不必先查 {@link #eof()}）。 */
    public char peek() {
        return peek(0);
    }

    /** 从当前位置往前数 offset 个字符；越界返回 {@code '\0'}。 */
    public char peek(int offset) {
        int at = pos + offset;
        return at >= 0 && at < source.length() ? source.charAt(at) : '\0';
    }

    /** 当前位置起是否正好是 {@code text}。 */
    public boolean at(String text) {
        return source.startsWith(text, pos);
    }

    /** 从当前位置起 offset 处是否正好是 {@code text}（§3.4 的转义要用：反斜杠后面紧跟起始序列）。 */
    public boolean at(int offset, String text) {
        return source.startsWith(text, pos + offset);
    }

    /** 取一个字符并前进（{@code \n} 计行）。 */
    public char next() {
        char c = source.charAt(pos++);
        if (c == '\n') {
            line++;
        }
        return c;
    }

    /** 前进 n 个字符（{@code \n} 计行）。 */
    public void advance(int n) {
        int end = Math.min(source.length(), pos + Math.max(n, 0));
        for (int i = pos; i < end; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        pos = end;
    }

    /** 前进 n 个字符并返回吃掉的原文（§3.4 的转义要把起始序列原样留在文本里）。 */
    public String take(int n) {
        int start = pos;
        advance(n);
        return source.substring(start, pos);
    }

    /** 跳过空白（参数之间以空白分隔且允许跨行，§3.3）。 */
    public void skipWhitespace() {
        while (!eof() && Character.isWhitespace(peek())) {
            next();
        }
    }

    /**
     * 报错文案用（§10.1："把实际值原样贴出来"）：把 {@code [from, to)} 这段原文贴出来。
     * 换行与制表转义成 {@code \n} / {@code \t}，否则一条报错会撑破三行格式。
     */
    public String excerpt(int from, int to) {
        int start = Math.max(0, Math.min(from, source.length()));
        int end = Math.max(start, Math.min(to, source.length()));
        return source.substring(start, end)
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** 从 from 起往后最多 max 个字符；后面还有内容时以 {@code …} 结尾。 */
    public String excerptAhead(int from, int max) {
        int end = Math.min(source.length(), from + max);
        String text = excerpt(from, end);
        return end < source.length() ? text + "…" : text;
    }

    /** 构造一条带本模板路径与行号的报错（§10.1）。 */
    public PublishException error(PublishErrorCode code, String what, int lineNo, String actual, String advice) {
        return PublishException.error(code, what, templatePath, lineNo, actual, advice);
    }
}
