package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.model.Anchor;
import com.lingjiuw.cms.module.cms.publish.model.BuiltinFields;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.PageType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 渲染上下文（static-publish.md §5.1 的"作用域栈"、§5.4 的预解析结果、§5.6 的分页信息）。
 *
 * <p><b>栈的结构恒为</b>：栈底 = 本页面的当前条目（匿名）→ 具名作用域 → 循环项（匿名，可能多层）
 * → include 的 {@code param} 层。
 *
 * <p>本类只做**状态与解析**，不做任何格式化与转义：字段输出的转义与 formatter 在
 * {@code FieldRenderer} 里（§5.2）。这样"作用域从哪取值"与"值怎么变成字符串"两件事
 * 各自只有一处实现。
 *
 * <p>几个承重口径都写在代码里而不是注释里：
 * <ul>
 *   <li>无前缀 {@code [field:x/]} **只在匿名栈帧里**查找，不含具名作用域（§5.1 第（3）条）；</li>
 *   <li>{@code param} 是唯一允许同名多层、就近解析的具名作用域（§5.3）；</li>
 *   <li>循环项永远匿名、永远在栈顶（§5.1）；</li>
 *   <li>"锚定项"的判定顺序是先循环项、后当前条目，没有"或"（§3.7 裁定三）。</li>
 * </ul>
 */
public final class RenderContext {

    /** 6 个保留名（§5.1）：不得用作类型 code、字段 code 或 include 参数名。单一来源见 BuiltinFields。 */
    public static final List<String> RESERVED_NAMES = BuiltinFields.RESERVED_NAMES;

    private static final Object MISS = new Object();

    private final PageType pageType;
    private final String typeCode;

    /** 本页面的当前条目（§5.1 第（1）条）；没有时为 null。匿名栈底是它的字段浅拷贝，不是同一个对象。 */
    private final ContentItem currentEntry;

    /** 匿名栈帧：栈底是当前条目（若有），之上是循环项。迭代顺序 = 从栈底到栈顶。 */
    private final Deque<Map<String, Object>> anonymous = new ArrayDeque<>();

    /**
     * 与 {@link #anonymous} 一一对应的"这一帧的字段属于哪个内容类型"。
     *
     * <p>字段声明（{@code FieldDef}）要靠它才能查到：{@code {cms:list type='product'}} 的迭代项
     * 属于 {@code product}，而页面本身的类型可能是 {@code article}——只按页面类型查会取错声明，
     * 而 §5.2.1 的 {@code raw}、§2.2 第 4 条的 formatter 合法性、{@code format='label'} 的选项表
     * 全都依赖字段声明。为 null 表示"继承页面类型"。
     *
     * <p><b>必须是允许 null 的容器</b>：{@code ArrayDeque.addLast} 遇到 null 直接抛 NPE，
     * 而"继承页面类型"正是用 null 表达的——用它会让每个 {@code {cms:foreach}} 一进循环就炸。
     */
    private final Deque<String> anonymousTypes = new LinkedList<>();

    /** 具名作用域：每个名字一叠层（绝大多数只有一层，{@code param} 可能多层）。栈顶在队首。 */
    private final Map<String, Deque<Map<String, Object>>> named = new LinkedHashMap<>();

    /**
     * 每个标签节点自己的中间结果（预解析阶段算好、渲染阶段取用，见 §5.4）。
     *
     * <p><b>必须是按"引用"比较的 Map，而且 key 就是节点本身</b>：AST 节点是 record，
     * 两个内容相同的节点按 {@code equals} 相等，但它们是模板里的两处不同标签、各有各的结果。
     * 反过来也不能用"包装键 + IdentityHashMap"——{@code IdentityHashMap} 的 {@code get}
     * 按引用比较，每次都 {@code new} 一个包装键就永远读不回来（这条踩过：
     * 预解析写进去、渲染读不到，派生页全都退化成第 1 页）。
     */
    private final Map<Object, Object> nodeState = new IdentityHashMap<>();

    /** 分页 URL 由页面计划注入（§5.6 / §7.1）；没注入时分页字段的 URL 一律为空串。 */
    private PageUrlBuilder pageUrls;

    /** 字段声明的查询出口（自定义字段）；没注入时只剩内置字段表可用。 */
    private FieldDefLookup fieldDefLookup;

    private int loopDepth;

