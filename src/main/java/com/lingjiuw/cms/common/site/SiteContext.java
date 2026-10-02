package com.lingjiuw.cms.common.site;

import com.lingjiuw.cms.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;

/**
 * 当前请求的站点上下文。
 *
 * <p>站点 id 由 {@link SiteInterceptor} 在进入 Controller 之前解析并写入，业务层用
 * {@link #siteId()} 取用。内容相关的查询与写入都必须带上它，否则会读到别的站点的数据。
 *
 * <p>与 {@code SecurityUtils} 取当前登录用户是同一套思路：请求级状态放在 ThreadLocal 里，
 * 避免每个 Service 方法都多一个 siteId 入参。
 */
@Slf4j
public final class SiteContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private SiteContext() {
    }

    /**
     * 显式设置当前线程的站点。请求内由 {@link SiteInterceptor} 调用；定时任务等后台线程没有请求可依赖，
     * 必须按 {@code cms_site} 列表逐站点设置（见 {@code SiteTaskRunner}），用完在 finally 里清掉。
     */
    public static void set(Long siteId) {
        CURRENT.set(siteId);
    }

    /** 清掉当前线程的站点上下文；线程会被复用，后台任务执行完必须调 */
    public static void clear() {
        CURRENT.remove();
    }

    /** 当前站点 id，由拦截器保证一定有值 */
    public static Long siteId() {
        Long siteId = CURRENT.get();
        if (siteId == null) {
            // 安全边界：站点缺失的两种根因（拦截器没生效 / 后台线程没 set）只能靠日志区分
            log.warn("缺少站点上下文：thread={}", Thread.currentThread().getName());
            throw new BizException("缺少站点上下文，请刷新页面后重试");
        }
        return siteId;
    }
}
