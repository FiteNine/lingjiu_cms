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
import com.lingjiuw.cms.module.cms.dto.CategorySaveRequest;
import com.lingjiuw.cms.module.cms.dto.MenuItemSaveRequest;
import com.lingjiuw.cms.module.cms.dto.TagSaveRequest;
import com.lingjiuw.cms.module.cms.entity.CmsMedia;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.service.CategoryService;
import com.lingjiuw.cms.module.cms.service.CmsMenuService;
import com.lingjiuw.cms.module.cms.service.ContentTypeService;
import com.lingjiuw.cms.module.cms.service.FieldService;
import com.lingjiuw.cms.module.cms.service.MediaService;
import com.lingjiuw.cms.module.cms.service.SiteService;
import com.lingjiuw.cms.module.cms.service.TagService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 站点结构与素材工具（docs/ai-copilot.md §4.2 的 CONTENT 组）：
 * 分类、标签、导航菜单、媒体库、站点信息、内容类型与字段定义。
 *
 * <p>与 {@link ContentToolProvider} 一样是现有 Service 的薄包装；列表形态的返回值按 §7.7 瘦身，
 * 只留模型判断与后续调用需要的字段。
 */
@Component
@RequiredArgsConstructor
public class StructureToolProvider implements ToolProvider {

    /** 列表每页条数上限（§7.7） */
    private static final int MAX_PAGE_SIZE = 50;

    private final ToolArgs toolArgs;
    private final ObjectMapper objectMapper;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final CmsMenuService cmsMenuService;
    private final MediaService mediaService;
    private final SiteService siteService;
    private final ContentTypeService contentTypeService;
    private final FieldService fieldService;

    @Override
    public List<ToolSpec> tools() {
        return List.of(
                ToolSpec.builder("cms_category_tree")
                        .title("查询分类树")
                        .description("查询当前站点的分类树（含每个分类下的内容数）。无入参。")
                        .permission("cms:category:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(NoArgs.class))
                        .handler(args -> {
                            toolArgs.bindEmpty(NoArgs.class);
                            var tree = categoryService.tree();
                            return ToolResult.ok("共 " + tree.size() + " 个顶级分类",
                                    objectMapper.valueToTree(tree));
                        })
                        .build(),

                ToolSpec.builder("cms_category_create")
                        .title("新增分类")
                        .description("新增一个分类。name 必填；parentId 为空或 0 表示顶级分类。")
                        .permission("cms:category:add")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(CategorySaveRequest.class))
                        .handler(args -> {
                            CategorySaveRequest request = toolArgs.bind(args, CategorySaveRequest.class);
                            categoryService.create(request);
                            return ToolResult.ok("已新增分类《" + request.name() + "》", null);
                        })
                        .build(),

                ToolSpec.builder("cms_category_update")
                        .title("编辑分类")
                        .description("编辑一个分类，id 必填（字段与新增一致，未传的按空处理）。")
                        .permission("cms:category:edit")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(CategoryUpdateRequest.class))
                        .handler(args -> {
                            CategoryUpdateRequest request = toolArgs.bind(args,
                                    CategoryUpdateRequest.class);
                            categoryService.update(request.id(), new CategorySaveRequest(
                                    request.parentId(), request.name(), request.slug(),
                                    request.description(), request.cover(), request.sort(),
                                    request.status()));
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("id", request.id());
                            return ToolResult.ok("已更新分类 #" + request.id() + "《" + request.name() + "》",
                                    data);
                        })
                        .build(),

                ToolSpec.builder("cms_category_delete")
                        .title("删除分类")
                        .description("删除一个分类。存在子分类或分类下有内容时会被拒绝。id 必填。")
                        .permission("cms:category:delete")
                        .risk(ToolRisk.DESTRUCTIVE)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(IdRequest.class))
                        .handler(args -> {
                            IdRequest request = toolArgs.bind(args, IdRequest.class);
                            categoryService.delete(request.id());
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("id", request.id());
                            return ToolResult.ok("已删除分类 #" + request.id(), data);
                        })
                        .build(),