    /**
     * 这一页**来源**的页面类型。
     *
     * <p>它与 {@link #pageType} 只在一处不同：派生页（{@code page-2/…}）。派生页的
     * {@code pageType} 是 {@link PageType#DPAGE}（它决定 noindex、sitemap、矩阵归属），
     * 但它其实是**列表页 / 详情页的第 N 页**，必须继承来源页的语义——否则 §7.2.1 的
     * "分类索引页 {@code {cms:list}} 缺省 {@code type='all'} + {@code category=当前栏目}"
     * 在第二页起就失效，报 E1002。页面计划知道来源是哪一种，因此在建上下文时把两者都给出。
     *
     * <p>为 null 表示与 {@link #pageType} 相同（绝大多数页面）。
     */
    private PageType sourcePageType;

    private RenderContext(PageType pageType, String typeCode, ContentItem currentEntry) {
        this.pageType = pageType;
        this.typeCode = typeCode;
        this.currentEntry = currentEntry;
    }

    /**
     * 建一个页面的渲染上下文，并把"当前条目"压入匿名栈底（§5.1 第（1）条）。
     *
     * @param pageType     页面类型
     * @param typeCode     当前页面的内容类型 code；没有则为 null
     * @param currentEntry 当前条目；首页 / 列表页 / 标签页 / 归档页传 null
     */
    public static RenderContext forPage(PageType pageType, String typeCode, ContentItem currentEntry) {
        RenderContext ctx = new RenderContext(pageType, typeCode, currentEntry);
        // `page` 作用域**恒存在**（§5.1 第（2）条 v2.2 修正）：§7.6 要求每个 DPAGE 输出
        // page.noindex，§4.5 又允许详情页一个分页主体都没有——若懒建，模板里必须用到的
        // 字段就会"不存在"，而报错文案还会说"该页面类型上没有这个作用域"。
        ctx.pageScope();
        if (currentEntry != null) {
            ctx.anonymous.addLast(new LinkedHashMap<>(currentEntry.values()));
            ctx.anonymousTypes.addLast(typeCode);
            ctx.named.put("item", stackOf(currentEntry.values()));
        }
        return ctx;
    }

    public PageType pageType() {
        return pageType;
    }

    /**
     * 这一页**来源**的页面类型；派生页返回来源类型（{@code LIST} / {@code DETAIL}…），
     * 其余页面与 {@link #pageType()} 相同。见字段注释。
     */
    public PageType sourcePageType() {
        return sourcePageType == null ? pageType : sourcePageType;
    }

    /** 页面计划在建上下文时声明"这一页的来源页面类型"（只有派生页需要传）。 */
    public void sourcePageType(PageType type) {
        this.sourcePageType = type;
    }

    /** 注入分页 URL 构造器（页面计划在渲染前调用，§5.6）。 */
    public void pageUrls(PageUrlBuilder builder) {
        this.pageUrls = builder;
    }

    /** 分页 URL 构造器；页面计划没注入时返回 null，调用方据此把 URL 字段留空。 */
    public PageUrlBuilder pageUrls() {
        return pageUrls;
    }

    /** 注入字段声明的查询出口（自定义字段）；不注入时只有内置字段表可用。 */
    public void fieldDefLookup(FieldDefLookup lookup) {
        this.fieldDefLookup = lookup;
    }

    /**
     * 字段声明的查询出口（§2.2 的 {@code cms_field}）。数据层落地后由页面计划注入
     * （{@code (typeCode, fieldCode) -> provider.type(typeCode).field(fieldCode)}）；
     * 期 1 的测试可以直接给一个 Map 支撑的实现。
     */
    @FunctionalInterface
    public interface FieldDefLookup {
        /** 找不到返回 null。 */
        FieldDef find(String typeCode, String fieldCode);
    }

    /** 当前页面的内容类型 code（{@code {cms:list}} 的 {@code type} 缺省值来源）。 */
    public String typeCode() {
        return typeCode;
    }

    /* ---------------- 作用域栈 ---------------- */

    /** 压入一个匿名栈帧（循环项），字段类型继承页面类型。 */
    public void pushAnonymous(Map<String, Object> values) {
        pushAnonymous(values, null);
    }

    /**
     * 压入一个匿名栈帧（循环项）。
     *
     * @param values   迭代项的字段
     * @param fieldType 这一帧的字段属于哪个内容类型 code；null = 继承页面类型。
     *                  跨类型列表（{@code type='all'} 或 {@code {cms:list type='product'}} 出现在
     *                  {@code article} 页面上）**必须传**，否则字段声明会取错。
     */
    public void pushAnonymous(Map<String, Object> values, String fieldType) {
        anonymous.addLast(new LinkedHashMap<>(values));
        anonymousTypes.addLast(fieldType);
    }

