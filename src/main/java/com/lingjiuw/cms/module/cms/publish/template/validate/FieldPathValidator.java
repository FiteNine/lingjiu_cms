package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.BuiltinFields;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.Node;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * §4.5 第 4 条 ★ + 第 5 条 ★（**合并成一个类**，见报告）：字段名是否存在于"该模板上下文可用的
 * 字段集合"（E1004）与字段路径的中间段是否可解析（E1005）。
 *
 * <p>合并的理由：两条校验共用同一台机器——第 4 条要的"可用字段集合"就是第 5 条要的"路径首段所在
 * 的那一帧"，拆成两个类要么走查两遍，要么把作用域栈暴露成公共状态；而两者的报错文案本来也连着写
 * （"字段 price 不存在" 与 "路径 images.0.url 在第 2 段断掉"）。错误码仍然各归各的：名字找不到 → E1004，
 * 名字在但路径下钻断掉 / {@code .count} 用错 → E1005。
 *
 * <p>三处字段引用都要校验，用的是同一套规则（§6.2："{@code {cms:if}} 的 {@code field} 写法与
 * {@code [field:xxx/]} 完全一致"）：
 * <ul>
 *   <li>{@code [field:…/]} 的路径；</li>
 *   <li>{@code {cms:if field='…'}}；</li>
 *   <li>{@code {cms:foreach field='…'}}——它还必须是一个**多值**字段（§6.2：迭代手上已有的多值字段）。</li>
 * </ul>
 *
 * <p><b>报错方式</b>：一个模板里几十个字段引用，因此**逐条写进 {@link ValidationReport#error} 后
 * 继续遍历**（§10.3 第 2 条：12 个字段名都写错时一次全报出来），由编译器统一汇总。
 *
 * <p>编译期能查的边界写死如下，越界的一律不查（宁可少报，不误伤）：
 * <ul>
 *   <li>多值下标的具体取值只能在渲染期查（§4.5 第 5 条自己写了"下标越界只能在渲染期查"），
 *       这里只查"能不能下标"；</li>
 *   <li>{@code JSON} 字段的键是数据（{@code [field:specs.weight/]} 的 {@code weight}），编译期无从知道；</li>
 *   <li>{@code param} 的 key 由 include 的字面量参数与 §7.2.3 的 4 个注入 key 决定，
 *       {@link CompileContext#namedScopeKeys} 已声明"编译期无法穷举"，因此只校验它是不是保留名，
 *       不校验具体 key（与内核口径一致）。include 传进来的字面量参数在编译期已被替换成文本
 *       （见 {@code TemplateCompiler.substituteParams}）。</li>
 * </ul>
 */
@Component
public final class FieldPathValidator implements TemplateValidator {

    private final TagRegistry registry;

    public FieldPathValidator(TagRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int order() {
        return 4;
    }

    @Override
    public String describe() {
        return "§4.5 第 4/5 条：字段名与字段路径（E1004 / E1005）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        Map<String, String> namedQueries = namedQueryTypes(ast, ctx);
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            /** 标签体里的产出字段由标签自己声明（§6.3 的统一产出契约）；声明不了才按开放帧放行。 */
            @Override
            public Set<String> bodyKeys(TagNode node, com.lingjiuw.cms.module.cms.publish.model.PageType pageType) {
                TagHandler handler = registry.handler(node.name());
                return handler == null ? null : handler.bodyKeys(pageType);
            }

            @Override
            public void onField(FieldNode node, ScopeWalker.Scope scope) {
                report.error(check(node.path(), node.sourcePath(), node.lineNo(), scope, ctx,
                        namedQueries, false));
            }

            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                TagHandler handler = registry.handler(node.name());
                if (handler == null) {
                    return;
                }
                if ("if".equals(node.name())) {
                    String field = node.arg("field");
                    if (field != null) {
                        report.error(check(split(field), node.sourcePath(), node.lineNo(), scope, ctx,
                                namedQueries, false));
                    }
                }
                if ("foreach".equals(node.name())) {
                    String field = node.arg("field");
                    if (field != null) {
                        report.error(check(split(field), node.sourcePath(), node.lineNo(), scope, ctx,
                                namedQueries, true));
                    }
                }
            }
        });
    }

    /* ---------------- 路径检查（返回 null 表示没问题） ---------------- */

    private PublishException check(List<String> path, String sourcePath, int lineNo,
                                   ScopeWalker.Scope scope, CompileContext ctx,
                                   Map<String, String> namedQueries, boolean mustBeMultiValued) {
        if (path.isEmpty() || path.get(0).isEmpty()) {
            return E1005(path, 0, sourcePath, lineNo, "路径是空的", "写法是 [field:title/] 或 field='title'");
        }
        for (int i = 1; i < path.size(); i++) {
            if (path.get(i).isEmpty()) {
                return E1005(path, i, sourcePath, lineNo, "路径里出现了空段（连续两个点）",
                        "段之间只写一个点，例如 images.0.url");
            }
        }
        String head = path.get(0);
        if (BuiltinFields.RESERVED_NAMES.contains(head)) {
            return checkNamedScope(path, sourcePath, lineNo, ctx, namedQueries);
        }
        if (!scope.has(head)) {
            Set<String> keys = scope.keys();
            return PublishException.error(PublishErrorCode.E1004,
                    "字段 " + head + " 不存在", sourcePath, lineNo,
                    "可用字段有 " + scope.describe(),
                    "字段 code 区分大小写；报错清单由引擎按「该页面类型 + 该类型定义」现算"
                            + Suggest.hint(head, keys));
        }
        if (mustBeMultiValued) {
            FieldType type = ScopeWalker.fieldTypeOf(head, scope.ownerType(), ctx);
            if (type != null && !type.multiValued()) {
                return E1005(path, 0, sourcePath, lineNo,
                        head + " 是 " + type + "（单值字段）",
                        "{cms:foreach} 只能迭代多值字段：" + String.join(" ", BuiltinFields.FOREACH_SOURCES));
            }
        }
        return checkDeeper(path, sourcePath, lineNo, scope, ctx, head);
    }

    /** 路径首段不是保留名时逐段下钻：多值可取 {@code count} / 下标，JSON 的键是数据（放过），单值不能再取段。 */
    private PublishException checkDeeper(List<String> path, String sourcePath, int lineNo,
                                         ScopeWalker.Scope scope, CompileContext ctx, String head) {
        if (path.size() < 2) {
            return null;
        }
        FieldType type = ScopeWalker.fieldTypeOf(head, scope.ownerType(), ctx);
        if (type == null || type == FieldType.JSON) {
            return null; // 声明未知或 JSON：键是数据，编译期无从查
        }
        if (!type.multiValued()) {
            return E1005(path, 1, sourcePath, lineNo,
                    head + " 是 " + type + "（单值字段），不能取下标 / 键",
                    "多值字段才能写 .count 或下标；要判断有没有值用 {cms:if field='" + head + "'}（§3.7 裁定一）");
        }
        String segment = path.get(1);
        if ("count".equals(segment)) {
            if (path.size() > 2) {
                return E1005(path, 2, sourcePath, lineNo,
                        "count 之后不能再取段", "count 是元素个数，写到 " + head + ".count 为止");
            }
            return null;
        }
        if (!segment.matches("\\d+")) {
            return E1005(path, 1, sourcePath, lineNo,
                    head + " 是多值字段，第二段只能是下标或 count",
                    "写法是 " + head + ".0 或 " + head + ".count");
        }
        Set<String> itemKeys = ScopeWalker.iteratorKeysOf(head, scope.ownerType(), ctx);
        if (itemKeys == null || itemKeys.isEmpty() || path.size() < 3) {
            return null;
        }
        String key = path.get(2);
        if (!itemKeys.contains(key)) {
            return E1005(path, 2, sourcePath, lineNo,
                    "迭代项里没有 " + key + " 这个 key",
                    "迭代项可用的 key 有 " + Suggest.join(itemKeys) + Suggest.hint(key, itemKeys));
        }
        return null;
    }

    /* ---------------- 具名作用域 ---------------- */

    private PublishException checkNamedScope(List<String> path, String sourcePath, int lineNo,
                                             CompileContext ctx, Map<String, String> namedQueries) {
        String name = path.get(0);
        if (!ctx.namedScopeExists(name)) {
            return PublishException.error(PublishErrorCode.E1004,
                    "当前页面类型 " + ctx.pageType() + " 上没有 " + name + " 作用域", sourcePath, lineNo,
                    "[" + name + ".…] 在这个页面类型上取不到值",
                    "具名作用域与页面类型的对应关系见 §5.1 第（4）条：" + namedScopes(ctx));
        }
        if (path.size() < 2) {
            return PublishException.error(PublishErrorCode.E1004,
                    "具名作用域 " + name + " 少了 key", sourcePath, lineNo,
                    "路径 " + String.join(".", path) + " 只写了作用域名",
                    "写法是 [field:" + name + ".xxx/]，可用的 key 有 " + Suggest.join(keysOf(name, ctx)));
        }
        if ("param".equals(name)) {
            return null; // §5.3：param 的 key 由 include 参数与 §7.2.3 的注入 key 决定，编译期不穷举
        }
        if ("query".equals(name)) {
            return checkQueryScope(path, sourcePath, lineNo, ctx, namedQueries);
        }
        String key = path.get(1);
        Set<String> keys = keysOf(name, ctx);
        if (!keys.contains(key)) {
            return PublishException.error(PublishErrorCode.E1004,
                    "具名作用域 " + name + " 上没有 key " + key, sourcePath, lineNo,
                    "[" + name + ".…] 可用的 key 有 " + Suggest.join(keys),
                    "key 区分大小写" + Suggest.hint(key, keys));
        }
        return null;
    }

    /** {@code query.<name>} 的第 2 段是**查询名**（不是 {@code query} 作用域的 key），之后才是元信息字段（§6.3）。 */
    private PublishException checkQueryScope(List<String> path, String sourcePath, int lineNo,
                                             CompileContext ctx, Map<String, String> namedQueries) {
        if (path.size() < 2) {
            return null;
        }
        String name = path.get(1);
        if (!namedQueries.containsKey(name)) {
            Set<String> known = new TreeSet<>(namedQueries.keySet());
            return PublishException.error(PublishErrorCode.E1004,
                    "查询名 " + name + " 不存在", sourcePath, lineNo,
                    "模板里命名的查询有 " + Suggest.join(known) + "（写 name='…' 才有）",
                    "query.<name> 的 <name> 必须是本模板里 name 参数声明的查询名" + Suggest.hint(name, known));
        }
        if (path.size() < 3) {
            return null;
        }
        String key = path.get(2);
        if (!BuiltinFields.QUERY_KEYS.contains(key)) {
            return PublishException.error(PublishErrorCode.E1004,
                    "query." + name + " 上没有 key " + key, sourcePath, lineNo,
                    "可用的 key 有 " + Suggest.join(BuiltinFields.QUERY_KEYS),
                    "key 区分大小写" + Suggest.hint(key, BuiltinFields.QUERY_KEYS));
        }
        // query.<name>.rows.<i>.<字段>：下标只能渲染期查，字段名可以编译期查（§6.3 v2.2）
        if (!"rows".equals(key) || path.size() < 5 || !path.get(3).matches("\\d+")) {
            return null;
        }
        String fieldCode = path.get(4);
        Set<String> fields = ctx.fieldsOfType(namedQueries.get(name));
        if (!fields.contains(fieldCode)) {
            return PublishException.error(PublishErrorCode.E1004,
                    "字段 " + fieldCode + " 不存在", sourcePath, lineNo,
                    "查询 " + name + "（类型 " + text(namedQueries.get(name)) + "）的迭代项可用字段有 "
                            + Suggest.join(fields),
                    "字段 code 区分大小写" + Suggest.hint(fieldCode, fields));
        }
        return null;
    }

    private Set<String> keysOf(String name, CompileContext ctx) {
        return ctx.namedScopeKeys(name);
    }


    private static String namedScopes(CompileContext ctx) {
        List<String> scopes = new ArrayList<>();
        for (String name : RenderContext.RESERVED_NAMES) {
            if (ctx.namedScopeExists(name)) {
                scopes.add(name);
            }
        }
        return String.join(" ", scopes);
    }

    /* ---------------- 命名查询表 ---------------- */

    /** 本模板里 {@code name='…'} 声明的查询 → 它作用的类型 code（没有 {@code type} 时按 type='all'）。 */
    private Map<String, String> namedQueryTypes(TemplateAst ast, CompileContext ctx) {
        Map<String, String> types = new HashMap<>();
        for (Node node : flatten(ast.nodes())) {
            // 与 NamedQueryValidator 同一口径：空白名不算命名查询（那边用 isBlank 过滤）
            if (!(node instanceof TagNode tag) || tag.arg("name") == null || tag.arg("name").isBlank()) {
                continue;
            }
            TagHandler handler = registry.handler(tag.name());
            if (handler != null && handler.queryTag()) {
                types.putIfAbsent(tag.arg("name"), ScopeWalker.queryTypeCode(tag, ctx));
            }
        }
        return types;
    }

    private static List<Node> flatten(List<Node> nodes) {
        List<Node> all = new ArrayList<>();
        for (Node node : nodes) {
            all.add(node);
            if (node instanceof TagNode tag && tag.body() != null) {
                all.addAll(flatten(tag.body()));
            }
        }
        return all;
    }

    private static List<String> split(String field) {
        if (field == null || field.isBlank()) {
            return List.of();
        }
        return List.of(field.trim().split("\\.", -1));
    }

    private static String text(String value) {
        return value == null ? "（未指定，按 type='all'）" : value;
    }

    private static PublishException E1005(List<String> path, int segment, String sourcePath, int lineNo,
                                          String actual, String advice) {
        return PublishException.error(PublishErrorCode.E1005,
                "路径 " + String.join(".", path) + " 在第 " + (segment + 1) + " 段断掉",
                sourcePath, lineNo, actual, advice);
    }
}
