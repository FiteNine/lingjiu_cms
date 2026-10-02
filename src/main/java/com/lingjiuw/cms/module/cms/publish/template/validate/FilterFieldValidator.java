package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.BuiltinFields;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.OrderBy;
import com.lingjiuw.cms.module.cms.publish.model.WhereCondition;
import com.lingjiuw.cms.module.cms.publish.model.WhereParser;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * §4.5 第 6 条 ★：{@code where} / {@code orderby} / {@code relate} 的字段可筛选性与语法 → E2007。
 *
 * <p>三件事复用既有的解析器与白名单，**不另建第二份清单**（§2.7："省略号是承重的"）：
 * <ul>
 *   <li>语法（{@code code:op:value}、{@code ,} 与 {@code |} 的转义）交给
 *       {@link WhereParser#parse}，它自己的报错就是 E2007；运算符白名单在
 *       {@link WhereCondition.Op}；</li>
 *   <li>排序解析交给 {@link OrderBy#parseList}；</li>
 *   <li>内置字段能不能筛 / 能不能排，问 {@link BuiltinFields#whereCapable} /
 *       {@link BuiltinFields#orderbyCapable}；自定义字段要求 {@code indexed=1}（§2.5）。</li>
 * </ul>
 *
 * <p>只看**查询标签**（{@code list} / {@code query} / {@code detail}，靠 {@link TagHandler#queryTag()}）：
 * {@code {cms:foreach}} 与 {@code {cms:tagnav}} 也有 {@code orderby} 参数，但那里的语义分别是
 * "迭代项内的字段"与"count|name|sort"（§6.2、§6.4），套查询侧的白名单会误报。
 *
 * <p>{@code type='all'} 的四条限定（§6.3 v2.2 定死）也在这里：不得配 {@code where}、
 * 不得配 {@code relate='field:<code>'}、不得配 {@code of}；{@code orderby} 只能用公共列。
 */
@Component
public final class FilterFieldValidator implements TemplateValidator {

    /** {@code relate} 里指向自定义字段的前缀（§6.3）。 */
    private static final String FIELD_PREFIX = "field:";

    private final TagRegistry registry;

    public FilterFieldValidator(TagRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int order() {
        return 6;
    }

    @Override
    public String describe() {
        return "§4.5 第 6 条：where / orderby / relate 的字段与语法（E2007）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                TagHandler handler = registry.handler(node.name());
                if (handler == null || !handler.queryTag()) {
                    return;
                }
                String typeCode = ScopeWalker.queryTypeCode(node, ctx);
                boolean crossType = typeCode == null || "all".equals(typeCode);
                // 逐条写进报告后继续遍历（§10.3 第 2 条：一个 where 里几个字段都错时一次全报）
                report.error(checkWhere(node, ctx, typeCode, crossType));
                report.error(checkOrderby(node, ctx, typeCode, crossType));
                report.error(checkRelate(node, ctx, typeCode, crossType));
                if (crossType && node.arg("of") != null) {
                    report.error(error(node, "of 不能与 type='all' 一起用",
                            "{cms:" + node.name() + "} of='" + node.arg("of") + "' type='all'",
                            "type='all' 是跨类型取数，没有「锚定项所属类型」的概念（§6.3）"));
                }
            }
        });
    }

    /* ---------------- where ---------------- */

    private PublishException checkWhere(TagNode node, CompileContext ctx, String typeCode,
                                        boolean crossType) {
        String raw = node.arg("where");
        if (raw == null) {
            return null;
        }
        // 专有的互斥提示要抢在语法错误之前：type='all' 配 where 是"这条规则不允许"，
        // 而不是"where 写错了"（§6.3）
        if (crossType) {
            return error(node, "where 不能与 type='all' 一起用",
                    "where='" + raw + "' type='" + (typeCode == null ? "all（缺省）" : typeCode) + "'",
                    "type='all' 只按 cms_content 公共列取数，where 一律不可用（§6.3）");
        }
        List<WhereCondition> conditions;
        try {
            conditions = WhereParser.parse(raw, node.sourcePath(), node.lineNo());
        } catch (PublishException e) {
            // WhereParser 语法不可解析时直接抛：这里收成一条错误交回报告，
            // 否则它会把本校验器对整棵模板的遍历打断（后续 where / orderby / relate 全漏检）
            return e;
        }
        for (WhereCondition condition : conditions) {
            String code = condition.fieldCode();
            if (BuiltinFields.whereCapable(code)) {
                continue;
            }
            FieldDef field = declared(ctx, typeCode, code);
            if (field != null && field.indexed()) {
                continue;
            }
            return error(node, "字段 " + code + " 不能用于 where",
                    "where='" + raw + "'；类型 " + typeCode + " 上"
                            + (field == null ? "没有这个自定义字段" : "该字段没有勾选「可筛选」"),
                    "到「内容类型 → 字段 → 可筛选」勾选后重试；内置字段的可筛选清单见 §2.2："
                            + Suggest.join(BuiltinFields.WHERE_CAPABLE));
        }
        return null;
    }

    /* ---------------- orderby ---------------- */

    private PublishException checkOrderby(TagNode node, CompileContext ctx, String typeCode,
                                          boolean crossType) {
        String raw = node.arg("orderby");
        if (raw == null) {
            return null;
        }
        String relate = node.arg("relate");
        boolean relatesToField = relate != null && relate.startsWith(FIELD_PREFIX);
        for (OrderBy orderBy : OrderBy.parseList(raw)) {
            String code = orderBy.fieldCode();
            if (BuiltinFields.RELATION_ORDER.equals(code)) {
                if (!relatesToField) {
                    return error(node, "orderby='relationOrder' 只能与 relate='field:<code>' 一起用",
                            "orderby='" + raw + "' relate='" + relate + "'",
                            "relationOrder 是「按该 RELATION 字段的数组顺序排」（§6.3）");
                }
                continue;
            }
            if (BuiltinFields.orderbyCapable(code)) {
                continue;
            }
            FieldDef field = crossType ? null : declared(ctx, typeCode, code);
            if (field != null && field.indexed()) {
                continue;
            }
            return error(node, "字段 " + code + " 不能用于 orderby",
                    "orderby='" + raw + "'；类型 " + (crossType ? "type='all'" : typeCode) + " 上"
                            + (crossType ? "跨类型只能用公共列"
                            : field == null ? "没有这个自定义字段" : "该字段没有勾选「可筛选」"),
                    "可用排序字段：" + Suggest.join(BuiltinFields.ORDERBY_CAPABLE)
                            + "，或到字段定义里勾选「可筛选」" + Suggest.hint(code, BuiltinFields.ORDERBY_CAPABLE));
        }
        return null;
    }

    /* ---------------- relate ---------------- */

    private PublishException checkRelate(TagNode node, CompileContext ctx, String typeCode,
                                         boolean crossType) {
        String raw = node.arg("relate");
        if (raw == null) {
            return null;
        }
        if ("tag".equals(raw) || "category".equals(raw)) {
            return null;
        }
        if (!raw.startsWith(FIELD_PREFIX) || raw.length() == FIELD_PREFIX.length()) {
            return error(node, "relate 的取值不对", "relate='" + raw + "'",
                    "允许 tag / category / field:<code> 三种（§6.3）");
        }
        String code = raw.substring(FIELD_PREFIX.length());
        if (crossType) {
            return error(node, "relate='field:…' 不能与 type='all' 一起用",
                    "relate='" + raw + "' type='" + (typeCode == null ? "all（缺省）" : typeCode) + "'",
                    "type='all' 时自定义字段一律不可用（§6.3）");
        }
        if (BuiltinFields.whereCapable(code)) {
            return null;
        }
        FieldDef field = declared(ctx, typeCode, code);
        if (field != null && field.indexed()) {
            return null;
        }
        return error(node, "字段 " + code + " 不能用于 relate",
                "relate='" + raw + "'；类型 " + typeCode + " 上"
                        + (field == null ? "没有这个自定义字段" : "该字段没有勾选「可筛选」"),
                "relate='field:<code>' 的 <code> 要么是 indexed=1 的自定义字段，要么是 §2.2 里可筛选的内置字段："
                        + Suggest.join(BuiltinFields.WHERE_CAPABLE));
    }

    /* ---------------- 小工具 ---------------- */

    /** 该类型上的自定义字段声明；类型不存在 / provider 缺失时返回 null。 */
    private static FieldDef declared(CompileContext ctx, String typeCode, String code) {
        if (typeCode == null || ctx.provider() == null) {
            return null;
        }
        ContentTypeDef def = ctx.provider().type(typeCode);
        return def == null ? null : def.field(code);
    }

    private static PublishException error(TagNode node, String what, String actual, String advice) {
        return PublishException.error(PublishErrorCode.E2007, what, node.sourcePath(), node.lineNo(),
                actual, advice);
    }
}
