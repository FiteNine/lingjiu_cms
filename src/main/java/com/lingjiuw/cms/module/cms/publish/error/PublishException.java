package com.lingjiuw.cms.module.cms.publish.error;

import com.lingjiuw.cms.common.exception.BizException;

import java.util.List;

/**
 * 发布引擎的报错，文案严格按 static-publish.md §10.1 的三行格式：
 *
 * <pre>
 * [E1001] 未知标签 {cms:arclist} · news/list.html:18
 *   → 现状：引擎不认识 arclist
 *   → 建议：可用标签见 §6.1；你是不是想写 {cms:list}？
 * </pre>
 *
 * <p>§10.3 的三条硬要求：能列清单就列清单、把实际值原样贴出来、能猜笔误就猜。
 * 因此 {@code actual} 与 {@code advice} 都是**必填**（可以为空串，但调用方要想清楚为什么）；
 * 传 {@code null} 会被当作"这一行不输出"——§10.1 的"→ 现状"是硬要求，所以只在确实不适用时
 * 才传 null（例如纯路径类错误没有"引擎看到了什么"可贴）。
 *
 * <p>继承 {@link BizException} 是为了沿用后台既有的统一异常响应；错误码本身在
 * {@link #code()} 里，不占用 {@code BizException} 的 int code。
 */
public class PublishException extends BizException {

    private final PublishErrorCode code;
    private final String what;
    private final String templatePath;
    private final int lineNo;
    private final String actual;
    private final String advice;
    private final List<String> includeChain;

    private PublishException(PublishErrorCode code, String what, String templatePath, int lineNo,
                             String actual, String advice, List<String> includeChain) {
        // super(...) 里就会调 format() → code.name() / 拼 what：在入口先校验，
        // 否则构造异常本身会抛 NPE，把真正的发布错误掩盖成"空指针"。
        super(1, format(java.util.Objects.requireNonNull(code, "PublishErrorCode 不能为空"),
                java.util.Objects.requireNonNull(what, "PublishException 的 what 不能为空（§10.1 第一行）"),
                templatePath, lineNo, actual, advice, includeChain));
        this.code = code;
        this.what = what;
        this.templatePath = templatePath;
        this.lineNo = lineNo;
        this.actual = actual;
        this.advice = advice;
        this.includeChain = includeChain == null ? List.of() : List.copyOf(includeChain);
    }

    /**
     * 构造一条模板报错。
     *
     * @param code         错误码，见 §10.2
     * @param what         第一行的"出了什么事"，不要带错误码前缀与位置（本方法会拼）
     * @param templatePath 模板相对路径，可为 null（非模板错误）
     * @param lineNo       起始行号，0 表示无行号
     * @param actual       "→ 现状"，把引擎看到的实际值原样贴出来
     * @param advice       "→ 建议"，能列清单就列清单
     */
    public static PublishException error(PublishErrorCode code, String what, String templatePath,
                                         int lineNo, String actual, String advice) {
        return new PublishException(code, what, templatePath, lineNo, actual, advice, null);
    }

    /** 非模板位置的报错（计划期、路径、接口层）。 */
    public static PublishException error(PublishErrorCode code, String what, String actual, String advice) {
        return new PublishException(code, what, null, 0, actual, advice, null);
    }

    /**
     * 附上 include 包含链（§5.3）：报错位置仍指向片段内的真实行号，并打印"谁包含谁"，
     * 便于从被包含的片段一路找回入口模板。
     *
     * <p>新异常要**继承原异常的堆栈与 cause**：{@code TemplateCompiler} 在多层 include 的
     * catch 里就是 {@code throw e.withIncludeChain(...)}，不继承的话日志里看到的抛出位置会
     * 退化成编译器的 enrich/catch 处，而不是真正出错的那一行（§10"可诊断"）。
     */
    public PublishException withIncludeChain(List<String> chain) {
        if (chain == null || chain.isEmpty()) {
            return this;
        }
        PublishException copy = new PublishException(code, what, templatePath, lineNo, actual, advice,
                chain);
        copy.setStackTrace(getStackTrace());
        if (getCause() != null) {
            copy.initCause(getCause());
        }
        return copy;
    }

    public PublishErrorCode code() {
        return code;
    }

    public String templatePath() {
        return templatePath;
    }

    public int lineNo() {
        return lineNo;
    }

    public String actual() {
        return actual;
    }

    public String advice() {
        return advice;
    }

    public List<String> includeChain() {
        return includeChain;
    }

    private static String format(PublishErrorCode code, String what, String templatePath, int lineNo,
                                 String actual, String advice, List<String> includeChain) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(code.name()).append("] ").append(what);
        if (templatePath != null) {
            sb.append(" · ").append(templatePath);
            if (lineNo > 0) {
                sb.append(':').append(lineNo);
            }
        } else if (lineNo > 0) {
            sb.append(" · 第 ").append(lineNo).append(" 行");
        }
        if (actual != null) {
            sb.append("\n  → 现状：").append(actual);
        }
        if (advice != null) {
            sb.append("\n  → 建议：").append(advice);
        }
        if (includeChain != null && !includeChain.isEmpty()) {
            sb.append("\n  → 包含链：").append(String.join(" → ", includeChain));
        }
        return sb.toString();
    }
}