    /** 弹出一个匿名栈帧。 */
    public void popAnonymous() {
        if (!anonymous.isEmpty()) {
            anonymous.removeLast();
            anonymousTypes.removeLast();
        }
    }

    /** 进入循环体 / 离开循环体：{@code of='self'} 的锚定项判定靠它（§3.7 裁定三）。 */
    public void enterLoop() {
        loopDepth++;
    }

    public void exitLoop() {
        if (loopDepth > 0) {
            loopDepth--;
        }
    }

    /** 当前是否位于 {@code {cms:list}} / {@code {cms:query}} / {@code {cms:foreach}} 的循环体内。 */
    public boolean inLoop() {
        return loopDepth > 0;
    }

    /** 压入一层具名作用域（{@code param} 会因此形成多层，就近解析）。 */
    public void pushNamed(String name, Map<String, Object> values) {
        named.computeIfAbsent(name, k -> new ArrayDeque<>()).addFirst(new LinkedHashMap<>(values));
    }

    /** 弹出最内层具名作用域；弹出后该名字没有任何层时把它整体移除（"是否存在"是承重的）。 */
    public void popNamed(String name) {
        Deque<Map<String, Object>> layers = named.get(name);
        if (layers == null || layers.isEmpty()) {
            return;
        }
        layers.removeFirst();
        if (layers.isEmpty()) {
            named.remove(name);
        }
    }

    /** 该具名作用域当前是否存在（§5.1 第（4）条的"存在条件"就是它）。 */
    public boolean hasNamed(String name) {
        Deque<Map<String, Object>> layers = named.get(name);
        return layers != null && !layers.isEmpty();
    }

    /** 具名作用域最内层的全部 key；不存在时返回空表。报错文案用它列清单。 */
    public Map<String, Object> namedValues(String name) {
        Deque<Map<String, Object>> layers = named.get(name);
        return layers == null || layers.isEmpty() ? Map.of() : layers.getFirst();
    }

    /** 往 {@code page} 作用域里放一个字段（{@code page} 恒存在，§5.1 第（2）条）。 */
    public void putPage(String key, Object value) {
        pageScope().put(key, value);
    }

    /** 往 {@code page} 作用域里批量放字段。 */
    public void putPageAll(Map<String, Object> values) {
        pageScope().putAll(values);
    }

    /** 读取 {@code page} 作用域的一个字段；不存在返回 null。 */
    public Object pageVar(String key) {
        return pageScope().get(key);
    }

    /** 注册一个命名查询的结果元信息，落进 {@code query.<name>}（§5.4）。 */
    public void putNamedQuery(String name, Map<String, Object> meta) {
        // 必须懒建这一层：query 作用域没有任何 pushNamed 的调用点（它不是"当前浏览位置"那种
        // 由页面计划压入的作用域，而是由查询标签自己注册的结果集合），直接 getFirst() 会在
        // 第一次注册时抛 NoSuchElementException。
        named.computeIfAbsent("query", k -> stackOf(new LinkedHashMap<>())).getFirst().put(name, meta);
    }

    /** 该名字的查询是否已经注册过（§4.5 第 16 条要求查重）。 */
    public boolean hasNamedQuery(String name) {
        return named.containsKey("query") && named.get("query").getFirst().containsKey(name);
    }

    /** 已经注册过的全部查询名，按注册顺序；报错文案用它列清单。 */
    public List<String> namedQueries() {
        Deque<Map<String, Object>> layers = named.get("query");
        return layers == null || layers.isEmpty() ? List.of() : List.copyOf(layers.getFirst().keySet());
    }

    private Map<String, Object> pageScope() {
        Deque<Map<String, Object>> layers = named.get("page");
        if (layers == null || layers.isEmpty()) {
            Map<String, Object> page = new LinkedHashMap<>();
            named.put("page", stackOf(page));
            return page;
        }
        return layers.getFirst();
    }

    /* ---------------- 标签中间结果 ---------------- */

    /** 取某个标签节点的预解析结果。 */
    public Object nodeState(Object node) {
        return nodeState.get(node);
    }

    /** 存某个标签节点的预解析结果。 */
    public void putNodeState(Object node, Object state) {
        nodeState.put(node, state);
    }

    /* ---------------- 当前条目与锚定项 ---------------- */

