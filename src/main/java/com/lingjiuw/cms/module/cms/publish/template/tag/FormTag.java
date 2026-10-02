package com.lingjiuw.cms.module.cms.publish.template.tag;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.EnumOption;
import com.lingjiuw.cms.module.cms.publish.model.FormDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.ParamSpec;
import com.lingjiuw.cms.module.cms.publish.template.RenderContext;
import com.lingjiuw.cms.module.cms.publish.template.TagHandler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateRenderer;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * {@code {cms:form}} —— 表单（static-publish.md §6.5，H 类：静态 HTML + 动态提交）。
 *
 * <p>渲染的是**规范 HTML**，五个部分一个不少：
 * <ol>
 *   <li>{@code <form method="post" action="/api/public/forms/{code}" data-cms-form="{code}">}；</li>
 *   <li>每个字段的 {@code <label>} 与 {@code <input|textarea|select>}（{@code required} /
 *       {@code placeholder} / {@code options} 来自表单定义，控件映射见 §9.5 的表）；</li>
 *   <li>{@code hidden} 解析出的隐藏域——<b>key 完全任意、不受校验</b>，<b>只有值</b>命中 5 个
 *       {@code self} 关键字才替换，其余按字面量输出且不报错（§6.5 明确：这是"写错静默"的唯一
 *       豁免点，因此不报错也不猜笔误）；</li>
 *   <li>蜜罐域（§9.9：视觉隐藏、{@code tabindex="-1"}、{@code autocomplete="off"}）；</li>
 *   <li>提交按钮 + 站点 id 隐藏域（{@code data-cms-site}，§11.1）。</li>
 * </ol>
 *
 * <p>{@code code} 指向的表单不存在 → E2008（编译期由 §4.5 第 12 条校验，这里渲染期兜底一次）。
 * 静态页里**不含验证码**（§6.5），需要时由 {@code cms.js} 加载。
 */
@Component
@RequiredArgsConstructor
public class FormTag implements TagHandler {

    /** §6.5 的封闭清单：只有这 5 个值会被替换，写在 key 的位置无效。 */
    private static final List<String> SELF_KEYWORDS = List.of(
            "contentId:self", "contentType:self", "contentUrl:self", "contentTitle:self", "pageUrl:self");

    @Nullable
    private final ContentProvider provider;

    @Override
    public String name() {
        return "form";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of(
                ParamSpec.required("code", "表单定义 code（cms_form，§9.5）"),
                ParamSpec.of("class", "附加到 <form> 的 class"),
                ParamSpec.of("hidden", "追加的隐藏域，k:v,k2:v2"),
                ParamSpec.of("submitLabel", "提交按钮文字（默认取表单定义）"),
                ParamSpec.boolOf("ajax", 1, "是否允许 cms.js 拦截为 fetch 提交；0 时强制原生 POST"));
    }

    /** 表单自己渲染完整 HTML，没有标签体。 */
    @Override
    public boolean needsBody() {
        return false;
    }

    /**
     * 没有标签体，也就没有"体内可用的字段"这回事：返回 {@code null} 让校验器把该帧当开放帧
     * （写不出迭代项的字段集合，硬给一个空集会把它变成"任何字段都报 E1004"）。
     */
    @Override
    public Set<String> bodyKeys(PageType pageType) {
        return null;
    }

    /** §6.7：除 feed 外都合法。 */
    @Override
    public int maxCount(PageType pageType) {
        return pageType.matrixColumn() == PageType.FEED ? 0 : -1;
    }

