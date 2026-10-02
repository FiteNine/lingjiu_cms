package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.ParamType;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * §4.5 第 2 条 ★：参数是否在该标签的 {@link ParamSpec} 内、类型是否可转换、必填是否缺失 → E1002。
 *
 * <p>三类检查各一处：
 * <ol>
 *   <li><b>未声明的参数</b>：{@link TagHandler#openParams()} 为真的标签（只有 {@code include}）放行，
 *       但它自己的参数名不得与 6 个保留名同名（§5.1、§6.2）；</li>
 *   <li><b>类型</b>：{@code INT} 转换失败、{@code BOOL} 不在 {@code 0/1/true/false}、{@code ENUM}
 *       不在枚举里，报错都把实际值原样贴出来（§3.3）；</li>
 *   <li><b>必填</b>：声明为 {@code required} 的没写 → 报错；另有两条**依页面类型而定**的必填
 *       （§6.3 的 {@code type} / {@code category}：该页"当前类型 / 当前栏目"不存在时必须显式给），
 *       {@link ParamSpec} 表达不了条件必填，因此在这里补。</li>
 * </ol>
 *
 * <p>§6.3 还写死"同标签同 key 出现两次 → E1002"与"参数值永远是字面量"，两者都在解析期
 * （重复 key 进不了 {@code Map}），不在本类。
 */
@Component
public final class ParamValidator implements TemplateValidator {

    /**
     * 没有"当前页面的类型"与"当前栏目"的页面类型（§6.3 的两条注）。
     *
     * <p>**没把 {@code LIST} 算进来**：§6.3 的原文写"类型列表页"，而 {@code LIST} 有两种来源
     * （类型列表页 / 分类索引页，§7.2.1），{@link CompileContext} 里没有区分它们的字段；而
     * §7.2.1 v2.2 给两种来源都定了缺省（分类索引页缺省 {@code category=当前分类} / {@code type='all'}，
     * 类型列表页缺省 {@code type=该类型}），硬要求会误伤。{@code STATIC} 的查询来自站点选项
     * {@code pages.static} 的 {@code query}、{@code FEED} 的来自 {@code feed.types}、
     * {@code TAGLIST} 是"标签总览页"（无列表主体），同理都不要求。
     *
     * <p>这条区分（LIST 的两种来源）留给期 2 的页面计划期校验——那里才知道这一页是类型列表页
     * 还是分类索引页。
     *
     * <p><b>已知缺口（裁定，记在报告里）</b>：§6.3 的 {@code category} 清单里确实写了"类型列表页"，
     * 而 {@code LIST} 上有两种来源，编译期分不清。这里选择**整列放行**：漏报的后果是运行期
     * {@code category} 缺省成"不限栏目"（静默但无害，页面仍出得来）；若改成整列必填，分类索引页
     * 的 {@code {cms:list}}（缺省 {@code category=当前分类}，§7.2.1）会被误拦——**误拦会挡住正确模板，
     * 比漏报更糟**。期 2 的计划期校验拿得到 LIST 的来源，在那里补这一条。
     */
    private static final Set<PageType> NEEDS_EXPLICIT_SCOPE = EnumSet.of(
            PageType.HOME, PageType.TAGPAGE, PageType.ARCHIVE, PageType.FACET,
            PageType.SEARCH, PageType.PAGE404);

    private final TagRegistry registry;

    public ParamValidator(TagRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int order() {
        return 2;
    }

    @Override
    public String describe() {
        return "§4.5 第 2 条：参数声明 / 类型 / 必填（E1002）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                TagHandler handler = registry.handler(node.name());
                if (handler == null) {
                    return; // 未知标签由第 1 条报 E1001，这里不重复报
                }
                // 逐条写进报告后继续遍历：一个模板里可能有十几个参数写错，§10.3 第 2 条要一次全报
                checkDeclared(node, handler, report);
                if (handler.queryTag()) {
                    checkRequiredScope(node, handler, ctx, report);
                }
            }
        });
    }

    /* ---------------- 参数声明与类型 ---------------- */

    private void checkDeclared(TagNode node, TagHandler handler, ValidationReport report) {
        Map<String, ParamSpec> specs = new java.util.LinkedHashMap<>();
        for (ParamSpec spec : handler.params()) {
            specs.put(spec.key(), spec);
        }
        for (Map.Entry<String, String> arg : node.args().entrySet()) {
            ParamSpec spec = specs.get(arg.getKey());
            if (spec == null) {
                if (handler.openParams()) {
                    // 只有 {cms:include} 是开放的，而它在校验器跑之前就已经展开掉了——
                    // "参数名不得与 6 个保留名同名"（§5.1、§6.2）因此落在 TemplateCompiler 的展开步骤里。
                    continue;
                }
                List<String> keys = new ArrayList<>(specs.keySet());
                report.error(error(node, "参数 " + arg.getKey() + " 不存在",
                        "{cms:" + handler.name() + "} 的参数有 " + Suggest.join(keys)
                                + "（全部列出）；实际写了 " + arg.getKey() + "='" + arg.getValue() + "'",
                        suggestion(arg.getKey(), keys)));
                continue;
            }
            report.error(checkType(node, handler, spec, arg.getValue()));
        }
        for (ParamSpec spec : handler.params()) {
            String value = node.arg(spec.key());
            // 空串 = 没提供：运行期的必填判定（{@code {cms:form}} 取 code、{@code {cms:include}} 取 file）
            // 都把空白当成缺失，编译期按 containsKey 判会让 code='' 漏过必填检查
            if (spec.required() && (value == null || value.isBlank())) {
                report.error(error(node, "参数 " + spec.key() + " 是必填的",
                        "{cms:" + handler.name() + "} 没有 " + spec.key() + " 参数（" + spec.desc() + "）",
                        "可用参数有 " + Suggest.join(specs.keySet())));
            }
        }
    }

    /** 类型不符时返回一条错误；合法返回 null。 */
    private PublishException checkType(TagNode node, TagHandler handler, ParamSpec spec, String value) {
        switch (spec.type()) {
            case STRING -> {
                return null;
            }
            case INT -> {
                try {
                    Integer.parseInt(value.trim());
                    return null;
                } catch (NumberFormatException e) {
                    return error(node, "参数 " + spec.key() + " 需要整数",
                            "{cms:" + handler.name() + "} 的 " + spec.key() + "='" + value + "'",
                            "写成整数，例如 " + spec.key() + "='10'");
                }
            }
            case BOOL -> {
                String text = value.trim().toLowerCase(java.util.Locale.ROOT);
                if (Set.of("0", "1", "true", "false").contains(text)) {
                    return null;
                }
                return error(node, "参数 " + spec.key() + " 需要 0 / 1 / true / false",
                        "{cms:" + handler.name() + "} 的 " + spec.key() + "='" + value + "'",
                        "BOOL 参数只接受 0 / 1 / true / false（忽略大小写，§3.3）");
            }
            case ENUM -> {
                if (spec.enumValues().contains(value)) {
                    return null;
                }
                return error(node, "参数 " + spec.key() + " 的取值不在允许范围",
                        "{cms:" + handler.name() + "} 的 " + spec.key() + "='" + value + "'",
                        "允许值：" + spec.typeText() + Suggest.hint(value, spec.enumValues()));
            }
            default -> {
                return null;
            }
        }
    }

    /* ---------------- 依页面类型而定的必填（§6.3） ---------------- */

    private void checkRequiredScope(TagNode node, TagHandler handler, CompileContext ctx,
                                    ValidationReport report) {
        // §6.3：type='all' 按公共列跨类型取数，没有默认条数 —— 与页面类型无关，因此独立判。
        // （曾经把它放在下面的页面类型分支里，于是详情页侧栏的 {cms:query type='all'} 漏报。）
        // 只对**声明了 row 参数的标签**判：{cms:detail} 的 ParamSpec 里没有 row（§6.3 的表里
        // row 只对 list / query 有意义），对它要求 row 是无解的假错误。
        boolean declaresRow = handler.params().stream().anyMatch(spec -> "row".equals(spec.key()));
        if (declaresRow && "all".equals(node.arg("type")) && node.arg("row") == null) {
            report.error(error(node, "type='all' 时参数 row 是必填的",
                    "type='all' 按公共列跨类型取数，没有默认条数（§6.3）",
                    "补上 row='8' 这类条数"));
        }
        if (!NEEDS_EXPLICIT_SCOPE.contains(ctx.pageType())) {
            return;
        }
        // 空串 = 没提供：运行期 QueryParams.resolve 正是按 isBlank() 回退"当前页面的类型 / 栏目"，
        // 拿不到就报 E1002。这里按 null 判会让 type='' 漏过必填检查、把错误推到渲染期
        if (node.arg("type") == null || node.arg("type").isBlank()) {
            report.error(error(node, "参数 type 是必填的",
                    ctx.pageType() + " 页没有「当前页面的类型」（§6.3）",
                    "显式写 type='article' 或 type='all'（不限类型）；" + handler.name()
                            + " 的可用类型：" + ctx.describeTypes()));
        }
        if (node.arg("category") == null || node.arg("category").isBlank()) {
            report.error(error(node, "参数 category 是必填的",
                    ctx.pageType() + " 页没有「当前栏目」（§6.3）",
                    "显式写 category='all'（不限栏目）或一个具体分类 id / slug"));
        }
    }

    private static String suggestion(String key, List<String> keys) {
        return "可用参数有 " + Suggest.join(keys) + Suggest.hint(key, keys);
    }

    private static PublishException error(TagNode node, String what, String actual, String advice) {
        return PublishException.error(PublishErrorCode.E1002, what, node.sourcePath(), node.lineNo(),
                actual, advice);
    }
}
