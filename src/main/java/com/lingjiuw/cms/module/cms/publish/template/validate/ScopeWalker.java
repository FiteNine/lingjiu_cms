package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.model.BuiltinFields;
import com.lingjiuw.cms.module.cms.publish.model.ContentTypeDef;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import com.lingjiuw.cms.module.cms.publish.model.PageType;
import com.lingjiuw.cms.module.cms.publish.template.CompileContext;
import com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.Node;
import com.lingjiuw.cms.module.cms.publish.template.ast.TagNode;
import com.lingjiuw.cms.module.cms.publish.template.ast.TemplateAst;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 共用的**作用域走查器**（static-publish.md §4.5 第 4/5/8/13/14/15/16 条共用一次遍历）。
 *
 * <p>为什么是一个走查器而不是每条校验各写一份：§4.5 的第 4/5 条要"边走查 AST、边维护作用域栈"
 * （进 {@code {cms:list}} 换成被查询类型的字段集合、进 {@code {cms:foreach}} 换成
 * {@link CompileContext#iteratorKeys} 的集合、出循环还原），第 8 条要判断"是否在
 * {@code {cms:if}} 体内 / 循环体内"，第 14 条要判断锚定项是否存在，第 16 条要判断是否在循环体内。
 * 各写一份遍历必然出现"两份栈的语义不一致"，而那种不一致的表现是"同一模板一处报错一处不报"。
 *
 * <p><b>作用域栈与渲染期同构</b>（§5.1）：栈底是当前条目（{@link CompileContext#currentEntryFields()}），
 * 循环体压入迭代项，查询标签的体压入被查询类型的字段集合。首段解析规则（保留名走具名作用域、
 * 其余从栈顶向下就近查找）由 {@link FieldPathValidator} 实现，本类只负责**给出该点的栈**。
 *
 * <p><b>标签体作用域的来源</b>：结构标签与查询标签（{@code if} / {@code foreach} / {@code list} /
 * {@code query} / {@code detail}）由本类按 §5.1、§6.2、§6.3 的口径算；其余标签**问标签自己**——
 * {@link Visitor#bodyKeys} 默认返回 null，由持有 {@code TagRegistry} 的校验器转发
 * {@link com.lingjiuw.cms.module.cms.publish.template.TagHandler#bodyKeys}。声明了就是**封闭帧**
 * （逐字校验，§4.5 第 4 条 ★ 在这些体内同样生效），声明不了才是**开放帧**（放行任意字段名——
 * 那是 E1004 会漏报的地方，所以能声明就请声明）。
 */
public final class ScopeWalker {

    /** 循环体里每次迭代都提供的通用字段（§3.5 裁定五）。 */
    public static final Set<String> LOOP_KEYS = Set.of("index", "index0", "isFirst", "isLast", "count");

    /** 结构标签名取自 §6.1 的封闭清单（14 个，一个不多）。 */
    private static final String IF = "if";
    private static final String FOREACH = "foreach";
    private static final String LIST = "list";
    private static final String QUERY = "query";
    private static final String DETAIL = "detail";

    /** 这三个标签的标签体是"循环体"（§3.7 裁定三的锚定项判定、§5.4 约束三）。 */
    private static final Set<String> LOOP_TAGS = Set.of(FOREACH, LIST, QUERY);

    private final TemplateAst ast;
    private final CompileContext ctx;
    private final Visitor visitor;

    private final List<Frame> frames = new ArrayList<>();
    private final List<TagNode> enclosingIfs = new ArrayList<>();
    private final List<TagNode> ancestors = new ArrayList<>();
    private boolean inLoop;
    private String ownerType;

    private ScopeWalker(TemplateAst ast, CompileContext ctx, Visitor visitor) {
        this.ast = ast;
        this.ctx = ctx;
        this.visitor = visitor;
    }

    /**
     * 走查一整棵（已展开 include 的）模板树。
     *
     * @param ast     编译产物；{@code ast.nodes()} 已经是展开后的树
     * @param ctx     编译上下文
     * @param visitor 只重写关心的回调
     */
    public static void walk(TemplateAst ast, CompileContext ctx, Visitor visitor) {
        new ScopeWalker(ast, ctx, visitor).run();
    }

    /** 走查回调；每条校验只重写自己关心的那几个。 */
    public interface Visitor {

        /** 进入一个标签节点（**进入之前**的作用域，即该标签所在位置的作用域）。 */
        default void onTag(TagNode node, Scope scope) {
        }

        /** 遇到一个字段引用 {@code [field:…/]}。 */
        default void onField(FieldNode node, Scope scope) {
        }

        /**
         * 该标签体内迭代项提供哪些字段——转发
         * {@link com.lingjiuw.cms.module.cms.publish.template.TagHandler#bodyKeys}。
         * 只有持有 {@code TagRegistry} 的校验器实现它；返回 null = 声明不了（压开放帧）。
         */
        default Set<String> bodyKeys(TagNode node, PageType pageType) {
            return null;
        }
    }

    /** 一帧作用域。{@code open} 为真表示"key 由别人决定，编译期不校验"。 */
    private record Frame(String label, Set<String> keys, boolean open) {
    }

    /** 走查到的位置：该点可用的字段集合与所处结构。不可变快照。 */
    public static final class Scope {

        private final List<Frame> frames;
        private final String ownerType;
        private final boolean inLoop;
        private final List<TagNode> enclosingIfs;
        private final List<TagNode> ancestors;
        private final boolean topLevel;

        private Scope(List<Frame> frames, String ownerType, boolean inLoop, List<TagNode> enclosingIfs,
                      List<TagNode> ancestors, boolean topLevel) {
            this.frames = List.copyOf(frames);
            this.ownerType = ownerType;
            this.inLoop = inLoop;
            this.enclosingIfs = List.copyOf(enclosingIfs);
            this.ancestors = List.copyOf(ancestors);
            this.topLevel = topLevel;
        }

        /** 该点能否解析出这个字段名（任一帧含它即可，渲染期就是"从栈顶向下找第一个含它的帧"）。 */
        public boolean has(String key) {
            for (Frame frame : frames) {
                if (frame.open || frame.keys.contains(key)) {
                    return true;
                }
            }
            return false;
        }

        /** 该点是否有"开放帧"（名字由标签/include 决定，编译期不查）。 */
        public boolean open() {
            return frames.stream().anyMatch(Frame::open);
        }

        /** 全部可用 key 的并集，按字典序。 */
        public Set<String> keys() {
            Set<String> all = new TreeSet<>();
            for (Frame frame : frames) {
                all.addAll(frame.keys);
            }
            return all;
        }

        /** 迭代项 / 当前条目所属的内容类型 code；未知为 null。 */
        public String ownerType() {
            return ownerType;
        }

        /** 是否位于循环体内（{@code foreach} / {@code list} / {@code query} 的标签体内）。 */
        public boolean inLoop() {
            return inLoop;
        }

        /** 是否位于 {@code {cms:if}} 体内（含嵌套）。 */
        public boolean insideIf() {
            return !enclosingIfs.isEmpty();
        }

        /** 最内层的 {@code {cms:if}}；不在 if 里时为 null。 */
        public TagNode enclosingIf() {
            return enclosingIfs.isEmpty() ? null : enclosingIfs.get(enclosingIfs.size() - 1);
        }

        /** 全部外层 {@code {cms:if}}，从外到内。 */
        public List<TagNode> enclosingIfs() {
            return enclosingIfs;
        }

        /** 是否直接位于模板根（不在任何标签体内）。 */
        public boolean topLevel() {
            return topLevel;
        }

        /** 从外到内的祖先标签。 */
        public List<TagNode> ancestors() {
            return ancestors;
        }

        /** 报错文案里的"可用字段"清单：逐帧列出（§10.3 第 1 条：能列清单就列清单）。 */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            for (int i = frames.size() - 1; i >= 0; i--) {
                Frame frame = frames.get(i);
                if (sb.length() > 0) {
                    sb.append("；");
                }
                sb.append(frame.label).append("：");
                sb.append(frame.open ? "（名字由标签或 include 参数决定，编译期不校验）" : Suggest.join(frame.keys));
            }
            return sb.toString();
        }
    }

    /* ---------------- 遍历 ---------------- */

    private void run() {
        Set<String> entry = ctx.currentEntryFields();
        String label = ctx.hasCurrentEntry()
                ? "当前条目（类型 " + text(ctx.typeCode()) + "）"
                : "当前条目（" + ctx.pageType() + " 页没有当前条目）";
        frames.add(new Frame(label, entry, false));
        ownerType = ctx.typeCode();
        walkList(ast.nodes(), true);
    }

    private void walkList(List<Node> nodes, boolean topLevel) {
        for (Node node : nodes) {
            if (node instanceof FieldNode field) {
                visitor.onField(field, scope(topLevel));
                continue;
            }
            if (!(node instanceof TagNode tag)) {
                continue;
            }
            visitor.onTag(tag, scope(topLevel));
            if (tag.body() == null) {
                continue;
            }
            List<Frame> pushed = new ArrayList<>();
            String previousOwner = ownerType;
            boolean previousLoop = inLoop;
            if (!tag.body().isEmpty()) {
                Push push = frameFor(tag);
                if (push != null) {
                    frames.add(push.frame);
                    pushed.add(push.frame);
                    ownerType = push.ownerType;
                }
            }
            if (LOOP_TAGS.contains(tag.name())) {
                inLoop = true;
            }
            if (IF.equals(tag.name())) {
                enclosingIfs.add(tag);
            }
            ancestors.add(tag);
            walkList(tag.body(), false);
            ancestors.remove(ancestors.size() - 1);
            if (IF.equals(tag.name())) {
                enclosingIfs.remove(enclosingIfs.size() - 1);
            }
            inLoop = previousLoop;
            ownerType = previousOwner;
            for (int i = 0; i < pushed.size(); i++) {
                frames.remove(frames.size() - 1);
            }
        }
    }

    /** 压帧的结果：帧本身 + 体内"迭代项所属类型"（供嵌套的 {@code foreach field='children'} 用）。 */
    private record Push(Frame frame, String ownerType) {
    }

    /** 一个标签体压入的作用域帧；不需要压帧时返回 null（例如 {@code if} 的体继承外层）。 */
    private Push frameFor(TagNode tag) {
        switch (tag.name()) {
            case FOREACH -> {
                String source = tag.arg("field");
                Set<String> keys = iteratorKeysOf(source, ownerType, ctx);
                // field 缺失 / 空白时 iteratorKeysOf 返回空集（形状未知），不能当成"封闭空帧"——
                // 那会让体内任何对迭代项的引用都被误报 E1004，与本类"声明不了就压开放帧"相矛盾
                if (keys == null || source == null || source.isBlank()) {
                    return new Push(new Frame("迭代项 " + text(source), Set.of(), true), ownerType);
                }
                Set<String> withLoop = new LinkedHashSet<>(keys);
                withLoop.addAll(LOOP_KEYS);
                return new Push(new Frame("迭代项 " + text(source) + " 的字段", withLoop, false), ownerType);
            }
            case LIST, QUERY -> {
                String typeCode = queryTypeCode(tag, ctx);
                return new Push(new Frame("迭代项（类型 " + text(typeCode) + "）的字段",
                        union(ctx.fieldsOfType(typeCode), LOOP_KEYS), false), typeCode);
            }
            case DETAIL -> {
                String typeCode = tag.arg("type") != null ? tag.arg("type") : ctx.typeCode();
                // detail 不在 LOOP_TAGS 里（渲染期 DetailTag 只压匿名帧、不进循环），
                // 因此不能并入 LOOP_KEYS——否则 [field:index/] 之类编译期放行、运行期取不到值
                return new Push(new Frame("当前条目（类型 " + text(typeCode) + "）的字段",
                        ctx.fieldsOfType(typeCode), false), typeCode);
            }
            default -> {
                if (IF.equals(tag.name())) {
                    return null; // if 的体继承外层作用域，不压帧
                }
                Set<String> keys = visitor.bodyKeys(tag, ctx.pageType());
                return keys == null
                        ? new Push(new Frame("{cms:" + tag.name() + "} 的迭代项", Set.of(), true), ownerType)
                        : new Push(new Frame("{cms:" + tag.name() + "} 的迭代项",
                        union(keys, LOOP_KEYS), false), ownerType);
            }
        }
    }

    /**
     * 一个字段名（迭代来源）的迭代项字段（§6.2 的清单 + §3.5 裁定五）。
     *
     * @param source    字段名或预加载名（{@code images} / {@code toc} / {@code children} / {@code site.alternates}…）
     * @param ownerType 该字段所属内容项的类型 code
     * @param ctx       编译上下文
     * @return 已知的 key 集合；{@code null} 表示"这个来源的迭代项形状编译期不知道"（按开放处理）
     */
    public static Set<String> iteratorKeysOf(String source, String ownerType, CompileContext ctx) {
        if (source == null || source.isBlank()) {
            return Set.of();
        }
        Set<String> known = ctx.iteratorKeys(source, ownerType);
        if (!known.isEmpty()) {
            return known;
        }
        // 自定义字段的迭代项形状与内置同类字段一致（IMAGES 的项是 {url,alt,…}，TAGS 的项是 {name,slug,…}）
        FieldType type = fieldTypeOf(source, ownerType, ctx);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case IMAGES -> ctx.iteratorKeys("images", ownerType);
            case FILES -> ctx.iteratorKeys("files", ownerType);
            case TAGS -> ctx.iteratorKeys("tags", ownerType);
            case RELATION -> ctx.iteratorKeys("related", ownerType);
            case JSON, ENUM_MULTI -> null;
            default -> null;
        };
    }

    /** 一个字段声明的类型：先查该类型的自定义字段，再退回内置字段表；都查不到返回 null。 */
    public static FieldType fieldTypeOf(String code, String typeCode, CompileContext ctx) {
        if (typeCode != null && !"all".equals(typeCode) && ctx.provider() != null) {
            ContentTypeDef def = ctx.provider().type(typeCode);
            if (def != null) {
                FieldDef field = def.field(code);
                if (field != null) {
                    return field.fieldType();
                }
            }
        }
        FieldDef builtin = BuiltinFields.builtinDef(code);
        return builtin == null ? null : builtin.fieldType();
    }

    private Scope scope(boolean topLevel) {
        return new Scope(frames, ownerType, inLoop, enclosingIfs, ancestors, topLevel);
    }

    private static Set<String> union(Set<String> keys, Set<String> extra) {
        Set<String> all = new LinkedHashSet<>(keys);
        all.addAll(extra);
        // 保序的只读视图：Set.copyOf 的迭代顺序不确定，会让"可用字段有 …"的报错文案在
        // 同一模板的多次编译之间抖动
        return java.util.Collections.unmodifiableSet(all);
    }

    private static String text(String value) {
        return value == null ? "（未指定）" : value;
    }

    /* ---------------- 给各校验器用的共用小工具 ---------------- */

    /**
     * 一个查询标签实际作用的类型 code（§6.3）：显式 {@code type} 优先，否则取当前页面的类型；
     * 两者都没有时返回 {@code null}——调用方按 {@code type='all'} 处理（分类索引页的缺省就是
     * {@code type='all'}，§7.2.1），因为 {@link CompileContext#fieldsOfType(String)}
     * 对 {@code null} / {@code all} 给出的是同一份"跨类型可用字段"。
     */
    public static String queryTypeCode(TagNode node, CompileContext ctx) {
        String explicit = node.arg("type");
        return explicit != null ? explicit : ctx.typeCode();
    }
}
