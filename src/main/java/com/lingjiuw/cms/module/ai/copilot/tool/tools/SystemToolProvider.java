package com.lingjiuw.cms.module.ai.copilot.tool.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingjiuw.cms.common.api.PageResult;
import com.lingjiuw.cms.common.exception.BizException;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolArgs;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolGroup;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolProvider;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolResult;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolRisk;
import com.lingjiuw.cms.module.ai.copilot.tool.ToolSpec;
import com.lingjiuw.cms.module.system.dto.OperLogVO;
import com.lingjiuw.cms.module.system.dto.UserVO;
import com.lingjiuw.cms.module.system.entity.SysDictItem;
import com.lingjiuw.cms.module.system.service.DictService;
import com.lingjiuw.cms.module.system.service.OperLogService;
import com.lingjiuw.cms.module.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SYSTEM_READ 组工具：系统管理只读（docs/ai-copilot.md §4.2）。
 *
 * <p>本组**只读，不给任何写权限**——系统管理的写操作会带来提权与自锁风险（§4.3），
 * 一律不交给模型。{@code sys:role:list} 也刻意不做：读权限面没有业务价值。
 */
@Component
@RequiredArgsConstructor
public class SystemToolProvider implements ToolProvider {

    /** 列表默认页码 */
    private static final long DEFAULT_PAGE = 1L;

    /** 列表默认每页条数 */
    private static final long DEFAULT_SIZE = 10L;

    /** 每页条数上限（§7.7） */
    private static final long MAX_SIZE = 50L;

    private final ToolArgs toolArgs;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final DictService dictService;
    private final OperLogService operLogService;

    @Override
    public List<ToolSpec> tools() {
        return List.of(
                ToolSpec.builder("sys_user_list")
                        .title("查询用户列表")
                        .description("按用户名/启用状态分页查询后台用户，返回账号基本信息（不含密码）")
                        .permission("sys:user:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.SYSTEM_READ)
                        .schema(toolArgs.schemaOf(UserListArgs.class))
                        .handler(args -> {
                            UserListArgs request = toolArgs.bind(args, UserListArgs.class);
                            PageResult<UserVO> result = userService.page(
                                    pageOf(request.page()), sizeOf(request.size()),
                                    request.username(), request.status());
                            return ToolResult.ok("查询到 " + result.getTotal() + " 个用户",
                                    objectMapper.valueToTree(result));
                        })
                        .build(),
                ToolSpec.builder("sys_dict_items")
                        .title("查询字典项")
                        .description("按字典类型 id 或字典编码查询字典项，二者至少给一个")
                        .permission("sys:dict:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.SYSTEM_READ)
                        .schema(toolArgs.schemaOf(DictItemsArgs.class))
                        .handler(args -> {
                            DictItemsArgs request = toolArgs.bind(args, DictItemsArgs.class);
                            if (request.typeId() == null && request.code() == null) {
                                throw new BizException("请给出 typeId 或 code");
                            }
                            List<SysDictItem> items = dictService.items(request.typeId(), request.code());
                            // 瘦身：只回字典项本身，不带审计字段
                            ArrayNode data = objectMapper.createArrayNode();
                            for (SysDictItem item : items) {
                                ObjectNode node = data.addObject();
                                node.put("id", item.getId());
                                node.put("typeId", item.getTypeId());
                                node.put("label", item.getLabel());
                                node.put("value", item.getValue());
                                node.put("sort", item.getSort());
                            }
                            return ToolResult.ok("查询到 " + items.size() + " 个字典项", data);
                        })
                        .build(),
                ToolSpec.builder("sys_oper_log_list")
                        .title("查询操作日志")
                        .description("按操作人/操作名分页查询后台操作日志")
                        .permission("sys:log:list")
                        .risk(ToolRisk.READ)
                        .group(ToolGroup.SYSTEM_READ)
                        .schema(toolArgs.schemaOf(OperLogListArgs.class))
                        .handler(args -> {
                            OperLogListArgs request = toolArgs.bind(args, OperLogListArgs.class);
                            PageResult<OperLogVO> result = operLogService.page(
                                    pageOf(request.page()), sizeOf(request.size()),
                                    request.username(), request.action());
                            return ToolResult.ok("查询到 " + result.getTotal() + " 条操作日志",
                                    objectMapper.valueToTree(result));
                        })
                        .build()
        );
    }

    /** 页码：缺省第 1 页。 */
    private static long pageOf(Integer page) {
        return page == null ? DEFAULT_PAGE : Math.max(DEFAULT_PAGE, page);
    }

    /** 每页条数：缺省 10，收敛到 [1,50]。 */
    private static long sizeOf(Integer size) {
        return size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1L, size));
    }

    /** 查询用户列表入参。 */
    private record UserListArgs(Integer page, Integer size, String username, Integer status) {
    }

    /** 查询字典项入参；typeId 与 code 二选一。 */
    private record DictItemsArgs(Long typeId, String code) {
    }

    /** 查询操作日志入参。 */
    private record OperLogListArgs(Integer page, Integer size, String username, String action) {
    }
}
