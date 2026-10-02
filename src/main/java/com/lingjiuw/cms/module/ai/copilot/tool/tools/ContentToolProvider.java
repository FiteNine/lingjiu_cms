package com.lingjiuw.cms.module.ai.copilot.tool.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolArgs;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolGroup;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolProvider;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolResult;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolRisk;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolSpec;
import com.lingjiuw.cms.module.cms.dto.ContentQueryRequest;
import com.lingjiuw.cms.module.cms.dto.ContentSaveRequest;
import com.lingjiuw.cms.module.cms.dto.ContentVO;
import com.lingjiuw.cms.module.cms.service.ContentService;
import com.lingjiuw.cms.module.cms.service.StatsService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 内容运营工具（docs/ai-copilot.md §4.2 的 CONTENT 组）。
 *
 * <p>全部是现有 Service 的薄包装：事务、站点隔离、业务校验一律复用，不在这里重写（§2 的"包装 Service
 * 而不是 Controller"）。返回值按 §7.7 瘦身——列表不回正文，正文只走详情工具且截断 8K。
 *
 * <p>写工具粒度"一次一条"（§4.4）：批量由模型自己多轮调用，每一步都可确认、可中止、可审计。
 */
@Component
@RequiredArgsConstructor
public class ContentToolProvider implements ToolProvider {

    /** 详情正文的字符上限（§7.7）：超过就截断并标注，避免一次 get 撑爆上下文 */
    private static final int MAX_CONTENT = 8000;

    /** 列表每页条数上限（§7.7） */
    private static final int MAX_PAGE_SIZE = 50;

    private final ToolArgs toolArgs;
    private final ObjectMapper objectMapper;
    private final ContentService contentService;
    private final StatsService statsService;

    @Override
    public List<ToolSpec> tools() {
        return List.of(
                ToolSpec.builder("cms_stats")
                        .title("站点概览统计")
                        .description("查看当前站点的概览统计：内容总数/已发布/草稿、分类数、标签数、"
                                + "媒体数、用户数与最近内容。无入参。")
                        .permission(null)
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(NoArgs.class))
                        .handler(args -> {
                            toolArgs.bindEmpty(NoArgs.class);
                            var stats = statsService.stats();
                            return ToolResult.ok("内容 " + stats.contentTotal() + " 条（已发布 "
                                    + stats.contentPublished() + " / 草稿 " + stats.contentDraft() + "）",
                                    objectMapper.valueToTree(stats));
                        })
                        .build(),

                ToolSpec.builder("cms_content_list")
                        .title("查询内容列表")
                        .description("按类型/状态/关键字分页查询当前站点的内容，返回摘要字段（不含正文）。"
                                + "page 默认 1，size 默认 10、上限 50。")
                        .permission("cms:content:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(ContentListRequest.class))
                        .handler(args -> {
                            ContentListRequest request = toolArgs.bind(args, ContentListRequest.class);
                            long page = pageOf(request.page());
                            long size = sizeOf(request.size(), 10);
                            PageResult<ContentVO> result = contentService.page(new ContentQueryRequest(
                                    page, size, request.typeCode(), request.status(), request.keyword()));
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("total", result.getTotal());
                            ArrayNode records = data.putArray("records");
                            for (ContentVO vo : result.getRecords()) {
                                ObjectNode item = records.addObject();
                                item.put("id", vo.id());
                                item.put("title", vo.title());
                                item.put("slug", vo.slug());
                                item.put("status", vo.status());
                                item.put("typeCode", vo.typeCode());
                                item.set("updateTime", objectMapper.valueToTree(vo.updateTime()));
                            }
                            return ToolResult.ok("共 " + result.getTotal() + " 条内容，本页返回 "
                                    + result.getRecords().size() + " 条", data);
                        })
                        .build(),

                ToolSpec.builder("cms_content_get")
                        .title("读取内容详情")
                        .description("读取一条内容的完整详情（含正文与自定义字段），正文超过 "
                                + MAX_CONTENT + " 字符会被截断并标注。")
                        .permission("cms:content:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(IdRequest.class))
                        .handler(args -> {
                            IdRequest request = toolArgs.bind(args, IdRequest.class);
                            ContentVO vo = contentService.detail(request.id());
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("id", vo.id());
                            data.put("typeCode", vo.typeCode());
                            data.put("typeName", vo.typeName());
                            data.put("parentId", vo.parentId());
                            data.put("slug", vo.slug());
                            data.put("title", vo.title());
                            data.put("summary", vo.summary());
                            data.put("cover", vo.cover());
                            data.put("status", vo.status());
                            data.put("sort", vo.sort());
                            data.put("top", vo.top());
                            data.put("recommend", vo.recommend());
                            data.set("publishTime", objectMapper.valueToTree(vo.publishTime()));
                            data.put("authorName", vo.authorName());
                            data.put("viewCount", vo.viewCount());
                            data.put("contentFormat", vo.contentFormat());
                            data.put("content", truncate(vo.content()));
                            data.put("wordCount", vo.wordCount());
                            data.set("data", objectMapper.valueToTree(vo.data()));
                            data.put("seoTitle", vo.seoTitle());
                            data.put("seoDescription", vo.seoDescription());
                            data.put("seoKeywords", vo.seoKeywords());
                            data.set("categoryIds", longArray(vo.categoryIds()));
                            data.set("tagIds", longArray(vo.tagIds()));
                            data.set("createTime", objectMapper.valueToTree(vo.createTime()));
                            data.set("updateTime", objectMapper.valueToTree(vo.updateTime()));
                            return ToolResult.ok("已读取内容 #" + vo.id() + "《" + vo.title() + "》", data);
                        })
                        .build(),

                ToolSpec.builder("cms_content_create")
                        .title("新增内容")
                        .description("在当前站点新增一条内容。一次只处理一条，不提供批量。"
                                + "typeCode 必填（内容类型标识），title 必填。")
                        .permission("cms:content:add")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(ContentSaveRequest.class))
                        .handler(args -> {
                            ContentSaveRequest request = toolArgs.bind(args, ContentSaveRequest.class);
                            contentService.create(request);
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("title", request.title());
                            return ToolResult.ok("已新增内容《" + request.title() + "》", data);
                        })
                        .build(),

                ToolSpec.builder("cms_content_update")
                        .title("编辑内容")
                        .description("编辑当前站点已有内容的全部字段。一次只处理一条，id 必填。")
                        .permission("cms:content:edit")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(ContentUpdateRequest.class))
                        .handler(args -> {
                            ContentUpdateRequest request = toolArgs.bind(args, ContentUpdateRequest.class);
                            contentService.update(request.id(), new ContentSaveRequest(
                                    request.typeCode(), request.parentId(), request.slug(), request.title(),
                                    request.summary(), request.cover(), request.status(), request.sort(),
                                    request.top(), request.recommend(), request.publishTime(),
                                    request.authorId(), request.data(), request.content(),
                                    request.contentFormat(), request.seoTitle(), request.seoDescription(),
                                    request.seoKeywords(), request.categoryIds(), request.tagIds()));
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("id", request.id());
                            return ToolResult.ok("已更新内容 #" + request.id() + "《" + request.title() + "》",
                                    data);
                        })
                        .build(),

                ToolSpec.builder("cms_content_set_status")
                        .title("修改内容状态")
                        .description("修改内容状态。status 常见取值：DRAFT（草稿）/ PUBLISHED（已发布）/ "
                                + "OFFLINE（已下线）。")
                        .permission("cms:content:publish")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(ContentSetStatusRequest.class))
                        .handler(args -> {
                            ContentSetStatusRequest request = toolArgs.bind(args,
                                    ContentSetStatusRequest.class);
                            contentService.updateStatus(request.id(), request.status());
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("id", request.id());
                            data.put("status", request.status());
                            return ToolResult.ok("已把内容 #" + request.id() + " 的状态改为 "
                                    + request.status(), data);
                        })
                        .build(),