    @Override
    public void render(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        CurrentMarks.requireAllowed(this, node, ctx);

        String code = CurrentMarks.str(node, "code", null);
        if (code == null) {
            throw CurrentMarks.missingParam(this, node, "code");
        }
        FormDef form = db().form(code);
        if (form == null) {
            throw PublishException.error(PublishErrorCode.E2008, "表单 " + code + " 不存在",
                    node.sourcePath(), node.lineNo(),
                    "{cms:form} 的 code = '" + code + "'；本站表单有 " + formCodes(),
                    "表单 code 见后台「表单 → 表单定义」（§9.5）");
        }

        boolean ajax = CurrentMarks.boolOf(node, "ajax", true);
        String className = CurrentMarks.str(node, "class", null);
        out.append("<form method=\"post\" action=\"")
                .append(escape(renderer, "/api/public/forms/" + code)).append('"');
        if (ajax) {
            // §9.3 的挂载点：cms.js 按它拦截为 fetch 提交；ajax='0' 时不给挂载点 = 强制原生 POST
            out.append(" data-cms-form=\"").append(escape(renderer, code)).append('"');
        }
        if (className != null) {
            out.append(" class=\"").append(escape(renderer, className)).append('"');
        }
        if (hasFile(form)) {
            out.append(" enctype=\"multipart/form-data\"");
        }
        out.append(">\n");

        for (FormDef.FormField field : form.fields()) {
            renderField(field, renderer, out);
        }
        renderHidden(node, ctx, renderer, out);
        out.append("<div style=\"position:absolute;left:-9999px\" aria-hidden=\"true\">")
                .append("<input type=\"text\" name=\"_hp\" tabindex=\"-1\" autocomplete=\"off\"></div>\n");
        // §11.1 的产物烤 id：无 JS 时原生 POST 也带着它
        out.append("<input type=\"hidden\" name=\"siteId\" value=\"").append(db().siteId())
                .append("\" data-cms-site=\"").append(db().siteId()).append("\">\n");
        out.append("<button type=\"submit\">")
                .append(escape(renderer, CurrentMarks.str(node, "submitLabel", form.submitLabelOrDefault())))
                .append("</button>\n");
        out.append("</form>");
    }

    /* ---------------- 字段 → 控件（§9.5 的表） ---------------- */

    private void renderField(FormDef.FormField field, TemplateRenderer renderer, StringBuilder out) {
        String code = field.code();
        if ("hidden".equals(field.type())) {
            out.append("<input type=\"hidden\" name=\"").append(escape(renderer, code))
                    .append("\" value=\"\">\n");
            return;
        }
        if (field.textarea()) {
            out.append("<label for=\"").append(escape(renderer, code)).append("\">")
                    .append(escape(renderer, field.label())).append("</label>\n");
            out.append("<textarea id=\"").append(escape(renderer, code)).append("\" name=\"")
                    .append(escape(renderer, code)).append("\" rows=\"5\"")
                    .append(placeholder(renderer, field)).append(field.required() ? " required" : "")
                    .append("></textarea>\n");
            return;
        }
        if (field.select()) {
            out.append("<label for=\"").append(escape(renderer, code)).append("\">")
                    .append(escape(renderer, field.label())).append("</label>\n");
            out.append("<select id=\"").append(escape(renderer, code)).append("\" name=\"")
                    .append(escape(renderer, code)).append('"')
                    .append(field.required() ? " required" : "").append(">\n");
            for (EnumOption option : field.options()) {
                out.append("<option value=\"").append(escape(renderer, option.value())).append("\">")
                        .append(escape(renderer, option.label())).append("</option>\n");
            }
            out.append("</select>\n");
            return;
        }
        if ("checkbox".equals(field.type()) && !field.options().isEmpty()) {
            out.append("<label>").append(escape(renderer, field.label())).append("</label>\n");
            boolean first = true;
            for (EnumOption option : field.options()) {
                out.append("<label><input type=\"checkbox\" name=\"").append(escape(renderer, code))
                        .append("\" value=\"").append(escape(renderer, option.value())).append('"')
                        // 多选组里 required 只标第一个，否则等于要求全选
                        .append(field.required() && first ? " required" : "").append("> ")
                        .append(escape(renderer, option.label())).append("</label>\n");
                first = false;
            }
            return;
        }
        out.append("<label for=\"").append(escape(renderer, code)).append("\">")
                .append(escape(renderer, field.label())).append("</label>\n");
        out.append("<input type=\"").append(field.inputType()).append("\" id=\"")
                .append(escape(renderer, code)).append("\" name=\"").append(escape(renderer, code))
                .append('"').append(placeholder(renderer, field))
                .append(field.required() ? " required" : "").append(">\n");
    }

