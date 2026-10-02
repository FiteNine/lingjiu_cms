package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
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
 * {@code {cms:query}} —— 任意条件列表（static-publish.md §6.3）：首页 / 侧栏的"最新 / 热门 /
 * 推荐 / 相关 / 子内容 / 目录树"。
 *
 * <p><b>它不参与分页</b>：{@link #bodyKind()} 是 {@link BodyKind#NONE}，也不做派生页计划。
 * 结果为空时标签自身不渲染（正确行为）；需要占位文案时把它**提到顶层并命名**，
 * 用 {@code query.<name>.empty} 判断（§5.4 约束三）。
 *
 * <p>顶层命名后额外提供 {@code query.<name>.rows.<i>.<字段>} 的下标访问（§6.3 v2.2 新增，
 * 用于产品对比表）。**循环体内禁止命名**（§5.4 约束三 → E2011），那条由校验器在编译期报。
 *
 * <p>{@code row} 缺省是 {@code 10}（§6.3 的参数表）。
 */
@Component
public class QueryTag extends AbstractQueryTag {

    private final ContentProvider provider;

    public QueryTag(@Nullable ContentProvider provider) {
        this.provider = provider;
    }

    @Override
    public String name() {
        return "query";
    }

    @Override
    public List<ParamSpec> params() {
        // §6.3 的 20 个：共用参数表 + 仅 query 的 offset / depth / name / cache
        return queryParams();
    }

    @Override
    protected List<String> acceptedParams() {
        return params().stream().map(ParamSpec::key).toList();
    }

    @Override
    protected ContentProvider provider() {
        return db();
    }

    @Override
    public BodyKind bodyKind() {
        return BodyKind.NONE;
    }

    @Override
    public boolean preResolve() {
        return true;
    }

    @Override
    public int maxCount(PageType pageType) {
        // §6.7 矩阵：{cms:query} 在所有页面类型上都是 ✓（不限个数），但不分页
        return -1;
    }

    @Override
    protected int rowDefault(String typeCode) {
        return 10;   // §6.3：query 的 row 缺省 = 10
    }

    @Override
    protected boolean paginates(RenderContext ctx) {
        return false;   // {cms:query} 不参与分页（§6.3 标题就写着）
    }

    @Override
    protected boolean listPageDefaults() {
        // §7.2.1 的"分类索引页缺省"只说 {cms:list}；query 在首页/侧栏上必须自己写清 type
        return false;
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        renderQuery(node, ctx, renderer, out);
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
