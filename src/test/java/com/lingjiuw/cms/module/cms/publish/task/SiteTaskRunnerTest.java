package com.lingjiuw.cms.module.cms.publish.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 期 0 的验收判据：定时任务在没有 HTTP 请求时也能取到站点，并且执行完不泄漏 ThreadLocal
 * （static-publish.md §12.2）。这里不起 Spring 上下文、不连数据库：站点列表由假 mapper 提供，
 * 恰好也就是"没有请求、没有拦截器"的现场。
 */
class SiteTaskRunnerTest {

    private final CmsSiteMapper siteMapper = mock(CmsSiteMapper.class);
    private final SiteTaskRunner runner = new SiteTaskRunner(siteMapper);

    /** 纯单测里没有 MyBatis 运行时，LambdaQueryWrapper 取列名要靠这份 TableInfo 缓存 */
    @BeforeAll
    static void 初始化实体元数据() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), CmsSite.class);
    }

    @AfterEach
    void 清理上下文() {
        SiteContext.clear();
    }

    @Test
    void 无请求时按站点列表逐个设置上下文() {
        when(siteMapper.selectList(any())).thenReturn(List.of(site(1L), site(7L)));

        // 现场就是后台线程：没有请求、没有 SiteInterceptor，站点上下文是空的
        assertThrows(BizException.class, SiteContext::siteId);

        List<Long> visited = new ArrayList<>();
        List<Long> contextInCallback = new ArrayList<>();
        runner.runForEachSite(siteId -> {
            visited.add(siteId);
            contextInCallback.add(SiteContext.siteId());
        });

        assertEquals(List.of(1L, 7L), visited);
        assertEquals(List.of(1L, 7L), contextInCallback);
        verify(siteMapper).selectList(any());
        // 执行完必须清掉：线程会被复用，下一个任务不能读到上一个站点的上下文
        assertThrows(BizException.class, SiteContext::siteId);
    }

    @Test
    void 没有启用站点时什么都不做() {
        when(siteMapper.selectList(any())).thenReturn(List.of());

        runner.runForEachSite(siteId -> {
            throw new IllegalStateException("不应该被调用：" + siteId);
        });

        assertThrows(BizException.class, SiteContext::siteId);
    }

    @Test
    void 单个站点失败不影响其余站点且上下文仍被清理() {
        when(siteMapper.selectList(any())).thenReturn(List.of(site(1L), site(2L), site(3L)));

        List<Long> visited = new ArrayList<>();
        runner.runForEachSite(siteId -> {
            visited.add(siteId);
            if (siteId == 2L) {
                throw new IllegalStateException("这个站点的任务炸了");
            }
        });

        assertEquals(List.of(1L, 2L, 3L), visited);
        assertThrows(BizException.class, SiteContext::siteId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 只取启用站点并按id升序() {
        when(siteMapper.selectList(any())).thenReturn(List.of());

        runner.runForEachSite(siteId -> {
        });

        ArgumentCaptor<Wrapper<CmsSite>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(siteMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().toUpperCase(Locale.ROOT);
        assertTrue(sql.contains("STATUS"), sql);
        assertTrue(sql.contains("ORDER BY"), sql);
    }

    /** {@code @EnableScheduling} 必须随遍历器一起进容器，否则期 7 加 @Scheduled 也不会被调度 */
    @Test
    void 启用调度的开关随遍历器一起生效() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(CmsSiteMapper.class, () -> siteMapper);
            ctx.register(SiteTaskRunner.class);
            ctx.refresh();

            assertEquals(1, ctx.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class).length);
        }
    }

    private static CmsSite site(Long id) {
        CmsSite site = new CmsSite();
        site.setId(id);
        site.setStatus(1);
        return site;
    }
}
