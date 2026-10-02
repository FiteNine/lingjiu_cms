package com.lingjiuw.cms.module.system.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.system.dto.DictItemSaveRequest;
import com.lingjiuw.cms.module.system.dto.DictTypeSaveRequest;
import com.lingjiuw.cms.module.system.entity.SysDictItem;
import com.lingjiuw.cms.module.system.entity.SysDictType;
import com.lingjiuw.cms.module.system.service.DictService;
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
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/types")
    @PreAuthorize("hasAuthority('sys:dict:list')")
    public Result<PageResult<SysDictType>> typePage(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) String code,
                                                    @RequestParam(required = false) String name) {
        return Result.ok(dictService.typePage(page, size, code, name));
    }

    @PostMapping("/types")
    @PreAuthorize("hasAuthority('sys:dict:add')")
    @OperLog(module = "字典管理", action = "新增字典类型")
    public Result<Void> createType(@RequestBody @Valid DictTypeSaveRequest request) {
        dictService.createType(request);
        return Result.ok();
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasAuthority('sys:dict:edit')")
    @OperLog(module = "字典管理", action = "编辑字典类型")
    public Result<Void> updateType(@PathVariable Long id, @RequestBody @Valid DictTypeSaveRequest request) {
        dictService.updateType(id, request);
        return Result.ok();
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasAuthority('sys:dict:delete')")
    @OperLog(module = "字典管理", action = "删除字典类型")
    public Result<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return Result.ok();
    }

    @GetMapping("/items")
    @PreAuthorize("hasAuthority('sys:dict:list')")
    public Result<List<SysDictItem>> items(@RequestParam(required = false) Long typeId,
                                           @RequestParam(required = false) String code) {
        return Result.ok(dictService.items(typeId, code));
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('sys:dict:add')")
    @OperLog(module = "字典管理", action = "新增字典项")
    public Result<Void> createItem(@RequestBody @Valid DictItemSaveRequest request) {
        dictService.createItem(request);
        return Result.ok();
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAuthority('sys:dict:edit')")
    @OperLog(module = "字典管理", action = "编辑字典项")
    public Result<Void> updateItem(@PathVariable Long id, @RequestBody @Valid DictItemSaveRequest request) {
        dictService.updateItem(id, request);
        return Result.ok();
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasAuthority('sys:dict:delete')")
    @OperLog(module = "字典管理", action = "删除字典项")
    public Result<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return Result.ok();
    }
}
