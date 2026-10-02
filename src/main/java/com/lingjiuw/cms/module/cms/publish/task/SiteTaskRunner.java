package com.lingjiuw.cms.module.cms.publish.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 站点遍历器：定时任务跑在没有 HTTP 请求的后台线程上，{@link SiteContext} 在这种线程里是空的
 * （它只由 {@code SiteInterceptor} 在请求内写入），所以每次执行都要按 {@code cms_site} 列表
 * 逐站点显式设置上下文，执行完恢复原值。见 static-publish.md §8.8 / §12.2（期 0）。
 *
 * <p>期 7 的五个定时任务在自己的方法上加 {@code @Scheduled}，方法体里调
 * {@link #runForEachSite(Consumer)} 即可；{@code @EnableScheduling} 放在这个类上是为了不动
 * {@code config/} 下的既有文件。
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class SiteTaskRunner {

    /** {@code cms_site.status} 的"启用"取值。 */
    private static final int ENABLED_STATUS = 1;

    private final CmsSiteMapper siteMapper;

    /**
     * 遍历全部启用站点：逐站点设置 {@link SiteContext} 后执行 {@code action}。
     *
     * <p>单个站点抛 {@link RuntimeException} 只记日志、不影响其余站点（{@code Error} 照旧向上抛——
     * 那类失败继续跑剩下的站点只会更糟）。每个站点执行完都在 {@code finally} 里**恢复进入本方法前
     * 的上下文**：后台线程上原值为空，等价于清掉；万一以后由请求线程调用，也不会把该请求已有的
     * 站点上下文一起抹掉。
     */
    public void runForEachSite(Consumer<Long> action) {
        Long previous = currentSiteOrNull();
        try {
            for (Long siteId : siteMapper.selectList(Wrappers.<CmsSite>lambdaQuery()
                            .select(CmsSite::getId)
                            .eq(CmsSite::getStatus, ENABLED_STATUS)
                            .orderByAsc(CmsSite::getId)).stream()
                    .map(CmsSite::getId)
                    .toList()) {
                SiteContext.set(siteId);
                try {
                    action.accept(siteId);
                } catch (RuntimeException e) {
                    log.error("站点任务执行失败: siteId={}", siteId, e);
                } finally {
                    SiteContext.clear();
                }
            }
        } finally {
            if (previous == null) {
                SiteContext.clear();
            } else {
                SiteContext.set(previous);
            }
        }
    }

    /** 当前线程的站点 id；没有上下文时返回 null（{@code SiteContext.siteId()} 在缺失时会抛）。 */
    private static Long currentSiteOrNull() {
        try {
            return SiteContext.siteId();
        } catch (BizException e) {
            return null;
        }
    }
}
