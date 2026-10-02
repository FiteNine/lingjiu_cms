package com.lingjiuw.cms.module.ai.copilot.tool.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolArgs;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolGroup;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolProvider;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolResult;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolRisk;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolSpec;
import com.lingjiuw.cms.module.cms.dto.ContentTypeSaveRequest;
import com.lingjiuw.cms.module.cms.dto.FieldSaveRequest;
import com.lingjiuw.cms.module.cms.dto.PublishOptionSaveRequest;
import com.lingjiuw.cms.module.cms.service.ContentTypeService;
import com.lingjiuw.cms.module.cms.service.FieldService;
import com.lingjiuw.cms.module.cms.service.PublishOptionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PLATFORM 组工具：内容类型 / 字段 / 发布选项的新增、编辑与删除（docs/ai-copilot.md §4.2）。
 *
 * <p><b>这一组默认不暴露。</b>它们"改整站形态"——内容类型决定页面形态与 URL 规则，字段决定内容模型，
 * 发布选项决定引擎行为。必须在 {@code ai_agent.tool_scope} 里显式写上 {@code PLATFORM} 才会被模型看到。
 *
 * <p>分成两个信任级别的理由：{@code cms:content} 是"改内容"，{@code cms:type} / {@code cms:field} /
 * {@code cms:publish:option} 是"改整站形态"。若混进默认组，等于把"改内容"的授权顺带升级成了"改整站形态"——
 * 写坏一个模板配置，下一次全站发布会整体坏页（§4.6）。删除类工具标为 DESTRUCTIVE，执行前需人工确认。
 */
@Component
@RequiredArgsConstructor
public class PlatformToolProvider implements ToolProvider {

    private final ToolArgs toolArgs;
    private final ObjectMapper objectMapper;
    private final ContentTypeService contentTypeService;
    private final FieldService fieldService;
    private final PublishOptionService publishOptionService;

