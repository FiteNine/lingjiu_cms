package com.lingjiuw.cms.module.cms.publish.dev;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.CmsApplication;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.mapper.CmsSiteMapper;
import com.lingjiuw.cms.module.cms.publish.service.PublishDtos;
import com.lingjiuw.cms.module.cms.publish.service.PublishFacade;
import com.lingjiuw.cms.module.cms.publish.service.SitePublishService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开发用的一键全站静态化命令行入口。
 *
 * <p><b>为什么它存在</b>：手工验收时最需要的是"改一行模板，立刻看到 {@code www/} 里的产物"。
 * HTTP 接口已经能做这件事（{@code POST /api/cms/publish/site}），但它要一个登录态与一个
 * 站点上下文；命令行把同一条链（{@code PublishFacade → SitePublishService}）在本地重放一遍。
 * 因此它**不是第二套逻辑**：只换了入口，没有换实现。
 *
 * <p><b>为什么还要落一份文件</b>：在 Windows 上把 Java 进程的 stdout 通过管道转发给别的程序，
 * 中文会被终端编码吃掉（控制台看到的是一串乱码，而乱码里正好藏着"失败在哪一页"这个关键信息）。
 * 因此除了打印，它还把完整报告按 UTF-8 写到 {@code --report=<路径>}，验收时读那个文件即可。
 *
 * <p>用法：{@code SitePublishCli <站点code> [站点目录] [full|incremental] [--report=路径]}。
 */
public final class SitePublishCli {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SitePublishCli() {
    }

    public static void main(String[] args) throws IOException {
        List<String> positional = new ArrayList<>();
        Path reportPath = null;
        for (String arg : args) {
            if (arg.startsWith("--report=")) {
                String value = arg.substring("--report=".length()).trim();
                if (value.isEmpty()) {
                    System.err.println("--report= 后面要跟一个文件路径");
                    System.exit(2);
                }
                reportPath = Path.of(value);
            } else {
                positional.add(arg);
            }
        }

        String siteCode = positional.size() > 0 ? positional.get(0) : "demo";
        Path siteDir = Path.of(positional.size() > 1 ? positional.get(1) : "./sites/" + siteCode)
                .toAbsolutePath().normalize();
        // 非法 mode 以前静默降级成 full（全量重写），与用户的"只想增量"预期不符：直接报错
        String modeText = positional.size() > 2 ? positional.get(2).trim() : "full";
        if (!"full".equalsIgnoreCase(modeText) && !"incremental".equalsIgnoreCase(modeText)) {
            System.err.println("mode 只接受 full 或 incremental，收到：" + modeText
                    + "\n用法：SitePublishCli <站点code> [站点目录] [full|incremental] [--report=路径]");
            System.exit(2);
        }
        SitePublishService.Mode mode = "incremental".equalsIgnoreCase(modeText)
                ? SitePublishService.Mode.incremental : SitePublishService.Mode.full;

        List<String> report = new ArrayList<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        int exitCode = 0;

        try (ConfigurableApplicationContext context =
                     SpringApplication.run(CmsApplication.class, new String[]{"--server.port=0"})) {
            // 站点目录直接作为落盘目录传下去，因此必须落在 cms.site.root-dir 之内（§7.8 的路径边界）：
            // 不校验就等于"命令行给哪个路径就往哪里写一套产物"。
            assertInsideSiteRoot(context, siteDir);
            PublishFacade facade = context.getBean(PublishFacade.class);
            long siteId = siteIdOf(context.getBean(CmsSiteMapper.class), siteCode);
            summary.put("siteCode", siteCode);
            summary.put("siteId", siteId);
            summary.put("siteDir", siteDir.toString());
            summary.put("mode", mode.name());
            report.add("站点 " + siteCode + "（id=" + siteId + "） 目录 " + siteDir + " 模式 " + mode);

            PublishDtos.PublishPreview preview = facade.preview(siteId, siteDir);
            report.add("预演：主题 " + preview.theme() + "，计划 " + preview.totalPages() + " 个页面");
            preview.warnings().forEach(warning -> report.add("  [预演警告] " + warning));
            preview.problems().forEach(problem -> report.add("  [预演问题] " + problem));
            summary.put("theme", preview.theme());
            summary.put("plannedPages", preview.totalPages());
            summary.put("previewWarnings", preview.warnings());
            summary.put("previewProblems", preview.problems());
            for (PublishDtos.PublishedPage page : preview.pages()) {
                report.add("  " + page.pageType() + "  " + page.url() + "  →  " + page.path()
                        + "   [" + page.template() + "]");
            }

            if (!preview.problems().isEmpty()) {
                report.add("预演不通过，停止发布");
                summary.put("published", false);
                exitCode = 1;
            } else {
                SitePublishService.PublishResult result = facade.publish(siteId, siteDir, mode, "manual");
                summary.put("published", true);
                summary.put("batchId", result.batchId());
                summary.put("totalPages", result.totalPages());
                summary.put("writtenPages", result.writtenPages());
                summary.put("skippedPages", result.skippedPages());
                summary.put("failedPages", result.failedPages());
                summary.put("deletedArtifacts", result.deletedArtifacts());
                summary.put("aggregateArtifacts", result.aggregateArtifacts());
                summary.put("elapsedMillis", result.elapsedMillis());
                summary.put("outputDir", result.outputDir());
                summary.put("warnings", result.warnings());
                summary.put("errors", result.errors());
                summary.put("success", result.success());

                report.add("批次 " + result.batchId() + "，用时 " + result.elapsedMillis() + " ms");
                report.add("计划 " + result.totalPages() + " 页 / 写出 " + result.writtenPages()
                        + " / 跳过 " + result.skippedPages() + " / 失败 " + result.failedPages()
                        + " / 删除产物 " + result.deletedArtifacts());
                report.add("聚合产物：" + String.join(", ", result.aggregateArtifacts()));
                report.add("输出目录：" + result.outputDir());
                result.warnings().forEach(warning -> report.add("  [警告] " + warning));
                result.errors().forEach(error -> report.add("  [错误] " + error));
                if (!result.success()) {
                    report.add("发布未全部成功");
                    exitCode = 1;
                }
                summary.put("artifacts",
                        result.outputDir() == null ? 0 : countArtifacts(Path.of(result.outputDir())));
            }
        } catch (Exception e) {
            // 受检异常（preview/publish 的 IOException）与运行时异常一样要留下报告与退出码，
            // 否则一次失败既没有报告文件也没有非零退出码，脚本层看不出发生过什么
            report.add("发布抛出异常：" + e);
            summary.put("published", false);
            summary.put("exception", String.valueOf(e));
            exitCode = 1;
        }

        String text = String.join("\n", report) + "\n";
        System.out.println(text);
        Path target = reportPath != null ? reportPath : Path.of("target", "publish-report.txt");
        writeReport(target, text, summary);
        System.out.println("报告已写入 " + target.toAbsolutePath());
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static void writeReport(Path target, String text, Map<String, Object> summary)
            throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, text, StandardCharsets.UTF_8);
        Files.writeString(target.resolveSibling(target.getFileName() + ".json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary),
                StandardCharsets.UTF_8);
    }

