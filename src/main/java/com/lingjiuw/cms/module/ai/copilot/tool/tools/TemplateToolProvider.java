package com.lingjiuw.cms.module.ai.copilot.tool.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolArgs;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolGroup;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolProvider;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolResult;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolRisk;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolSpec;
import com.lingjiuw.cms.module.cms.dto.SiteFileContent;
import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishErrors;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentProvider;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.provider.DbContentProviderFactory;
import com.lingjiuw.cms.module.cms.publish.service.FileTemplateSource;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.TagRegistry;
import com.lingjiuw.cms.module.cms.publish.template.TemplateCompiler;
import com.lingjiuw.cms.module.cms.publish.template.TemplateValidator;
import com.lingjiuw.cms.module.cms.publish.template.ValidationReport;
import com.lingjiuw.cms.module.cms.service.SiteFileService;
import com.lingjiuw.cms.module.cms.service.SiteService;
import com.lingjiuw.cms.module.cms.service.ThemeService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 模板 / 站点文件工具（docs/ai-copilot.md §4.2、§4.6）。
 *
 * <p>读工具（主题列表、文件浏览、文件读取、语法速查、模板诊断）在 <b>TEMPLATE_READ</b> 组，
 * 默认暴露——只读、不写盘、无副作用，而且是 AI 理解站点的必要输入。
 *
 * <p>写工具（精确替换、新建、删除）在 <b>PLATFORM</b> 组，默认不暴露：它们属于"改整站形态"，
 * 写坏一个模板下一次全站发布会整体坏页。
 *
 * <p><b>刻意不提供整文件覆盖</b>（§4.6.3）：精确替换天然有乐观锁（{@code oldText} 不唯一匹配
 * 就拒绝）、diff 就是 oldText/newText（前端直接渲染）、token 成本只按改动片段算。
 * 回滚也不靠备份文件——旧内容天然留在 {@code ai_chat_message.tool_args} 里
 * （往 {@code template/} 放 {@code .bak} 会被 {@code copyStatic} 拷进 {@code www/} 跟着发布出去）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateToolProvider implements ToolProvider {

    private static final String READ_PERMISSION = "cms:site:file:list";
    /** 模板诊断的默认页面类型：多数模板是列表/详情模板，标签名、块配对、编码这类错误与页面类型无关 */
    private static final PageType DEFAULT_PAGE_TYPE = PageType.LIST;

    private final ToolArgs toolArgs;
    private final ObjectMapper objectMapper;
    private final ThemeService themeService;
    private final SiteFileService siteFileService;
    private final SiteService siteService;
    private final DbContentProviderFactory providerFactory;
    private final TagRegistry tagRegistry;
    private final List<TemplateValidator> validators;

    /** 语法速查：类路径资源，只在首次调用时读入内存 */
    private volatile String syntaxCache;

    private record Empty() {
    }

    private record PathArgs(@NotBlank(message = "文件路径不能为空") String path) {
    }

    private record CheckArgs(
            @NotBlank(message = "模板路径不能为空") @Size(max = 255, message = "模板路径最长 255 字符") String path,
            String pageType,
            String typeCode) {
    }

    private record ReplaceArgs(
            @NotBlank(message = "文件路径不能为空") String path,
            @NotBlank(message = "oldText 不能为空") String oldText,
            @NotBlank(message = "newText 不能为空") String newText) {
    }

    private record CreateArgs(
            String parent,
            @NotBlank(message = "文件名不能为空") @Size(max = 64, message = "文件名最长 64 字符") String name,
            @NotBlank(message = "文件内容不能为空") String content) {
    }

    @Override
    public List<ToolSpec> tools() {
        return List.of(themeList(), fileList(), fileRead(), syntaxTool(), templateCheck(),
                fileReplace(), fileCreate(), fileDelete());
    }

    private ToolSpec themeList() {
        return ToolSpec.builder("cms_theme_list")
                .title("查询主题列表")
                .description("列出当前站点 template/ 下的主题（目录即主题，元数据在 theme.json）。")
                .permission("cms:theme:list")
                .risk(ToolRisk.READ)
                .group(ToolGroup.TEMPLATE_READ)
                .schema(toolArgs.schemaOf(Empty.class))
                .handler(args -> {
                    var themes = themeService.list();
                    return ToolResult.ok("共 " + themes.size() + " 个主题",
                            objectMapper.valueToTree(themes));
                })
                .build();
    }

    private ToolSpec fileList() {
        return ToolSpec.builder("cms_site_file_list")
                .title("列出站点目录")
                .description("列出当前站点目录下某个路径的直接子目录与文件。path 留空表示站点目录本身；"
                        + "站点模板在 template/<主题>/ 下，发布产物在 www/ 下。"
                        + "kind=TEXT 的可以读与精确替换，IMAGE 只能看信息，OTHER 不支持编辑。")
                .permission(READ_PERMISSION)
                .risk(ToolRisk.READ)
                .group(ToolGroup.TEMPLATE_READ)
                .schema(toolArgs.schemaOf(PathArgs.class))
                .handler(args -> {
                    PathArgs request = toolArgs.bind(args, PathArgs.class);
                    var listing = siteFileService.list(request.path());
                    return ToolResult.ok("共 " + listing.dirs().size() + " 个目录、"
                            + listing.files().size() + " 个文件", objectMapper.valueToTree(listing));
                })
                .build();
    }

    private ToolSpec fileRead() {
        return ToolSpec.builder("cms_site_file_read")
                .title("读取站点文件")
                .description("读取当前站点目录下的一个文本文件（模板、样式、脚本、json 等）。"
                        + "图片只返回文件信息，不回 base64 内容。")
                .permission(READ_PERMISSION)
                .risk(ToolRisk.READ)
                .group(ToolGroup.TEMPLATE_READ)
                .schema(toolArgs.schemaOf(PathArgs.class))
                .handler(args -> {
                    PathArgs request = toolArgs.bind(args, PathArgs.class);
                    SiteFileContent content = siteFileService.read(request.path());
                    ObjectNode data = objectMapper.createObjectNode();
                    data.put("path", content.path());
                    data.put("name", content.name());
                    data.put("size", content.size());
                    data.put("kind", content.kind());
                    if (content.text() != null) {
                        data.put("text", content.text());
                    } else {
                        data.put("note", "IMAGE".equals(content.kind())
                                ? "图片文件，只返回文件信息" : "非文本文件，不支持在线读取");
                    }
                    return ToolResult.ok("已读取 " + content.path() + "（" + content.size() + " 字节）", data);
                })
                .build();
    }

    private ToolSpec syntaxTool() {
        return ToolSpec.builder("cms_template_syntax")
                .title("模板语法速查")
                .description("返回本站模板引擎的完整语法速查：5 种词法元素、参数语法与转义、标签总表与参数表、"
                        + "标签 × 页面类型合法性矩阵、刻意不提供的标签。写模板前先调它。")
                .permission(null)
                .risk(ToolRisk.READ)
                .group(ToolGroup.TEMPLATE_READ)
                .schema(toolArgs.schemaOf(Empty.class))
                .handler(args -> {
                    String text = syntaxText();
                    ObjectNode data = objectMapper.createObjectNode();
                    data.put("syntax", text);
                    return ToolResult.ok("模板语法速查（约 " + text.length() + " 字）", data);
                })
                .build();
    }

    private ToolSpec templateCheck() {
        return ToolSpec.builder("cms_template_check")
                .title("模板结构诊断")
                .description("按模板引擎的编译期规则体检一个模板文件：返回**文件:行号 + 错误码 + 错误说明 + "
                        + "怎么改**的结构化诊断，以及警告。改完模板应当调它确认。"
                        + "path 相对主题目录（如 index.html / list.html / detail.html / _partials/header.html），"
                        + "也接受站点目录里的完整写法 template/<主题>/index.html（会自动剥掉前缀）。"
                        + "pageType 决定校验上下文（HOME/LIST/DETAIL/SINGLE/ARCHIVE/TAGLIST/SEARCH/404/feed…），"
                        + "默认 LIST；typeCode 是该模板服务的内容类型标识，可留空。")
                .permission("cms:publish:run")
                .risk(ToolRisk.READ)
                .group(ToolGroup.TEMPLATE_READ)
                .schema(toolArgs.schemaOf(CheckArgs.class))
                .handler(args -> {
                    CheckArgs request = toolArgs.bind(args, CheckArgs.class);
                    return check(request);
                })
                .build();
    }

    private ToolSpec fileReplace() {
        return ToolSpec.builder("cms_site_file_replace")
                .title("精确替换站点文件片段")
                .description("在当前站点的文本文件里做一次精确替换：oldText 必须**唯一匹配**，"
                        + "匹配到 0 处或 2 处以上都会拒绝（这是天然的乐观锁，防止并发/误覆盖）。"
                        + "只传改动片段，不要重写整个文件。写模板用这个，不要用它改 www/ 下的产物。")
                .permission("cms:site:file:edit")
                .risk(ToolRisk.DESTRUCTIVE)
                .group(ToolGroup.PLATFORM)
                .schema(toolArgs.schemaOf(ReplaceArgs.class))
                .handler(args -> {
                    ReplaceArgs request = toolArgs.bind(args, ReplaceArgs.class);
                    if (request.oldText().equals(request.newText())) {
                        throw new BizException("oldText 与 newText 相同，没有需要改动的内容");
                    }
                    SiteFileContent file = siteFileService.read(request.path());
                    if (file.text() == null) {
                        throw new BizException("只能替换文本文件：" + file.path());
                    }
                    int matches = countOccurrences(file.text(), request.oldText());
                    if (matches == 0) {
                        throw new BizException("oldText 在文件中找不到（注意空格、缩进与换行必须完全一致）：" + file.path());
                    }
                    if (matches > 1) {
                        throw new BizException("oldText 在文件中出现 " + matches + " 次，不唯一匹配；"
                                + "请带上更多上下文让它唯一");
                    }
                    siteFileService.save(file.path(), file.text().replace(request.oldText(), request.newText()));
                    ObjectNode data = objectMapper.createObjectNode();
                    data.put("path", file.path());
                    data.put("replaced", 1);
                    data.put("oldLines", lineCount(request.oldText()));
                    data.put("newLines", lineCount(request.newText()));
                    return ToolResult.ok("已替换 " + file.path(), data);
                })
                .build();
    }

    private ToolSpec fileCreate() {
        return ToolSpec.builder("cms_site_file_create")
                .title("新建站点文件")
                .description("在当前站点目录下新建一个文本文件并写入内容。parent 是相对站点目录的上级路径"
                        + "（如 template/mint/_partials），留空表示站点目录本身。已存在同名文件会失败。")
                .permission("cms:site:file:add")
                .risk(ToolRisk.DESTRUCTIVE)
                .group(ToolGroup.PLATFORM)
                .schema(toolArgs.schemaOf(CreateArgs.class))
                .handler(args -> {
                    CreateArgs request = toolArgs.bind(args, CreateArgs.class);
                    String path = siteFileService.createFile(request.parent(), request.name());
                    siteFileService.save(path, request.content());
                    ObjectNode data = objectMapper.createObjectNode();
                    data.put("path", path);
                    return ToolResult.ok("已新建文件 " + path, data);
                })
                .build();
    }

    private ToolSpec fileDelete() {
        return ToolSpec.builder("cms_site_file_delete")
                .title("删除站点文件")
                .description("删除当前站点目录下的一个文件或**空**文件夹（非空文件夹需先清空）。"
                        + "站点目录本身不能删。")
                .permission("cms:site:file:delete")
                .risk(ToolRisk.DESTRUCTIVE)
                .group(ToolGroup.PLATFORM)
                .schema(toolArgs.schemaOf(PathArgs.class))
                .handler(args -> {
                    PathArgs request = toolArgs.bind(args, PathArgs.class);
                    siteFileService.delete(request.path());
                    return ToolResult.ok("已删除 " + request.path(), null);
                })
                .build();
    }

    /* ---------------- 模板诊断 ---------------- */

    /**
     * 结构化体检：直接注入 {@code List<TemplateValidator>} 跑一遍编译器，
     * 把 {@link PublishException} 原样映射成"文件 / 行号 / 错误码 / 说明 / 实际值 / 建议"。
     *
     * <p>为什么不用 {@code PublishFacade.preview()}：它把问题拍平成 {@code List<String>}，
     * 而 {@code PublishException} 里其实带着 {@code templatePath / lineNo / actual / advice}。
     * AI 需要的是可定位、可修改的诊断，一句中文串不够（§4.6.2）。
     */
    private ToolResult check(CheckArgs request) {
        long siteId = SiteContext.siteId();
        ContentProvider provider = providerFactory.forSite(siteId);
        String theme = provider.site().theme() == null || provider.site().theme().isBlank()
                ? "_default" : provider.site().theme();
        Path themeRoot = siteService.siteDir(siteId).resolve("template").resolve(theme);
        PageType pageType = parsePageType(request.pageType());
        // AI 既能给"主题内相对路径"，也能给 cms_site_file_list 看到的"站点目录里的完整路径"；
        // 不剥前缀时后者会被拼成 template/<theme>/template/<theme>/x 而报"模板不存在"
        String templatePath = themeRelative(request.path(), theme);

        TemplateCompiler compiler = new TemplateCompiler(new FileTemplateSource(themeRoot),
                tagRegistry, validators);
        ValidationReport report = new ValidationReport();
        List<PublishException> errors = new ArrayList<>();
        try {
            compiler.compile(templatePath, new CompileContext(siteId, pageType, blankToNull(request.typeCode()), provider), report);
        } catch (PublishErrors e) {
            errors.addAll(e.errors());
        } catch (PublishException e) {
            errors.add(e);
        } catch (RuntimeException e) {
            // FileTemplateSource 读文件失败等：不吞掉，转成一条可读的诊断
            errors.add(PublishException.error(PublishErrorCode.E4002, "模板体检失败",
                    templatePath, 0, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    "确认文件存在且是 UTF-8 文本"));
        }

        ObjectNode data = objectMapper.createObjectNode();
        data.put("path", templatePath);
        data.put("theme", theme);
        data.put("pageType", pageType.name());
        data.put("typeCode", blankToNull(request.typeCode()));
        ArrayNode problems = data.putArray("errors");
        for (PublishException error : errors) {
            problems.add(problemNode(error));
        }
        ArrayNode warnings = data.putArray("warnings");
        for (ValidationReport.Warning warning : report.warnings()) {
            ObjectNode row = warnings.addObject();
            row.put("code", warning.code().name());
            row.put("codeSummary", warning.code().summary());
            row.put("message", warning.message());
        }
        data.put("ok", errors.isEmpty());
        String summary = errors.isEmpty()
                ? "体检通过：" + templatePath + "（警告 " + warnings.size() + " 条）"
                : "体检发现 " + errors.size() + " 个错误：" + templatePath;
        return ToolResult.ok(summary, data);
    }

    /**
     * 把用户给的路径归一成"相对主题目录"的形式：剥掉前导 {@code /}，以及站点目录里的完整前缀
     * {@code template/<主题>/}。
     *
     * <p>只剥 {@code template/<主题>/} 这一整段，不单独剥 {@code template/}：
     * 后者会让主题里一个真叫 {@code template} 的子目录被误伤，而且"只写 template/ 不带主题名"
     * 本身也分不清指的是哪个主题。
     */
    static String themeRelative(String path, String theme) {
        String value = path == null ? "" : path.trim().replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        String prefix = "template/" + (theme == null || theme.isBlank() ? "_default" : theme) + "/";
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private ObjectNode problemNode(PublishException error) {
        ObjectNode row = objectMapper.createObjectNode();
        PublishErrorCode code = error.code();
        row.put("code", code == null ? null : code.name());
        row.put("codeSummary", code == null ? null : code.summary());
        row.put("file", error.templatePath());
        row.put("line", error.lineNo());
        row.put("message", error.getMessage());
        if (error.actual() != null) {
            row.put("actual", error.actual());
        }
        if (error.advice() != null) {
            row.put("advice", error.advice());
        }
        if (!error.includeChain().isEmpty()) {
            row.set("includeChain", objectMapper.valueToTree(error.includeChain()));
        }
        return row;
    }

    /* ---------------- 杂项 ---------------- */

    private String syntaxText() {
        String cached = syntaxCache;
        if (cached != null) {
            return cached;
        }
        try {
            cached = new ClassPathResource("copilot/template-syntax.md")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException("模板语法速查缺失：copilot/template-syntax.md（" + e.getMessage() + "）");
        }
        syntaxCache = cached;
        return cached;
    }

    /** 页面类型：接受枚举名，以及 {@code 404} / {@code feed} 两个对外别名。 */
    private static PageType parsePageType(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PAGE_TYPE;
        }
        String name = value.trim().toUpperCase(Locale.ROOT);
        if ("404".equals(name)) {
            return PageType.PAGE404;
        }
        if ("FEED".equals(name)) {
            return PageType.FEED;
        }
        try {
            return PageType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new BizException("未知的 pageType：" + value + "（可用：HOME/LIST/TAGLIST/TAGPAGE/ARCHIVE/DETAIL/DPAGE/SINGLE/FACET/SEARCH/STATIC/404/feed）");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static int lineCount(String text) {
        return text.split("\n", -1).length;
    }
}
