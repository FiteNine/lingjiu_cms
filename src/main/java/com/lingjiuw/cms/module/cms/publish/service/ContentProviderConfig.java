package com.lingjiuw.cms.module.cms.publish.service;

import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentQuery;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.FormDef;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.model.SiteConfig;
import com.lingjiuw.cms.module.cms.publish.provider.DbContentProviderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 标签的取数出口装配（static-publish.md §4.1）。
 *
 * <p><b>它解决什么</b>：{@code tag/*.java} 的构造参数是 {@code @Nullable ContentProvider}，
 * 而"某个站点的取数出口"是**按站点**造的实例（{@link DbContentProviderFactory}）。
 * 没有 Bean 时 Spring 注入 {@code null}，一直等到渲染期才在 {@code TagSupport.provider} 里
 * 报 E3012"没有取数出口"。
 *
 * <p><b>它为什么不是一个 {@code @RequestScope} 的 Bean</b>——这是本项目踩过最深的一个坑，
 * 写在这里免得再踩。有两条路都试过、都不通：
 * <ol>
 *   <li><b>直接 {@code @RequestScope} + 作用域代理</b>：发布引擎在线程池里渲染页面，
 *       那里没有请求，代理一被调用就抛
 *       {@code ScopeNotActiveException: Scope 'request' is not active for the current thread}。
 *       结果是"计划 70 页、失败 70 页"，报错文案还看不出与发布引擎有关。</li>
 *   <li><b>在 {@code @Bean} 方法体里判 {@code RequestContextHolder}</b>：没用——
 *       作用域检查发生在调用 {@code @Bean} 方法**之前**，方法体根本执行不到。</li>
 * </ol>
 *
 * <p><b>最终做法</b>：标签注入一个**普通单例**，它在每次调用时自己决定去哪儿取数：
 * <ul>
 *   <li>有请求上下文且有站点（后台预览、模板体检）→ 向工厂要当前站点的出口；</li>
 *   <li>没有请求（发布线程池、定时任务、命令行）→ 用站点 id 0 的空出口。
 *       {@code [field:…/]} 输出空串而不是抛异常；真正的发布路径由页面计划显式设置
 *       {@code CurrentProvider}，标签优先用它，因此**永远走不到这里**。</li>
 * </ul>
 * 于是整条链上没有任何作用域代理，也就没有任何 {@code ScopeNotActiveException} 的可能。
 *
 * <p>取值是**按调用现算**而不是缓存：请求线程会被复用，缓存会把上一个请求的站点带过来。
 * 数据层的实例本身带快照缓存，因此现算的代价只是"多 new 一个轻对象"。
 */
@Slf4j
@Configuration
public class ContentProviderConfig {

    /** 非请求线程上使用的站点 id：0 不是任何合法站点，因此取数结果为空而不是"读到别人的数据"。 */
    static final long NO_SITE = 0L;

    @Bean
    public ContentProvider contentProvider(DbContentProviderFactory factory) {
        return new RequestAwareProvider(factory);
    }

    /** 按调用决定"取哪个站点的数"的出口；见类注释。 */
    static final class RequestAwareProvider implements ContentProvider {

        private final DbContentProviderFactory factory;

        RequestAwareProvider(DbContentProviderFactory factory) {
            this.factory = factory;
        }

        /**
         * 当前线程该用的出口。
         *
         * <p>{@code SiteContext.siteId()} 在缺失时抛 {@link BizException}——那是有意的
         * （业务层必须带站点）。这里把它当作"没有请求"来处理：非请求线程上它必然缺失，
         * 而"缺站点上下文"对标签来说与"没有出口"是同一件事，不该升级成渲染失败。
         *
         * <p>其它运行时异常**照旧降级但必须留日志**：以前一律静默退化成站点 0 的空出口，
         * 于是"数据库/配置故障"会伪装成"这个页面没有内容"，排查从"页面怎么是空的"开始。
         */
        private ContentProvider target() {
            try {
                return factory.forSite(SiteContext.siteId());
            } catch (BizException e) {
                // 非请求线程（发布线程池 / 定时任务 / 命令行）缺少站点上下文：按类注释退化为空出口
                return factory.forSite(NO_SITE);
            } catch (RuntimeException e) {
                log.error("获取站点取数出口失败，降级为空出口以免渲染中断", e);
                return factory.forSite(NO_SITE);
            }
        }

        /* ---------------- 站点与定义 ---------------- */

        @Override
        public long siteId() {
            return target().siteId();
        }

        @Override
        public SiteConfig site() {
            return target().site();
        }

        @Override
        public List<ContentTypeDef> types() {
            return target().types();
        }

        @Override
        public ContentTypeDef type(String typeCode) {
            return target().type(typeCode);
        }

        @Override
        public FormDef form(String code) {
            return target().form(code);
        }

        @Override
        public List<String> formCodes() {
            return target().formCodes();
        }

        @Override
        public List<String> menuCodes() {
            return target().menuCodes();
        }

        @Override
        public boolean categoryExists(String idOrSlug) {
            return target().categoryExists(idOrSlug);
        }

        @Override
        public boolean tagExists(String slug) {
            return target().tagExists(slug);
        }

        @Override
        public boolean authorExists(String idOrSlug) {
            return target().authorExists(idOrSlug);
        }

        /* ---------------- 单条内容 ---------------- */

        @Override
        public ContentItem content(String typeCode, long id) {
            return target().content(typeCode, id);
        }

        @Override
        public ContentItem contentById(long id) {
            return target().contentById(id);
        }

        @Override
        public ContentItem contentBySlug(String typeCode, String slug) {
            return target().contentBySlug(typeCode, slug);
        }

        /* ---------------- 列表查询 ---------------- */

        @Override
        public QueryResult query(ContentQuery query) {
            return target().query(query);
        }

        @Override
        public long count(ContentQuery query) {
            return target().count(query);
        }

        @Override
        public List<ContentItem> children(long parentId, String typeCode, int depth) {
            return target().children(parentId, typeCode, depth);
        }

        @Override
        public List<ContentItem> contentAncestors(long contentId) {
            return target().contentAncestors(contentId);
        }

        /* ---------------- 导航来源 ---------------- */

        @Override
        public List<NavItem> categories(Long parentId, int depth, CountScope countScope) {
            return target().categories(parentId, depth, countScope);
        }

        @Override
        public NavItem category(String idOrSlug) {
            return target().category(idOrSlug);
        }

        @Override
        public List<NavItem> categoryAncestors(long categoryId) {
            return target().categoryAncestors(categoryId);
        }

        @Override
        public List<NavItem> typeNodes() {
            return target().typeNodes();
        }

        @Override
        public List<NavItem> menu(String code) {
            return target().menu(code);
        }

        @Override
        public List<NavItem> facetValues(String typeCode, String fieldCode) {
            return target().facetValues(typeCode, fieldCode);
        }

        /* ---------------- 标签与归档 ---------------- */

        @Override
        public List<NavItem> tags(String typeCode, int row, String orderby, int minCount) {
            return target().tags(typeCode, row, orderby, minCount);
        }

        @Override
        public NavItem tag(String slug) {
            return target().tag(slug);
        }

        @Override
        public List<NavItem> archives(String typeCode, String mode, int row, String category,
                                     boolean includeChildren) {
            return target().archives(typeCode, mode, row, category, includeChildren);
        }

        /* ---------------- 邻接 ---------------- */

        @Override
        public Neighbors neighbors(String typeCode, long anchorId, String within, String category) {
            return target().neighbors(typeCode, anchorId, within, category);
        }
    }
}
