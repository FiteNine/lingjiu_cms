package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.security.SecurityUtils;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.ContentQueryRequest;
import com.lingjiuw.cms.module.cms.dto.ContentSaveRequest;
import com.lingjiuw.cms.module.cms.dto.ContentVO;
import com.lingjiuw.cms.module.cms.entity.CmsCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContent;
import com.lingjiuw.cms.module.cms.entity.CmsContentCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContentIndex;
import com.lingjiuw.cms.module.cms.entity.CmsContentTag;
import com.lingjiuw.cms.module.cms.entity.CmsContentType;
import com.lingjiuw.cms.module.cms.entity.CmsField;
import com.lingjiuw.cms.module.cms.entity.CmsTag;
import com.lingjiuw.cms.module.cms.mapper.CmsCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentIndexMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTagMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentTypeMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsFieldMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsTagMapper;
import com.lingjiuw.cms.module.cms.publish.model.FieldType;
import com.lingjiuw.cms.module.cms.publish.provider.Rows;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通用内容 {@code cms_content} 的读写（static-publish.md §2.3–§2.5，后台 C-3）。
 *
 * <p>保存内容时**同一个事务内**维护三张派生表，它们是发布引擎唯一的取数来源：
 * <ul>
 *   <li>{@code cms_content_index}：{@code cms_field.indexed=1} 的字段逐值一行。{@code where} /
 *       {@code orderby} / {@code facet} / {@code relate='field:<code>'} 只读这张表，
 *       漏一行就等于"这个取值筛不到内容"（§2.5）；</li>
 *   <li>{@code cms_content_category}：恰好一行 {@code dimension='primary'}（第一个分类 id），
 *       其余为 {@code secondary}。主分类决定 categoryUrl、面包屑与 canonical（§2.3）；</li>
 *   <li>{@code cms_content_tag}：内容 ↔ 标签。</li>
 * </ul>
 *
 * <p>站点隔离：{@code site_id} 一律取 {@link SiteContext#siteId()}，列表 / 详情 / 改 / 删全部带站点条件。
 */
@Service
@RequiredArgsConstructor
public class ContentService {

    private static final Set<String> VALID_STATUS = Set.of("DRAFT", "PUBLISHED", "OFFLINE");

    /** §2.3：{@code content_format} 只有 RICHTEXT（入库清洗）/ MARKDOWN（入库预渲染）两种。 */
    private static final Set<String> VALID_FORMAT = Set.of("RICHTEXT", "MARKDOWN");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CmsContentMapper contentMapper;
    private final CmsContentTypeMapper contentTypeMapper;
    private final CmsFieldMapper fieldMapper;
    private final CmsContentIndexMapper contentIndexMapper;
    private final CmsContentCategoryMapper contentCategoryMapper;
    private final CmsContentTagMapper contentTagMapper;
    private final CmsCategoryMapper categoryMapper;
    private final CmsTagMapper tagMapper;

    /* ================= 查询 ================= */

    public PageResult<ContentVO> page(ContentQueryRequest request) {
        IPage<CmsContent> result = contentMapper.selectPage(new Page<>(request.page(), request.size()),
                Wrappers.<CmsContent>lambdaQuery()
                        .eq(CmsContent::getSiteId, SiteContext.siteId())
                        .eq(StringUtils.hasText(request.typeCode()), CmsContent::getTypeCode,
                                request.typeCode())
                        .eq(StringUtils.hasText(request.status()), CmsContent::getStatus, request.status())
                        .like(StringUtils.hasText(request.keyword()), CmsContent::getTitle, request.keyword())
                        .orderByDesc(CmsContent::getSort)
                        .orderByDesc(CmsContent::getPublishTime)
                        .orderByDesc(CmsContent::getId));
        if (result.getRecords().isEmpty()) {
            return new PageResult<>(List.of(), result.getTotal(), result.getCurrent(), result.getSize());
        }
        // 分类 / 标签 id 批量取（列表页要显示它们，逐行查就是 N+1）
        List<Long> contentIds = result.getRecords().stream().map(CmsContent::getId).toList();
        Map<Long, List<Long>> categoryIds = refIdsOf(
                contentCategoryMapper.selectContentCategories(contentIds));
        Map<Long, List<Long>> tagIds = refIdsOf(contentTagMapper.selectContentTags(contentIds));
        // 类型名一次查完：逐行 selectOne 是一页 N 次查询
        Map<String, String> typeNames = typeNames(result.getRecords().stream()
                .map(CmsContent::getTypeCode).distinct().toList());
        List<ContentVO> records = new ArrayList<>(result.getRecords().size());
        for (CmsContent row : result.getRecords()) {
            records.add(toVO(row, typeNames.getOrDefault(row.getTypeCode(), row.getTypeCode()),
                    categoryIds.getOrDefault(row.getId(), List.of()),
                    tagIds.getOrDefault(row.getId(), List.of()), false));
        }
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 一页内容的 {@code type_code → 类型名}，只认当前站点的类型定义。 */
    private Map<String, String> typeNames(List<String> typeCodes) {
        if (typeCodes.isEmpty()) {
            return Map.of();
        }
        return contentTypeMapper.selectList(Wrappers.<CmsContentType>lambdaQuery()
                        .eq(CmsContentType::getSiteId, SiteContext.siteId())
                        .in(CmsContentType::getCode, typeCodes)).stream()
                .collect(Collectors.toMap(CmsContentType::getCode, CmsContentType::getName, (a, b) -> a));
    }

    /** 关联行按 content_id 归组（列表页回显用）。 */
    private static Map<Long, List<Long>> refIdsOf(List<Rows.RelationRow> rows) {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (Rows.RelationRow row : rows) {
            result.computeIfAbsent(row.getContentId(), key -> new ArrayList<>()).add(row.getRefId());
        }
        return result;
    }

    /** 详情：带 {@code data} 对象、正文与 SEO 字段，外加分类 / 标签关联 id（编辑页回填用）。 */
    public ContentVO detail(Long id) {
        CmsContent content = requireContent(id);
        return toVO(content, typeName(content.getTypeCode()),
                categoryIds(id), tagIds(id), true);
    }

    /* ================= 写入 ================= */

    @Transactional
    public void create(ContentSaveRequest request) {
        CmsContentType type = requireType(request.typeCode());
        Map<String, Object> data = normalizedData(type, request.data());
        CmsContent content = new CmsContent();
        applyRequest(content, request, data);
        content.setSiteId(SiteContext.siteId());
        content.setTypeCode(type.getCode());
        LocalDateTime now = LocalDateTime.now();
        content.setCreateBy(SecurityUtils.userId());
        content.setCreateTime(now);
        content.setUpdateBy(content.getCreateBy());
        content.setUpdateTime(now);
        checkSingleContent(type, null);
        checkSlugUnique(content);
        // 新建时自身还没有 id：只校验父级存在、属于当前站点且同类型
        checkParent(content.getId(), content.getTypeCode(), content.getParentId());
        contentMapper.insertContent(content);
        syncRelations(content, data, request);
    }

    @Transactional
    public void update(Long id, ContentSaveRequest request) {
        CmsContent content = requireContent(id);
        CmsContentType type = requireType(request.typeCode());
        if (!type.getCode().equals(content.getTypeCode())) {
            throw new BizException("内容类型不能修改");
        }
        Map<String, Object> data = normalizedData(type, request.data());
        applyRequest(content, request, data);
        content.setUpdateBy(SecurityUtils.userId());
        content.setUpdateTime(LocalDateTime.now());
        checkSingleContent(type, id);
        checkSlugUnique(content);
        checkParent(id, content.getTypeCode(), content.getParentId());
        contentMapper.updateContent(content);
        syncRelations(content, data, request);
    }

    @Transactional
    public void delete(Long id) {
        requireContent(id);
        // 删父后子内容挂不到任何根上（内容树遍历里整条子树静默消失），先挡住
        Long children = contentMapper.selectCount(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getParentId, id)
                .eq(CmsContent::getSiteId, SiteContext.siteId()));
        if (children != null && children > 0) {
            throw new BizException("存在子内容，不能删除");
        }
        // 逻辑删除内容本身；关联与索引行一并清掉，否则发布引擎仍会从索引表里读到它
        contentMapper.deleteById(id);
        clearRelations(id);
    }

    /** 发布 / 下线 / 草稿：PUBLISHED 且未给 {@code publish_time} 时填 now()。 */
    public void updateStatus(Long id, String status) {
        if (!VALID_STATUS.contains(status)) {
            throw new BizException("非法的内容状态");
        }
        CmsContent content = requireContent(id);
        content.setStatus(status);
        if ("PUBLISHED".equals(status) && content.getPublishTime() == null) {
            content.setPublishTime(LocalDateTime.now());
        }
        content.setUpdateBy(SecurityUtils.userId());
        content.setUpdateTime(LocalDateTime.now());
        contentMapper.updateContent(content);
    }

    /* ================= 请求 → 实体 ================= */

    /**
     * 把请求写进实体。{@code data} 已经过 {@link #normalizedData} 归一（数值、布尔、日期、
     * 数组形态都按字段类型定死），这里只做序列化。
     */
    private void applyRequest(CmsContent content, ContentSaveRequest request, Map<String, Object> data) {
        if (request.status() != null && !VALID_STATUS.contains(request.status())) {
            throw new BizException("非法的内容状态");
        }
        if (request.contentFormat() != null && !VALID_FORMAT.contains(request.contentFormat())) {
            throw new BizException("非法的内容格式");
        }
        content.setParentId(request.parentId() == null || request.parentId() < 0 ? 0L : request.parentId());
        content.setSlug(request.slug());
        content.setTitle(request.title());
        content.setSummary(request.summary());
        content.setCover(request.cover());
        content.setStatus(StringUtils.hasText(request.status()) ? request.status() : "DRAFT");
        content.setSort(request.sort() == null ? 0 : request.sort());
        content.setTop(Boolean.TRUE.equals(request.top()) ? 1 : 0);
        content.setRecommend(Boolean.TRUE.equals(request.recommend()) ? 1 : 0);
        content.setPublishTime(request.publishTime());
        if ("PUBLISHED".equals(content.getStatus()) && content.getPublishTime() == null) {
            content.setPublishTime(LocalDateTime.now());
        }
        content.setAuthorId(request.authorId());
        content.setAuthorName(authorName(request.authorId()));
        content.setContentFormat(StringUtils.hasText(request.contentFormat())
                ? request.contentFormat() : "RICHTEXT");
        content.setSeoTitle(request.seoTitle());
        content.setSeoDescription(request.seoDescription());
        content.setSeoKeywords(request.seoKeywords());
        content.setData(writeJson(data));
        content.setContent(request.content());
        // MARKDOWN 的预渲染结果本期不生成（引擎读 content_html 时回退到 content，§5.2.2）
        content.setContentHtml(null);
        content.setContentToc(null);
        content.setWordCount(wordCount(request.content()));
        if (content.getId() == null) {
            // 新建时把运营计数列明确置 0：XML 的 insert 显式列出这些列，db 默认值不会生效
            content.setViewCount(0L);
            content.setViewCountDay(0L);
            content.setViewCountWeek(0L);
            content.setCommentCount(0);
            content.setRatingAvg(BigDecimal.ZERO);
            content.setRatingCount(0);
        }
    }

    /* ================= 校验 ================= */

    /** 类型必须是当前站点定义过的（与发布引擎同一个口径：只认未删的类型）。 */
    private CmsContentType requireType(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            throw new BizException("内容类型不能为空");
        }
        CmsContentType type = contentTypeMapper.selectOne(Wrappers.<CmsContentType>lambdaQuery()
                .eq(CmsContentType::getSiteId, SiteContext.siteId())
                .eq(CmsContentType::getCode, typeCode));
        if (type == null) {
            throw new BizException("内容类型不存在：" + typeCode);
        }
        return type;
    }

    /** §2.3：SINGLE 类型每个站点至多一条；数据库的部分唯一索引只兜内置 {@code type_code='single'}。 */
    private void checkSingleContent(CmsContentType type, Long excludeId) {
        if (!"SINGLE".equalsIgnoreCase(type.getKind())) {
            return;
        }
        if (contentMapper.countByType(SiteContext.siteId(), type.getCode(), excludeId) > 0) {
            throw new BizException("单例内容类型「" + type.getName() + "」只能有一条内容");
        }
    }

    /**
     * slug 在"站点内 + 同类型 + 同父"下唯一（§2.3）。数据库的两条部分唯一索引是最终保证，
     * 这里先查一次只为给出可读的错误；分层内容（TREE）的 slug 允许在不同父下重复。
     */
    private void checkSlugUnique(CmsContent content) {
        if (!StringUtils.hasText(content.getSlug())) {
            return;
        }
        Long count = contentMapper.selectCount(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getSiteId, SiteContext.siteId())
                .eq(CmsContent::getTypeCode, content.getTypeCode())
                .eq(CmsContent::getParentId, content.getParentId())
                .eq(CmsContent::getSlug, content.getSlug())
                .ne(content.getId() != null, CmsContent::getId, content.getId()));
        if (count != null && count > 0) {
            throw new BizException("同一父级下已存在相同的 slug");
        }
    }

    /**
     * 上级内容必须存在、属于当前站点、与当前内容同类型，且不能是自身或自身的后代：
     * 成环后这条链上的内容既不会被挂到任何根上，也不会出现在内容树的遍历结果里。
     */
    private void checkParent(Long id, String typeCode, Long parentId) {
        if (parentId == null || parentId <= 0) {
            return;
        }
        if (parentId.equals(id)) {
            throw new BizException("父级内容不能选择自身");
        }
        Set<Long> seen = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor > 0 && seen.add(cursor)) {
            if (cursor.equals(id)) {
                throw new BizException("父级内容不能选择自己的下级");
            }
            CmsContent parent = contentMapper.selectOne(Wrappers.<CmsContent>lambdaQuery()
                    .eq(CmsContent::getId, cursor)
                    .eq(CmsContent::getSiteId, SiteContext.siteId()));
            if (parent == null) {
                throw new BizException("父级内容不存在或已被删除");
            }
            if (!typeCode.equals(parent.getTypeCode())) {
                throw new BizException("父级内容必须与当前内容属于同一类型");
            }
            cursor = parent.getParentId();
        }
    }

    private void validateData(CmsContentType type, Map<String, Object> data) {
        normalizedData(type, data);
    }

    /**
     * 按 {@code cms_field} 定义校验并归一 {@code data}：只保留该类型声明过的字段（表外的键直接丢掉，
     * §2.3 的"只保存声明过的字段"），多值字段收成字符串列表（索引表要逐值一行），标量按类型收成
     * 字符串 / 数值。JSON 字段原样保留。
     */
    private Map<String, Object> normalizedData(CmsContentType type, Map<String, Object> data) {
        List<CmsField> fields = fieldsOf(type.getCode());
        Map<String, Object> source = data == null ? Map.of() : data;
        Map<String, Object> result = new LinkedHashMap<>();
        for (CmsField field : fields) {
            Object raw = source.get(field.getCode());
            if (raw == null || isEmptyText(raw)) {
                if (isRequired(field)) {
                    throw new BizException("字段「" + field.getLabel() + "」不能为空");
                }
                continue;
            }
            FieldType fieldType = fieldType(field);
            if (fieldType == FieldType.JSON) {
                // §2.2：JSON 是任意 jsonb（规格参数表等），不做形态校验
                result.put(field.getCode(), raw);
                continue;
            }
            if (fieldType.multiValued()) {
                List<String> values = multiValues(raw);
                if (values.isEmpty()) {
                    if (isRequired(field)) {
                        throw new BizException("字段「" + field.getLabel() + "」不能为空");
                    }
                    continue;
                }
                result.put(field.getCode(), values);
                continue;
            }
            result.put(field.getCode(), scalarValue(field, fieldType, raw));
        }
        return result;
    }

    /** 标量字段：数值 / 0-1 / 日期串 / ENUM 取值 / 文本，形态不对直接报错（§2.2 的存储列）。 */
    private Object scalarValue(CmsField field, FieldType fieldType, Object raw) {
        switch (fieldType) {
            case INT -> {
                return integerOf(field, raw);
            }
            case DECIMAL -> {
                return decimalOf(field, raw);
            }
            case BOOL -> {
                return boolOf(field, raw);
            }
            case DATE, DATETIME -> {
                return timeText(field, raw, fieldType == FieldType.DATE);
            }
            case ENUM -> {
                String value = textOf(raw);
                if (!enumValues(field.getOptions()).contains(value)) {
                    throw new BizException("字段「" + field.getLabel() + "」的取值不在选项里：" + value);
                }
                return value;   // §2.2：ENUM 存存储值，不存标签
            }
            default -> {
                return textOf(raw);
            }
        }
    }

    /**
     * 多值字段收成字符串列表。IMAGES / FILES 的元素是媒体对象（{@code {url,alt,…}}），
     * 取它的 url——索引行的 {@code value_key} 是"单个取值"（§2.5）。空表返回空列表。
     */
    private List<String> multiValues(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof Collection<?>) && !raw.getClass().isArray()
                && !(raw instanceof Map<?, ?>)) {
            // 单值也接受（前端把多选收敛成一个值时不用报错），等价于只给了一个取值
            String single = textOf(raw);
            return single.isEmpty() ? List.of() : List.of(single);
        }
        List<String> values = new ArrayList<>();
        for (Object element : elementsOf(raw)) {
            String value = keyOf(element);
            if (!value.isEmpty() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private List<Object> elementsOf(Object raw) {
        if (raw instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (raw instanceof Object[] array) {
            return new ArrayList<>(List.of(array));
        }
        return List.of(raw);
    }

    /* ================= 索引与关联（派生表） ================= */

    /**
     * 重建内容的三张派生表。**顺序不能颠倒**：先逻辑删旧行（部分唯一索引只看 {@code deleted = 0}），
     * 再插新行；整段在同一事务里，任一步失败一起回滚。
     */
    private void syncRelations(CmsContent content, Map<String, Object> data, ContentSaveRequest request) {
        clearRelations(content.getId());
        List<CmsContentIndex> rows = buildIndexRows(content, data);
        if (!rows.isEmpty()) {
            contentIndexMapper.insertBatch(rows);
        }
        syncCategories(content.getId(), request.categoryIds());
        List<Long> tagIds = distinct(request.tagIds());
        requireOwnedTags(tagIds);
        if (!tagIds.isEmpty()) {
            contentTagMapper.insertBatch(content.getId(), tagIds);
        }
    }

    /**
     * 分类 / 标签 id 必须都属于当前站点：{@code cms_content_category} 与 {@code cms_content_tag}
     * 只存 id，客户端传来别的站点的 id 会留下跨站点关联与后续的数据泄露。
     */
    private void requireOwnedCategories(List<Long> ids) {
        Long owned = categoryMapper.selectCount(Wrappers.<CmsCategory>lambdaQuery()
                .eq(CmsCategory::getSiteId, SiteContext.siteId())
                .in(CmsCategory::getId, ids));
        if (owned == null || owned < ids.size()) {
            throw new BizException("存在不属于当前站点的分类，请刷新后重试");
        }
    }

    private void requireOwnedTags(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        Long owned = tagMapper.selectCount(Wrappers.<CmsTag>lambdaQuery()
                .eq(CmsTag::getSiteId, SiteContext.siteId())
                .in(CmsTag::getId, ids));
        if (owned == null || owned < ids.size()) {
            throw new BizException("存在不属于当前站点的标签，请刷新后重试");
        }
    }

    private void clearRelations(Long contentId) {
        contentIndexMapper.delete(Wrappers.<CmsContentIndex>lambdaQuery()
                .eq(CmsContentIndex::getContentId, contentId));
        contentCategoryMapper.delete(Wrappers.<CmsContentCategory>lambdaQuery()
                .eq(CmsContentCategory::getContentId, contentId));
        contentTagMapper.delete(Wrappers.<CmsContentTag>lambdaQuery()
                .eq(CmsContentTag::getContentId, contentId));
    }

    /**
     * {@code cms_field.indexed = 1} 的字段逐值一行（§2.5）：
     * <ul>
     *   <li>标量字段恰好一行，{@code value_key='default'}；</li>
     *   <li>多值字段每个取值一行，{@code value_key} 就是那个取值（ENUM_MULTI 存存储值、
     *       TAGS / RELATION 存 id 串、IMAGES / FILES 存 url）；</li>
     *   <li>{@code value_type} 写字段定义里的类型名，值按类型落 {@code num_value} /
     *       {@code time_value} / {@code str_value}——与 {@code DbContentProvider.valueKindForField}
     *       读索引表时选的那一列完全一致，否则 {@code where} 永远筛不到（§2.5）。</li>
     * </ul>
     */
    private List<CmsContentIndex> buildIndexRows(CmsContent content, Map<String, Object> data) {
        Map<String, Object> source = data == null ? Map.of() : data;
        List<CmsContentIndex> rows = new ArrayList<>();
        for (CmsField field : fieldsOf(content.getTypeCode())) {
            if (!flag(field.getIndexed()) || !source.containsKey(field.getCode())) {
                continue;
            }
            Object raw = source.get(field.getCode());
            if (raw == null) {
                continue;
            }
            String valueType = field.getFieldType();
            List<String> values = fieldType(field).multiValued()
                    ? multiValues(raw) : List.of(textOf(raw));
            for (String value : values) {
                if (value.isEmpty()) {
                    continue;
                }
                CmsContentIndex row = new CmsContentIndex();
                row.setSiteId(content.getSiteId());
                row.setContentId(content.getId());
                row.setTypeCode(content.getTypeCode());
                row.setFieldCode(field.getCode());
                row.setValueKey(fieldType(field).multiValued() ? value : "default");
                row.setValueType(valueType);
                applyIndexValue(row, field, value);
                rows.add(row);
            }
        }
        return rows;
    }

    /** 值落到索引表的哪一列；口径与 {@code DbContentProvider} 读索引表时选列的方式一一对应。 */
    private void applyIndexValue(CmsContentIndex row, CmsField field, String value) {
        switch (fieldType(field)) {
            case INT, DECIMAL -> row.setNumValue(new BigDecimal(value));
            // BOOL 存 0 / 1（§2.4 的 data 形态），索引行的 num_value 也是 0 / 1
            case BOOL -> row.setNumValue("1".equals(value) || "true".equalsIgnoreCase(value)
                    ? BigDecimal.ONE : BigDecimal.ZERO);
            case DATE, DATETIME -> row.setTimeValue(parseTime(value));
            // §2.5：RELATION 的索引行把目标 id 落 num_value（orderby / where 都是数值比较）
            case RELATION -> row.setNumValue(relationIdOf(field, value));
            default -> row.setStrValue(value);
        }
    }

    /** RELATION 的取值是目标 id：不是数字就报业务错，别让 NumberFormatException 直穿成 500。 */
    private static BigDecimal relationIdOf(CmsField field, String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BizException("字段「" + field.getLabel() + "」的关联目标必须是数字 id：" + value);
        }
    }

    /** 分类关联：第一个 id 是主分类（恰好一行 primary），其余 secondary（§2.3、§2.4）。 */
    private void syncCategories(Long contentId, List<Long> categoryIds) {
        List<Long> distinct = distinct(categoryIds);
        if (distinct.isEmpty()) {
            return;
        }
        requireOwnedCategories(distinct);
        contentCategoryMapper.insertBatch(contentId, List.of(distinct.get(0)), "primary");
        if (distinct.size() > 1) {
            contentCategoryMapper.insertBatch(contentId, distinct.subList(1, distinct.size()), "secondary");
        }
    }

    /* ================= 读取辅助 ================= */

    private List<CmsField> fieldsOf(String typeCode) {
        return fieldMapper.selectList(Wrappers.<CmsField>lambdaQuery()
                .eq(CmsField::getSiteId, SiteContext.siteId())
                .eq(CmsField::getTypeCode, typeCode)
                .orderByAsc(CmsField::getSort)
                .orderByAsc(CmsField::getId));
    }

    private String typeName(String typeCode) {
        CmsContentType type = contentTypeMapper.selectOne(Wrappers.<CmsContentType>lambdaQuery()
                .eq(CmsContentType::getSiteId, SiteContext.siteId())
                .eq(CmsContentType::getCode, typeCode));
        return type == null ? typeCode : type.getName();
    }

    private List<Long> categoryIds(Long contentId) {
        List<Long> ids = new ArrayList<>();
        for (Rows.RelationRow row : contentMapper.selectContentCategoryRefs(contentId)) {
            ids.add(row.getRefId());
        }
        return ids;
    }

    private List<Long> tagIds(Long contentId) {
        List<Long> ids = new ArrayList<>();
        for (Rows.RelationRow row : contentMapper.selectContentTagRefs(contentId)) {
            ids.add(row.getRefId());
        }
        return ids;
    }

    private String authorName(Long authorId) {
        if (authorId == null) {
            return null;
        }
        CmsContent author = contentMapper.selectOne(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getSiteId, SiteContext.siteId())
                .eq(CmsContent::getId, authorId));
        return author == null ? null : author.getTitle();
    }

    /** 取当前站点下的内容：别的站点的 id 在这里就当不存在。 */
    private CmsContent requireContent(Long id) {
        CmsContent content = contentMapper.selectOne(Wrappers.<CmsContent>lambdaQuery()
                .eq(CmsContent::getId, id)
                .eq(CmsContent::getSiteId, SiteContext.siteId()));
        if (content == null) {
            throw new BizException("内容不存在或已被删除");
        }
        return content;
    }

    private ContentVO toVO(CmsContent content, String typeName, List<Long> categoryIds,
                           List<Long> tagIds, boolean withDetail) {
        return new ContentVO(
                content.getId(), content.getTypeCode(), typeName, content.getParentId(), content.getSlug(),
                content.getTitle(), content.getSummary(), content.getCover(), content.getStatus(),
                content.getSort(), flag(content.getTop()), flag(content.getRecommend()),
                content.getPublishTime(), content.getAuthorName(), content.getViewCount(),
                content.getContentFormat(),
                withDetail ? content.getContent() : null,
                content.getWordCount(),
                withDetail ? readData(content.getData()) : null,
                withDetail ? content.getSeoTitle() : null,
                withDetail ? content.getSeoDescription() : null,
                withDetail ? content.getSeoKeywords() : null,
                categoryIds, tagIds,
                content.getCreateTime(), content.getUpdateTime());
    }

    /* ================= 小工具 ================= */

    /** 字段类型名 → 枚举；表外取值按 TEXT 处理（与 {@code DbContentProvider.fieldType} 同口径）。 */
    private static FieldType fieldType(CmsField field) {
        String text = field.getFieldType();
        if (text != null && !text.isBlank()) {
            for (FieldType type : FieldType.values()) {
                if (type.name().equalsIgnoreCase(text.trim())) {
                    return type;
                }
            }
        }
        return FieldType.TEXT;
    }

    /**
     * ENUM / ENUM_MULTI 的选项取值（{@code "值:标签"} 逗号分隔，§2.2）：值取冒号前一段，空项跳过。
     * FieldService 保存字段定义时按同一口径校验选项，所以解析口子收在这里。
     */
    static Set<String> enumValues(String options) {
        Set<String> values = new LinkedHashSet<>();
        if (options == null || options.isBlank()) {
            return values;
        }
        for (String piece : options.split(",")) {
            String item = piece.trim();
            if (item.isEmpty()) {
                continue;
            }
            int colon = item.indexOf(':');
            values.add(colon < 0 ? item : item.substring(0, colon).trim());
        }
        return values;
    }

    private static boolean isRequired(CmsField field) {
        return flag(field.getRequired());
    }

    private static boolean flag(Integer value) {
        return value != null && value != 0;
    }

    private static boolean isEmptyText(Object raw) {
        return raw instanceof String text && text.isBlank();
    }

    private static String textOf(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    /** 索引行的 value_key：媒体对象取 url，其余取字符串形态（截到列宽 200）。 */
    private static String keyOf(Object element) {
        String value;
        if (element instanceof Map<?, ?> map) {
            value = textOf(map.get("url"));
            if (value.isEmpty()) {
                return "";
            }
        } else {
            value = textOf(element);
        }
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    private static Long integerOf(CmsField field, Object raw) {
        if (raw instanceof Number number) {
            if (number.longValue() != number.doubleValue()) {
                throw new BizException("字段「" + field.getLabel() + "」必须是整数");
            }
            return number.longValue();
        }
        try {
            return Long.valueOf(textOf(raw));
        } catch (NumberFormatException e) {
            throw new BizException("字段「" + field.getLabel() + "」必须是整数");
        }
    }

    private static BigDecimal decimalOf(CmsField field, Object raw) {
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(textOf(raw));
        } catch (NumberFormatException e) {
            throw new BizException("字段「" + field.getLabel() + "」必须是数字");
        }
    }

    /** BOOL 在 {@code data} 里是 0 / 1（§2.4），别处也接受 true / false。 */
    private static Integer boolOf(CmsField field, Object raw) {
        if (raw instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        if (raw instanceof Number number) {
            return number.intValue() == 0 ? 0 : 1;
        }
        String value = textOf(raw);
        if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
            return 1;
        }
        if ("0".equals(value) || "false".equalsIgnoreCase(value)) {
            return 0;
        }
        throw new BizException("字段「" + field.getLabel() + "」只能是 0 或 1");
    }

    /** DATE / DATETIME：入库前就按引擎认的形态收口，免得索引行落不进 time_value。 */
    private static String timeText(CmsField field, Object raw, boolean dateOnly) {
        LocalDateTime time;
        if (raw instanceof LocalDateTime value) {
            time = value;
        } else if (raw instanceof LocalDate value) {
            time = value.atStartOfDay();
        } else {
            time = parseTime(textOf(raw));
        }
        if (time == null) {
            throw new BizException("字段「" + field.getLabel() + "」的日期格式无法识别");
        }
        return dateOnly
                ? time.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 时间串 → {@code LocalDateTime}。形态与 {@code DbContentProvider.parseTime} 一致——
     * 索引行的 time_value 与 {@code where} 里 cast 成 timestamp 的字面量必须能对上。
     */
    private static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm")) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (RuntimeException ignored) {
                // 换下一种形态
            }
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (RuntimeException ignored) {
            // 继续试日期形态
        }
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 正文字数：去掉标签与空白后的字符数（与 {@code DbContentProvider.plainLength} 同一口径，§2.2）。 */
    private static int wordCount(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        String text = content.replaceAll("<[^>]*>", "");
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private static List<Long> distinct(List<Long> ids) {
        List<Long> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (Long id : ids) {
            if (id != null && id > 0 && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private static String writeJson(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data == null ? Map.of() : data);
        } catch (Exception e) {
            throw new BizException("自定义字段值不是合法的 JSON");
        }
    }

    private static Map<String, Object> readData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