    /** 本页面的当前条目；没有（首页 / 列表页 / 标签页 / 归档页）返回 null。 */
    public ContentItem currentItem() {
        return currentEntry;
    }

    /** 本页面当前条目的 id；没有时返回 0。列表项算 {@code current} / {@code class} 靠它（§5.5）。 */
    public long currentEntryId() {
        ContentItem item = currentItem();
        return item == null ? 0L : item.id();
    }

    /**
     * 锚定项（§3.7 裁定三）：在循环体内 = 栈顶迭代项；否则 = 本页面的当前条目。
     * 两处都没有时返回 {@link Anchor#NONE}。
     */
    public Anchor anchor() {
        if (loopDepth > 0 && !anonymous.isEmpty()) {
            return anchorOf(anonymous.peekLast());
        }
        ContentItem item = currentItem();
        return item == null ? Anchor.NONE : anchorOf(item.values());
    }

    private static Anchor anchorOf(Map<String, Object> values) {
        long id = asLong(values.get("id"));
        if (id <= 0) {
            return Anchor.NONE;
        }
        return new Anchor(id, asLong(values.get("parentId")), asString(values.get("typeCode")),
                asString(values.get("title")));
    }

    /* ---------------- 字段解析（§5.1） ---------------- */

    /**
     * 解析一条字段路径。规则写死在 §5.1 的表格里：
     * <ul>
     *   <li>首段是保留名 → 只在该具名作用域里找，第二段是 key，之后按路径下钻；</li>
     *   <li>首段不是保留名 → 只在**匿名栈帧**里从栈顶向下找第一个含它的帧（循环项 &gt; 当前条目）；</li>
     *   <li>{@code param} 是唯一多层、就近解析的具名作用域；</li>
     *   <li>{@code .count} 段对多值（列表 / 数组）取元素个数。</li>
     * </ul>
     */
    public Resolution resolve(List<String> path) {
        if (path == null || path.isEmpty()) {
            return Resolution.missing(0, "", "（空路径）");
        }
        String head = path.get(0);
        int start;
        Map<String, Object> frame;

        if (RESERVED_NAMES.contains(head)) {
            Deque<Map<String, Object>> layers = named.get(head);
            if (layers == null || layers.isEmpty()) {
                return Resolution.missing(1, absentScopeText(head),
                        "具名作用域 " + head + "（在当前页面类型 " + pageType + " 上不存在）");
            }
            if (path.size() < 2) {
                return Resolution.missing(1, namedKeys(head), "具名作用域 " + head);
            }
            String key = path.get(1);
            frame = null;
            for (Map<String, Object> layer : layers) {
                if (layer.containsKey(key)) {
                    frame = layer;
                    break;
                }
            }
            if (frame == null) {
                return Resolution.missing(1, namedKeys(head), "具名作用域 " + head);
            }
            start = 2;
        } else {
            frame = null;
            // 从栈顶向下：循环项永远优先于当前条目（§5.1 的承重口径）。
            // ArrayDeque 的迭代顺序是 head→tail（= 栈底→栈顶），所以必须用 descendingIterator 才是"从栈顶向下"。
            java.util.Iterator<Map<String, Object>> topDown = anonymous.descendingIterator();
            while (topDown.hasNext()) {
                Map<String, Object> candidate = topDown.next();
                if (candidate.containsKey(head)) {
                    frame = candidate;
                    break;
                }
            }
            if (frame == null) {
                return Resolution.missing(0, anonymousKeys(), "当前条目与循环项（匿名作用域）");
            }
            start = 1;
        }

        Object value = frame.get(path.get(start - 1));
        for (int i = start; i < path.size(); i++) {
            Object next = child(value, path.get(i));
            if (next == MISS) {
                // site.option.<code>：**站点发布选项是可以后加的配置**，模板里写了一个
                // 本站还没填的选项名时输出空串，而不是让整站发布停在这一页。
                // 反例是很实在的：站长平台的验证串（seo.verifyBaidu）本来就是"注册完才填"，
                // 填之前整站发不出去等于逼着运营先编一个值；而选项名写错与尚未填写在编译期
                // 无从区分（第三段起是数据），因此这里选择"空值"这一侧。
                // 判定只看前缀（site → option），其余路径的缺段照旧报错。
                if ("site".equals(head) && path.size() > 2 && "option".equals(path.get(1))) {
                    return Resolution.ok("", "站点发布选项（本站尚未填写，输出空串）");
                }
                return Resolution.missing(i, keysOf(value), "路径 " + String.join(".", path.subList(0, i))
                        + " 的值");
            }
            value = next;
        }
        return Resolution.ok(value, RESERVED_NAMES.contains(head) ? "具名作用域 " + head : "匿名作用域");
    }

