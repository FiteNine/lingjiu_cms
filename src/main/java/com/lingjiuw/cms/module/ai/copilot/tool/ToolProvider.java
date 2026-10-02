package com.lingjiuw.cms.module.ai.copilot.tool;

import java.util.List;

/**
 * 一组工具的提供者。实现类打成 {@code @Component}，{@link ToolRegistry} 按 Spring 注入的列表汇总，
 * 新增工具只要新增一个 Provider（或往现有 Provider 里加一条声明）。
 */
public interface ToolProvider {

    List<ToolSpec> tools();
}
