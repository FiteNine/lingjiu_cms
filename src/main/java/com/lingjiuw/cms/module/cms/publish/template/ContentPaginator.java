package com.lingjiuw.cms.module.cms.publish.template;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * 正文分页切分（static-publish.md §5.2.3）。
 *
 * <p>三条规则，逐条落地：
 * <ol>
 *   <li>分页符是 HTML 注释 {@code <!--cms:page-->}（清洗器必须显式保留它，那是入库清洗的事）；</li>
 *   <li><b>只切顶层</b>：切点落在某个块级元素**内部**时不切，并给一条 {@code W5002} 警告。
 *       "顶层"由 DOM 判定，不由字符串判定——{@code <script>} / {@code <style>} 里出现的同名字符串
 *       **不是注释节点**，因此天然不会被当成分页符；</li>
 *   <li><b>首尾与连续分页符产生的空切片一律丢弃</b>，{@code totalPages} 按**非空切片**计（v2.2 定死）。</li>
 * </ol>
 *
 * <p><b>为什么用 jsoup 而不是正则</b>：§5.2.2 已经写明 jsoup 一库三用（清洗、抽标题树、算字数），
 * 这里再加一用。正则做不到第 2 条——它认不出"这个 {@code <!--cms:page-->} 在 {@code <div>} 里面"
 * 与"它只是 {@code <pre>} 里的一段文本"。
 *
 * <p>切出来的切片是**原始 HTML 的子串**（不是序列化结果）：切片要按 {@code raw} 原样输出，
 * 序列化会改掉实体（{@code &nbsp;} → 不间断空格）而不只是重排标签。
 */
public final class ContentPaginator {

    /** 分页符的注释内容（不含 {@code <!--} 与 {@code -->}）。 */
    public static final String MARKER = "cms:page";

    /** 分页符的完整字面量。 */
    public static final String MARKER_HTML = "<!--" + MARKER + "-->";

    private ContentPaginator() {
    }

    /**
     * 一个切分结果。
     *
     * @param slices   非空切片，按顺序；**一条内容不分页时也只有一个元素**，正文为空时是空列表
     * @param warnings {@code W5002} 警告（落在块级元素内部的分页符的行号），不阻断发布
     */
    public record Split(List<String> slices, List<Warning> warnings) {

        public Split {
            slices = List.copyOf(slices);
            warnings = List.copyOf(warnings);
        }

        /** §5.2.3：{@code totalPages} 按**非空切片**计。 */
        public int totalPages() {
            return slices.size();
        }

        /** 取第 {@code pageNo} 页的切片（从 1 起）；越界返回空串。 */
        public String page(int pageNo) {
            return pageNo >= 1 && pageNo <= slices.size() ? slices.get(pageNo - 1) : "";
        }
    }

    /**
     * 一条 {@code W5002} 警告（§10.2："正文分页符落在块级元素内部"，不阻断发布）。
     *
     * @param lineNo    分页符在正文字段里的行号（正文是独立的一段文本，行号从 1 起）
     * @param container 它落在哪个块级元素里面（如 {@code div}）
     */
    public record Warning(int lineNo, String container) {

        /** 按 §10.1 的三行格式给出文案。 */
        public String message(String fieldCode) {
            return "[" + PublishErrorCode.W5002.name() + "] 正文分页符落在块级元素内部"
                    + "\n  → 现状：它落在 <" + container + "> 里面（" + fieldCode + " 第 " + lineNo + " 行），"
                    + "切开会得到未闭合的标签"
                    + "\n  → 建议：把 " + MARKER_HTML + " 移到两个块级元素之间（分页只切 " + fieldCode + " 的顶层）";
        }
    }

