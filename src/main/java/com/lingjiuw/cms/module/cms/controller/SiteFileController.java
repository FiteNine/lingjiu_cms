package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.dto.SiteFileContent;
import com.lingjiuw.cms.module.cms.dto.SiteFileCreateRequest;
import com.lingjiuw.cms.module.cms.dto.SiteFileListing;
import com.lingjiuw.cms.module.cms.dto.SiteFileSaveRequest;
import com.lingjiuw.cms.module.cms.service.SiteFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站点目录（网站文件）浏览与编辑。范围是**当前站点**（请求头 X-Site-Id）的目录，
 * 也就是 `cms.site.root-dir` 下该站点 root_dir 那一个目录，翻不到别的站点去。
 */
@RestController
@RequestMapping("/api/cms/sites/files")
@RequiredArgsConstructor
public class SiteFileController {

    private final SiteFileService siteFileService;

    /** 列出当前站点目录下某个路径的子目录与文件；path 留空表示站点目录本身 */
    @GetMapping
    @PreAuthorize("hasAuthority('cms:site:file:list')")
    public Result<SiteFileListing> list(@RequestParam(required = false) String path) {
        return Result.ok(siteFileService.list(path));
    }

    /** 读取文件内容：文本给原文，图片给 data URL，其余类型只给文件信息 */
    @GetMapping("/content")
    @PreAuthorize("hasAuthority('cms:site:file:list')")
    public Result<SiteFileContent> content(@RequestParam String path) {
        return Result.ok(siteFileService.read(path));
    }

    @PutMapping("/content")
    @PreAuthorize("hasAuthority('cms:site:file:edit')")
    @OperLog(module = "站点目录", action = "保存站点文件")
    public Result<Void> save(@RequestBody @Valid SiteFileSaveRequest request) {
        siteFileService.save(request.path(), request.content());
        return Result.ok();
    }

    /** 新建文件：返回相对站点目录的路径，页面拿到后直接打开编辑 */
    @PostMapping
    @PreAuthorize("hasAuthority('cms:site:file:add')")
    @OperLog(module = "站点目录", action = "新建站点文件")
    public Result<String> create(@RequestBody @Valid SiteFileCreateRequest request) {
        return Result.ok(siteFileService.createFile(request.parent(), request.name()));
    }

    /** 在当前站点目录下新建文件夹 */
    @PostMapping("/dirs")
    @PreAuthorize("hasAuthority('cms:site:file:add')")
    @OperLog(module = "站点目录", action = "新建站点文件夹")
    public Result<String> createDir(@RequestBody @Valid SiteFileCreateRequest request) {
        return Result.ok(siteFileService.createDir(request.parent(), request.name()));
    }

    /** 删除文件或空文件夹；path 相对站点目录 */
    @DeleteMapping
    @PreAuthorize("hasAuthority('cms:site:file:delete')")
    @OperLog(module = "站点目录", action = "删除站点文件")
    public Result<Void> delete(@RequestParam String path) {
        siteFileService.delete(path);
        return Result.ok();
    }
}
