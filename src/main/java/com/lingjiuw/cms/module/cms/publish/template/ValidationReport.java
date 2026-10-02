package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验过程中收集到的**非致命结果**：多条错误 + 警告。
 *
 * <p><b>为什么错误也要进这里</b>：§10.3 第 2 条要求"编译期把同一模板的**全部**错误一次返回
 * （上限 20 条）"。一个校验器遍历几十个 `[field:x/]` 时，如果每发现一个错就 {@code throw}，
 * 那么"12 个字段名都写错"只能报出 1 条——"改一处、跑一次"就变成了十几轮。
 * 因此校验器有**两种**报错方式，按场景选：
 * <ul>
 *   <li>只可能有一个错（例如标签名不存在、结构不配对）→ 直接 {@code throw PublishException}；</li>
 *   <li>会遍历出多个错（字段名、参数、引用、筛选字段…）→ 用 {@link #error(PublishException)}
 *       逐条记下来，继续遍历，由编译器统一汇总。</li>
 * </ul>
 * 编译器把"记下来的错误"与"抛出来的错误"合并后按 §10.3 第 2 条截断：
 * 1 条抛裸 {@code PublishException}，≥2 条抛 {@code PublishErrors}。
 *
 * <p>警告（§10.2 的 W5xxx）不阻断发布，只进批次报告——"报错优于静默"是本设计的底线
 * （§1.3、§10.3），警告只用于"主题包被多个站点共用"这类**不该拦下发布**的情况（W5001）。
 */
public final class ValidationReport {

    /** 一条警告。 */
    public record Warning(PublishErrorCode code, String message) {
    }

    private final List<PublishException> errors = new ArrayList<>();
    private final List<Warning> warnings = new ArrayList<>();

    /** 记一条错误（不抛，继续遍历；由编译器汇总并按上限截断）。 */
    public void error(PublishException error) {
        if (error != null) {
            errors.add(error);
        }
    }

    /** 记多条错误。 */
    public void errors(List<PublishException> list) {
        if (list != null) {
            list.forEach(this::error);
        }
    }

    /** 记一条警告。与 {@link #error(PublishException)} 同一口径地过滤 null，避免警告列表里出现脏数据。 */
    public void warn(PublishErrorCode code, String message) {
        if (code != null) {
            warnings.add(new Warning(code, message));
        }
    }

    public List<PublishException> errors() {
        return List.copyOf(errors);
    }

    public List<Warning> warnings() {
        return List.copyOf(warnings);
    }

    public boolean empty() {
        return errors.isEmpty() && warnings.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