    private static String placeholder(TemplateRenderer renderer, FormDef.FormField field) {
        return field.placeholder() == null || field.placeholder().isBlank()
                ? "" : " placeholder=\"" + escape(renderer, field.placeholder()) + "\"";
    }

    private static boolean hasFile(FormDef form) {
        for (FormDef.FormField field : form.fields()) {
            if ("file".equals(field.type())) {
                return true;
            }
        }
        return false;
    }

    /* ---------------- hidden 的 5 个 self 关键字（§6.5） ---------------- */

    /**
     * 一段 {@code k:v}：**整段**就是关键字时（{@code hidden='contentId:self'}，§6.5 的示例写法），
     * 名字取冒号前那段（{@code contentId}）；否则按第一个冒号切成 key 与值，**值**命中关键字才替换
     * （{@code hidden='pid:contentId:self'}）。两种写法都在 §6.5 里出现过，因此两种都认；
     * 其余一律按字面量输出、不报错，key 也不做任何校验（"写错静默"的唯一豁免点）。
     */
    private void renderHidden(TagNode node, RenderContext ctx, TemplateRenderer renderer, StringBuilder out) {
        String hidden = CurrentMarks.str(node, "hidden", null);
        if (hidden == null) {
            return;
        }
        for (String pair : hidden.split(",", -1)) {
            String text = pair.trim();
            if (text.isEmpty()) {
                continue;
            }
            String key;
            String value;
            if (SELF_KEYWORDS.contains(text)) {
                key = text.substring(0, text.indexOf(':')).trim();
                value = selfValue(ctx, text);
            } else {
                int colon = text.indexOf(':');
                key = (colon < 0 ? text : text.substring(0, colon)).trim();
                value = selfValue(ctx, colon < 0 ? "" : text.substring(colon + 1).trim());
            }
            if (key.isEmpty()) {
                continue;
            }
            out.append("<input type=\"hidden\" name=\"").append(escape(renderer, key))
                    .append("\" value=\"").append(escape(renderer, value)).append("\">\n");
        }
    }

    /**
     * 值命中 5 个关键字才替换，其余按字面量输出（**不报错**，key 也不校验）。
     */
    private static String selfValue(RenderContext ctx, String raw) {
        if (!SELF_KEYWORDS.contains(raw)) {
            return raw;
        }
        ContentItem item = ctx.currentItem();
        return switch (raw) {
            case "contentId:self" -> item == null ? "" : String.valueOf(item.id());
            case "contentType:self" -> ctx.typeCode() == null ? "" : ctx.typeCode();
            // 内容自己的 URL（不含分页号，§2.2 的 canonical 口径）
            case "contentUrl:self" -> item == null || item.url() == null ? "" : item.url();
            case "contentTitle:self" -> item == null || item.title() == null ? "" : item.title();
            // 这一页的 URL（含分页号，§5.6 的 page.url）
            case "pageUrl:self" -> {
                String pageUrl = RenderContext.asString(ctx.pageVar("url"));
                yield pageUrl == null ? "" : pageUrl;
            }
            // 守卫已限定取值只能来自 SELF_KEYWORDS，走到这里说明清单与分支脱节
            default -> "";
        };
    }

    /** 所有动态文本与属性值都过渲染器的格式化出口（§5.2："转义只发生在字段输出时"）。 */
    private static String escape(TemplateRenderer renderer, String value) {
        return renderer.formatUndeclared(value, null, null);
    }

    private String formCodes() {
        List<String> codes = db().formCodes();
        return codes.isEmpty() ? "（一个都没有）" : String.join("、", codes);
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