                ToolSpec.builder("cms_tag_list")
                        .title("查询标签列表")
                        .description("查询当前站点的标签列表（含内容数），可按关键字过滤名称。")
                        .permission("cms:tag:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(TagListRequest.class))
                        .handler(args -> {
                            TagListRequest request = toolArgs.bind(args, TagListRequest.class);
                            var tags = tagService.list(request.keyword());
                            return ToolResult.ok("共 " + tags.size() + " 个标签",
                                    objectMapper.valueToTree(tags));
                        })
                        .build(),

                ToolSpec.builder("cms_tag_create")
                        .title("新增标签")
                        .description("新增一个标签。name 必填；slug 可选，只能是小写字母、数字与连字符。")
                        .permission("cms:tag:add")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(TagSaveRequest.class))
                        .handler(args -> {
                            TagSaveRequest request = toolArgs.bind(args, TagSaveRequest.class);
                            tagService.create(request);
                            return ToolResult.ok("已新增标签《" + request.name() + "》", null);
                        })
                        .build(),

                ToolSpec.builder("cms_tag_update")
                        .title("编辑标签")
                        .description("编辑一个标签，id 必填。")
                        .permission("cms:tag:edit")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(TagUpdateRequest.class))
                        .handler(args -> {
                            TagUpdateRequest request = toolArgs.bind(args, TagUpdateRequest.class);
                            tagService.update(request.id(), new TagSaveRequest(request.name(),
                                    request.slug()));
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("id", request.id());
                            return ToolResult.ok("已更新标签 #" + request.id() + "《" + request.name() + "》",
                                    data);
                        })
                        .build(),

                ToolSpec.builder("cms_menu_tree")
                        .title("查询导航菜单")
                        .description("查询当前站点的导航菜单与菜单项树。无入参。")
                        .permission("cms:menu:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(NoArgs.class))
                        .handler(args -> {
                            toolArgs.bindEmpty(NoArgs.class);
                            var menus = cmsMenuService.list();
                            return ToolResult.ok("共 " + menus.size() + " 个导航菜单",
                                    objectMapper.valueToTree(menus));
                        })
                        .build(),

                ToolSpec.builder("cms_menu_item_create")
                        .title("新增菜单项")
                        .description("在指定菜单下新增一个菜单项。menuId 与 kind 必填；kind 合法取值："
                                + "category / content / url / type / tag / archive / custom / author。"
                                + "url 类型必须填 url，type/tag/archive 必须填 refCode，"
                                + "category/content 必须填 refId。")
                        .permission("cms:menu:add")
                        .risk(ToolRisk.WRITE)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(MenuItemCreateRequest.class))
                        .handler(args -> {
                            MenuItemCreateRequest request = toolArgs.bind(args,
                                    MenuItemCreateRequest.class);
                            cmsMenuService.createItem(request.menuId(), new MenuItemSaveRequest(
                                    request.parentId(), request.label(), request.kind(), request.refId(),
                                    request.refCode(), request.url(), request.target(), request.rel(),
                                    request.visible(), request.sort()));
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("menuId", request.menuId());
                            return ToolResult.ok("已在菜单 #" + request.menuId() + " 下新增菜单项", data);
                        })
                        .build(),

                ToolSpec.builder("cms_media_list")
                        .title("查询媒体库")
                        .description("分页查询当前站点的媒体库，可按文件名关键字过滤。"
                                + "page 默认 1，size 默认 10、上限 50。")
                        .permission("cms:media:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(MediaListRequest.class))
                        .handler(args -> {
                            MediaListRequest request = toolArgs.bind(args, MediaListRequest.class);
                            PageResult<CmsMedia> result = mediaService.page(
                                    pageOf(request.page()), sizeOf(request.size(), 10),
                                    request.keyword());
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("total", result.getTotal());
                            ArrayNode records = data.putArray("records");
                            for (CmsMedia media : result.getRecords()) {
                                ObjectNode item = records.addObject();
                                item.put("id", media.getId());
                                item.put("name", media.getName());
                                item.put("path", media.getPath());
                                item.put("url", media.getUrl());
                                item.put("size", media.getSize());
                                item.put("mimeType", media.getMimeType());
                                item.put("width", media.getWidth());
                                item.put("height", media.getHeight());
                            }
                            return ToolResult.ok("共 " + result.getTotal() + " 个媒体文件，本页返回 "
                                    + result.getRecords().size() + " 个", data);
                        })
                        .build(),

                ToolSpec.builder("cms_site_get")
                        .title("查询站点信息")
                        .description("查询当前用户可访问的站点列表（只回站点概要字段）。无入参。")
                        .permission("cms:site:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(NoArgs.class))
                        .handler(args -> {
                            toolArgs.bindEmpty(NoArgs.class);
                            List<CmsSite> sites = siteService.list();
                            ArrayNode data = objectMapper.createArrayNode();
                            for (CmsSite site : sites) {
                                ObjectNode item = data.addObject();
                                item.put("id", site.getId());
                                item.put("name", site.getName());
                                item.put("code", site.getCode());
                                item.put("domain", site.getDomain());
                                item.put("theme", site.getTheme());
                                item.put("status", site.getStatus());
                                item.put("isDefault", site.getIsDefault());
                            }
                            return ToolResult.ok("共 " + sites.size() + " 个站点", data);
                        })
                        .build(),

                ToolSpec.builder("cms_content_type_list")
                        .title("查询内容类型")
                        .description("分页查询当前站点的内容类型定义，可按标识或名称关键字过滤。"
                                + "page 默认 1，size 默认 20、上限 50。")
                        .permission("cms:type:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(ContentTypeListRequest.class))
                        .handler(args -> {
                            ContentTypeListRequest request = toolArgs.bind(args,
                                    ContentTypeListRequest.class);
                            var result = contentTypeService.page(pageOf(request.page()),
                                    sizeOf(request.size(), 20), request.keyword());
                            return ToolResult.ok("共 " + result.getTotal() + " 个内容类型",
                                    objectMapper.valueToTree(result));
                        })
                        .build(),

                ToolSpec.builder("cms_field_list")
                        .title("查询字段定义")
                        .description("查询某个内容类型下的字段定义。typeCode 必填（内容类型标识）。")
                        .permission("cms:type:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.CONTENT)
                        .schema(toolArgs.schemaOf(FieldListRequest.class))
                        .handler(args -> {
                            FieldListRequest request = toolArgs.bind(args, FieldListRequest.class);
                            var fields = fieldService.list(request.typeCode());
                            return ToolResult.ok("类型「" + request.typeCode() + "」共 " + fields.size()
                                    + " 个字段", objectMapper.valueToTree(fields));
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

    /** 无入参工具的空 schema 载体。 */
    private record NoArgs() {
    }

    private record IdRequest(@NotNull(message = "id 不能为空") Long id) {
    }

    private record TagListRequest(String keyword) {
    }

    private record MediaListRequest(Long page, Long size, String keyword) {
    }

    private record ContentTypeListRequest(Long page, Long size, String keyword) {
    }

    private record FieldListRequest(@NotBlank(message = "内容类型标识不能为空") String typeCode) {
    }

    /**
     * update 专用入参：record 不能继承，把 {@link CategorySaveRequest} 的字段逐字复制一份再加 id。
     */
    private record CategoryUpdateRequest(
            @NotNull(message = "分类 id 不能为空") Long id,
            @PositiveOrZero(message = "上级分类 id 不能为负数") Long parentId,
            @NotBlank(message = "分类名称不能为空")
            @Size(max = 64, message = "分类名称最长 64 字符") String name,
            @Size(max = 64, message = "分类标识最长 64 字符") String slug,
            @Size(max = 255, message = "分类描述最长 255 字符") String description,
            @Size(max = 255, message = "分类封面最长 255 字符") String cover,
            @PositiveOrZero(message = "排序值不能为负数") Integer sort,
            @Min(value = 0, message = "分类状态只能是 0 或 1")
            @Max(value = 1, message = "分类状态只能是 0 或 1") Integer status) {
    }

    /** update 专用入参：{@link TagSaveRequest} 的字段 + id。 */
    private record TagUpdateRequest(
            @NotNull(message = "标签 id 不能为空") Long id,
            @NotBlank(message = "标签名称不能为空")
            @Size(max = 64, message = "标签名称最长 64 字符") String name,
            @Size(max = 64, message = "标签 slug 长度不能超过 64 个字符")
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                    message = "slug 只能包含小写字母、数字和连字符") String slug) {
    }

    /**
     * 新增菜单项入参：{@link MenuItemSaveRequest} 的字段 + 所属菜单 menuId。
     */
    private record MenuItemCreateRequest(
            @NotNull(message = "菜单 id 不能为空") Long menuId,
            @PositiveOrZero(message = "上级菜单项 id 不能为负数") Long parentId,
            @Size(max = 64, message = "菜单项文字最长 64 字符") String label,
            @NotBlank(message = "菜单项类型不能为空") String kind,
            Long refId,
            @Size(max = 64, message = "指向对象的标识最长 64 字符") String refCode,
            @Size(max = 500, message = "链接地址最长 500 字符") String url,
            @Size(max = 16, message = "target 最长 16 字符") String target,
            @Size(max = 32, message = "rel 最长 32 字符") String rel,
            @Min(value = 0, message = "visible 只能是 0 或 1")
            @Max(value = 1, message = "visible 只能是 0 或 1") Integer visible,
            Integer sort) {
    }
}
