package com.lingjiuw.cms.module.cms.publish.error;

import com.lingjiuw.cms.common.exception.BizException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多条报错的载体（static-publish.md §10.3 第 2 条："编译期把同一模板的**全部**错误一次返回
 * （上限 20 条，超出提示『还有 N 条』）。逐条报错会让『改一处、跑一次』变成几十轮"）。
 *
 * <p><b>为什么不继承 {@link PublishException}</b>：{@code PublishException} 有单一错误码，
 * 而这里是多个码的集合；硬套一个码会让 §10.4 的"按错误码分组"统计失真。
 * 因此两者是并列的，调用方按 {@code BizException} 捕获即可（后台的统一异常响应就是这条路径）。
 *
 * <p><b>一条写死的约定</b>：编译器在只收集到 **1 条**错误时抛裸的 {@link PublishException}，
 * **≥2 条**时才抛本类。这样"只有一个错"的用例与断言不受影响，而"多个错"的场景才出现新类型——
 * 否则每个已有用例都要改成"从集合里取第一条"。
 */
public class PublishErrors extends BizException {

    /** §10.3 第 2 条的上限：列出前 20 条，其余只报条数。 */
    public static final int MAX_REPORTED = 20;

    private final List<PublishException> errors;

    private PublishErrors(List<PublishException> errors) {
        super(1, format(errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * 汇总若干条报错。
     *
     * @param errors 按发现顺序排列；调用方负责去重与排序（通常按校验器 {@code order()}）
     * @throws IllegalArgumentException 一条都没有——那说明调用方判断错了，应当抛裸的
     *                                  {@link PublishException} 或直接不抛
     */
    public static PublishErrors of(List<PublishException> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("PublishErrors 至少要有一条错误；单条错误请直接抛 PublishException");
        }
        // format(...) 与 List.copyOf(...) 都在 super(...) 阶段执行，混进一个 null 元素的话
        // 调用方拿到的是裸 NPE，而不是本类约定的 IllegalArgumentException。
        for (PublishException error : errors) {
            if (error == null) {
                throw new IllegalArgumentException("PublishErrors 的错误列表不能包含 null 元素");
            }
        }
        return new PublishErrors(errors);
    }

    /** 全部错误，按发现顺序。 */
    public List<PublishException> errors() {
        return errors;
    }

    public int totalCount() {
        return errors.size();
    }

    /** 是否因为超过上限而截断（此时消息末尾有"还有 N 条"）。 */
    public boolean truncated() {
        return errors.size() > MAX_REPORTED;
    }

    /** 按错误码分组计数，供 §10.4 的"批次报告页按错误码分组（E1004 × 12 处）"使用。 */
    public Map<PublishErrorCode, Integer> countByCode() {
        Map<PublishErrorCode, Integer> counts = new LinkedHashMap<>();
        for (PublishException error : errors) {
            counts.merge(error.code(), 1, Integer::sum);
        }
        return counts;
    }

    private static String format(List<PublishException> errors) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(errors.size(), MAX_REPORTED);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append('(').append(i + 1).append('/').append(errors.size()).append(") ")
                    .append(errors.get(i).getMessage());
        }
        if (errors.size() > MAX_REPORTED) {
            sb.append("\n\n（本次共 ").append(errors.size()).append(" 条错误，上面列出前 ")
                    .append(MAX_REPORTED).append(" 条，还有 ")
                    .append(errors.size() - MAX_REPORTED).append(" 条）");
        }
        return sb.toString();
    }

    /** 便捷方法：把若干条报错收成一个列表（跳过 null，便于校验器用累加写法）。 */
    public static List<PublishException> collect(PublishException... errors) {
        List<PublishException> list = new ArrayList<>();
        for (PublishException error : errors) {
            if (error != null) {
                list.add(error);
            }
        }
        return list;
    }
}
