package com.lingjiuw.cms.module.ai.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.ai.dto.ChatResult;
import com.lingjiuw.cms.module.ai.dto.ProviderSaveRequest;
import com.lingjiuw.cms.module.ai.dto.ProviderTestRequest;
import com.lingjiuw.cms.module.ai.dto.ProviderVO;
import com.lingjiuw.cms.module.ai.service.AiProviderService;
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
@RequestMapping("/api/ai/providers")
@RequiredArgsConstructor
public class AiProviderController {

    private final AiProviderService providerService;

    @GetMapping
    @PreAuthorize("hasAuthority('ai:provider:list')")
    public Result<PageResult<ProviderVO>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String protocol) {
        return Result.ok(providerService.page(page, size, keyword, protocol));
    }

    /** 智能体表单的服务商下拉（API Key 已掩码，两个 AI 页面任一权限即可读） */
    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('ai:provider:list', 'ai:agent:list')")
    public Result<List<ProviderVO>> options() {
        return Result.ok(providerService.options().stream().map(ProviderVO::of).toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ai:provider:add')")
    @OperLog(module = "AI服务商", action = "新增服务商")
    public Result<Void> create(@RequestBody @Valid ProviderSaveRequest request) {
        providerService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:provider:edit')")
    @OperLog(module = "AI服务商", action = "编辑服务商")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid ProviderSaveRequest request) {
        providerService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:provider:delete')")
    @OperLog(module = "AI服务商", action = "删除服务商")
    public Result<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('ai:provider:list')")
    public Result<ChatResult> test(@PathVariable Long id, @RequestBody @Valid ProviderTestRequest request) {
        return Result.ok(providerService.test(id, request.model()));
    }
}
