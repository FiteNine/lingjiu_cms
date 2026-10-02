package com.lingjiuw.cms.module.cms.publish.template;

/**
 * 模板源码的读取出口（static-publish.md §4.4、§5.3）。
 *
 * <p>编译器与渲染器**不直接碰磁盘**：模板查找（§7.3）与主题目录（§8.9）在期 2 落地，
 * 期 1 只需要"给一个相对路径，拿到源码与失效信息"。路径边界由
 * {@code common/site/SitePathBoundary} 负责（§11.4），本接口不重复实现它。
 */
public interface TemplateSource {

    /**
     * 片段目录（§7.3 的目录约定）：{@code {cms:include}} 的 {@code file} **相对它**解析
     * （§5.3："file 为纯字面量，相对当前主题的片段目录解析"）。
     *
     * <p>证据是两处互相印证：§7.3 的目录清单把 {@code header.html footer.html card.html
     * pagelist.html} 放在 {@code _partials/} 下并注明"不参与页面查找，只被 include"，
     * 而 §5.3 的示例写的是 {@code {cms:include file='header.html' .../}}——两者只有
     * "base = {@code _partials/}" 这一种读法能同时成立。
     */
    String FRAGMENT_DIR = "_partials/";

    /**
     * 把 include 的 {@code file} 解析成模板树里的真实相对路径。
     *
     * <p>单独成一个方法而不是在编译器里拼字符串：主题布局是 {@link TemplateSource} 的知识
     * （期 2 的主题查找、期 6 的主题包都实现这个接口），编译器只该问"片段在哪"。
     *
     * <p>作者已经写了 {@code _partials/} 前缀时不再重复拼——`file='_partials/header.html'`
     * 与 `file='header.html'` 的意图完全一致，静默接受比报"文件不存在"更有用（这不是
     * "两种写法都合法"的模糊地带，而是同一种意图的两种写法）。
     */
    default String fragmentPath(String file) {
        return file.startsWith(FRAGMENT_DIR) ? file : FRAGMENT_DIR + file;
    }

    /**
     * 读一个模板。
     *
     * @param relativePath 相对主题根目录的路径（片段形如 {@code _partials/header.html}）
     * @return 模板内容与失效信息；不存在返回 null
     */
    Template load(String relativePath);

    /** 模板是否存在（§4.5 第 11 条的模板查找、§5.3 的 include 越界都用它）。 */
    default boolean exists(String relativePath) {
        return load(relativePath) != null;
    }

    /**
     * 一份模板的源码与失效四要素（§4.4 v2.2 定死：{@code 路径 + mtime + size + 内容 sha256}）。
     * **sha256 不能省**：片段同长度替换时字节数不变，只靠 {@code mtime + size} 会让包含它的
     * 模板不重编译，页面上就是静默陈旧。
     *
     * @param path         模板相对路径
     * @param source       源码文本（已按 §4.5 第 17 条校验过编码）
     * @param lastModified 文件修改时间（毫秒）；内存实现可以传 0
     * @param size         字节数
     */
    record Template(String path, String source, long lastModified, long size) {

        /** 供 {@code astVersion} 累加的一段指纹。 */
        public String fingerprint() {
            return path + '|' + lastModified + '|' + size + '|' + sha256(source);
        }

        static String sha256(String text) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
                }
                return sb.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("JDK 缺少 SHA-256", e);
            }
        }
    }
}
