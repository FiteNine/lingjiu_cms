package com.lingjiuw.cms.module.ai.controller;

import com.lingjiuw.cms.annotation.OperLog;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.api.Result;
import com.lingjiuw.cms.module.ai.dto.AgentSaveRequest;
import com.lingjiuw.cms.module.ai.dto.AgentVO;
import com.lingjiuw.cms.module.ai.dto.AiChatRequest;
import com.lingjiuw.cms.module.ai.dto.ChatResult;
import com.lingjiuw.cms.module.ai.service.AiAgentService;
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

@RestController
@RequestMapping("/api/ai/agents")
@RequiredArgsConstructor
public class AiAgentController {

    private final AiAgentService agentService;

    @GetMapping
    @PreAuthorize("hasAuthority('ai:agent:list')")
    public Result<PageResult<AgentVO>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long providerId) {
        return Result.ok(agentService.page(page, size, keyword, providerId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ai:agent:add')")
    @OperLog(module = "智能体", action = "新增智能体")
    public Result<Void> create(@RequestBody @Valid AgentSaveRequest request) {
        agentService.create(request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:agent:edit')")
    @OperLog(module = "智能体", action = "编辑智能体")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid AgentSaveRequest request) {
        agentService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:agent:delete')")
    @OperLog(module = "智能体", action = "删除智能体")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.ok();
    }

    /** 试聊：不加 @OperLog，避免把对话内容写进操作日志 */
    @PostMapping("/{id}/chat")
    @PreAuthorize("hasAuthority('ai:agent:chat')")
    public Result<ChatResult> chat(@PathVariable Long id, @RequestBody @Valid AiChatRequest request) {
        return Result.ok(agentService.chat(id, request.messages()));
    }
}