    /** 一次取一个子值；取不到返回 MISS。 */
    private static Object child(Object container, String segment) {
        if (container == null) {
            return MISS;
        }
        if (container instanceof Map<?, ?> map) {
            if (map.containsKey(segment)) {
                return map.get(segment);
            }
            // JSON 字段与结构化的站点选项是 Map 形态的多值容器：§5.1 的 .count 对它同样成立
            // （E1005 的建议文案就把 JSON 列在"count 有效的多值字段"里）
            return "count".equals(segment) ? sizeOf(container) : MISS;
        }
        if (container instanceof ContentItem item) {
            return item.has(segment) ? item.get(segment) : MISS;
        }
        // 导航/迭代项（§6.4 的 channel / pagelist / breadcrumb / tagnav / archive）也是"字段的集合"，
        // 只是被包在 NavItem 里。不认它的话 `[field:children.0.url/]` 会在第一段之后断掉——
        // 而 `{cms:channel depth='2'}` 的 children 元素正是 NavItem。
        if (container instanceof com.lingjiuw.cms.module.cms.publish.model.NavItem nav) {
            return nav.has(segment) ? nav.get(segment) : MISS;
        }
        int size = sizeOf(container);
        if (size >= 0) {
            if ("count".equals(segment)) {
                return size;
            }
            int index = parseIndex(segment);
            if (index >= 0 && index < size) {
                return elementAt(container, index);
            }
        }
        return MISS;
    }

