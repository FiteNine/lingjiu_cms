package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;
import com.lingjiuw.cms.module.cms.publish.model.ContentItem;
import com.lingjiuw.cms.module.cms.publish.model.EnumOption;
import com.lingjiuw.cms.module.cms.publish.model.FieldDef;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import com.lingjiuw.cms.module.cms.publish.model.NavItem;
import com.lingjiuw.cms.module.cms.publish.template.ast.FieldNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 字段值的输出：**转义（§5.2）与 formatter（§2.2）的唯一实现处**。
 *
 * <p>两条死限写在这里，别处不许再实现一遍：
 * <ul>
 *   <li>转义只发生在字段输出时、且只对 {@code raw=false} 的字段，转义字符 {@code & < > " '}
 *       （§5.2.1）；模板文本永不转义；**没有关闭转义的开关**；</li>
 *   <li>formatter 的调用语法按 §2.2 v2.2 定死的四条：规范形式 {@code format='<name>'}、
 *       快捷形式（专属参数名反推）、一个字段只能有一个 formatter、组合合法性由字段类型决定。</li>
 * </ul>
 *
 * <p>一切都从**字段声明**（{@link FieldDef}）出发：{@code raw}、默认输出、类型相关的合法性、
 * {@code format='label'} 的选项表。声明用 {@link RenderContext#fieldDef(List)} 取；
 * {@code def == null} 表示声明未知——此时只做语法层校验（参数名封闭清单、formatter 名、
 * 单 formatter），跳过类型相关的校验，并按 {@code raw=false} 输出（安全的默认）。
 */
public final class FieldOutput {

    private FieldOutput() {
    }

    /* ---------------- 公开入口 ---------------- */

    /**
     * 渲染一个 {@code [field:…/]}：应用 formatter，再按 §5.2 决定是否转义。
     *
     * @param value 字段值（可为 null，输出空串）
     * @param def   字段声明；{@code null} = 声明未知
     * @param node  模板里的字段节点（formatter 参数与报错位置都从它取）
     */
    public static String render(Object value, FieldDef def, FieldNode node) {
        FieldType type = typeOf(def, node);
        Applied applied = resolve(type, def == null ? null : def.formatter(), node.args(),
                node.pathText(), node.sourcePath(), node.lineNo());
        String text = apply(applied, value, def, type, node.pathText(), node.sourcePath(), node.lineNo());
        return finish(text, def != null && def.raw(), type);
    }

    /**
     * {@link TemplateRenderer#format(Object, FieldDef, Map)} 的实现：把一个值按 §5.2 输出成字符串
     * （含转义与 formatter）。标签产出 HTML 属性（如 {@code url}）时用它。
     *
     * @param def        字段声明；{@code null} = 声明未知
     * @param formatArgs 模板里为该字段写的 formatter 参数
     */
    public static String format(Object value, FieldDef def, Map<String, String> formatArgs) {
        FieldType type = def == null ? null : def.fieldType();
        Applied applied = resolve(type, def == null ? null : def.formatter(), formatArgs, null, null, 0);
        String text = apply(applied, value, def, type, null, null, 0);
        return finish(text, def != null && def.raw(), type);
    }

    /**
     * 某种字段类型可用的 formatter 名，按 §2.2 的书写顺序；日期类额外给出模式串形态。
     * 报错文案（§2.2 第 4 条要求"必须列出该字段类型可用的 formatter"）与编译期校验都读它。
     *
     * @param type {@code null} = 类型未知，返回全部 18 个
     */
    public static List<String> legalFormatterNames(FieldType type) {
        List<String> names = new ArrayList<>();
        for (Formatter formatter : Formatter.values()) {
            if (type == null || LEGAL.get(type).contains(formatter)) {
                names.add(formatter.code);
            }
        }
        if (type != null && type.temporal()) {
            names.add("（日期模式串，如 format='Y-m-d'）");
        }
        return List.copyOf(names);
    }

    /**
     * 这条路径的字段类型：声明给的类型，外加一条修正——末段是 {@code .count} 时产出的是整数
     * （§5.1："多值字段取元素个数"），因此按 {@code INT} 校验 formatter，否则
     * {@code [field:images.count format='number'/]} 会被误判成"IMAGES 不能 number"。
     */
    private static FieldType typeOf(FieldDef def, FieldNode node) {
        if (def == null) {
            return null;
        }
        List<String> path = node.path();
        if (path.size() > 1 && "count".equals(path.get(path.size() - 1))) {
            return FieldType.INT;
        }
        return def.fieldType();
    }

    /* ---------------- 18 个 formatter 与合法性表（§2.2） ---------------- */

    /** §2.2 第 1 条列出的 18 个 formatter 名，外加结构化数据用的 {@code json}。 */
    private enum Formatter {
        MAXLEN("maxlen"), MASK("mask"), UPPER("upper"), LOWER("lower"),
        NUMBER("number"), COMPACT("compact"), FILESIZE("filesize"), DURATION("duration"),
        PERCENT("percent"), MONEY("money"), FIXED("fixed"), YESNO("yesno"), SHOW("show"),
        LABEL("label"), RELATIVE("relative"), ISO("iso"), WEEKDAY("weekday"), SIZE("size"),
        JSON("json");

        private final String code;

        Formatter(String code) {
            this.code = code;
        }

        static Formatter byCode(String code) {
            for (Formatter formatter : values()) {
                if (formatter.code.equals(code)) {
                    return formatter;
                }
            }
            return null;
        }
    }

    /** 专属参数名（§2.2 第 2 条的封闭清单）→ 它反推出来的 formatter。 */
    private static final Map<String, Formatter> EXCLUSIVE = Map.of(
            "maxlen", Formatter.MAXLEN,
            "mask", Formatter.MASK,
            "size", Formatter.SIZE,
            "show", Formatter.SHOW,
            "fixed", Formatter.FIXED,
            "money", Formatter.MONEY,
            "symbol", Formatter.MONEY);

    /** 只能配合 {@code format='…'} 用的参数名：它们不专属，反推不出 formatter（yesno 的 yes / no）。 */
    private static final Set<String> FORMAT_ONLY_ARGS = Set.of("yes", "no");

    /** 每个 formatter 接受的参数名；不在表里的参数 → E1002。 */
    private static final Map<Formatter, Set<String>> PARAMS = params();

    /** §2.2 的"字段类型 × formatter"表，逐行落地成代码。 */
    private static final Map<FieldType, Set<Formatter>> LEGAL = legal();

    private static Map<Formatter, Set<String>> params() {
        Map<Formatter, Set<String>> map = new EnumMap<>(Formatter.class);
        for (Formatter formatter : Formatter.values()) {
            map.put(formatter, Set.of());
        }
        map.put(Formatter.MAXLEN, Set.of("maxlen"));
        map.put(Formatter.MASK, Set.of("mask"));
        map.put(Formatter.SIZE, Set.of("size"));
        map.put(Formatter.FIXED, Set.of("fixed"));
        map.put(Formatter.SHOW, Set.of("show", "yes"));
        map.put(Formatter.YESNO, Set.of("yes", "no"));
        map.put(Formatter.MONEY, Set.of("symbol", "money"));
        return map;
    }

    private static Map<FieldType, Set<Formatter>> legal() {
        // JSON 只对文本类字段开放：它的用途是"把同一份文案安全地嵌进 <script type=ld+json>"
        Set<Formatter> textual = EnumSet.of(Formatter.MAXLEN, Formatter.MASK,
                Formatter.UPPER, Formatter.LOWER, Formatter.JSON);
        // §2.2 第 4 条：money / fixed / symbol 对 DECIMAL 与 INT 都合法（表里 INT 行只写到了 percent）
        Set<Formatter> numeric = EnumSet.of(Formatter.NUMBER, Formatter.COMPACT, Formatter.FILESIZE,
                Formatter.DURATION, Formatter.PERCENT, Formatter.MONEY, Formatter.FIXED);
        Set<Formatter> temporal = EnumSet.of(Formatter.RELATIVE, Formatter.ISO, Formatter.WEEKDAY);
        Map<FieldType, Set<Formatter>> table = new EnumMap<>(FieldType.class);
        table.put(FieldType.TEXT, textual);
        table.put(FieldType.TEXTAREA, textual);
        table.put(FieldType.RICHTEXT, Set.of());
        table.put(FieldType.MARKDOWN, Set.of());
        table.put(FieldType.INT, numeric);
        table.put(FieldType.DECIMAL, numeric);
        table.put(FieldType.BOOL, EnumSet.of(Formatter.YESNO, Formatter.SHOW));
        table.put(FieldType.DATE, temporal);
        table.put(FieldType.DATETIME, temporal);
        table.put(FieldType.ENUM, EnumSet.of(Formatter.LABEL));
        table.put(FieldType.ENUM_MULTI, EnumSet.of(Formatter.LABEL));
        table.put(FieldType.COLOR, Set.of());
        table.put(FieldType.IMAGE, EnumSet.of(Formatter.SIZE));
        table.put(FieldType.IMAGES, EnumSet.of(Formatter.SIZE));
        table.put(FieldType.FILE, EnumSet.of(Formatter.FILESIZE));
        table.put(FieldType.FILES, EnumSet.of(Formatter.FILESIZE));
        table.put(FieldType.RELATION, Set.of());
        table.put(FieldType.TAGS, Set.of());
        table.put(FieldType.JSON, Set.of());
        return Map.copyOf(table);
    }

    /* ---------------- formatter 的解析与校验（§2.2 第 1–4 条） ---------------- */

    /** 解析出来的 formatter 与它的参数（"format" 只在解析期用，不进 params）。 */
    private record Applied(Formatter formatter, String datePattern, Map<String, String> params) {
    }

    private static Applied resolve(FieldType type, String defFormatter, Map<String, String> rawArgs,
                                   String pathText, String templatePath, int lineNo) {
        Map<String, String> args = rawArgs == null ? Map.of() : rawArgs;
        String subject = pathText == null ? "这个字段" : "字段 " + pathText;

        // ① 参数名必须是 format、专属参数名（§2.2 第 2 条的封闭清单）或 yesno 的 yes / no
        for (String key : args.keySet()) {
            if (!"format".equals(key) && !EXCLUSIVE.containsKey(key) && !FORMAT_ONLY_ARGS.contains(key)) {
                throw E1002("formatter 参数 " + key + " 不存在", subject,
                        "写了 " + key + "='" + args.get(key) + "'；[field:…/] 可用的参数只有 format 与专属参数名 "
                                + "maxlen mask size show money fixed symbol，以及 yesno 的 yes no",
                        suggestionFor(key, EXCLUSIVE.keySet()), templatePath, lineNo);
            }
        }

        // ② 模板侧写了哪个 formatter：规范形式（含日期模式串）或快捷形式
        Set<Formatter> written = new LinkedHashSet<>();
        String pattern = null;
        String formatArg = args.get("format");
        if (formatArg != null) {
            Formatter named = Formatter.byCode(formatArg);
            if (named != null) {
                written.add(named);
            } else if (isDatePattern(formatArg)) {
                pattern = formatArg;
            } else {
                throw unknownFormatter(subject, formatArg, templatePath, lineNo);
            }
        }
        for (Map.Entry<String, Formatter> entry : EXCLUSIVE.entrySet()) {
            if (args.containsKey(entry.getKey())) {
                written.add(entry.getValue());
            }
        }
        if (written.size() > 1 || (pattern != null && !written.isEmpty())) {
            List<String> names = new ArrayList<>();
            written.forEach(formatter -> names.add("format='" + formatter.code + "'"));
            if (pattern != null) {
                names.add("format='" + pattern + "'");
            }
            throw E1002("一个字段只能有一个 formatter", subject,
                    "写了 " + String.join(" ", names) + "（" + describeArgs(args) + "）",
                    "把多余的那个删掉——「先格式化再格式化」没有定义（§2.2 第 3 条）",
                    templatePath, lineNo);
        }

        // ③ 字段定义里声明的 formatter（FieldDef.formatter）：与模板写的撞了就是两个 formatter
        Formatter declared = null;
        String declaredPattern = null;
        if (defFormatter != null && !defFormatter.isBlank()) {
            declared = Formatter.byCode(defFormatter);
            if (declared == null) {
                if (isDatePattern(defFormatter)) {
                    declaredPattern = defFormatter;
                } else {
                    throw unknownFormatter(subject, defFormatter, templatePath, lineNo);
                }
            }
        }
        Formatter writtenOne = written.isEmpty() ? null : written.iterator().next();
        if (declared != null || declaredPattern != null) {
            boolean same = declared != null && declared == writtenOne
                    || declaredPattern != null && declaredPattern.equals(pattern);
            boolean templateSaid = writtenOne != null || pattern != null;
            if (!same && templateSaid) {
                throw E1002("一个字段只能有一个 formatter", subject,
                        "字段定义里写了 formatter='" + defFormatter + "'，模板里又写了 " + describeArgs(args),
                        "删掉模板里那个，或到「内容类型 → 字段 → formatter」把定义里的改掉",
                        templatePath, lineNo);
            }
            if (!templateSaid) {
                writtenOne = declared;
                pattern = declaredPattern;
            }
        }

        Formatter formatter = pattern != null ? null : writtenOne;
        if (formatter == null && pattern == null) {
            if (!args.isEmpty()) {
                // 只有 yes / no：它们不是专属参数名，反推不出 formatter
                throw E1002("formatter 参数不能单独使用", subject,
                        "写了 " + describeArgs(args) + "；yes no 只属于 yesno，show 只属于 show",
                        "补上 format，例如 format='yesno' yes='有' no='无'",
                        templatePath, lineNo);
            }
            return null;
        }

        // ④ 组合合法性由字段类型决定（§2.2 第 4 条）
        if (type != null) {
            if (pattern != null && !type.temporal()) {
                throw E1002("日期模式串 format='" + pattern + "' 不能用在 " + type + " 字段上", subject,
                        "字段类型 " + type + " 可用的 formatter 有：" + String.join(" ", legalFormatterNames(type)),
                        "日期格式化只对 DATE / DATETIME 合法",
                        templatePath, lineNo);
            }
            if (formatter != null && !LEGAL.get(type).contains(formatter)) {
                throw E1002("formatter " + formatter.code + " 不能用在 " + type + " 字段上", subject,
                        "字段类型 " + type + " 可用的 formatter 有：" + String.join(" ", legalFormatterNames(type)),
                        whyIllegal(formatter),
                        templatePath, lineNo);
            }
        }

        // ⑤ formatter 自己的参数：多了报错，缺了也报错（MAXLEN / MASK / SIZE / FIXED / SHOW 必须有值）
        Map<String, String> params = new LinkedHashMap<>(args);
        params.remove("format");
        if (formatter != null) {
            for (String key : params.keySet()) {
                if (!PARAMS.get(formatter).contains(key)) {
                    throw E1002("formatter " + formatter.code + " 不接受参数 " + key, subject,
                            "写了 " + describeArgs(args) + "；" + formatter.code + " 的参数有："
                                    + (PARAMS.get(formatter).isEmpty()
                                    ? "（没有）" : String.join(" ", sorted(PARAMS.get(formatter)))),
                            "参数名必须属于当前的 formatter（§2.2 第 1、2 条）",
                            templatePath, lineNo);
                }
            }
            if (needsValue(formatter) && !params.containsKey(formatter.code)
                    && !(formatter == Formatter.SHOW && params.containsKey("yes"))) {
                throw E1002("formatter " + formatter.code + " 缺少参数", subject,
                        "字段定义里写了 formatter='" + formatter.code + "'，但参数要写在模板里",
                        "规范形式：format='" + formatter.code + "' " + requiredParamText(formatter),
                        templatePath, lineNo);
            }
            // mask 的取值是封闭集合（§2.2）：未知取值不能落进 email 分支——
            // 那会把本该脱敏的手机号/邮箱原样输出到静态页
            if (formatter == Formatter.MASK
                    && !"phone".equals(params.get("mask")) && !"email".equals(params.get("mask"))) {
                throw E1002("formatter mask 的取值不可用", subject,
                        "写了 " + describeArgs(args) + "；mask 只接受 phone / email",
                        "手机号用 mask='phone'，邮箱用 mask='email'（§2.2）",
                        templatePath, lineNo);
            }
        }
        return new Applied(formatter, pattern, Map.copyOf(params));
    }

    private static boolean needsValue(Formatter formatter) {
        return switch (formatter) {
            case MAXLEN, MASK, SIZE, FIXED, SHOW -> true;
            default -> false;
        };
    }

    private static String requiredParamText(Formatter formatter) {
        return switch (formatter) {
            case MAXLEN -> "maxlen='20'";
            case MASK -> "mask='phone|email'";
            case SIZE -> "size='thumb|medium|large'";
            case FIXED -> "fixed='2'";
            case SHOW -> "show='真时要输出的文本'";
            default -> "";
        };
    }

    /** §2.2 第 4 条那三组限定的"为什么"。 */
    private static String whyIllegal(Formatter formatter) {
        return switch (formatter) {
            case SIZE -> "size 只对 IMAGE / IMAGES 合法";
            case MONEY, FIXED -> "money / fixed / symbol 只对 DECIMAL / INT 合法";
            case MAXLEN, MASK, UPPER, LOWER -> "maxlen / mask / upper / lower 只对文本类字段合法";
            case YESNO, SHOW -> "yesno / show 只对 BOOL 字段合法";
            case LABEL -> "label 只对 ENUM / ENUM_MULTI 合法";
            case RELATIVE, ISO, WEEKDAY -> "relative / iso / weekday 只对 DATE / DATETIME 合法";
            case FILESIZE -> "filesize 只对数值类与 FILE / FILES 合法";
            default -> "这个组合没有定义";
        };
    }

    private static PublishException unknownFormatter(String subject, String text, String templatePath,
                                                     int lineNo) {
        List<String> codes = new ArrayList<>();
        for (Formatter formatter : Formatter.values()) {
            codes.add(formatter.code);
        }
        return E1002("formatter " + text + " 不存在", subject,
                "写了 format='" + text + "'；可用的 formatter 有：" + String.join(" ", codes)
                        + "（日期类字段还可以直接写模式串，字母只有 Y m d H i s）",
                suggestionFor(text, codes), templatePath, lineNo);
    }

    /** §2.2 第 1 条的日期模式串：只认 Y m d H i s 六个字母，其余字符（含中文）一律按字面量输出。 */
    private static boolean isDatePattern(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 128 && Character.isLetter(c) && "YmdHis".indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    /* ---------------- formatter 的应用 ---------------- */

    private static String apply(Applied applied, Object value, FieldDef def, FieldType type,
                                String pathText, String templatePath, int lineNo) {
        if (applied == null) {
            return defaultText(value, type);
        }
        if (applied.datePattern() != null) {
            LocalDateTime time = temporal(value);
            return time == null ? defaultText(value, type) : datePattern(time, applied.datePattern());
        }
        Formatter formatter = applied.formatter();
        Map<String, String> params = applied.params();
        return switch (formatter) {
            case MAXLEN -> maxlen(defaultText(value, type), intParam(params, "maxlen", formatter,
                    pathText, templatePath, lineNo));
            case MASK -> mask(defaultText(value, type), params.get("mask"));
            case UPPER -> defaultText(value, type).toUpperCase(Locale.ROOT);
            case LOWER -> defaultText(value, type).toLowerCase(Locale.ROOT);
            case NUMBER -> group(numberText(mediaNumber(value)));
            case COMPACT -> compact(mediaNumber(value));
            case FILESIZE -> filesize(mediaNumber(value));
            case DURATION -> duration(value);
            case PERCENT -> numberText(value) + "%";
            case MONEY -> money(value, params.getOrDefault("symbol", "¥"));
            case FIXED -> fixed(value, intParam(params, "fixed", formatter, pathText, templatePath, lineNo));
            case YESNO -> RenderContext.truthy(value)
                    ? params.getOrDefault("yes", "是") : params.getOrDefault("no", "否");
            case SHOW -> RenderContext.truthy(value)
                    ? params.getOrDefault("show", params.getOrDefault("yes", "")) : "";
            case LABEL -> label(value, def);
            case RELATIVE -> relative(value);
            case ISO -> iso(value);
            case WEEKDAY -> weekday(value);
            case SIZE -> sized(value, params.get("size"));
            case JSON -> jsonText(defaultText(value, type));
        };
    }

    /**
     * {@code format='json'}：把文案转成可以安全放进 JSON 字符串字面量的形态。
     *
     * <p><b>为什么用 {@code \\uXXXX} 而不是 {@code \\"} 表示引号</b>：本方法的返回值紧接着还要过
     * §5.2.1 的 HTML 转义（{@code finish}），而转义表里有 {@code "} → {@code &quot;}。
     * 写成 {@code \\"} 会先被拆成 {@code \\} + {@code "}，引号那半被转成 {@code &quot;}，
     * JSON 立刻断掉。改用 {@code \\u0022}、{@code \\u0026}、{@code \\u003c}、{@code \\u003e}、
     * {@code \\u0027} 之后，产出里不再出现 HTML 转义表管得到的字符，过一遍 {@code escape()} 原样通过，
     * 而 JSON 解析器会把 {@code \\u0022} 还原成引号——两边都对。
     *
     * <p>换行用 {@code \\n} / {@code \\r} / {@code \\t}（它们不含特殊字符，不受 HTML 转义影响）；
     * 其余控制字符走 {@code \\u00XX}。
     */
    private static String jsonText(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\u0022");
                case '\\' -> out.append("\\\\");
                case '&' -> out.append("\\u0026");
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                case '\'' -> out.append("\\u0027");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** §5.2.1：转义只对 {@code raw=false}；{@code raw=true} 原样；模板文本永不经过这里。 */
    private static String finish(String text, boolean raw, FieldType type) {
        if (raw) {
            return text;
        }
        String escaped = escape(text);
        // §2.2：TEXTAREA 的默认输出是"转义文本 + <br>"，所以 <br> 只能在转义之后加
        if (type == FieldType.TEXTAREA) {
            return escaped.replace("\r\n", "<br>").replace("\n", "<br>").replace("\r", "<br>");
        }
        return escaped;
    }

    /** 转义字符 {@code & < > " '}（§5.2.1）；一处实现，全引擎只有这一个。 */
    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /* ---------------- 值 → 字符串（§2.2 的"默认输出"列、§5.1 的下标取值） ---------------- */

    /**
     * 没写 formatter 时的默认输出（§2.2 的"默认输出"列）。
     *
     * <p><b>媒体类字段（{@code IMAGE} / {@code IMAGES} / {@code FILE} / {@code FILES}）输出的是
     * {@code url}，不是名字。</b>这一条是承重的：媒体值在这一层永远是"带
     * {@code url / name / size / mime} 的对象"，而 {@link #stringify} 对对象取的是
     * {@code name → title → url} 里第一个非空的（那对 {@code [field:x.0/]} 这种"取个可读名字"的
     * 场合是对的）。媒体字段若跟着走通用取向，{@code <a href="[field:download/]">} 会拿到**文件名**
     * 而不是 URL，产物里就是一串相对路径的死链——"下载按钮点了 404"的成因。
     * {@code IMAGES} 同理输出**首图**的 url（§2.2 原文）。
     */
    private static String defaultText(Object value, FieldType type) {
        if (type == FieldType.IMAGE || type == FieldType.IMAGES
                || type == FieldType.FILE || type == FieldType.FILES) {
            Object first = firstOf(value);
            return first == null ? "" : mediaUrlText(first);
        }
        return stringify(value);
    }

    /** 媒体值 → 它的 {@code url}；没有 url 键（值本身就是 URL 字符串）时退回通用形态。 */
    private static String mediaUrlText(Object value) {
        Map<?, ?> map = asMap(value);
        if (map != null) {
            Object url = map.get("url");
            return url == null ? objectText(map) : stringify(url);
        }
        return stringify(value);
    }

    /**
     * 媒体值 → 它的**字节数**（{@code size}），供 {@code format='filesize'} 用。
     *
     * <p>为什么要单独剥一层：{@code [field:download format='filesize'/]} 的 {@code value} 是那个
     * {@code {url,name,size,mime}} 对象，直接把对象交给 {@code filesize()} 只会得到"0 B"。
     * 其余值原样返回，让 formatter 自己按数字处理。
     */
    private static Object mediaNumber(Object value) {
        Map<?, ?> map = asMap(value);
        if (map != null) {
            Object size = map.get("size");
            return size == null ? value : size;
        }
        return value;
    }

    /** 集合 / 数组的第一个元素；其余值原样返回。 */
    private static Object firstOf(Object value) {
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                return item;
            }
            return null;
        }
        if (value != null && value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) == 0
                    ? null : java.lang.reflect.Array.get(value, 0);
        }
        return value;
    }

    /**
     * 值的默认字符串形态：{@code BOOL} 是 {@code 1} / {@code 0}，多值用逗号连接，
     * 对象取 {@code name} / {@code title} / {@code url} 中第一个非空者（§5.1 对 {@code [field:x.0/]}
     * 的口径，同一份规则用在整字段输出上）。
     */
    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        if (value instanceof Boolean flag) {
            return flag ? "1" : "0";
        }
        if (value instanceof Number number) {
            return numberText(number);
        }
        if (value instanceof EnumOption option) {
            return option.label() == null ? option.value() : option.label();
        }
        Map<?, ?> map = asMap(value);
        if (map != null) {
            return objectText(map);
        }
        if (value instanceof Collection<?> collection) {
            return joinItems(collection);
        }
        if (value.getClass().isArray()) {
            List<Object> items = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                items.add(java.lang.reflect.Array.get(value, i));
            }
            return joinItems(items);
        }
        return String.valueOf(value);
    }

    private static Map<?, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        if (value instanceof ContentItem item) {
            return item.values();
        }
        if (value instanceof NavItem nav) {
            return nav.values();
        }
        return null;
    }

    private static String objectText(Map<?, ?> map) {
        for (String key : List.of("name", "title", "url")) {
            Object child = map.get(key);
            if (child != null) {
                String text = stringify(child);
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    private static String joinItems(Collection<?> items) {
        List<String> parts = new ArrayList<>();
        for (Object item : items) {
            parts.add(stringify(item));
        }
        return String.join(",", parts);
    }

    /** 数值的朴素文本：{@code 9.00} → {@code 9}，{@code 1.5} → {@code 1.5}，不用科学计数法。 */
    private static String numberText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return stripZeros(decimal).toPlainString();
        }
        if (value instanceof Number number) {
            if (number instanceof Double || number instanceof Float) {
                double d = number.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    return number.toString();
                }
                return stripZeros(BigDecimal.valueOf(d)).toPlainString();
            }
            return number.toString();
        }
        if (value instanceof CharSequence text) {
            return text.toString().trim();
        }
        return stringify(value);
    }

    private static BigDecimal stripZeros(BigDecimal decimal) {
        BigDecimal stripped = decimal.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped;
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof CharSequence text) {
            try {
                return Double.valueOf(text.toString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** 千分位：{@code 1234567.5} → {@code 1,234,567.5}。 */
    private static String group(String numeric) {
        int dot = numeric.indexOf('.');
        String integer = dot < 0 ? numeric : numeric.substring(0, dot);
        String fraction = dot < 0 ? "" : numeric.substring(dot);
        boolean negative = integer.startsWith("-");
        if (negative) {
            integer = integer.substring(1);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < integer.length(); i++) {
            if (i > 0 && (integer.length() - i) % 3 == 0) {
                out.append(',');
            }
            out.append(integer.charAt(i));
        }
        return (negative ? "-" : "") + out + fraction;
    }

    /** 1.2万 / 1.5亿（§2.2 的 INT / DECIMAL 行）。 */
    private static String compact(Object value) {
        Double number = asDouble(value);
        if (number == null) {
            return stringify(value);
        }
        double abs = Math.abs(number);
        if (abs >= 1e8) {
            return trimZero(round(number / 1e8, 1)) + "亿";
        }
        if (abs >= 1e4) {
            return trimZero(round(number / 1e4, 1)) + "万";
        }
        return numberText(value);
    }

    /** 字节 → B / KB / MB / GB（1024 进制）。 */
    private static String filesize(Object value) {
        Double bytes = asDouble(value);
        if (bytes == null) {
            return stringify(value);
        }
        double abs = Math.abs(bytes);
        if (abs < 1024) {
            return trimZero(round(bytes, 0)) + " B";
        }
        double kb = bytes / 1024;
        if (Math.abs(kb) < 1024) {
            return trimZero(round(kb, 1)) + " KB";
        }
        double mb = kb / 1024;
        if (Math.abs(mb) < 1024) {
            return trimZero(round(mb, 1)) + " MB";
        }
        return trimZero(round(mb / 1024, 1)) + " GB";
    }

    /** 秒 → 时:分:秒（§2.2 的 INT 行）。 */
    private static String duration(Object value) {
        Double seconds = asDouble(value);
        if (seconds == null) {
            return stringify(value);
        }
        long total = Math.round(seconds);
        return String.format("%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60);
    }

    /** 金额：两位小数 + 货币符号（§2.2：{@code symbol='¥'}）。 */
    private static String money(Object value, String symbol) {
        Double number = asDouble(value);
        if (number == null) {
            // 空值输出空串（与 fixed / number 等同口径）：不能只留一个没有金额的货币符号
            String fallback = stringify(value);
            return fallback.isEmpty() ? "" : symbol + fallback;
        }
        return symbol + BigDecimal.valueOf(number).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String fixed(Object value, int digits) {
        Double number = asDouble(value);
        if (number == null) {
            return stringify(value);
        }
        return BigDecimal.valueOf(number).setScale(digits, RoundingMode.HALF_UP).toPlainString();
    }

    /** 按字符截断 + {@code …}（§2.2：{@code maxlen='20'}）。 */
    private static String maxlen(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit) + "…";
    }

    /** {@code mask='phone'} → 138****8888；{@code mask='email'} → ab***@example.com。 */
    private static String mask(String text, String kind) {
        if ("phone".equals(kind)) {
            if (text.length() < 7) {
                return "*".repeat(text.length());
            }
            return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
        }
        int at = text.indexOf('@');
        if (at <= 0) {
            return text;
        }
        String local = text.substring(0, at);
        int keep = local.length() <= 2 ? 1 : 2;
        return local.substring(0, keep) + "***" + text.substring(at);
    }

    /** ENUM / ENUM_MULTI 的 {@code format='label'}：存储值 → 选项标签；映射不到就原样输出存储值。 */
    private static String label(Object value, FieldDef def) {
        List<EnumOption> options = def == null ? List.of() : def.options();
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>();
            for (Object item : collection) {
                parts.add(labelOf(item, options));
            }
            return String.join(",", parts);
        }
        if (value != null && value.getClass().isArray()) {
            List<String> parts = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                parts.add(labelOf(java.lang.reflect.Array.get(value, i), options));
            }
            return String.join(",", parts);
        }
        return labelOf(value, options);
    }

    private static String labelOf(Object value, List<EnumOption> options) {
        String stored = stringify(value);
        for (EnumOption option : options) {
            if (option.value().equals(stored)) {
                return option.label() == null ? stored : option.label();
            }
        }
        return stored;
    }

    /**
     * {@code size='thumb'}：取派生尺寸（§7.7）。值的形态由数据层决定，这里兼容两种：
     * 媒体对象（{@code Map}：先取 {@code size} 名对应的键，再取 {@code derive} / {@code sizes} 子表，
     * 最后回退 {@code url}）与"已经是 URL 的字符串"；多值（图集）先取首项。
     * 派生 URL 含内容哈希，只有数据层知道，渲染期不重新拼。
     */
    private static String sized(Object value, String sizeName) {
        if (sizeName == null) {
            return stringify(value);
        }
        Object target = value instanceof Collection<?> || (value != null && value.getClass().isArray())
                ? firstOf(value) : value;
        if (target == null) {
            return "";
        }
        Map<?, ?> media = asMap(target);
        if (media == null) {
            return stringify(target);
        }
        String direct = urlOf(media.get(sizeName));
        if (direct != null) {
            return direct;
        }
        for (String holder : List.of("derive", "sizes")) {
            Map<?, ?> nested = asMap(media.get(holder));
            if (nested != null) {
                String url = urlOf(nested.get(sizeName));
                if (url != null) {
                    return url;
                }
            }
        }
        String fallback = urlOf(media.get("url"));
        return fallback == null ? stringify(target) : fallback;
    }

    private static String urlOf(Object node) {
        if (node == null) {
            return null;
        }
        if (node instanceof CharSequence text) {
            return text.toString();
        }
        Map<?, ?> map = asMap(node);
        if (map != null) {
            Object url = map.get("url");
            return url == null ? null : String.valueOf(url);
        }
        return null;
    }

    /** {@code relative}：3 天前 / 2 小时前 / 刚刚（§2.2 的 DATE / DATETIME 行）。 */
    private static String relative(Object value) {
        LocalDateTime time = temporal(value);
        if (time == null) {
            return stringify(value);
        }
        long seconds = Duration.between(time, LocalDateTime.now()).getSeconds();
        boolean future = seconds < 0;
        long abs = Math.abs(seconds);
        String suffix = future ? "后" : "前";
        if (abs < 60) {
            return "刚刚";
        }
        if (abs < 3600) {
            return abs / 60 + " 分钟" + suffix;
        }
        if (abs < 86400) {
            return abs / 3600 + " 小时" + suffix;
        }
        if (abs < 86400L * 30) {
            return abs / 86400 + " 天" + suffix;
        }
        if (abs < 86400L * 365) {
            return abs / (86400L * 30) + " 个月" + suffix;
        }
        return abs / (86400L * 365) + " 年" + suffix;
    }

    /** {@code iso}：JSON-LD 用的 ISO-8601。 */
    private static String iso(Object value) {
        if (value instanceof Instant instant) {
            return DateTimeFormatter.ISO_INSTANT.format(instant);
        }
        if (value instanceof OffsetDateTime offset) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(offset);
        }
        if (value instanceof ZonedDateTime zoned) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zoned);
        }
        LocalDateTime time = temporal(value);
        return time == null ? stringify(value) : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(time);
    }

    /** {@code weekday}：星期一 … 星期日。 */
    private static String weekday(Object value) {
        LocalDateTime time = temporal(value);
        if (time == null) {
            return stringify(value);
        }
        return switch (time.getDayOfWeek()) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    /** 日期模式串：{@code Y} 年 {@code m} 月 {@code d} 日 {@code H} 时 {@code i} 分 {@code s} 秒。 */
    private static String datePattern(LocalDateTime time, String pattern) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            switch (pattern.charAt(i)) {
                case 'Y' -> out.append(pad(time.getYear(), 4));
                case 'm' -> out.append(pad(time.getMonthValue(), 2));
                case 'd' -> out.append(pad(time.getDayOfMonth(), 2));
                case 'H' -> out.append(pad(time.getHour(), 2));
                case 'i' -> out.append(pad(time.getMinute(), 2));
                case 's' -> out.append(pad(time.getSecond(), 2));
                default -> out.append(pattern.charAt(i));
            }
        }
        return out.toString();
    }

    /**
     * 把值读成时间。数据层给的通常是 {@code yyyy-MM-dd HH:mm:ss} 字符串或 ISO 串；
     * {@code Number} 一律按 epoch **毫秒**读。
     */
    private static LocalDateTime temporal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toLocalDateTime();
        }
        if (value instanceof ZonedDateTime zoned) {
            return zoned.toLocalDateTime();
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        if (value instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        if (value instanceof Number number) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(number.longValue()),
                    ZoneId.systemDefault());
        }
        if (value instanceof CharSequence text) {
            return parseTime(text.toString().trim());
        }
        return null;
    }

    private static LocalDateTime parseTime(String text) {
        if (text.isEmpty()) {
            return null;
        }
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm")) {
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
            } catch (RuntimeException ignored) {
                // 换下一种形态
            }
        }
        // 纯日期形态必须单独用 LocalDate 解析：LocalDateTime.parse 对只有年月日的
        // formatter 拿不到 LocalTime，必定抛 DateTimeException（原来这个分支是死代码）
        try {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
        } catch (RuntimeException ignored) {
            // 继续尝试带偏移量的形态
        }
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String pad(int value, int width) {
        return String.format("%0" + width + "d", value);
    }

    private static String round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private static String trimZero(String text) {
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /* ---------------- 参数取值与报错 ---------------- */

    private static int intParam(Map<String, String> params, String key, Formatter formatter,
                                String pathText, String templatePath, int lineNo) {
        String text = params.get(key);
        try {
            int value = Integer.parseInt(text == null ? "" : text.trim());
            if (value < 0) {
                throw new NumberFormatException(text);
            }
            return value;
        } catch (NumberFormatException e) {
            throw E1002("formatter " + formatter.code + " 的参数 " + key + " 不是非负整数",
                    pathText == null ? "这个字段" : "字段 " + pathText,
                    "写了 " + key + "='" + text + "'",
                    "写一个非负整数，例如 " + requiredParamText(formatter),
                    templatePath, lineNo);
        }
    }

    private static String describeArgs(Map<String, String> args) {
        List<String> parts = new ArrayList<>();
        args.forEach((key, value) -> parts.add(key + "='" + value + "'"));
        return String.join(" ", parts);
    }

    private static List<String> sorted(Set<String> values) {
        List<String> list = new ArrayList<>(values);
        list.sort(String::compareTo);
        return list;
    }

    /** 编辑距离 ≤ 2 的最近候选，给了就写成"你是不是想写 …？"（§10.1）。距离算法复用标签注册表里那一份。 */
    private static String suggestionFor(String typo, Collection<String> candidates) {
        String best = null;
        int bestDistance = 3;
        for (String candidate : candidates) {
            int distance = TagRegistry.editDistance(typo, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best == null ? "可用值见 §2.2 的字段类型 × formatter 表"
                : "你是不是想写 " + best + "？";
    }

    private static PublishException E1002(String what, String subject, String actual, String advice,
                                          String templatePath, int lineNo) {
        String head = what + " · " + subject;
        if (templatePath == null && lineNo <= 0) {
            return PublishException.error(PublishErrorCode.E1002, head, actual, advice);
        }
        return PublishException.error(PublishErrorCode.E1002, head, templatePath, lineNo, actual, advice);
    }
}
