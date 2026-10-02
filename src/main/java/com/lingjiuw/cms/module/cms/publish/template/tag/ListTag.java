package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.BodyKind;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code {cms:list}} —— 页面主体列表（static-publish.md §6.3）。
 *
 * <p>它是**分页主体**：一个模板至多一个（§4.5 两条死限），URL 规则决定第 1 页与第 2..N 页的形态
 * （§5.6）。合法位置见 §6.7 矩阵：{@code HOME} / {@code LIST} / {@code TAGPAGE} / {@code ARCHIVE}
 * / {@code DETAIL} / {@code FACET} / {@code STATIC}（限声明了 {@code query} 的列表页）可以 0–1 个，
 * {@code SINGLE} / {@code SEARCH} / {@code 404} / {@code feed} 上一个都不许有（E3012）。
 *
 * <p><b>"详情页 + 可分页列表"是合法形态</b>（目录 / 专题 / 作者文章 / 系列篇目，§5.6）：
 * 分页 URL 用**同一个** {@code detail_url_pattern} 的 {@code {n}} 形态，由 {@code ctx.pageUrls()}
 * 给（模板里禁止裸拼 {@code .html}）。
 *
 * <p>两处缺省按页面类型分叉：
 * <ul>
 *   <li>{@code row} 缺省取类型的 {@code per_page}（§6.3）；</li>
 *   <li><b>分类索引页（{@code LIST}）</b>上 {@code type} 缺省 {@code 'all'}、{@code category}
 *       缺省当前分类（§7.2.1 v2.2："该分类下所有类型的混合流——这正是『栏目』的语义"）。</li>
 * </ul>
 *
 * <p><b>参数表是"共用参数 + of/relate/exclude/where"这一份</b>：§6.3 把 {@code offset} /
 * {@code depth} / {@code name} / {@code cache} 的适用列写的是"仅 {@code query}"，
 * 因此它们不在本标签的声明里——写了会由校验器报 E1002，而不是"声明了却被分页覆盖"。
 */
@Component
public class ListTag extends AbstractQueryTag {

    private final ContentProvider provider;

    public ListTag(@Nullable ContentProvider provider) {
        this.provider = provider;
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public List<ParamSpec> params() {
        return sharedParams();
    }

    @Override
    protected ContentProvider provider() {
        return db();
    }

    @Override
    protected List<String> acceptedParams() {
        return params().stream().map(ParamSpec::key).toList();
    }

    @Override
    public BodyKind bodyKind() {
        return BodyKind.LIST;
    }

    @Override
    public boolean preResolve() {
        return true;
    }

    @Override
    public int maxCount(PageType pageType) {
        // §6.7 矩阵：HOME / LIST / TAGPAGE / ARCHIVE / DETAIL / FACET 上 0–1；
        // STATIC 只有"声明了 query 的列表页"能放恰 1 个（※1），这里取 1，由页面计划再收紧；
        // SINGLE / SEARCH / 404 / feed 上是 ✗（E3012）
        return switch (pageType.matrixColumn()) {
            case HOME, LIST, TAGPAGE, ARCHIVE, DETAIL, FACET, STATIC -> 1;
            default -> 0;
        };
    }

    @Override
    protected int rowDefault(String typeCode) {
        // §6.3：list 的 row 缺省 = 类型的 per_page；type='all' 时没有类型可取，退回 20
        ContentTypeDef def = typeCode == null ? null : db().type(typeCode);
        return def == null ? 20 : def.perPageOrDefault();
    }

    @Override
    protected boolean paginates(RenderContext ctx) {
        // {cms:list} 永远是分页主体（§4.5 口径表）
        return true;
    }

    @Override
    protected boolean listPageDefaults() {
        // §7.2.1：分类索引页上缺省 type='all'、category=当前分类
        return true;
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        renderBody(node, ctx, renderer, out);
    }


    /**
     * 本页真正该用的取数出口：优先 {@link CurrentProvider}（页面计划显式设置的那个），
     * 没有被设置（请求内的预览 / 模板体检）时退回 Spring 注入的实例。
     *
     * <p>Spring 注入进来的是**请求作用域代理**，发布线程池里没有请求，一调用就抛
     * {@code ScopeNotActiveException}——发布路径必须走 {@code CurrentProvider}。
     */
    private ContentProvider db() {
        ContentProvider current = CurrentProvider.current();
        return current != null ? current : provider;
    }

}