    /** 多值容器的元素个数；不是多值容器时返回 -1。 */
    public static int sizeOf(Object value) {
        if (value instanceof Collection<?> c) {
            return c.size();
        }
        if (value instanceof Map<?, ?> m) {
            return m.size();
        }
        if (value != null && value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value);
        }
        return -1;
    }

    private static Object elementAt(Object container, int index) {
        if (container instanceof List<?> list) {
            return list.get(index);
        }
        if (container instanceof Collection<?> c) {
            int i = 0;
            for (Object o : c) {
                if (i++ == index) {
                    return o;
                }
            }
            return MISS;
        }
        if (container.getClass().isArray()) {
            return java.lang.reflect.Array.get(container, index);
        }
        return MISS;
    }

    private static int parseIndex(String segment) {
        if (segment.isEmpty() || segment.length() > 9) {
            return -1;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return -1;
            }
        }
        return Integer.parseInt(segment);
    }

    /** 匿名栈帧的可用 key 并集（按字母序，供 E1004 的"→ 现状"用）。 */
    public String anonymousKeys() {
        TreeSet<String> keys = new TreeSet<>();
        for (Map<String, Object> frame : anonymous) {
            keys.addAll(frame.keySet());
        }
        return String.join(", ", keys);
    }

    /**
     * 具名作用域的可用 key 清单（§5.1 第（2）条）。
     *
     * <p>§5.1 要求"「找不到」一律报错并**列出该作用域的可用字段**"，而 §10.3 第 1 条把"能列清单
     * 就列清单"列为硬要求。清单不能从当前那一层里现取——层里可能恰好没有用户要找的 key，
     * 那样报错会以"可用的字段有："结尾却什么都没有。因此这里按**契约的封闭清单**现算。
     */
    public String namedKeys(String name) {
        java.util.Set<String> keys = switch (name) {
            case "site" -> BuiltinFields.SITE_KEYS;
            case "page" -> BuiltinFields.PAGE_KEYS;
            case "query" -> named.containsKey("query")
                    ? new java.util.LinkedHashSet<>(named.get("query").getFirst().keySet())
                    : BuiltinFields.QUERY_KEYS;
            case "channel" -> BuiltinFields.channelKeys(pageType, null);
            case "item" -> currentEntry == null ? java.util.Set.of() : currentEntry.values().keySet();
            case "param" -> BuiltinFields.INJECTED_PARAM_KEYS;
            default -> java.util.Set.of();
        };
        if (keys.isEmpty()) {
            return "（该作用域在当前页面类型上没有可用字段）";
        }
        return String.join(", ", new TreeSet<>(keys));
    }

    /** 具名作用域整个不存在时，"可用字段"那一栏该写什么——直接说明它不存在，不要留空。 */
    private String absentScopeText(String name) {
        return "（当前页面类型 " + pageType + " 上没有 " + name + " 作用域，"
                + "见 §5.1 第（4）条的存在条件表）";
    }

    /* ---------------- 字段声明（§2.2） ---------------- */

    /**
     * 解析一条字段路径对应的**字段声明**。三件事必须靠它，否则做不出来：
     * <ul>
     *   <li>§5.2.1 的 {@code raw}——"RICHTEXT 字段在**字段声明**里标为 raw"，模板作者无权改；</li>
     *   <li>§2.2 第 4 条"组合合法性由字段类型决定"（{@code size} 用在 {@code TEXT} 上 → E1002）；</li>
     *   <li>§2.2 的 {@code format='label'}——{@code ENUM} 的"值 → 标签"要 {@code FieldDef.options()}。</li>
     * </ul>
     *
     * <p>查找顺序：**含该字段的那一帧声明的类型** → 页面类型 → **内置字段表**
     * （{@link BuiltinFields#builtinDef}）。第一条是承重的：跨类型列表里迭代项的字段声明
     * 属于被查询的类型，不属于页面类型。
     *
     * @return 找不到声明返回 null；调用方据此退化——只做语法与"专属参数名"校验，
     *         不做类型相关的合法性校验，并按需要转义（安全的默认）
     */
    public FieldDef fieldDef(List<String> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String head = path.get(0);
        if (RESERVED_NAMES.contains(head)) {
            // 具名作用域不是内容字段：item 是"当前条目本身"，其余（site/channel/page/query/param）
            // 由引擎自己填，没有字段声明。
            if (!"item".equals(head) || path.size() < 2) {
                return null;
            }
            return declared(typeCode, path.get(1));
        }
        List<Map<String, Object>> frames = new ArrayList<>(anonymous);
        List<String> types = new ArrayList<>(anonymousTypes);
        String owner = typeCode;
        for (int i = frames.size() - 1; i >= 0; i--) {
            if (frames.get(i).containsKey(head)) {
                String declared = i < types.size() ? types.get(i) : null;
                owner = declared != null ? declared : typeCode;
                break;
            }
        }
        return declared(owner, head);
    }

    /** 先查数据层的字段定义，再退回内置字段表。 */
    private FieldDef declared(String ownerType, String fieldCode) {
        if (fieldDefLookup != null && ownerType != null) {
            FieldDef def = fieldDefLookup.find(ownerType, fieldCode);
            if (def != null) {
                return def;
            }
        }
        return BuiltinFields.builtinDef(fieldCode);
    }

    private static String keysOf(Object container) {
        if (container == null) {
            return "（空值）";
        }
        Collection<String> keys;
        if (container instanceof Map<?, ?> map) {
            keys = map.keySet().stream().map(String::valueOf).sorted().toList();
        } else if (container instanceof ContentItem item) {
            keys = new TreeSet<>(item.values().keySet());
        } else {
            int size = sizeOf(container);
            if (size < 0) {
                return "（" + container.getClass().getSimpleName() + "，没有可取的 key）";
            }
            List<String> list = new ArrayList<>();
            list.add("count");
            for (int i = 0; i < Math.min(size, 10); i++) {
                list.add(String.valueOf(i));
            }
            keys = list;
        }
        return keys.isEmpty() ? "（一个都没有）" : String.join(", ", keys);
    }

    private static Deque<Map<String, Object>> stackOf(Map<String, Object> values) {
        Deque<Map<String, Object>> deque = new ArrayDeque<>();
        deque.addFirst(values);
        return deque;
    }

    /* ---------------- 值判定与类型转换（§3.7 裁定一） ---------------- */

    /**
     * {@code {cms:if}} 的真假判定。**判定顺序写死**：先 {@code trim}，再按字面量比对，
     * 因此 {@code "0.0"} / {@code "0.00"} / {@code "00"} 与 {@code DECIMAL} 的 {@code 0.00}
     * 一律判假（§3.7 裁定一，v2.2 定稿）。
     */
    public static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0d;
        }
        if (value instanceof CharSequence cs) {
            String text = cs.toString().trim();
            if (text.isEmpty() || "0".equals(text) || "false".equalsIgnoreCase(text)) {
                return false;
            }
            Double number = asNumber(text);
            return number == null || number != 0d;
        }
        if (value instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) > 0;
        }
        return true;
    }

    /** 能按数字字面量解析成 0 的字符串一律判假；解析不出来返回 null。 */
    private static Double asNumber(String text) {
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof CharSequence cs) {
            try {
                return Long.parseLong(cs.toString().trim());
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    public static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
