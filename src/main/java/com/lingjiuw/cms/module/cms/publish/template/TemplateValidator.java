package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

/**
 * 一条编译期校验（static-publish.md §4.5 的 18 条）。
 *
 * <p>拆成独立实现类而不是写成一个巨型方法，理由只有一个：**§12.3 第一批测试就是
 * 为这 18 条里的 12 条★写的表驱动用例**，一条校验一个类，用例与实现一一对应，
 * 报错文案也能各自打磨（§10.3 要求列清单、贴实际值、猜笔误）。
 *
 * <p>校验**在 include 展开之后**跑（§5.3："位置类校验只在展开后的模板上做一次"），
 * 因此实现者可以假定 {@code ast} 是一棵完整展开的树。
 *
 * <p>报错有**两种并列**的方式（见 {@link ValidationReport} 的类注释）：
 * 只可能有一个错时直接抛 {@code PublishException}；会遍历出多个错（字段名 / 参数 / 引用 /
 * 筛选字段 / 表单 code 等）时用 {@link ValidationReport#error(PublishException)} 逐条记录后
 * 继续遍历，由编译器统一汇总——§10.3 第 2 条要的是"同一模板的全部错误一次返回"。
 * 警告写进 {@link ValidationReport}（§10.2 的 W5xxx）。
 */
public interface TemplateValidator {

    /**
     * 校验序号（对应 §4.5 表格的第几行），也决定执行顺序，小的先跑。
     *
     * <p><b>要求全局唯一</b>：编译器按本值升序排序后依次执行，两个实现返回同一个序号时执行
     * 次序会退化成注册顺序，报错顺序不再可复现。
     *
     * <p><b>注意 {@code order() == 1} 是编译器的 ABORT_ORDER</b>：这一条抛出的
     * {@code PublishException} 会**立即中断**，不再汇总其余校验（理由见 {@code TemplateCompiler}
     * 的常量注释：标签名不认识时后续校验全是级联噪音）。其余序号只累积不中断。
     */
    int order();

    /** 这条校验管什么，用于日志与失败信息。 */
    String describe();

    /** 默认对所有页面类型适用（例如块配对、字段名）。 */
    default boolean applies(CompileContext ctx) {
        return true;
    }

    /**
     * 执行校验。
     *
     * @param ast    展开 include 之后的完整节点树
     * @param ctx    编译上下文（可用的字段集合、页面类型、类型定义都在这里）
     * @param report 错误与警告的收集处；只可能有一个错时也可以直接抛 {@code PublishException}
     */
    void validate(TemplateAst ast, CompileContext ctx, ValidationReport report);
}
