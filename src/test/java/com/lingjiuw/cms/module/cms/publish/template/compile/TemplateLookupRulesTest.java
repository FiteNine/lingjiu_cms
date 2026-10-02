package com.lingjiuw.cms.module.cms.publish.template.compile;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;
import com.lingjiuw.cms.module.cms.publish.template.validate.TemplateLookupRules;
import com.lingjiuw.cms.module.cms.publish.template.validate.TemplateLookupRules.LookupKind;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4.5 第 11 条 ★：模板查找结果是否存在 → E4002（§7.3 的候选顺序与三条口径）。
 *
 * <p>期 1 只到**判定函数**这一层：存在性由 {@code Predicate} 注入，所以这里不需要磁盘。
 */
class TemplateLookupRulesTest {

    record Case(String name, LookupKind kind, String typeCode, Set<String> existing,
                String expected, PublishErrorCode code, String... keywords) {
    }

    /** 带 {@code detail_template} / {@code list_template} 的类型定义：它们**优先于**主题默认规则（§7.3 第（1）条）。 */
    private static final ContentTypeDef PRODUCT = new ContentTypeDef(1L, "product", "产品",
            ContentTypeDef.Kind.CONTENT, false, "/product/{slug}.html", "/product/",
            "custom_detail.html", "custom_list.html", null, "publishTime", "desc", 20, null, null,
            List.of(), Map.of());

    static Stream<Case> 用例() {
        return Stream.of(
                new Case("兜底 list.html 命中", LookupKind.TYPE_LIST, "news", Set.of("list.html"),
                        "list.html", null),
                new Case("类型专属模板优先", LookupKind.TYPE_LIST, "news",
                        Set.of("news_list.html", "list.html"), "news_list.html", null),
                new Case("类型定义里的 list_template 最优先", LookupKind.TYPE_LIST, "product",
                        Set.of("custom_list.html", "product_list.html", "list.html"), "custom_list.html",
                        null),
                new Case("分类索引页", LookupKind.CATEGORY_LIST, null,
                        Set.of("category_list.html", "list.html"), "category_list.html", null),
                new Case("标签页回退顺序", LookupKind.TAGPAGE, "article",
                        Set.of("article_list.html", "list.html"), "article_list.html", null),
                new Case("标签总览页", LookupKind.TAGLIST, null, Set.of("tags.html", "list.html"),
                        "tags.html", null),
                new Case("归档页", LookupKind.ARCHIVE, null, Set.of("list.html"), "list.html", null),
                new Case("筛选页", LookupKind.FACET, "product",
                        Set.of("product_list.html", "list.html"), "product_list.html", null),
                new Case("详情页兜底 detail.html", LookupKind.DETAIL, "news", Set.of("detail.html"),
                        "detail.html", null),
                new Case("单页回退 single.html", LookupKind.SINGLE, "about", Set.of("single.html"),
                        "single.html", null),
                new Case("站点声明的静态页", LookupKind.STATIC, null, Set.of("rank.html"), "rank.html",
                        null),
                new Case("全都不存在 → E4002", LookupKind.TYPE_LIST, "news", Set.of(), null,
                        PublishErrorCode.E4002,
                        "页面类型 TYPE_LIST（测试页）找不到模板", "依次找过 news_list.html、list.html",
                        "至少提供 list.html"),
                new Case("详情页全都不存在 → E4002", LookupKind.DETAIL, "news", Set.of(), null,
                        PublishErrorCode.E4002, "依次找过 news_detail.html、detail.html"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("用例")
    void 表驱动(Case item) {
        ContentTypeDef typeDef = "product".equals(item.typeCode()) ? PRODUCT : null;
        if (item.code() == null) {
            String hit = TemplateLookupRules.require(item.kind(), item.typeCode(), typeDef,
                    "rank.html", "测试页", item.existing()::contains);
            assertEquals(item.expected(), hit);
            return;
        }
        PublishException error = org.junit.jupiter.api.Assertions.assertThrows(PublishException.class,
                () -> TemplateLookupRules.require(item.kind(), item.typeCode(), typeDef, "rank.html",
                        "测试页", item.existing()::contains));
        CompileFixture.assertError(error, item.code(), item.keywords());
    }

    /** 三个"有内置兜底"的种类找不到模板不报错（§7.3：搜索页壳 / 默认 404 / 内置 feed 模板）。 */
    @Test
    void 有内置兜底的种类不报错() {
        assertNull(TemplateLookupRules.require(LookupKind.SEARCH, null, null, null, null,
                path -> false));
        assertNull(TemplateLookupRules.require(LookupKind.PAGE404, null, null, null, null,
                path -> false));
        assertNull(TemplateLookupRules.require(LookupKind.FEED, null, null, null, null,
                path -> false));
    }

    /** §7.3 第（1）条：{@code _} 前缀的目录与文件不参与页面查找。 */
    @Test
    void 候选里没有下划线前缀的片段目录() {
        for (LookupKind kind : LookupKind.values()) {
            List<String> candidates = TemplateLookupRules.candidates(kind, "article", PRODUCT, "rank.html");
            assertTrue(candidates.stream().noneMatch(path -> path.startsWith("_")),
                    kind + " 的候选不该含片段目录：" + candidates);
        }
    }

    /** §7.3 第（3）条：站点选项关掉的页面类型不开工（与 W5001 共用一份映射）。 */
    @Test
    void 站点选项关掉时不开工() {
        SiteConfig off = site(Map.of("page.archive", 0, "page.tag", 0));
        assertTrue(TemplateLookupRules.disabledBySiteOptions(PageType.ARCHIVE, null, off));
        assertTrue(TemplateLookupRules.disabledBySiteOptions(PageType.TAGPAGE, null, off));
        // LIST 的两种来源编译期分不清，因此不映射到 page.category（不猜）
        assertTrue(!TemplateLookupRules.disabledBySiteOptions(PageType.LIST, "article",
                site(Map.of("page.category", 0))));
    }

    private static SiteConfig site(Map<String, Object> options) {
        return new SiteConfig(1L, "demo", "示例站", "example.com", "https", null, "zh-CN", null,
                null, null, null, null, null, "/sites/demo", null, null, "default", null, null,
                options);
    }
}
