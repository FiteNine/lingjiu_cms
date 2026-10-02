package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.dto.ContentTypeSaveRequest;
import com.lingjiuw.cms.module.cms.dto.ContentTypeVO;
import com.lingjiuw.cms.module.cms.service.ContentTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cms/types")
@RequiredArgsConstructor
public class ContentTypeController {

    private final ContentTypeService contentTypeService;

    @GetMapping
    @PreAuthorize("hasAuthority('cms:type:list')")
    public Result<PageResult<ContentTypeVO>> page(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "20") long size,
                                                  @RequestParam(required = false) String keyword) {
        return Result.ok(contentTypeService.page(page, size, keyword));
    }

    /** 下拉选项：登录即可读（与 /api/cms/sites/options 同理），只含当前站点的类型 */
    @GetMapping("/options")
    public Result<List<ContentTypeVO.Option>> options() {
        return Result.ok(contentTypeService.listOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:type:list')")
    public Result<ContentTypeVO> detail(@PathVariable Long id) {
        return Result.ok(contentTypeService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('cms:type:add')")
    @OperLog(module = "内容类型", action = "新增内容类型")
    public Result<Void> create(@RequestBody @Valid ContentTypeSaveRequest request) {
        contentTypeService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:type:edit')")
    @OperLog(module = "内容类型", action = "编辑内容类型")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid ContentTypeSaveRequest request) {
        contentTypeService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:type:delete')")
    @OperLog(module = "内容类型", action = "删除内容类型")
    public Result<Void> delete(@PathVariable Long id) {
        contentTypeService.delete(id);
        return Result.ok();
    }
}
