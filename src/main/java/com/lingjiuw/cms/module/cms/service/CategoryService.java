package com.lingjiuw.cms.module.cms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.common.site.SiteContext;
import com.lingjiuw.cms.module.cms.dto.CategoryContentCount;
import com.lingjiuw.cms.module.cms.dto.CategoryNode;
import com.lingjiuw.cms.module.cms.dto.CategorySaveRequest;
import com.lingjiuw.cms.module.cms.entity.CmsCategory;
import com.lingjiuw.cms.module.cms.entity.CmsContentCategory;
import com.lingjiuw.cms.module.cms.mapper.CmsCategoryMapper;
import com.lingjiuw.cms.module.cms.mapper.CmsContentCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CmsCategoryMapper categoryMapper;
    private final CmsContentCategoryMapper contentCategoryMapper;

    public List<CategoryNode> tree() {
        List<CmsCategory> categories = categoryMapper.selectList(Wrappers.<CmsCategory>lambdaQuery()
                .eq(CmsCategory::getSiteId, SiteContext.siteId())
                .orderByAsc(CmsCategory::getSort)
                .orderByAsc(CmsCategory::getId));
        Map<Long, Long> counts = categoryMapper.selectContentCounts().stream()
                .collect(Collectors.toMap(CategoryContentCount::categoryId,
                        CategoryContentCount::count, (a, b) -> a));
        return buildTree(categories, counts);
    }

    public List<CmsCategory> list() {
        return categoryMapper.selectList(Wrappers.<CmsCategory>lambdaQuery()
                .eq(CmsCategory::getSiteId, SiteContext.siteId())
                .orderByAsc(CmsCategory::getSort)
                .orderByAsc(CmsCategory::getId));
    }

    public void create(CategorySaveRequest request) {
        checkSlugUnique(request.slug(), null);
        CmsCategory category = new CmsCategory();
        applyRequest(category, request);
        category.setSiteId(SiteContext.siteId());
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException e) {
            // 唯一索引是最终保证：并发下 checkSlugUnique 可能同时通过，这里兜成业务提示
            throw new BizException("分类标识已存在");
        }
    }

    public void update(Long id, CategorySaveRequest request) {
        CmsCategory category = requireCategory(id);
        checkParent(id, request.parentId());
        checkSlugUnique(request.slug(), id);
        applyRequest(category, request);
        categoryMapper.updateById(category);
    }

    @Transactional
    public void delete(Long id) {
        requireCategory(id);
        Long children = categoryMapper.selectCount(Wrappers.<CmsCategory>lambdaQuery()
                .eq(CmsCategory::getParentId, id)
                .eq(CmsCategory::getSiteId, SiteContext.siteId()));
        if (children != null && children > 0) {
            throw new BizException("存在子分类，不能删除");
        }
        Long contents = contentCategoryMapper.selectCount(
                Wrappers.<CmsContentCategory>lambdaQuery().eq(CmsContentCategory::getCategoryId, id));
        if (contents != null && contents > 0) {
            throw new BizException("分类下存在内容，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    private void applyRequest(CmsCategory category, CategorySaveRequest request) {
        category.setParentId(request.parentId() == null ? 0L : request.parentId());
        category.setName(request.name());
        category.setSlug(request.slug());
        category.setDescription(request.description());
        category.setCover(request.cover());
        category.setSort(request.sort() == null ? 0 : request.sort());
        category.setStatus(request.status() == null ? 1 : request.status());
    }

    /**
     * 上级分类必须存在、属于当前站点，且不能是自身或自身的后代：成环后这条链上的分类
     * 既不会被挂到任何根上，也不会出现在 {@code tree()} 的结果里。
     */
    private void checkParent(Long id, Long parentId) {
        if (parentId == null || parentId <= 0) {
            return;
        }
        if (parentId.equals(id)) {
            throw new BizException("父级分类不能选择自身");
        }
        Set<Long> seen = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor > 0 && seen.add(cursor)) {
            if (cursor.equals(id)) {
                throw new BizException("父级分类不能选择自己的下级");
            }
            CmsCategory parent = categoryMapper.selectOne(Wrappers.<CmsCategory>lambdaQuery()
                    .eq(CmsCategory::getId, cursor)
                    .eq(CmsCategory::getSiteId, SiteContext.siteId()));
            if (parent == null) {
                throw new BizException("父级分类不存在或已被删除");
            }
            cursor = parent.getParentId();
        }
    }

    /** 分类标识在站点内唯一 */
    private void checkSlugUnique(String slug, Long excludeId) {
        if (!StringUtils.hasText(slug)) {
            return;
        }
        Long count = categoryMapper.selectCount(Wrappers.<CmsCategory>lambdaQuery()
                .eq(CmsCategory::getSiteId, SiteContext.siteId())
                .eq(CmsCategory::getSlug, slug)
                .ne(excludeId != null, CmsCategory::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("分类标识已存在");
        }
    }

    /** 取当前站点下的分类：别的站点的 id 在这里就当不存在 */
    private CmsCategory requireCategory(Long id) {
        CmsCategory category = categoryMapper.selectOne(Wrappers.<CmsCategory>lambdaQuery()
                .eq(CmsCategory::getId, id)
                .eq(CmsCategory::getSiteId, SiteContext.siteId()));
        if (category == null) {
            throw new BizException("分类不存在或已被删除");
        }
        return category;
    }

    private List<CategoryNode> buildTree(List<CmsCategory> categories, Map<Long, Long> counts) {
        Map<Long, CategoryNode> nodes = new LinkedHashMap<>();
        for (CmsCategory category : categories) {
            CategoryNode node = new CategoryNode();
            node.setId(category.getId());
            node.setParentId(category.getParentId());
            node.setName(category.getName());
            node.setSlug(category.getSlug());
            node.setDescription(category.getDescription());
            node.setCover(category.getCover());
            node.setSort(category.getSort());
            node.setStatus(category.getStatus());
            node.setContentCount(counts.getOrDefault(category.getId(), 0L));
            nodes.put(category.getId(), node);
        }
        List<CategoryNode> roots = new ArrayList<>();
        for (CategoryNode node : nodes.values()) {
            CategoryNode parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }
}