    @Override
    public List<ToolSpec> tools() {
        return List.of(
                ToolSpec.builder("cms_content_type_create")
                        .title("新增内容类型")
                        .description("新增一个内容类型（决定该类型内容的页面形态、URL 规则与模板）。属于改整站形态的操作")
                        .permission("cms:type:add")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.PLATFORM)
                        .schema(toolArgs.schemaOf(ContentTypeSaveRequest.class))
                        .handler(args -> {
                            ContentTypeSaveRequest request = toolArgs.bind(args, ContentTypeSaveRequest.class);
                            contentTypeService.create(request);
                            return ToolResult.ok("已新增内容类型 " + request.name(), objectMapper.valueToTree(request));
                        })
                        .build(),
                ToolSpec.builder("cms_content_type_update")
                        .title("编辑内容类型")
                        .description("按 id 编辑一个内容类型，需提交全部字段（未传字段会被覆盖为空）。属于改整站形态的操作")
                        .permission("cms:type:edit")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.PLATFORM)
                        .schema(toolArgs.schemaOf(ContentTypeUpdateRequest.class))
                        .handler(args -> {
                            ContentTypeUpdateRequest request = toolArgs.bind(args, ContentTypeUpdateRequest.class);
                            contentTypeService.update(request.id(), request.toSaveRequest());
                            return ToolResult.ok("已更新内容类型 #" + request.id(), objectMapper.valueToTree(request));
                        })
                        .build(),
                ToolSpec.builder("cms_content_type_delete")
                        .title("删除内容类型")
                        .description("按 id 删除一个内容类型；会连带影响该类型下的内容与页面。属于改整站形态的破坏性操作")
                        .permission("cms:type:delete")
                        .risk(ToolRisk.DESTRUCTIVE)
                        .group(ToolGroup.PLATFORM)
                        .schema(toolArgs.schemaOf(IdRequest.class))
                        .handler(args -> {
                            IdRequest request = toolArgs.bind(args, IdRequest.class);
                            contentTypeService.delete(request.id());
                            return ToolResult.ok("已删除内容类型 #" + request.id(),
                                    objectMapper.createObjectNode().put("id", request.id()));
                        })
                        .build(),
                ToolSpec.builder("cms_field_create")
                        .title("新增字段")
                        .description("为指定内容类型新增一个字段（内容模型的一部分）。属于改整站形态的操作")
                        .permission("cms:field:add")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.PLATFORM)
                        .schema(toolArgs.schemaOf(FieldSaveRequest.class))
                        .handler(args -> {
                            FieldSaveRequest request = toolArgs.bind(args, FieldSaveRequest.class);
                            fieldService.create(request);
                            return ToolResult.ok("已为类型 " + request.typeCode() + " 新增字段 " + request.code(),
                                    objectMapper.valueToTree(request));
                        })
                        .build(),
                ToolSpec.builder("cms_field_update")
                        .title("编辑字段")
                        .description("按 id 编辑一个字段，需提交全部字段（五个开关未传会按 0 覆盖）。属于改整站形态的操作")
                        .permission("cms:field:edit")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.PLATFORM)
                        .schema(toolArgs.schemaOf(FieldUpdateRequest.class))
                        .handler(args -> {
                            FieldUpdateRequest request = toolArgs.bind(args, FieldUpdateRequest.class);
                            fieldService.update(request.id(), request.toSaveRequest());
                            return ToolResult.ok("已更新字段 #" + request.id(), objectMapper.valueToTree(request));
                        })
                        .build(),
                ToolSpec.builder("cms_field_delete")
                        .title("删除字段")
                        .description("按 id 删除一个字段；会连带影响该字段的数据与页面。属于改整站形态的破坏性操作")
                        .permission("cms:field:delete")
                        .risk(ToolRisk.DESTRUCTIVE)
                        .group(ToolGroup.PLATFORM)
                        .schema(toolArgs.schemaOf(IdRequest.class))
                        .handler(args -> {
                            IdRequest request = toolArgs.bind(args, IdRequest.class);
                            fieldService.delete(request.id());
                            return ToolResult.ok("已删除字段 #" + request.id(),
                                    objectMapper.createObjectNode().put("id", request.id()));
                        })
                        .build(),
                ToolSpec.builder("cms_publish_option_save")
                        .title("保存发布选项")
                        .description("批量保存当前站点的发布选项（整批 upsert），影响发布引擎行为。属于改整站形态的操作")
                        .permission("cms:publish:option:edit")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.PLATFORM)
                        .schema(toolArgs.schemaOf(PublishOptionSaveRequest.class))
                        .handler(args -> {
                            PublishOptionSaveRequest request = toolArgs.bind(args, PublishOptionSaveRequest.class);
                            publishOptionService.save(request);
                            return ToolResult.ok("已保存 " + request.options().size() + " 个发布选项",
                                    objectMapper.valueToTree(request));
                        })
                        .build()
        );
    }

    /** 仅一个 id 的入参（删除类工具）。 */
    private record IdRequest(@NotNull(message = "id 不能为空") Long id) {
    }

    /** 编辑内容类型入参：id + {@link ContentTypeSaveRequest} 的全部字段。 */
    private record ContentTypeUpdateRequest(
            @NotNull(message = "内容类型 id 不能为空")
            Long id,
            @NotBlank(message = "类型标识不能为空")
            @Pattern(regexp = "[a-z0-9_]{1,64}", message = "类型标识只能用小写字母、数字与下划线")
            String code,
            @NotBlank(message = "类型名称不能为空")
            @Size(max = 64, message = "类型名称最长 64 字符")
            String name,
            @NotBlank(message = "类型形态不能为空")
            String kind,
            @Min(value = 0, message = "是否层级只能是 0 或 1")
            @Max(value = 1, message = "是否层级只能是 0 或 1")
            Integer hierarchical,
            @Size(max = 255, message = "详情页 URL 规则最长 255 字符")
            String detailUrlPattern,
            @Size(max = 255, message = "列表页 URL 规则最长 255 字符")
            String listUrlPattern,
            @Size(max = 255, message = "详情页模板最长 255 字符")
            String detailTemplate,
            @Size(max = 255, message = "列表页模板最长 255 字符")
            String listTemplate,
            @Size(max = 64, message = "正文分页字段最长 64 字符")
            String paginateBody,
            @Size(max = 64, message = "默认排序字段最长 64 字符")
            String sortField,
            @Size(max = 8, message = "排序方向最长 8 字符")
            String sortOrder,
            @Positive(message = "每页条数必须大于 0")
            @Max(value = 200, message = "每页条数最大 200")
            Integer perPage,
            @Size(max = 64, message = "SEO 标题字段最长 64 字符")
            String seoTitleField,
            @Size(max = 64, message = "SEO 描述字段最长 64 字符")
            String seoDescField,
            @Size(max = 4000, message = "类型选项最长 4000 字符")
            String options,
            @Min(value = 0, message = "启用状态只能是 0 或 1")
            @Max(value = 1, message = "启用状态只能是 0 或 1")
            Integer status,
            @Min(value = 0, message = "排序号不能为负数")
            Integer sort) {

        ContentTypeSaveRequest toSaveRequest() {
            return new ContentTypeSaveRequest(code, name, kind, hierarchical, detailUrlPattern, listUrlPattern,
                    detailTemplate, listTemplate, paginateBody, sortField, sortOrder, perPage, seoTitleField,
                    seoDescField, options, status, sort);
        }
    }

    /** 编辑字段入参：id + {@link FieldSaveRequest} 的全部字段。 */
    private record FieldUpdateRequest(
            @NotNull(message = "字段 id 不能为空")
            Long id,
            @NotBlank(message = "所属内容类型不能为空")
            @Size(max = 64, message = "所属内容类型最长 64 字符")
            String typeCode,
            @NotBlank(message = "字段名不能为空")
            @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]{0,63}",
                    message = "字段名只能由字母、数字与下划线组成，并以字母或下划线开头")
            String code,
            @NotBlank(message = "字段显示名不能为空")
            @Size(max = 64, message = "字段显示名最长 64 字符")
            String label,
            @NotBlank(message = "字段类型不能为空")
            String fieldType,
            @Size(max = 255, message = "格式化器最长 255 字符")
            String formatter,
            @NotNull(message = "是否原样输出不能为空")
            @Min(value = 0, message = "是否原样输出只能是 0 或 1")
            @Max(value = 1, message = "是否原样输出只能是 0 或 1")
            Integer raw,
            @NotNull(message = "是否必填不能为空")
            @Min(value = 0, message = "是否必填只能是 0 或 1")
            @Max(value = 1, message = "是否必填只能是 0 或 1")
            Integer required,
            @Size(max = 500, message = "默认值最长 500 字符")
            String defaultValue,
            @Size(max = 1000, message = "选项最长 1000 字符")
            String options,
            @NotNull(message = "是否进搜索索引不能为空")
            @Min(value = 0, message = "是否进搜索索引只能是 0 或 1")
            @Max(value = 1, message = "是否进搜索索引只能是 0 或 1")
            Integer searchable,
            @NotNull(message = "是否进字段索引不能为空")
            @Min(value = 0, message = "是否进字段索引只能是 0 或 1")
            @Max(value = 1, message = "是否进字段索引只能是 0 或 1")
            Integer indexed,
            @NotNull(message = "是否跨站点不能为空")
            @Min(value = 0, message = "是否跨站点只能是 0 或 1")
            @Max(value = 1, message = "是否跨站点只能是 0 或 1")
            Integer crossSite,
            @Size(max = 255, message = "填写提示最长 255 字符")
            String help,
            @PositiveOrZero(message = "排序值不能为负")
            Integer sort) {

        FieldSaveRequest toSaveRequest() {
            return new FieldSaveRequest(typeCode, code, label, fieldType, formatter, raw, required, defaultValue,
                    options, searchable, indexed, crossSite, help, sort);
        }
    }
}