    private static int countArtifacts(Path outputDir) {
        if (!Files.isDirectory(outputDir)) {
            return 0;
        }
        try (var stream = Files.walk(outputDir)) {
            return (int) stream.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            // 以前静默返回 -1，会被当成"产物数量 -1"写进 summary；这里明确记为未知并告警
            System.err.println("统计产物数量失败：" + outputDir + "（" + e.getMessage() + "）");
            return 0;
        }
    }

    /**
     * 站点目录必须在 {@code cms.site.root-dir} 之内（{@link com.lingjiuw.cms.common.site.SitePathBoundary}
     * 的边界就是它）。未配置该项时只提醒一次，不挡开发使用。
     */
    private static void assertInsideSiteRoot(ConfigurableApplicationContext context, Path siteDir) {
        String rootDir = context.getEnvironment().getProperty("cms.site.root-dir");
        if (rootDir == null || rootDir.isBlank()) {
            System.err.println("注意：未配置 cms.site.root-dir，跳过站点目录的边界校验：" + siteDir);
            return;
        }
        Path root = Path.of(rootDir).toAbsolutePath().normalize();
        if (!siteDir.startsWith(root)) {
            System.err.println("站点目录越界：" + siteDir + " 不在 cms.site.root-dir（" + root + "）之内");
            System.exit(2);
        }
    }

    private static long siteIdOf(CmsSiteMapper mapper, String code) {
        CmsSite site = mapper.selectOne(Wrappers.<CmsSite>lambdaQuery()
                .eq(CmsSite::getCode, code).last("limit 1"));
        if (site == null) {
            throw new IllegalStateException("站点 code 不存在：" + code);
        }
        return site.getId();
    }
}
