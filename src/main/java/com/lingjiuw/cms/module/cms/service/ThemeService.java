package com.lingjiuw.cms.module.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.common.site.SitePathBoundary;
import com.lingjiuw.cms.module.cms.dto.ThemeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 主题列表（{@code cms_site.theme} 的下拉）。主题是目录不是表：
 * {@code <站点目录>/template/<主题名>/}，元数据在该目录的 {@code theme.json} 里（§7.3；
 * 现成示例见 {@code sites/demo/template/mint/theme.json}）。
 *
 * <p>目录边界复用 {@link SiteService#siteDir} + {@link SitePathBoundary}：只列当前站点
 * {@code template/} 的直接子目录，路径不自己拼——绝对路径与 {@code ..} 上跳由边界工具拦下，
 * 符号链接目录不跟随。
 *
 * <p>{@code theme.json} 缺失或不是 JSON 对象时用目录名兜底并标 {@code valid=false}：
 * 主题元数据完整是发布期的要求，后台列表不该因为一个手工放进来的目录整页打不开。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeService {

    /** 主题目录：站点目录下固定存在（建站时自动创建，见 SiteService.prepareSiteDir） */
    private static final String TEMPLATE_DIR = "template";

    /** 主题元数据文件名 */
    private static final String THEME_FILE = "theme.json";

    private final SiteService siteService;
    private final ObjectMapper objectMapper;

    public List<ThemeVO> list() {
        Path siteDir = siteService.siteDir(SiteContext.siteId());
        Path templateDir = SitePathBoundary.resolveUnder(siteDir, TEMPLATE_DIR);
        if (!Files.isDirectory(templateDir, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(templateDir)) {
            return stream.filter(item -> Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing((Path item) -> item.getFileName().toString()))
                    .map(themeDir -> readTheme(siteDir, themeDir))
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            // Files.list 是惰性流：迭代中途的 IO 错误被包装成 UncheckedIOException，不接住会冒泡成 500
            throw new BizException("读取主题目录失败：" + e.getMessage());
        }
    }

    private ThemeVO readTheme(Path siteDir, Path themeDir) {
        String code = themeDir.getFileName().toString();
        String path = SitePathBoundary.relative(siteDir, themeDir);
        JsonNode meta = readMeta(themeDir.resolve(THEME_FILE));
        if (meta == null) {
            return new ThemeVO(code, code, null, null, path, false);
        }
        return new ThemeVO(code, text(meta, "name", code), text(meta, "version", null),
                text(meta, "description", null), path, true);
    }

    /** 读 theme.json：不存在、不是普通文件、读不出来、不是 JSON 对象，都返回 null（调用方兜底） */
    private JsonNode readMeta(Path file) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            // 走字节而不是字符串：Windows 上手工存的 theme.json 常带 UTF-8 BOM，
            // Jackson 在读字节时会自己认编码并跳过 BOM
            JsonNode node = objectMapper.readTree(Files.readAllBytes(file));
            return node != null && node.isObject() ? node : null;
        } catch (IOException e) {
            log.warn("读取主题元数据失败：{} - {}", file, e.getMessage());
            return null;
        }
    }

    /** 取字符串字段：缺失或空串一律回落到兜底值 */
    private static String text(JsonNode meta, String field, String fallback) {
        JsonNode node = meta.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return fallback;
        }
        return node.asText().trim();
    }
}