    /**
     * 按 {@code <!--cms:page-->} 切分正文。
     *
     * @param contentHtml 正文字段的值（{@code content} / {@code contentHtml}）；null / 空白返回空结果
     */
    public static Split split(String contentHtml) {
        if (contentHtml == null || contentHtml.isBlank()) {
            return new Split(List.of(), List.of());
        }
        Element context = new Element("div");
        List<Node> nodes = Parser.htmlParser().setTrackPosition(true)
                .parseFragmentInput(contentHtml, context, "");

        List<Integer> cuts = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        // 位置追踪取不到时的字符串查找游标：顶层注释按文档顺序出现，必须从上一次找到的位置之后
        // 继续找——否则第二个及以后的分页符都会命中"整篇首次出现"的位置（见 positionOf）
        int cursor = 0;
        for (Node node : nodes) {
            if (isMarker(node)) {
                int position = positionOf((Comment) node, contentHtml, cursor);
                if (position < cursor) {
                    // 定位不到（注释未闭合、位置追踪缺失）就不切：宁可少一页，
                    // 也不能把非法位置交给 substring 中断整站发布
                    continue;
                }
                cuts.add(position);
                cursor = position + markerLength(contentHtml, position);
            } else {
                // 有分页符但不在顶层：不切，逐个记一条 W5002（§5.2.3 第 2 条）
                for (int position : markerPositions(node, contentHtml)) {
                    warnings.add(new Warning(lineOf(contentHtml, position), node.nodeName()));
                }
            }
        }
        cuts.sort(Integer::compareTo);

        List<String> slices = new ArrayList<>();
        int from = 0;
        for (int cut : cuts) {
            // 切点必须是单调不减、且落在正文之内的合法下标，否则跳过这个切点
            // （位置追踪或字面量查找给出脏值时，substring 会抛 StringIndexOutOfBounds）
            if (cut < from || cut > contentHtml.length()) {
                continue;
            }
            addIfNotEmpty(slices, contentHtml.substring(from, cut));
            from = cut + markerLength(contentHtml, cut);
        }
        addIfNotEmpty(slices, contentHtml.substring(from));
        return new Split(slices, warnings);
    }

    /** 这个节点本身就是分页符注释。 */
    private static boolean isMarker(Node node) {
        return node instanceof Comment comment && MARKER.equals(comment.getData().trim());
    }

    /** 该子树里全部分页符注释在原文里的位置（文档顺序；定位不到的记为 -1）。 */
    private static List<Integer> markerPositions(Node node, String html) {
        List<Integer> positions = new ArrayList<>();
        collectMarkerPositions(node, html, 0, positions);
        return positions;
    }

    /** 一次遍历同时完成"有没有分页符"与"每个分页符在哪"；返回继续搜索用的游标。 */
    private static int collectMarkerPositions(Node node, String html, int from, List<Integer> positions) {
        if (isMarker(node)) {
            int position = positionOf((Comment) node, html, from);
            positions.add(position);
            return position < 0 ? from : position + markerLength(html, position);
        }
        for (Node child : node.childNodes()) {
            from = collectMarkerPositions(child, html, from, positions);
        }
        return from;
    }

    /**
     * 注释在原文里的起始位置。位置追踪在 jsoup 1.18+ 才可靠，取不到时从 {@code from}
     * （上一次找到的分页符之后）起按注释自身的字面量继续查找；都找不到返回 -1。
     */
    private static int positionOf(Comment comment, String html, int from) {
        int position = comment.sourceRange().startPos();
        if (position >= 0) {
            return position;
        }
        return html.indexOf("<!--" + comment.getData() + "-->", Math.max(from, 0));
    }

    /** 注释在原文里的长度：{@code <!--} + 内容 + {@code -->}。 */
    private static int markerLength(String html, int start) {
        int end = html.indexOf("-->", start);
        return end < 0 ? html.length() - start : end + 3 - start;
    }

    private static void addIfNotEmpty(List<String> slices, String slice) {
        if (slice != null && !slice.isBlank()) {
            slices.add(slice);
        }
    }

    /** 某个位置在原文里的行号（位置未知时按第 1 行算）。 */
    private static int lineOf(String html, int position) {
        if (position < 0) {
            position = 0;
        }
        int line = 1;
        for (int i = 0; i < position && i < html.length(); i++) {
            if (html.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
