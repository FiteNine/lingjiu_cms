package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.cms.dto.DirCreateRequest;
import com.lingjiuw.cms.module.cms.dto.DirListing;
import com.lingjiuw.cms.module.cms.dto.SiteOption;
import com.lingjiuw.cms.module.cms.dto.SiteSaveRequest;
import com.lingjiuw.cms.module.cms.entity.CmsSite;
import com.lingjiuw.cms.module.cms.service.SiteService;
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
@RequestMapping("/api/cms/sites")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    @GetMapping
    @PreAuthorize("hasAuthority('cms:site:list')")
    public Result<List<CmsSite>> list() {
        return Result.ok(siteService.list());
    }

    /** 右上角站点切换器的下拉选项：登录即可读（与 /api/ai/providers/options 同理），只含当前用户可访问的站点 */
    @GetMapping("/options")
    public Result<List<SiteOption>> options() {
        return Result.ok(siteService.options());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('cms:site:add')")
    @OperLog(module = "站点管理", action = "新增站点")
    public Result<Void> create(@RequestBody @Valid SiteSaveRequest request) {
        siteService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:site:edit')")
    @OperLog(module = "站点管理", action = "编辑站点")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid SiteSaveRequest request) {
        requireAccessible(id);
        siteService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:site:delete')")
    @OperLog(module = "站点管理", action = "删除站点")
    public Result<Void> delete(@PathVariable Long id) {
        requireAccessible(id);
        siteService.delete(id);
        return Result.ok();
    }

    /**
     * 站点是按 id 直接改 / 删的：先确认它在当前用户可访问的站点里（{@code sys_user_site} 绑定，
     * admin 角色全部可见）。{@code resolveSiteId} 会把不可访问或已删除的 id 回落成别的站点，
     * 回落值不等于原 id 即说明这个 id 当前用户碰不得——与发布入口 PublishFacade 同一把尺子。
     */
    private void requireAccessible(Long id) {
        if (!id.equals(siteService.resolveSiteId(id))) {
            throw new BizException("该站点不存在或无权访问");
        }
    }

    /** 站点目录选择器：列出 cms.site.root-dir 下某个路径的子目录 */
    @GetMapping("/dirs")
    @PreAuthorize("hasAuthority('cms:site:list')")
    public Result<DirListing> dirs(@RequestParam(required = false) String path) {
        return Result.ok(siteService.listDirs(path));
    }

    /** 站点目录选择器：新建文件夹（新增与编辑站点时都可能用） */
    @PostMapping("/dirs")
    @PreAuthorize("hasAnyAuthority('cms:site:add', 'cms:site:edit')")
    @OperLog(module = "站点管理", action = "新建站点目录")
    public Result<Void> createDir(@RequestBody @Valid DirCreateRequest request) {
        siteService.createDir(request.parent(), request.name());
        return Result.ok();
    }
}
