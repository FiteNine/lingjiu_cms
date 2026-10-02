package com.lingjiuw.cms.module.cms.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.cms.dto.FieldSaveRequest;
import com.lingjiuw.cms.module.cms.dto.FieldVO;
import com.lingjiuw.cms.module.cms.service.FieldService;
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
@RequestMapping("/api/cms/fields")
@RequiredArgsConstructor
public class FieldController {

    private final FieldService fieldService;

    /** 列表沿用 cms:type:list：字段是内容类型的从属定义，另造一个 list 权限位没有意义 */
    @GetMapping
    @PreAuthorize("hasAuthority('cms:type:list')")
    public Result<List<FieldVO>> list(@RequestParam(required = false) String typeCode) {
        return Result.ok(fieldService.list(typeCode));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('cms:field:add')")
    @OperLog(module = "字段定义", action = "新增字段")
    public Result<Void> create(@RequestBody @Valid FieldSaveRequest request) {
        fieldService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:field:edit')")
    @OperLog(module = "字段定义", action = "编辑字段")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid FieldSaveRequest request) {
        fieldService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cms:field:delete')")
    @OperLog(module = "字段定义", action = "删除字段")
    public Result<Void> delete(@PathVariable Long id) {
        fieldService.delete(id);
        return Result.ok();
    }
}
