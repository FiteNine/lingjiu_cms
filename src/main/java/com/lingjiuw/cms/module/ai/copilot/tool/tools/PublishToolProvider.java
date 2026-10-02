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
import com.lingjiuw.cms.module.cms.entity.CmsPublishTask;
import com.lingjiuw.cms.module.cms.publish.service.PublishDtos;
import com.lingjiuw.cms.module.cms.publish.service.PublishFacade;
import com.lingjiuw.cms.module.cms.publish.service.SitePublishService;
import com.lingjiuw.cms.module.cms.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 发布相关工具（docs/ai-copilot.md §4.2 CONTENT 组、§4.4）。
 *
 * <p>三条刻意的设计：
 * <ol>
 *   <li>{@code cms_publish_run} 用独立线程提交、立即返回 runId，模型轮询
 *       {@code cms_publish_status}——发布同步阻塞数秒到数分钟，塞进 tool loop 会让 SSE 假死；</li>
 *   <li>发布前**必须先预演**（内部直接调 {@code preview}），有问题一律不写盘——这是"模板写错 →
 *       全站坏页"最便宜的一道防线；</li>
 *   <li>默认 {@code incremental}；{@code full} 会带 GC 删多余产物，只有用户在确认弹窗里
 *       显式勾选才允许（参数默认值写死在这里，不靠提示词约束模型）。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class PublishToolProvider implements ToolProvider {

    private static final String PERMISSION = "cms:publish:run";
    /** 单次返回的发布记录条数上限 */
    private static final int MAX_STATUS_LIMIT = 20;

    private final ToolArgs toolArgs;
    private final ObjectMapper objectMapper;
    private final PublishFacade publishFacade;
    private final SiteService siteService;
    private final PublishRunRegistry registry;

    /** 无参工具的空 schema。 */
    private record Empty() {
    }

    /** 查询发布状态：给了 runId 查本次后台发布，否则查站点最近的发布批次。 */
    private record StatusArgs(String runId, Integer limit) {
    }

    /** 发布入参：mode 默认 incremental，full 需用户在确认弹窗里显式勾选。 */
    private record RunArgs(String mode) {
    }

    @Override
    public List<ToolSpec> tools() {
        return List.of(preview(), status(), run());
    }

    private ToolSpec preview() {
        return ToolSpec.builder("cms_publish_preview")
                .title("预演全站发布")
                .description("预演当前站点的静态化：只算页面计划、不写盘不写库。返回本次会产出哪些页面"
                        + "（URL / 产物路径 / 页面类型 / 模板）、以及模板体检查出的问题。"
                        + "发布前应当先调它确认没有 problems。")
                .permission(PERMISSION)
                .risk(ToolRisk.READ)
                .group(ToolGroup.CONTENT)
                .schema(toolArgs.schemaOf(Empty.class))
                .handler(args -> {
                    PublishDtos.PublishPreview preview = previewFacade();
                    ObjectNode data = objectMapper.createObjectNode();
                    data.put("siteId", preview.siteId());
                    data.put("theme", preview.theme());
                    data.put("totalPages", preview.totalPages());
                    data.set("pages", objectMapper.valueToTree(preview.pages()));
                    data.set("warnings", objectMapper.valueToTree(preview.warnings()));
                    data.set("problems", objectMapper.valueToTree(preview.problems()));
                    String summary = preview.problems().isEmpty()
                            ? "预演完成：将产出 " + preview.totalPages() + " 个页面，无问题"
                            : "预演发现问题 " + preview.problems().size() + " 条，暂不可发布";
                    return ToolResult.ok(summary, data);
                })
                .build();
    }

    private ToolSpec status() {
        return ToolSpec.builder("cms_publish_status")
                .title("查询发布状态")
                .description("查询发布进度。给 runId 查 cms_publish_run 提交的那一次（在途或已完成）；"
                        + "不给 runId 则返回该站点最近的发布批次记录。")
                .permission(PERMISSION)
                .risk(ToolRisk.READ)
                .group(ToolGroup.CONTENT)
                .schema(toolArgs.schemaOf(StatusArgs.class))
                .handler(args -> {
                    StatusArgs request = toolArgs.bind(args, StatusArgs.class);
                    ObjectNode data = objectMapper.createObjectNode();
                    if (request.runId() != null && !request.runId().isBlank()) {
                        PublishRunRegistry.RunStatus run = registry.status(request.runId().trim());
                        if (run == null) {
                            throw new BizException("找不到发布记录：" + request.runId() + "（可能已过期，可在不带 runId 时查看最近批次）");
                        }
                        data.set("run", objectMapper.valueToTree(run));
                        return ToolResult.ok("发布 " + run.runId() + " 当前状态：" + run.state(), data);
                    }
                    int limit = request.limit() == null ? 5 : Math.max(1, Math.min(request.limit(), MAX_STATUS_LIMIT));
                    List<CmsPublishTask> tasks = publishFacade.recentTasks(SiteContext.siteId(), limit);
                    ArrayNode rows = data.putArray("tasks");
                    for (CmsPublishTask task : tasks) {
                        ObjectNode row = rows.addObject();
                        row.put("batchId", task.getBatchId());
                        row.put("trigger", task.getTrigger());
                        row.put("mode", task.getMode());
                        row.put("status", task.getStatus());
                        row.put("total", task.getTotal());
                        row.put("done", task.getDone());
                        row.put("failed", task.getFailed());
                        row.put("createTime", task.getCreateTime() == null ? null : task.getCreateTime().toString());
                    }
                    return ToolResult.ok("最近 " + rows.size() + " 条发布批次", data);
                })
                .build();
    }

    private ToolSpec run() {
        return ToolSpec.builder("cms_publish_run")
                .title("执行全站静态化")
                .description("把当前站点发布成静态站。这是重操作：内部先自动预演，预演出 problems 时直接拒绝、"
                        + "不写盘；通过后在后台线程提交，立即返回 runId，用 cms_publish_status 轮询。"
                        + "mode 默认 incremental（增量，只重写内容变了的页）；full 会全量重写并删除多余产物，"
                        + "只有用户明确要求全量时才传 full。")
                .permission(PERMISSION)
                .risk(ToolRisk.DESTRUCTIVE)
                .group(ToolGroup.CONTENT)
                .schema(toolArgs.schemaOf(RunArgs.class))
                .handler(args -> {
                    RunArgs request = toolArgs.bind(args, RunArgs.class);
                    SitePublishService.Mode mode = parseMode(request.mode());
                    PublishDtos.PublishPreview preview = previewFacade();
                    if (!preview.problems().isEmpty()) {
                        throw new BizException("预演未通过，已取消发布：" + String.join("；", preview.problems()));
                    }
                    long siteId = SiteContext.siteId();
                    Path siteDir = siteService.siteDir(siteId);
                    PublishRunRegistry.RunStatus run = registry.start(siteId, siteDir, mode);
                    ObjectNode data = objectMapper.createObjectNode();
                    data.set("run", objectMapper.valueToTree(run));
                    data.put("plannedPages", preview.totalPages());
                    return ToolResult.ok("已在后台提交" + ("full".equals(mode.name()) ? "全量" : "增量")
                            + "发布（计划 " + preview.totalPages() + " 个页面），runId=" + run.runId()
                            + "，请用 cms_publish_status 轮询", data);
                })
                .build();
    }

    private PublishDtos.PublishPreview previewFacade() {
        long siteId = SiteContext.siteId();
        return publishFacade.preview(siteId, siteService.siteDir(siteId));
    }

    /** 默认 incremental：不传、传空、传错都落增量；只有明确写 full 才是全量。 */
    private static SitePublishService.Mode parseMode(String mode) {
        if (mode != null && "full".equals(mode.trim().toLowerCase(Locale.ROOT))) {
            return SitePublishService.Mode.full;
        }
        return SitePublishService.Mode.incremental;
    }
}