                ToolSpec.builder("cms_content_delete")
                        .title("删除内容")
                        .description("删除一条内容（逻辑删除，连同分类/标签关联与索引行）。id 必填。")
                        .permission("cms:content:delete")
                        .risk(ToolRisk.DESTRUCTIVE)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(IdRequest.class))
                        .handler(args -> {
                            IdRequest request = toolArgs.bind(args, IdRequest.class);
                            // 先取标题再删：删完 detail 就查不到了
                            ContentVO vo = contentService.detail(request.id());
                            contentService.delete(request.id());
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("id", request.id());
                            return ToolResult.ok("已删除内容 #" + request.id() + "《" + vo.title() + "》",
                                    data);
                        })
                        .build());
    }

    /** 省略分页参数时按默认值补齐，并把上限压到 {@link #MAX_PAGE_SIZE}。 */
    private static long pageOf(Long page) {
        return page == null || page < 1 ? 1 : page;
    }

    private static long sizeOf(Long size, long fallback) {
        long value = size == null || size < 1 ? fallback : size;
        return Math.min(value, MAX_PAGE_SIZE);
    }

    private static String truncate(String content) {
        if (content == null || content.length() <= MAX_CONTENT) {
            return content;
        }
        return content.substring(0, MAX_CONTENT) + "...(已截断)";
    }

    private ArrayNode longArray(List<Long> values) {
        ArrayNode array = objectMapper.createArrayNode();
        if (values != null) {
            for (Long value : values) {
                array.add(value);
            }
        }
        return array;
    }

    /** 无入参工具的空 schema 载体。 */
    private record NoArgs() {
    }

    private record ContentListRequest(Long page, Long size, String typeCode, String status,
                                      String keyword) {
    }

    private record IdRequest(@NotNull(message = "内容 id 不能为空") Long id) {
    }

    private record ContentSetStatusRequest(
            @NotNull(message = "内容 id 不能为空") Long id,
            @NotBlank(message = "状态不能为空") String status) {
    }

    /**
     * update 专用入参：record 不能继承，所以把 {@link ContentSaveRequest} 的全部字段逐字复制一份，
     * 额外加 id；处理器里再组装回 ContentSaveRequest 交给 Service。
     */
    private record ContentUpdateRequest(
            @NotNull(message = "内容 id 不能为空") Long id,
            @NotBlank(message = "内容类型不能为空") @Size(max = 64, message = "内容类型最长 64 字符") String typeCode,
            Long parentId,
            @Size(max = 255, message = "内容标识最长 255 字符") String slug,
            @NotBlank(message = "标题不能为空") @Size(max = 255, message = "标题最长 255 字符") String title,
            @Size(max = 500, message = "摘要最长 500 字符") String summary,
            @Size(max = 255, message = "封面图最长 255 字符") String cover,
            @Size(max = 16, message = "内容状态最长 16 字符") String status,
            Integer sort,
            Boolean top,
            Boolean recommend,
            LocalDateTime publishTime,
            Long authorId,
            Map<String, Object> data,
            String content,
            String contentFormat,
            @Size(max = 255, message = "SEO 标题最长 255 字符") String seoTitle,
            @Size(max = 500, message = "SEO 描述最长 500 字符") String seoDescription,
            @Size(max = 255, message = "SEO 关键词最长 255 字符") String seoKeywords,
            List<Long> categoryIds,
            List<Long> tagIds) {
    }
}
