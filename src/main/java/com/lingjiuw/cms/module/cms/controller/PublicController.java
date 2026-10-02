package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.dto.CategoryNode;
import com.lingjiuw.cms.module.cms.dto.PublicArticleVO;
import com.lingjiuw.cms.module.cms.service.CategoryService;
import com.lingjiuw.cms.module.cms.service.PublicArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开内容接口（无需登录）：供门户/官网消费已发布内容。
 *
 * <p>内容按站点划分，门户用请求参数 {@code siteId} 指定要读哪个站点（如
 * {@code /api/public/articles?siteId=2}）；不传则读系统默认站点。站点 id 由
 * {@code common/site/SiteInterceptor} 统一解析。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final PublicArticleService publicArticleService;
    private final CategoryService categoryService;

    @GetMapping("/articles")
    public Result<PageResult<PublicArticleVO>> articles(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "10") long size,
                                                        @RequestParam(required = false) Long categoryId,
                                                        @RequestParam(required = false) String keyword) {
        return Result.ok(publicArticleService.page(page, size, categoryId, keyword));
    }

    @GetMapping("/articles/{slug}")
    public Result<PublicArticleVO> articleDetail(@PathVariable String slug) {
        return Result.ok(publicArticleService.detail(slug));
    }

    @GetMapping("/categories")
    public Result<List<CategoryNode>> categories() {
        return Result.ok(categoryService.tree());
    }
}
