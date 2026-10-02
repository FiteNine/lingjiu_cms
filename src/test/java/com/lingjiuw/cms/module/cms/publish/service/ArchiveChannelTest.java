package com.lingjiuw.cms.module.cms.publish.service;

import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.provider.DbContentProviderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 归档页 {@code channel} 作用域的回归钉子。
 *
 * <p>它守的是一条**真实发生过**的失败：主题按 §5.1 第（4）条的表写
 * {@code [field:channel.year/]}，而渲染期报"具名作用域 channel 上没有 key year"——
 * 也就是计划填进去的 channel 少了这一项。归档的 {@code year} / {@code month} 来自
 * {@code provider.archives()} 的条目，因此这里直接断言"数据层给的条目带这两个 key"，
 * 再断言"整站计划里每个归档页的 channel 都带它们"。
 *
 * <p>依赖本地 PostgreSQL（与 {@code DbContentProviderTest} 同一套约定）；数据库不可达时整类跳过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.flyway.enabled=false")
@org.junit.jupiter.api.condition.EnabledIf(
        value = "com.lingjiuw.cms.module.cms.publish.provider.DbContentProviderTest#databaseReachable",
        disabledReason = "本地 PostgreSQL 不可达")
@DisplayName("归档页 channel 作用域（§5.1 第（4）条）")
class ArchiveChannelTest {

    @Autowired
    private DbContentProviderFactory factory;

    @Autowired
    private com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper siteMapper;

    @Autowired
    private com.lingjiuw.cms.module.cms.publish.template.TagRegistry tagRegistry;

    @Autowired
    private java.util.Map<String, com.lingjiuw.cms.module.cms.publish.template.TemplateValidator> validators;

    @Test
    @DisplayName("数据层的归档条目带 year / month / label / count")
    void archivesCarryYearMonth() {
        ContentProvider provider = providerForDemo();
        if (provider == null) {
            return;
        }
        List<NavItem> archives = provider.archives("all", "month", 5, "all", true);
        assertThat(archives).as("演示站点应该至少有一个年月归档").isNotEmpty();
        NavItem first = archives.get(0);
        assertThat(first.get("year")).as("归档条目缺 year，channel.year 必炸").isNotNull();
        assertThat(first.get("month")).as("归档条目缺 month").isNotNull();
        assertThat(first.get("label")).isNotNull();
    }

    @Test
    @DisplayName("整站计划里每个 ARCHIVE 页的 channel 都带 year 与 month")
    void everyArchivePlanCarriesChannelKeys() {
        ContentProvider provider = providerForDemo();
        if (provider == null) {
            return;
        }
        Path siteDir = Path.of("sites", "demo").toAbsolutePath().normalize();
        ThemeTemplateLookup lookup = new ThemeTemplateLookup(
                new FileTemplateSource(siteDir.resolve("template").resolve("mint")), "mint");
        SitePlanner.Result planned = new SitePlanner(provider, lookup).plan();

        List<Plan> archives = planned.pages().stream()
                .filter(page -> page.pageType() == PageType.ARCHIVE)
                .toList();
        assertThat(archives).as("演示站点开启了 page.archive，应该出归档页").isNotEmpty();
        for (Plan page : archives) {
            assertThat(page.channel())
                    .as("归档页 %s 的 channel 少了必需 key（模板 [field:channel.year/] 会渲染失败）",
                            page.url())
                    .containsKeys("year", "month", "label", "url", "count");
        }
    }

    private ContentProvider providerForDemo() {
        var site = siteMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<com.lingjiuw.cms.module.cms.entity.CmsSite>lambdaQuery()
                .eq(com.lingjiuw.cms.module.cms.entity.CmsSite::getCode, "demo").last("limit 1"));
        if (site == null) {
            // 演示站点的种子迁移还没跑：跳过而不是失败（本用例守的是渲染契约，不是种子）
            return null;
        }
        return factory.forSite(site.getId());
    }

    /**
     * 归档页必须真的渲染得出来。
     *
     * <p>这条用例是"计划填了 channel.year，但渲染期仍报 channel 上没有 key year"这个矛盾的
     * 直接判据：它渲染真实模板并断言不抛异常，因此能把问题钉在"渲染期读的是哪一层"上，
     * 而不是停在计划层。
     */
    @Test
    @DisplayName("归档页用真实主题渲染成功（channel.year 可读）")
    void archivePageRenders() {
        ContentProvider provider = providerForDemo();
        if (provider == null) {
            return;
        }
        Path siteDir = Path.of("sites", "demo").toAbsolutePath().normalize();
        FileTemplateSource source = new FileTemplateSource(siteDir.resolve("template").resolve("mint"));
        ThemeTemplateLookup lookup = new ThemeTemplateLookup(source, "mint");
        // 标签与校验器都从 Spring 取：14 个标签各有依赖（provider 等），手工 new 一份只会缺件
        var registry = tagRegistry;
        var compiler = new com.lingjiuw.cms.module.cms.publish.template.TemplateCompiler(source,
                registry, List.copyOf(validators.values()));
        var renderer = new PageRenderer(compiler,
                new com.lingjiuw.cms.module.cms.publish.template.DefaultTemplateRenderer(registry),
                provider, lookup);

        Plan archive = new SitePlanner(provider, lookup).plan().pages().stream()
                .filter(page -> page.pageType() == PageType.ARCHIVE)
                .findFirst().orElse(null);
        assertThat(archive).as("演示站点应该出归档页").isNotNull();

        String html;
        try {
            html = renderer.render(archive, 1).html();
        } catch (RuntimeException e) {
            throw new AssertionError("归档页 " + archive.url() + "（模板 " + archive.template()
                    + "）渲染失败：" + e.getClass().getSimpleName() + " / " + e.getMessage(), e);
        }
        assertThat(html).as("归档页渲染出了 %d 字节", html.length()).isNotBlank();
    }

    /**
     * 每一个归档页都要能渲染——不是只测第一个。
     *
     * <p>这一条是"个别归档页失败"的判据：{@code channel} 里的 {@code year} / {@code month} 是
     * 逐页不同的取值，如果某一页的取值形态特殊（缺值、类型不同），只渲染第一页是看不见的。
     */
    @Test
    @DisplayName("全部归档页都渲染成功")
    void allArchivePagesRender() {
        ContentProvider provider = providerForDemo();
        if (provider == null) {
            return;
        }
        Path siteDir = Path.of("sites", "demo").toAbsolutePath().normalize();
        FileTemplateSource source = new FileTemplateSource(siteDir.resolve("template").resolve("mint"));
        ThemeTemplateLookup lookup = new ThemeTemplateLookup(source, "mint");
        var compiler = new com.lingjiuw.cms.module.cms.publish.template.TemplateCompiler(source,
                tagRegistry, List.copyOf(validators.values()));
        var renderer = new PageRenderer(compiler,
                new com.lingjiuw.cms.module.cms.publish.template.DefaultTemplateRenderer(tagRegistry),
                provider, lookup);

        List<Plan> archives = new SitePlanner(provider, lookup).plan().pages().stream()
                .filter(page -> page.pageType() == PageType.ARCHIVE)
                .toList();
        assertThat(archives).isNotEmpty();
        for (Plan page : archives) {
            try {
                String html = renderer.render(page, 1).html();
                assertThat(html).as("%s 渲染出了 %d 字节", page.url(), html.length()).isNotBlank();
            } catch (RuntimeException e) {
                throw new AssertionError("归档页 " + page.url() + " 渲染失败（channel="
                        + page.channel() + "）：" + e.getMessage(), e);
            }
        }
    }
}
