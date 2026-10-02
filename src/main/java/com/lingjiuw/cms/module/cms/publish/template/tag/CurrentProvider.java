package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;

/**
 * 正在渲染的那个页面的取数出口（static-publish.md §4.1）。
 *
 * <p><b>为什么需要它</b>：标签由 Spring 单例持有，而"取数出口"是按站点造的实例。
 * 直接注入 {@code ContentProvider} 只能拿到**请求作用域的代理**，而发布引擎在线程池里渲染页面
 * （没有请求），代理一被调用就抛：
 * <pre>
 * ScopeNotActiveException: Scope 'request' is not active for the current thread
 * </pre>
 * 于是"计划 70 页 / 失败 70 页"，报错还看不出与发布有关。
 *
 * <p>做法：页面计划**显式**把本页的出口挂在这里，标签优先用它（{@link #current}）；
 * 没有人在渲染时（请求内的预览、模板体检）退回 Spring 注入的那个代理。
 * 一次渲染全部发生在同一个线程上（一页一个线程、一页一个上下文），因此这里存的是
 * "本线程正在渲染哪一页的出口"，作用域与 {@code RenderContext} 一致。
 *
 * <p><b>如实记录的取舍</b>：更"正统"的做法是把出口塞进 {@code RenderContext}（它本来就是
 * 一次渲染的全部状态），但那要改 14 个标签的构造与取数调用点；而这里只有一处写入、一处读取。
 * 真正的约束是"**页面计划是唯一知道该用哪个出口的地方**"，这一点两种做法都满足。
 */
public final class CurrentProvider {

    private static final ThreadLocal<ContentProvider> CURRENT = new ThreadLocal<>();

    private CurrentProvider() {
    }

    /** 页面计划在渲染一页之前调用；用完必须在 finally 里 {@link #clear()}。 */
    public static void set(ContentProvider provider) {
        if (provider == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(provider);
        }
    }

    /** 本线程正在用的出口；没有设置时返回 null（调用方退回 Spring 注入的那个）。 */
    public static ContentProvider current() {
        return CURRENT.get();
    }

    /** 清掉本线程的出口（线程会被复用，不清会串到下一页）。 */
    public static void clear() {
        CURRENT.remove();
    }
}
