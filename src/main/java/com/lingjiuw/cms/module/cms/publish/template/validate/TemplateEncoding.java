package com.lingjiuw.cms.module.cms.publish.template.validate;

import com.lingjiuw.cms.module.cms.publish.error.PublishErrorCode;
import com.lingjiuw.cms.module.cms.publish.error.PublishException;

/**
 * 模板编码检查（static-publish.md §4.5 第 17 条）。
 *
 * <p>契约要求"非 UTF-8 → 报错，不做猜测"：**不做 GBK 之类的心跳回退**——猜错的代价是"页面上一半
 * 是乱码"，而报错的代价只是让人另存一次文件。两个能在**已解码的字符串**上查出来的现象在这里拦：
 * <ul>
 *   <li><b>BOM</b>（{@code U+FEFF} 开头）：§10.2 把"有 BOM"列在 E1007 的触发里。BOM 会被原样
 *       写进产物，让 {@code <!DOCTYPE html>} 之前多出三个字节；</li>
 *   <li><b>替换字符</b> {@code U+FFFD}：以宽松方式把 GBK 字节按 UTF-8 解码的典型结果。
 *       <b>这里是检测非 UTF-8 字节的唯一手段</b>：生产实现 {@code FileTemplateSource.load} 用的是
 *       {@code new String(bytes, UTF_8)}（宽松解码，非法字节替换成 U+FFFD），并不会抛
 *       {@code CodingErrorAction.REPORT}。别据"严格解码是 TemplateSource 的事"删掉这一段。</li>
 * </ul>
 */
public final class TemplateEncoding {

    private TemplateEncoding() {
    }

    /**
     * 校验一份模板源码的编码，返回可以交给解析器的文本。
     *
     * @param source 已解码的模板源码；{@code null} / 空串按空模板处理（返回空串，解析出空 AST）
     * @param path   模板相对路径（报错位置用）
     * @throws PublishException E1007（编码不是 UTF-8 / 有 BOM）
     */
    public static String check(String source, String path) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        if (source.charAt(0) == '\uFEFF') {
            throw PublishException.error(PublishErrorCode.E1007,
                    "模板带 UTF-8 BOM", path, 1,
                    "文件第 1 个字符是 U+FEFF",
                    "把文件另存为「UTF-8（无 BOM）」——BOM 会被原样写进产物（§4.5 第 17 条）");
        }
        int bad = source.indexOf('\uFFFD');
        if (bad >= 0) {
            throw PublishException.error(PublishErrorCode.E1007,
                    "模板编码不是 UTF-8", path, lineOf(source, bad),
                    "第 " + (bad + 1) + " 个字符是替换字符 U+FFFD（非 UTF-8 字节按 UTF-8 解码的结果）",
                    "把文件另存为 UTF-8（常见来源是 GBK / ANSI）；引擎不做编码猜测（§4.5 第 17 条）");
        }
        return source;
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
