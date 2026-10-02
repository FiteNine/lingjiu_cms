package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * §4.5 第 7 条 ★：{@code type} / {@code category} / {@code tag} / {@code author} / {@code code}
 * 是否指向存在的对象（含 {@code all}）→ E2006。
 *
 * <p>按**参数名**分派，不按标签名硬编码：这五个参数的语义在整份契约里是一致的（§6.3、§6.4），
 * 而带这些参数的标签不止查询标签（{@code {cms:channel type=…}}、{@code {cms:tagnav type=…}}、
 * {@code {cms:archive type=…}}），逐个标签写死会让"新增标签"变成两处修改。
 *
 * <p>三处例外，都写死在这里：
 * <ul>
 *   <li>{@code {cms:prenext type='prev|next|both'}} 的 {@code type} 是**方向**不是类型 code（§6.4），
 *       不能按类型校验——所以只对查询标签与 channel / tagnav / archive 校验 {@code type}；</li>
 *   <li>{@code type='all'}（不限类型）只在 {@code list} / {@code query} / {@code tagnav} /
 *       {@code archive} 上成立；写在 {@code {cms:detail}} 或 {@code {cms:channel}} 上 → E2006；</li>
 *   <li>{@code author='self'} 是引擎关键字（当前条目的作者），不是作者 slug（§6.3）。</li>
 * </ul>
 *
 * <p>{@code {cms:form code=…}} 的表单 code 属第 12 条（E2008），不在这里。
 */
@Component
public final class ReferenceValidator implements TemplateValidator {

    /** 需要校验 {@code type} 参数（= 内容类型 code）的标签：查询标签 + 这三个导航标签。 */
    private static final Set<String> TYPE_AWARE_TAGS = Set.of("channel", "tagnav", "archive");

    /** {@code type='all'} 合法的标签（§6.3 的"不限类型"）。 */
    private static final Set<String> ALLOW_ALL_TYPES = Set.of("list", "query", "tagnav", "archive");

    private final TagRegistry registry;

    public ReferenceValidator(TagRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int order() {
        return 7;
    }

    @Override
    public String describe() {
        return "§4.5 第 7 条：type / category / tag / author / code 指向的对象（E2006）";
    }

    @Override
    public void validate(TemplateAst ast, CompileContext ctx, ValidationReport report) {
        ScopeWalker.walk(ast, ctx, new ScopeWalker.Visitor() {
            @Override
            public void onTag(TagNode node, ScopeWalker.Scope scope) {
                TagHandler handler = registry.handler(node.name());
                if (handler == null) {
                    return;
                }
                // 逐条写进报告后继续遍历（§10.3 第 2 条：一次把全部引用错报出来）
                report.error(checkType(node, handler, ctx));
                report.error(checkCategory(node, ctx));
                report.error(checkTag(node, ctx));
                report.error(checkAuthor(node, ctx));
                report.error(checkMenuCode(node, ctx));
            }
        });
    }

    private PublishException checkType(TagNode node, TagHandler handler, CompileContext ctx) {
        String typeCode = node.arg("type");
        if (typeCode == null || (!handler.queryTag() && !TYPE_AWARE_TAGS.contains(node.name()))) {
            return null;
        }
        if ("all".equals(typeCode)) {
            if (ALLOW_ALL_TYPES.contains(node.name())) {
                return null;
            }
            return error(node, "type='all' 不能用在 {cms:" + node.name() + "} 上",
                    "{cms:" + node.name() + "} type='all'",
                    "type='all'（不限类型）只在 list / query / tagnav / archive 上成立（§6.3）；"
                            + "{cms:" + node.name() + "} 要一个具体类型");
        }
        if (ctx.provider() == null || ctx.provider().type(typeCode) != null) {
            return null;
        }
        return error(node, "内容类型 " + typeCode + " 不存在",
                "{cms:" + node.name() + "} type='" + typeCode + "'",
                "本站类型有 " + ctx.describeTypes() + Suggest.hint(typeCode, typeCodes(ctx)));
    }

    private PublishException checkCategory(TagNode node, CompileContext ctx) {
        String category = node.arg("category");
        if (category == null || "all".equals(category) || ctx.provider() == null) {
            return null;
        }
        if (ctx.provider().categoryExists(category)) {
            return null;
        }
        return error(node, "分类 " + category + " 不存在",
                "{cms:" + node.name() + "} category='" + category + "'",
                "category 可以写分类 id 或 slug；不限栏目写 category='all'（§6.3）");
    }

    private PublishException checkTag(TagNode node, CompileContext ctx) {
        String tag = node.arg("tag");
        if (tag == null || tag.isBlank() || ctx.provider() == null) {
            return null;
        }
        for (String slug : tag.split("\\|")) {
            String value = slug.trim();
            if (value.isEmpty() || ctx.provider().tagExists(value)) {
                continue;
            }
            return error(node, "标签 " + value + " 不存在",
                    "{cms:" + node.name() + "} tag='" + tag + "'",
                    "tag 写标签 slug，多值用 | 分隔（多值之间是 AND，§6.3）");
        }
        return null;
    }

    private PublishException checkAuthor(TagNode node, CompileContext ctx) {
        String author = node.arg("author");
        if (author == null || "self".equals(author) || ctx.provider() == null) {
            return null;
        }
        if (ctx.provider().authorExists(author)) {
            return null;
        }
        return error(node, "作者 " + author + " 不存在",
                "{cms:" + node.name() + "} author='" + author + "'",
                "author 写作者 slug 或 id；当前条目的作者写 author='self'（§6.3）");
    }

    private PublishException checkMenuCode(TagNode node, CompileContext ctx) {
        if (!"channel".equals(node.name()) || !"menu".equals(node.arg("source"))) {
            return null;
        }
        String code = node.arg("code");
        if (code == null || code.isBlank() || ctx.provider() == null) {
            return null;
        }
        List<String> codes = ctx.provider().menuCodes();
        if (codes.contains(code)) {
            return null;
        }
        return error(node, "菜单 " + code + " 不存在",
                "{cms:channel source='menu'} code='" + code + "'",
                "本站菜单有 " + Suggest.join(codes) + Suggest.hint(code, codes));
    }

    private static Set<String> typeCodes(CompileContext ctx) {
        Set<String> codes = new TreeSet<>();
        for (ContentTypeDef def : ctx.provider().types()) {
            codes.add(def.code());
        }
        return codes;
    }

    private static PublishException error(TagNode node, String what, String actual, String advice) {
        return PublishException.error(PublishErrorCode.E2006, what, node.sourcePath(), node.lineNo(),
                actual, advice);
    }
}
