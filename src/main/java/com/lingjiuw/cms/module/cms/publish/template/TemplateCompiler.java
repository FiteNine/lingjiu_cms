package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishErrors;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.BuiltinFields;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.Node;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;
import com.lingjiuw.cms.module.cms.publish.template.ast.TextNode;
import com.lingjiuw.cms.module.cms.publish.template.TemplateSource.Template;
import com.lingjiuw.cms.module.cms.publish.template.validate.TemplateEncoding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板编译器（static-publish.md §4.1、§4.4、§4.5、§5.3）。
 *
 * <p>四件事，顺序不能换：
 * <ol>
 *   <li><b>include 编译期展开</b>（§5.3）：{@code file} 是纯字面量，因此可以完全静态展开。
 *       深度上限 10、路径栈查循环、越界即报 E1006，且**报错一律附包含链**。
 *       展开后节点仍带**片段内的真实路径与行号**（{@link Node#sourcePath()} / {@link Node#lineNo()}），
 *       不换成包含者的位置；</li>
 *   <li><b>{@code {cms:else/}} 绑定</b>（§3.5 裁定四）：把 else 节点从外层 {@code {cms:if}} 的
 *       直接子节点里摘掉、转成 {@link TagNode#elseIndex()}。必须在展开**之后**做——片段里的
 *       {@code {cms:else/}} 允许绑到包含它的那个 {@code {cms:if}}（§5.3："片段被单独编译时不报位置错"）；</li>
 *   <li><b>位置类信息收敛</b>（§4.5 口径表）：命名查询、分页主体（至多一个）在这里定下来，
 *       写进 {@link TemplateAst}。数量与位置的**对错**由校验器判（E3001/E3002），编译器只做"零或一"的收敛；</li>
 *   <li><b>编译期校验</b>（§4.5 的 18 条）：按 {@link TemplateValidator#order()} 依次跑。</li>
 * </ol>
 *
 * <p><b>编译缓存</b>（§4.4）：key = {@code 站点 id + 模板相对路径 + defVersion + 上下文签名}；
 * 失效判定含 {@code mtime + size + defVersion + astVersion}。{@code astVersion} 是自身与 include 链上
 * 每一段的 {@code 路径 + mtime + size + sha256} 的累加（v2.2 定死）——只按 size 会漏掉"同长度替换片段"。
 * 本类**不做文件监听**：每次调用都按记录下来的链逐一比对指纹，指纹一致才算命中。
 *
 * <p>编译产物不可变，因此用 {@link ConcurrentHashMap} 共享天然线程安全（§4.4）。
 * 本类**不是 Spring Bean**：它依赖 {@link TemplateSource}，而期 1 没有该接口的 Bean
 * （真实模板查找是期 2 / 期 6 的活），加 {@code @Component} 会让应用起不来。
 */
public final class TemplateCompiler {

    /** include 递归深度上限（§5.3："递归深度上限 10 层"）。 */
    public static final int MAX_INCLUDE_DEPTH = 10;

    /**
     * 短路阈值：**只有第 1 条（标签名）失败时停止后续校验**。
     *
     * <p>标签名不认识 → 这个节点的参数表、字段集合、页面类型合法性全都无从谈起，继续跑只会产出
     * 一串连带的假错误（级联噪音）。其余校验器之间继续累加——§10.3 第 2 条要的是"一次把全部错误
     * 返回"，"改一处、跑一次"变成几十轮正是它要消灭的东西（第 2 条参数、第 3 条结构各自只影响
     * 自己那一个节点，不构成"后续无从判"）。
     */
    private static final int ABORT_ORDER = 1;

    private final TemplateSource source;
    private final TagRegistry registry;
    private final List<TemplateValidator> validators;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    public TemplateCompiler(TemplateSource source, TagRegistry registry, List<TemplateValidator> validators) {
        this.source = Objects.requireNonNull(source, "TemplateSource");
        this.registry = registry == null ? new TagRegistry(List.of()) : registry;
        List<TemplateValidator> sorted = new ArrayList<>(validators == null ? List.of() : validators);
        sorted.sort(Comparator.comparingInt(TemplateValidator::order));
        this.validators = List.copyOf(sorted);
    }

    /**
     * 编译一个模板。命中缓存时直接返回同一个（不可变的）AST，并把它上次记下的警告重放进
     * {@code report}。
     *
     * @param templatePath 相对主题根目录的模板路径（§7.3）
     * @param ctx          编译上下文（页面类型 + 类型定义 + defVersion）
     * @param report       警告收集处；可以传 {@code null}（W5001 这类警告就丢掉）
     */
    public TemplateAst compile(String templatePath, CompileContext ctx, ValidationReport report) {
        String key = cacheKey(templatePath, ctx);
        Entry cached = cache.get(key);
        if (cached != null && fresh(cached, ctx)) {
            replay(cached, report);
            return cached.ast;
        }
        ValidationReport sink = report == null ? new ValidationReport() : report;

        List<String> chain = new ArrayList<>();
        List<String> fingerprints = new ArrayList<>();
        Map<String, List<String>> reachedBy = new LinkedHashMap<>();
        List<Node> nodes = bind(expand(templatePath, null, chain, fingerprints, reachedBy, 0));

        Collected collected = collect(nodes, ctx);
        String astVersion = String.join(";", fingerprints);
        TemplateAst ast = new TemplateAst(templatePath, nodes, collected.namedQueries,
                collected.paginationBody, collected.paginationKind, astVersion);
        int warningsBefore = sink.warnings().size();
        runValidators(ast, ctx, sink, reachedBy);

        // 只把**本次编译新增**的警告写进缓存条目：调用方可以复用一个 ValidationReport
        // （先编译 A 再编译 B），整份 sink.warnings() 会把上一条模板的警告错挂到这条记录上，
        // 之后每次命中都把它重放进新的报告（与错误口径对齐）
        List<ValidationReport.Warning> warnings = sink.warnings();
        cache.put(key, new Entry(ast, List.copyOf(warnings.subList(warningsBefore, warnings.size())),
                List.copyOf(fingerprints), astVersion, ctx.defVersion().key()));
        evictSuperseded(templatePath, ctx, key);
        return ast;
    }

    /** 不需要警告的调用方用这个重载（§4.5 第 18 条 W5001 只进 {@link ValidationReport}）。 */
    public TemplateAst compile(String templatePath, CompileContext ctx) {
        return compile(templatePath, ctx, null);
    }

    /* ---------------- 缓存 ---------------- */

    private record Entry(TemplateAst ast, List<ValidationReport.Warning> warnings,
                         List<String> fingerprints, String astVersion, String defVersionKey) {
    }

    /** §4.4：{@code 站点 id + 模板相对路径 + defVersion + 上下文签名}。 */
    static String cacheKey(String templatePath, CompileContext ctx) {
        return ctx.siteId() + "|" + templatePath + "|" + ctx.defVersion().key() + "|" + ctx.signature();
    }

    /**
     * 失效判定（§4.4）：defVersion 变了、或链上任何一段的 {@code mtime + size + sha256} 变了
     * （= 重算出的 astVersion 与记录不一致）→ 重新编译。
     */
    private boolean fresh(Entry entry, CompileContext ctx) {
        if (!entry.defVersionKey.equals(ctx.defVersion().key())) {
            return false;
        }
        List<String> current = new ArrayList<>(entry.fingerprints.size());
        for (String path : chainOf(entry)) {
            Template template = source.load(path);
            if (template == null) {
                return false;
            }
            current.add(template.fingerprint());
        }
        return String.join(";", current).equals(entry.astVersion);
    }

    /**
     * 链上的路径清单：指纹串里已经带了路径（{@code path|mtime|size|sha256}），
     * 直接取第一段即可，不必另存一份路径列表。
     *
     * <p><b>依赖一条前提</b>：模板相对路径里不能含 {@code |}——这由 §11.4 的路径边界与
     * {@code SitePathBoundary.checkName} 保证（文件名不允许 {@code |}）。若哪天放宽文件名规则，
     * 这里会静默取到错的路径，届时要改成在 {@code Entry} 里另存路径列表。
     */
    private static List<String> chainOf(Entry entry) {
        List<String> paths = new ArrayList<>(entry.fingerprints.size());
        for (String fingerprint : entry.fingerprints) {
            paths.add(fingerprint.substring(0, fingerprint.indexOf('|')));
        }
        return paths;
    }

    /**
     * defVersion 进 key 的代价是"每改一次字段定义就多一条缓存记录"，旧记录再也不会被命中。
     * 这里只淘汰**同站点、同模板、同上下文签名**下更旧的 defVersion 记录——签名必须留在淘汰条件里：
     * 同一个 {@code list.html} 会被 HOME / LIST 等多个上下文共用（§4.4 把签名加进 key 的理由），
     * 按"站点 + 路径"淘汰会让两个上下文互相驱逐、每次都 miss。
     */
    private void evictSuperseded(String templatePath, CompileContext ctx, String keepKey) {
        String prefix = ctx.siteId() + "|" + templatePath + "|";
        String suffix = "|" + ctx.signature();
        cache.keySet().removeIf(existing -> existing.startsWith(prefix) && existing.endsWith(suffix)
                && !existing.equals(keepKey));
    }

    private static void replay(Entry entry, ValidationReport report) {
        if (report == null) {
            return;
        }
        for (ValidationReport.Warning warning : entry.warnings) {
            report.warn(warning.code(), warning.message());
        }
    }

    /* ---------------- ① include 编译期展开（§5.3） ---------------- */

    /**
     * 展开一个模板文件：读源码 → 查编码（§4.5 第 17 条）→ 解析 → 递归展开其中的 include。
     *
     * @param path         模板相对路径（入口模板按主题根；片段是 {@link TemplateSource#fragmentPath} 解析后的路径）
     * @param from         请求展开它的那个 include 节点；入口模板为 null（片段读不到时的报错位置用）
     * @param chain        当前包含链（入口模板在前，正在展开的模板在最后）
     * @param fingerprints astVersion 的累加顺序：入口在前，片段按发现顺序追加（§4.4）
     * @param reachedBy    片段路径 → 首次到达它的包含链，供校验期给片段内的报错补包含链
     * @param depth        已经展开的 include 层数
     */
    private List<Node> expand(String path, TagNode from, List<String> chain, List<String> fingerprints,
                              Map<String, List<String>> reachedBy, int depth) {
        Template template = source.load(path);
        if (template == null) {
            if (from == null) {
                throw PublishException.error(PublishErrorCode.E4002,
                        "模板不存在：" + path, null, 0,
                        "按路径 " + path + " 读不到模板",
                        "模板查找规则见 §7.3；至少提供 list.html 作为兜底");
            }
            // 位置指向**写出这个 include 的那一行**（§10.1：模板错误必须带 模板路径:行号）
            throw PublishException.error(PublishErrorCode.E1006,
                    "include 的片段不存在：" + path, from.sourcePath(), from.lineNo(),
                    "片段 " + path + " 读不到",
                    "检查文件名拼写；片段目录约定见 §7.3 的 _partials/")
                    .withIncludeChain(append(chain, path));
        }
        fingerprints.add(template.fingerprint());
        chain.add(path);
        if (chain.size() > 1) {
            reachedBy.putIfAbsent(path, List.copyOf(chain));
        }
        try {
            String text = TemplateEncoding.check(template.source(), path);
            return expandNodes(TemplateParser.parse(text, path), chain, fingerprints, reachedBy, depth);
        } catch (PublishException e) {
            // §5.3：片段内的报错要附包含链，位置仍指向片段内的真实行号
            if (chain.size() > 1 && e.includeChain().isEmpty()) {
                throw e.withIncludeChain(List.copyOf(chain));
            }
            throw e;
        } finally {
            chain.remove(chain.size() - 1);
        }
    }

    private List<Node> expandNodes(List<Node> nodes, List<String> chain, List<String> fingerprints,
                                   Map<String, List<String>> reachedBy, int depth) {
        List<Node> expanded = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            if (node instanceof TagNode tag && "include".equals(tag.name())) {
                expanded.addAll(expandInclude(tag, chain, fingerprints, reachedBy, depth));
                continue;
            }
            if (node instanceof TagNode tag && tag.body() != null) {
                List<Node> body = expandNodes(tag.body(), chain, fingerprints, reachedBy, depth);
                expanded.add(new TagNode(tag.name(), tag.args(), body, tag.elseIndex(),
                        tag.lineNo(), tag.sourcePath()));
                continue;
            }
            expanded.add(node);
        }
        return expanded;
    }

    private List<Node> expandInclude(TagNode include, List<String> chain, List<String> fingerprints,
                                     Map<String, List<String>> reachedBy, int depth) {
        String file = include.arg("file");
        if (file == null || file.isBlank()) {
            throw PublishException.error(PublishErrorCode.E1002,
                    "include 缺少必填参数 file", include.sourcePath(), include.lineNo(),
                    "{cms:include} 没有 file 参数",
                    "写法是 {cms:include file='_partials/header.html'/}");
        }
        if (include.body() != null) {
            throw PublishException.error(PublishErrorCode.E1003,
                    "{cms:include} 不接受标签体", include.sourcePath(), include.lineNo(),
                    "第 " + include.lineNo() + " 行的 {cms:include} 写了 …{/cms:include}",
                    "include 是自闭合标签，写成 {cms:include file='…'/}");
        }
        // §5.1 / §6.2：include 的参数名不得与 6 个保留名同名。这条必须在**展开之前**查——
        // 展开后 include 节点就不在树上了，校验器看不到它。
        for (String key : include.args().keySet()) {
            if (BuiltinFields.RESERVED_NAMES.contains(key)) {
                throw PublishException.error(PublishErrorCode.E1002,
                        "参数名 " + key + " 是保留名", include.sourcePath(), include.lineNo(),
                        "{cms:include} 的参数 " + key + "='" + include.arg(key) + "'",
                        "保留名 " + String.join(" ", BuiltinFields.RESERVED_NAMES)
                                + " 不能作为 include 的参数名（§5.1、§6.2）");
            }
        }
        assertLegalIncludePath(file, include);
        // §5.3：file 相对**当前主题的片段目录**解析（§7.3 的 _partials/）。主题布局是
        // TemplateSource 的知识，编译器不拼字符串；循环检测、包含链与 astVersion 指纹
        // 一律用解析后的真实路径，这样报错与缓存都指向真正的文件。
        String fragment = source.fragmentPath(file);
        if (chain.contains(fragment)) {
            // what 里给"循环的那一段"（§10.2 的 E1006 文案："a.html → b.html → a.html"），
            // 包含链给从入口模板一路到重复点的完整路径（§5.3：便于从片段找回入口）
            List<String> cycle = new ArrayList<>(chain.subList(chain.indexOf(fragment), chain.size()));
            cycle.add(fragment);
            throw PublishException.error(PublishErrorCode.E1006,
                    "include 循环", include.sourcePath(), include.lineNo(),
                    String.join(" → ", cycle),
                    "把公共部分抽到第三个片段里，不要让 A 与 B 互相包含")
                    .withIncludeChain(append(chain, fragment));
        }
        if (depth + 1 > MAX_INCLUDE_DEPTH) {
            throw PublishException.error(PublishErrorCode.E1006,
                    "include 超过 " + MAX_INCLUDE_DEPTH + " 层", include.sourcePath(), include.lineNo(),
                    "当前已经展开 " + (depth + 1) + " 层：" + String.join(" → ", append(chain, fragment)),
                    "片段层级压到 " + MAX_INCLUDE_DEPTH + " 层以内（§5.3）")
                    .withIncludeChain(append(chain, fragment));
        }
        List<Node> body = expand(fragment, include, chain, fingerprints, reachedBy, depth + 1);
        // §5.3：其余参数是字面量，压入名为 param 的作用域供片段内 [field:param.x/] 读取。
        // 展开后立刻做字面量替换，于是"内层 include 的同名参数覆盖外层"自然成立（就近解析）：
        // 内层片段里的 param.x 在更早的展开点就已经变成文本了。
        return substituteParams(body, include.args());
    }

    /**
     * 把片段里的 {@code [field:param.x/]} 换成它的字面量（§5.3：include 的参数都是字面量，
     * 可以完全静态展开）。递归到展开后的整棵子树上，嵌套 include 的"同名参数取最内层"自动成立。
     *
     * <p>{@code file} 不是"其余参数"，不进 param 作用域（§6.2 的参数表把 {@code file} 单列）。
     *
     * <p><b>一条如实记录的限制</b>：只覆盖 {@code [field:param.x/]}（§5.3、§6.2 的文档用法）。
     * {@code {cms:if field='param.x'}} 这类**运行时**读 param 作用域的写法仍然取不到值——
     * 要支持它得让 {@link TemplateAst} 携带"展开点 → 参数表"的旁表、由渲染器 push/pop 作用域，
     * 那是更大的改动，期 1 不做（见报告）。
     */
    private static List<Node> substituteParams(List<Node> nodes, Map<String, String> args) {
        List<Node> out = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            if (node instanceof FieldNode field && field.path().size() == 2
                    && "param".equals(field.path().get(0)) && !"file".equals(field.path().get(1))
                    && args.containsKey(field.path().get(1))) {
                out.add(new TextNode(args.get(field.path().get(1)), field.lineNo(), field.sourcePath()));
                continue;
            }
            if (node instanceof TagNode tag && tag.body() != null) {
                out.add(new TagNode(tag.name(), substituteArgs(tag.args(), args),
                        substituteParams(tag.body(), args), tag.elseIndex(), tag.lineNo(),
                        tag.sourcePath()));
                continue;
            }
            if (node instanceof TagNode tag) {
                out.add(new TagNode(tag.name(), substituteArgs(tag.args(), args), null,
                        tag.elseIndex(), tag.lineNo(), tag.sourcePath()));
                continue;
            }
            out.add(node);
        }
        return out;
    }

    /**
     * 标签**属性值**里的 {@code [field:param.x/]} 也换成字面量，例如
     * {@code {cms:query type='service' where='group:eq:[field:param.group/]'}}。
     *
     * <p>为什么必须有这一段：标签属性在**解析期**就固定下来（模板里的参数一律是字面量，§3.3），
     * 只有"文本替换"能在编译期把参数塞进去。少了它，参数化片段就只能参数化可见文案，
     * 参数化不了查询条件——而"一栏服务"这种片段恰恰是靠 {@code where} 区分的，
     * 结果就是同一段标记要按栏复制 4 份。
     *
     * <p>只替换出现在实参表里的名字（且没有 {@code param.x} 这种非 include 来源），
     * 其余属性原样保留；没写标记的属性不动。
     */
    private static Map<String, String> substituteArgs(Map<String, String> args,
                                                      Map<String, String> params) {
        if (args.isEmpty() || params.isEmpty()) {
            return args;
        }
        Map<String, String> replaced = null;
        for (Map.Entry<String, String> entry : args.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.indexOf("[field:param.") < 0) {
                continue;
            }
            String text = value;
            for (Map.Entry<String, String> param : params.entrySet()) {
                if ("file".equals(param.getKey()) || param.getValue() == null) {
                    continue;
                }
                text = text.replace("[field:param." + param.getKey() + "/]", param.getValue());
            }
            if (!text.equals(value)) {
                if (replaced == null) {
                    replaced = new LinkedHashMap<>(args);
                }
                replaced.put(entry.getKey(), text);
            }
        }
        return replaced == null ? args : replaced;
    }

    /**
     * include 的路径边界（§5.3 走 {@code SitePathBoundary}，§11.4）。编译器拿不到主题根目录
     * （{@link TemplateSource} 只暴露相对路径），因此这里做**语法层的拦截**：绝对路径、盘符、
     * {@code ..} 上跳、反斜杠、URL 编码分隔符一律拒绝；符号链接检查由 {@code TemplateSource}
     * 的实现（期 2）走 {@code SitePathBoundary.assertNoSymlink} 补上。
     */
    private static void assertLegalIncludePath(String file, TagNode include) {
        String lower = file.toLowerCase(java.util.Locale.ROOT);
        // `..` 只有作为**独立路径段**才是上跳：contains("..") 会连 a..b.html 这类合法文件名一起拒掉
        boolean upLevel = false;
        for (String segment : file.split("/", -1)) {
            if ("..".equals(segment)) {
                upLevel = true;
                break;
            }
        }
        boolean illegal = file.startsWith("/") || file.startsWith("\\")
                // 盘符只在"盘符 + 分隔符"时才拦：POSIX 上 a:b.html 是合法文件名
                || file.matches("^[A-Za-z]:[\\\\/].*")
                || file.contains("\\")
                || upLevel
                || lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c");
        if (illegal) {
            throw PublishException.error(PublishErrorCode.E1006,
                    "include 路径越界", include.sourcePath(), include.lineNo(),
                    "file='" + file + "'",
                    "只能写主题目录内的相对路径（如 _partials/header.html），不能有 .. 、盘符或绝对路径（§11.4）");
        }
    }

    private static List<String> append(List<String> chain, String path) {
        List<String> next = new ArrayList<>(chain);
        next.add(path);
        return next;
    }

    /* ---------------- ② {cms:else/} 绑定（§3.5 裁定四） ---------------- */

    private Node bind(Node node) {
        if (!(node instanceof TagNode tag)) {
            return node;
        }
        Bound body = tag.body() == null
                ? new Bound(null, -1)
                : bindList(tag.body(), tag);
        return new TagNode(tag.name(), tag.args(), body.nodes, body.elseIndex,
                tag.lineNo(), tag.sourcePath());
    }

    private List<Node> bind(List<Node> nodes) {
        return bindList(nodes, null).nodes;
    }

    private record Bound(List<Node> nodes, int elseIndex) {
    }

    /**
     * 摘掉 {@code owner} 直接子节点里的 {@code {cms:else/}}，并把它的位置记成 {@code elseIndex}
     * （{@link TagNode#bodyBeforeElse()} / {@link TagNode#bodyAfterElse()} 按"已摘除"的口径实现）。
     *
     * @param owner 直接的父标签；{@code null} 表示模板根，根上的 else 同样是错的
     */
    private Bound bindList(List<Node> source, TagNode owner) {
        List<Node> out = new ArrayList<>(source.size());
        int elseIndex = -1;
        TagNode firstElse = null;
        for (Node node : source) {
            if (node instanceof TagNode tag && "else".equals(tag.name())) {
                if (owner == null || !"if".equals(owner.name())) {
                    throw PublishException.error(PublishErrorCode.E1003,
                            "{cms:else/} 位置错", tag.sourcePath(), tag.lineNo(),
                            owner == null
                                    ? "第 " + tag.lineNo() + " 行的 {cms:else/} 不在任何 {cms:if} 里"
                                    : "第 " + tag.lineNo() + " 行的 {cms:else/} 在 {cms:" + owner.name()
                                    + "} 内（第 " + owner.lineNo() + " 行）",
                            "{cms:else/} 只能是 {cms:if} 的直接子节点，且同一个 {cms:if} 内最多一个（§3.5 裁定四）");
                }
                if (!tag.selfClosing() || !tag.args().isEmpty()) {
                    throw PublishException.error(PublishErrorCode.E1003,
                            "{cms:else/} 写法错", tag.sourcePath(), tag.lineNo(),
                            "{cms:else/} 不接受参数，也不是块标签",
                            "写法就是 {cms:else/}（§3.1 的元素 3）");
                }
                if (elseIndex >= 0) {
                    throw PublishException.error(PublishErrorCode.E1003,
                            "同一个 {cms:if} 里出现了两个 {cms:else/}", tag.sourcePath(), tag.lineNo(),
                            "第 " + firstElse.lineNo() + " 行与第 " + tag.lineNo() + " 行",
                            "一个 {cms:if} 最多一个 {cms:else/}；要三分支就嵌套 {cms:if}（§3.6 裁定二）");
                }
                firstElse = tag;
                elseIndex = out.size();
                continue;
            }
            out.add(bind(node));
        }
        return new Bound(out, elseIndex);
    }

    /* ---------------- ③ 命名查询与分页主体的收敛 ---------------- */

    private record Collected(List<String> namedQueries, TagNode paginationBody, PaginationKind paginationKind) {
    }

    /**
     * 收敛三件事（§4.4 的 {@link TemplateAst} 字段）：
     * <ul>
     *   <li>命名查询名（出现顺序），供 E2010 查重与 {@code query.<name>} 的清单；</li>
     *   <li>分页主体：第一个 {@code {cms:list}} 就是列表分页主体；没有它时，类型
     *       {@code paginate_body} 非空的 {@code {cms:detail}} 是正文分页主体（§4.5 口径表）。</li>
     * </ul>
     * "至多一个"是校验器的事（E3001/E3002）：编译器只保证 {@code TemplateAst} 有一个确定取值，
     * 数量与位置错在顺序更早的校验里就会拦下。
     */
    private Collected collect(List<Node> nodes, CompileContext ctx) {
        List<String> names = new ArrayList<>();
        TagNode list = null;
        TagNode detail = null;
        for (Node node : flatten(nodes)) {
            // flatten 收集的是**全部**节点（含 TextNode / FieldNode），而这里只关心标签节点。
            // 不判类型直接强转会让任何含文本的模板抛 ClassCastException——而"含文本"是模板的常态。
            if (!(node instanceof TagNode tag)) {
                continue;
            }
            String name = nameArg(tag);
            if (name != null) {
                names.add(name);
            }
            TagHandler handler = registry.handler(tag.name());
            if (handler == null) {
                continue;
            }
            if (handler.bodyKind() == BodyKind.LIST && list == null) {
                list = tag;
            }
            if (handler.bodyKind() == BodyKind.DETAIL && detail == null) {
                detail = tag;
            }
        }
        if (list != null) {
            return new Collected(names, list, PaginationKind.LIST);
        }
        if (detail != null && paginatesBody(ctx)) {
            return new Collected(names, detail, PaginationKind.CONTENT);
        }
        return new Collected(names, null, null);
    }

    /**
     * {@code {cms:detail}} 只有在**详情页**（{@code DETAIL} / {@code DPAGE}）上、且该类型的
     * {@code paginate_body} 非空时才是正文分页主体；单页上它只是"显式取出当前条目"（§4.5 口径表）。
     */
    private boolean paginatesBody(CompileContext ctx) {
        if (ctx.pageType() != PageType.DETAIL && ctx.pageType() != PageType.DPAGE) {
            return false;
        }
        ContentTypeDef def = ctx.typeCode() == null ? null : ctx.provider().type(ctx.typeCode());
        return def != null && def.paginatesBody();
    }

    private String nameArg(Node node) {
        if (!(node instanceof TagNode tag) || tag.arg("name") == null) {
            return null;
        }
        TagHandler handler = registry.handler(tag.name());
        return handler != null && handler.queryTag() ? tag.arg("name") : null;
    }

    /** 深度优先的全部标签节点（文档顺序）。 */
    private static List<Node> flatten(List<Node> nodes) {
        List<Node> all = new ArrayList<>();
        collectInto(nodes, all);
        return all;
    }

    private static void collectInto(List<Node> nodes, List<Node> all) {
        for (Node node : nodes) {
            all.add(node);
            if (node instanceof TagNode tag && tag.body() != null) {
                collectInto(tag.body(), all);
            }
        }
    }

    /* ---------------- ④ 编译期校验（§4.5 的 18 条） ---------------- */

    /**
     * 跑校验器（§4.5 的 18 条），按 {@link TemplateValidator#order()} 依次执行，并把**全部**错误
     * 按 §10.3 第 2 条一次返回（1 条抛裸 {@link PublishException}，≥2 条抛 {@link PublishErrors}，
     * 这是内核写死的约定）。
     *
     * <p>两种报错方式在这里汇合：校验器 {@code throw} 出来的，以及它写进
     * {@link ValidationReport#error} 后继续遍历的（字段名 / 参数 / 引用 / 筛选字段 / 表单 code
     * 这类"会遍历出多个错"的校验器用后者，见 {@code ValidationReport} 的类注释）。
     * 只统计**本次编译新增**的错误（{@code before} 偏移），调用方复用一个 report 也不会把上一次的
     * 错算进来。
     */
    private void runValidators(TemplateAst ast, CompileContext ctx, ValidationReport report,
                               Map<String, List<String>> reachedBy) {
        int before = report.errors().size();
        for (TemplateValidator validator : validators) {
            if (!validator.applies(ctx)) {
                continue;
            }
            try {
                validator.validate(ast, ctx, report);
            } catch (PublishException e) {
                if (validator.order() == ABORT_ORDER) {
                    throw enrich(e, reachedBy);
                }
                report.error(e);
            } catch (PublishErrors e) {
                e.errors().forEach(report::error);
            }
        }
        List<PublishException> all = report.errors();
        List<PublishException> mine = new ArrayList<>();
        // 统一在汇总处补包含链：错误有两种来路（抛出 / 写进报告），只在 catch 里补会漏掉后者
        for (PublishException error : all.subList(before, all.size())) {
            mine.add(enrich(error, reachedBy));
        }
        if (mine.size() == 1) {
            throw mine.get(0);
        }
        if (!mine.isEmpty()) {
            throw PublishErrors.of(mine);
        }
    }

    /** 片段内的报错补上包含链（§5.3）；入口模板的错误不加链（长度为 1 的链没有信息量）。 */
    private static PublishException enrich(PublishException e, Map<String, List<String>> reachedBy) {
        if (!e.includeChain().isEmpty() || e.templatePath() == null) {
            return e;
        }
        List<String> chain = reachedBy.get(e.templatePath());
        return chain != null && chain.size() > 1 ? e.withIncludeChain(chain) : e;
    }

    /* ---------------- 其他 ---------------- */

    /** 当前缓存里的记录数（诊断与测试用）。 */
    public int cachedCount() {
        return cache.size();
    }
}
