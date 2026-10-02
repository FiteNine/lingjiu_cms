package com.lingjiuw.cms.module.cms.publish.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.provider.DbContentProviderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 演示站点整站发布必须零失败的回归钉子。
 *
 * <p>它把"命令行的 {@code SitePublishCli} 跑一遍"变成一条可复跑的用例——两者走同一条链
 * （{@link PublishFacade} 与它背后的 {@link SitePublishService}），因此任何"只在真实发布路径上
 * 出现"的缺陷都会在这里暴露，而不是等到手工跑 CLI 才发现。
 *
 * <p>依赖本地 PostgreSQL 与 {@code sites/demo} 已播种；数据库不可达时整类跳过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.flyway.enabled=false")
@org.junit.jupiter.api.condition.EnabledIf(
        value = "com.lingjiuw.cms.module.cms.publish.provider.DbContentProviderTest#databaseReachable",
        disabledReason = "本地 PostgreSQL 不可达")
@DisplayName("演示站点：整站静态化零失败")
class DemoPublishTest {

    @Autowired
    private DbContentProviderFactory factory;

    @Autowired
    private CmsSiteMapper siteMapper;

    @Test
    @DisplayName("sites/demo + mint 主题：计划全部页面、失败 0、聚合产物齐全")
    void publishesDemoSiteWithoutFailures() {
        CmsSite site = siteMapper.selectOne(Wrappers.<CmsSite>lambdaQuery()
                .eq(CmsSite::getCode, "demo").last("limit 1"));
        assertThat(site).as("演示站点的种子迁移没跑：先执行 V20261003090001__demo_site_seed.sql").isNotNull();

        ContentProvider provider = factory.forSite(site.getId());
        Path siteDir = Path.of("sites", "demo").toAbsolutePath().normalize();
        SitePublishService service = publishService();

        SitePublishService.PublishResult result = service.publish(site.getId(), siteDir, provider,
                SitePublishService.Mode.full, "manual", false);

        assertThat(result.errors())
                .as("整站发布不允许有任何失败页（%d 页失败）：%s", result.failedPages(), result.errors())
                .isEmpty();
        assertThat(result.failedPages()).isZero();
        assertThat(result.writtenPages())
                .as("写出 0 页说明计划是空的或每页都走了跳过分支").isPositive();
        assertThat(result.aggregateArtifacts())
                .as("聚合产物必须齐全（§7.2.2：sitemap / feed / robots / 搜索索引 / cms-site.json）")
                .contains("sitemap.xml", "feed.xml", "cms-site.json");
    }

    @Autowired
    private SitePublishService injectedService;

    private SitePublishService publishService() {
        return injectedService;
    }
}
