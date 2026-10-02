# 全站 Agent（AI Copilot）实现方案

> 状态：**方案待评审，尚未实现。**
> 目标：后台 `AI管理 → 全站agent` 页面，用自然语言对话操控整个 CMS；所有操作受当前登录用户的权限约束；工具层按 Model Context Protocol 的 Tool 语义设计，为将来对外暴露标准 MCP Server 预留。

---

## 0. 已确认的四项决策

| 决策 | 选择 |
| --- | --- |
| Agent 形态 | **工具调用循环**（模型多轮 `tool_calls`，后端执行后回灌结果继续），危险操作前端弹窗人工确认 |
| MCP 边界 | **先按内部工具层落地**，工具注册表按 MCP Tool 语义设计（含 `readOnlyHint`/`destructiveHint`），第二阶段加 Streamable HTTP 端点即可对外 |
| 会话存储 | **入库**（`ai_chat_session` / `ai_chat_message`），可回看、可审计 |
| 工具范围 | **内容运营全量 + 系统管理只读**；"改架构"类操作（内容类型/字段/发布选项/站点文件）单独分组，默认不暴露 |
| 协议范围 | **一期只实现 OPENAI（OpenAI Chat Completions 兼容）**；Anthropic / Responses 只留接缝，不实现。见 §4.5 |

---

## 1. 会决定方案的硬约束

以下全部来自现有代码，不是推测。改动方案前请先核对这一节。

| # | 事实 | 出处 |
| --- | --- | --- |
| 1 | 权限 = JWT 里的 perms 列表。`selectPermsByUserId` **没有 admin 分支**；admin 之所以全通过，是种子把全部菜单授给了 role 1 | `SysMenuMapper.xml:5`、`V2__seed.sql:58` |
| 2 | 前端 `hasPerm` **有** `roles.includes('admin') → true` 旁路 | `stores/user.ts` |
| 3 | 站点上下文是 **ThreadLocal**（`SiteContext`），由 `SiteInterceptor` 在 `/api/**` 上写入、`afterCompletion` 清理 | `SiteInterceptor:39,47` |
| 4 | 13 个 CMS Service 的 61 处代码都读 `SiteContext.siteId()` | 全仓 grep |
| 5 | 安全上下文是 `SecurityContextHolder` ThreadLocal（默认 `MODE_THREADLOCAL`） | `JwtAuthenticationFilter:44` |
| 6 | 站点越权检查**只有一处**：`SiteService#accessibleSiteIds`。**无法访问的 siteId 静默回落到默认站点，不报错** | `SiteService:110`、`SiteInterceptor:19` |
| 7 | `ContentService#detail(Long)`、`ContentTypeService#detail(Long)`、`SiteService#siteDir(Long)` **不带 site_id 条件**，越权防线完全依赖 `SiteContext` | `ContentService:140` 等 |
| 8 | `AiHttpClient` 用 `SimpleClientHttpRequestFactory`，**整体缓冲响应**，注释明说"仅用于非流式调用" | `AiHttpClient:20` |
| 9 | 三种协议适配器**完全不支持 tools**：`AiMessage` 只有 `role`/`content`，`ChatResult` 只有 `content`/`reasoning`/tokens | `AiMessage:15`、`ChatResult:6` |
| 10 | 全站静态化发布**同步阻塞**，耗时数秒~数分钟；同站点用 `ReentrantLock` 串行排队，后到的请求**阻塞等待**而非拒绝 | `SitePublishService`（`SITE_LOCKS`） |
| 11 | `PublishFacade#publish(long siteId, Path siteDir, Mode, String trigger)` 是**唯一不需要 `SiteContext` 的重载** | `PublishFacade:86` |
| 12 | `ContentService.create/update` 在同事务里**重建 3 张派生表**（`cms_content_index`/`_category`/`_tag`） | `ContentController:30` 注释 |
| 13 | `OperLogAspect` 从 `RequestContextHolder` 取 method/uri/ip（**可为 null**），从 `SecurityContextHolder` 取用户 | `OperLogAspect:55-67` |
| 14 | 前端侧边栏是**手写静态 `el-menu`**；`sys_menu` 只管按钮级权限；**全站没有路由级鉴权** | `layout/index.vue:56-63` |
| 15 | `<router-view :key="siteStore.currentSiteId">` → **切站点会 remount 页面** | `layout/index.vue:134` |
| 16 | 全仓**没有任何 SSE / 流式代码**；axios 全局超时 30s | grep 0 命中、`request.ts:19` |
| 17 | 迁移文件**只增不改**，Flyway 比对校验和；SQL 一律写在 XML mapper 里 | `backend/AGENTS.md` |

### 由此推出来的三条铁律

1. **工具必须跑在带上下文的线程上。** Agent 循环不能直接在 `SseEmitter` 的回调里跑——那时请求线程已返回，两个 ThreadLocal 都空了。必须在**请求线程上先捕获** `LoginUser` + `siteId`，再带到执行线程上显式恢复。
2. **绝不去改 `MODE_INHERITABLETHREADLOCAL`。** 那会影响整个应用所有线程池的行为，是典型的"解决一处、污染全局"。
3. **站点作用域必须显式化。** 依赖 `X-Site-Id` 的静默回落，会出现"AI 说写成功了、实际写进了默认站点"。工具返回值必须回显实际生效的 `siteId`。

---

## 2. 总体架构

```
┌─ 前端 src/views/ai/copilot/index.vue ───────────────────────────────┐
│  智能体下拉(关联 ai_agent) · 当前站点徽标 · 消息流                     │
│  工具调用内联卡片(名称/参数/结果/耗时/状态) · 危险操作确认弹窗           │
│  src/utils/sse.ts：fetch + getReader + 手写 Authorization/X-Site-Id  │
└──────────────────────┬──────────────────────────────────────────────┘
                       │ POST /api/ai/copilot/chat      (text/event-stream)
                       │ POST /api/ai/copilot/confirm
                       │ GET  /api/ai/copilot/sessions  (列表/回放)
┌──────────────────────▼──────────────────────────────────────────────┐
│ CopilotController   @PreAuthorize("hasAuthority('ai:copilot:chat')") │
│   ← 请求线程：捕获 LoginUser + perms HashSet + siteId（收敛后的）      │
├─────────────────────────────────────────────────────────────────────┤
│ ChatSessionStore   内存热态(ConcurrentHashMap + TTL) + DB 追加日志     │
├─────────────────────────────────────────────────────────────────────┤
│ AgentLoopService   虚拟线程执行器                                     │
│   ①拼平台提示词 ②调模型(带 tools, 流式) ③取 tool_calls                │
│   ④权限预检 ⑤确认闸门 ⑥执行 ⑦回灌 → 回②                              │
│   上限 8 轮 / 全局 Semaphore(8) / 可取消                              │
├─────────────────────────────────────────────────────────────────────┤
│ ToolRegistry       ToolSpec{name, desc, inputSchema, permission,     │
│                             risk, trustLevel, handler, group}        │
│ ToolSchemaFactory  从 DTO record + jakarta 注解派生 JSON Schema        │
│ ToolPermission     纯 perms HashSet 判定，O(1)，零查库                 │
│ ToolExecutor       只读并发 / 写串行；显式恢复 SiteContext+Security    │
│ ToolAudit          WRITE/DESTRUCTIVE → sys_oper_log，全量 → 会话表     │
├─────────────────────────────────────────────────────────────────────┤
│ AiToolProtocolClient  一期只实现 OpenAiToolProtocolClient（按 protocol() 装配）│
├─────────────────────────────────────────────────────────────────────┤
│ 现有 Service（事务 / 站点隔离 / 业务校验 全部复用，一行不改）            │
└─────────────────────────────────────────────────────────────────────┘
```

**为什么包装 Service 而不是 Controller**：包装 Controller 要走一次 HTTP 自调用，得伪造 token、绕一圈网络，而且 SSE 请求自己调自己会把 Tomcat 线程池拖死。包装 Service 直接拿到事务、站点隔离、`BizException` 业务校验——这些恰好就是"验证权限"和"数据隔离"要的东西。

---

## 3. 权限模型（四层，缺一不可）

### 3.1 页面级

新增权限点 `ai:copilot:chat`。前端侧边栏是静态的，**不做菜单级隐藏**（与全站现状一致，没有任何菜单项带 `v-permission`）；真正的门是后端接口的 `@PreAuthorize`。

`ai:copilot:confirm` 单独一个权限点，用于"允许用户批准危险操作"——只读用户能聊但批不了删除。

### 3.2 工具级（核心）

每个工具声明一个 `permission` 字符串，**直接复用 Controller 上那个**（如 `cms:content:add`）。

判定方式：`LoginUser.getPerms()` 转成 `HashSet`，`contains(tool.permission)`。O(1)，零次 DB 查询。

**必须与后端语义对齐，不能抄前端的 admin 旁路。** 后端 `selectPermsByUserId` 没有 admin 例外，admin 全通过是因为种子数据授了全部菜单。工具层照此办理：纯按 perms 判定。这一点如果不刻意对齐，会出现"后端 403 但 AI 以为能做"或反之。

工具在 `@PreAuthorize` 之外**再判一次**是刻意的重复，不是冗余：工具调用不经过 Spring MVC 的方法拦截链，`@PreAuthorize` 根本不会被触发。

### 3.3 站点级

- 会话创建时把站点 id **钉死**（`SiteService.resolveSiteId(请求头站点)` 收敛后的值），存入 `ai_chat_session.site_id`。
- 每次工具执行前在目标线程 `SiteContext.set(钉死的 siteId)`，`finally` 里 `clear()`。
- **每个工具的返回值都带上 `siteId` 与站点名**，让模型和用户都能看到实际生效的站点。
- 切站点 = 新会话（前端本来就会 remount，`layout/index.vue:134`）。

### 3.4 智能体级（工具范围）

`ai_agent` 新增两列：

- `tool_enabled`（默认 1）：这个智能体是否允许调用工具。关掉即退化成现有的"试聊"。
- `tool_scope`：逗号分隔的工具组白名单，**空 = 只给默认组**。组见 §4.2。

这样"智能体与工具"的关联落在 `ai_agent` 上，页面上选哪个智能体就用哪套工具范围与提示词。

### 3.5 审计

- **全量**工具调用写入 `ai_chat_message`（`role='tool'`），带 `tool_name`、`tool_status`、`tool_duration_ms`、`site_id`。
- **只有 WRITE / DESTRUCTIVE 工具**额外写一行 `sys_oper_log`（`module='AI工具'`、`action=工具名`、`method='TOOL'`）。
  - 直接构造 `SysOperLog` 写入，**不走 `@OperLog` 切面**——切面依赖 `RequestContextHolder`，而工具跑在非请求线程上，`method`/`uri`/`ip` 会全空。
  - READ 工具不写 `sys_oper_log`，否则一次对话能把操作日志刷几十行，把人工操作的痕迹淹掉。

---

## 4. 工具层

### 4.1 工具定义

```java
ToolSpec.builder("cms_content_list")
    .description("按类型/状态/关键字分页查询当前站点的内容，返回摘要字段（不含正文）")
    .permission("cms:content:list")       // 与 Controller 上一模一样
    .risk(Risk.READ)                      // READ / WRITE / DESTRUCTIVE
    .group(ToolGroup.CONTENT)             // 用于 ai_agent.tool_scope
    .schema(ContentQueryRequest.class)    // 从 DTO 派生 JSON Schema
    .handler(args -> contentService.page(convert(args, ContentQueryRequest.class)))
    .build();
```

三个关键点：

1. **JSON Schema 从 `record` + jakarta 注解派生，不手写。**
   遍历 `Class.getRecordComponents()`，读 `@NotBlank`/`@Size`/`@Min`/`@Max`/`@DecimalMin`/`@Pattern` 生成约束。写一个约 100 行的 `ToolSchemaFactory` 即可，**不引 `jackson-module-jsonSchema`**。
   这么做的价值：**模型看到的约束 ≡ 实际校验的约束**。手写 schema 迟早和 DTO 漂移，表现是"模型以为能传 300 字符标题，实际 400"。
2. **参数校验复用 `@Valid`。** `ObjectMapper.convertValue` → Spring `Validator.validate`。AI 传的参数享受与 HTTP 请求**完全一致**的校验规则，不写第二套。
3. **`risk` 对齐 MCP 的 tool annotations**：`READ → readOnlyHint:true`、`WRITE/DESTRUCTIVE → destructiveHint:true`。第二阶段对外暴露时直接映射，前端弹窗也读同一个字段。

### 4.2 工具清单（默认组 26 个；阶段 B 加上模板只读组共 31 个）

**组 CONTENT —— 内容运营（默认暴露）**

| 工具 | permission | risk |
| --- | --- | --- |
| `cms_stats` | 仅登录 | READ |
| `cms_content_list` `cms_content_get` | `cms:content:list` | READ |
| `cms_category_tree` | `cms:category:list` | READ |
| `cms_tag_list` | `cms:tag:list` | READ |
| `cms_menu_tree` | `cms:menu:list` | READ |
| `cms_media_list` | `cms:media:list` | READ |
| `cms_site_get` | `cms:site:list` | READ |
| `cms_content_type_list` `cms_field_list` | `cms:type:list` | READ |
| `cms_publish_preview` | `cms:publish:run` | READ |
| `cms_publish_status` | `cms:publish:run` | READ |
| `cms_content_create` | `cms:content:add` | WRITE |
| `cms_content_update` | `cms:content:edit` | WRITE |
| `cms_content_set_status` | `cms:content:publish` | WRITE |
| `cms_category_create` `cms_category_update` | `cms:category:add` / `edit` | WRITE |
| `cms_tag_create` `cms_tag_update` | `cms:tag:add` / `edit` | WRITE |
| `cms_menu_item_create` | `cms:menu:add` | WRITE |
| `cms_content_delete` | `cms:content:delete` | **DESTRUCTIVE** |
| `cms_category_delete` | `cms:category:delete` | **DESTRUCTIVE** |
| `cms_publish_run` | `cms:publish:run` | **DESTRUCTIVE** |

**组 TEMPLATE_READ —— 模板/站点文件只读（默认暴露，无风险）**

| 工具 | permission | risk |
| --- | --- | --- |
| `cms_theme_list` | `cms:theme:list` | READ |
| `cms_site_file_list` `cms_site_file_read` | `cms:site:file:list` | READ |
| `cms_template_syntax` | 仅登录 | READ |
| `cms_template_check` | `cms:publish:run` | READ |

这五个读工具**放进默认组**：只读、不写盘、无副作用，而且是 AI 理解站点的必要输入（改模板要靠它们看现状）。

**组 SYSTEM_READ —— 系统只读（默认暴露 3 个）**

| 工具 | permission | risk |
| --- | --- | --- |
| `sys_user_list` | `sys:user:list` | READ |
| `sys_dict_items` | `sys:dict:list` | READ |
| `sys_oper_log_list` | `sys:log:list` | READ |

`sys:role:list` 刻意不做——读权限面没有业务价值。

**组 PLATFORM —— "改架构"类（默认不暴露，需在 `ai_agent.tool_scope` 显式打开）**

| 工具 | permission | risk |
| --- | --- | --- |
| `cms_content_type_create/update` | `cms:type:add` / `edit` | WRITE |
| `cms_field_create/update` | `cms:field:add` / `edit` | WRITE |
| `cms_publish_option_save` | `cms:publish:option:edit` | WRITE |
| `cms_site_file_replace` | `cms:site:file:edit` | **DESTRUCTIVE** |
| `cms_site_file_create` | `cms:site:file:add` | **DESTRUCTIVE** |
| `cms_content_type_delete` `cms_field_delete` | `cms:type:delete` / `field:delete` | **DESTRUCTIVE** |
| `cms_site_file_delete` | `cms:site:file:delete` | **DESTRUCTIVE** |

分成两个信任级别的理由：`cms:content` 是"改内容"，`cms:type`/`cms:field`/`publish:option`/`site:file` 是"改整站形态"。放在同一个工具集里，等于把"改内容"的授权顺带升级成了"改整站形态"——写坏一个模板，下一次全站发布会整体坏页（见 §4.6）。

### 4.3 明确不给 AI 的（20 个权限点）

`sys:user:add|edit|delete|reset`、`sys:role:add|edit|delete|assign`、`sys:menu:*`、`sys:dict:add|edit|delete`、`ai:provider:*`、`ai:agent:add|edit|delete`、`cms:site:add|edit|delete`、`cms:media:upload`、`cms:media:delete`、`/api/auth/**`。

四条理由：

1. **提权面**：`sys_menu.perms` 就是 `@PreAuthorize` 的权限串来源。让 AI 能写 `sys_menu` 或 `sys_role_menu`，一次调用就能给自己所在角色加满权限。
2. **自锁风险**：改掉角色、删掉菜单、停用账号——模型一次幻觉就可能把后台锁死。
3. **凭据**：`PUT /api/system/users/{id}` 请求体带 `password` 时**静默改密**，等价于账号接管能力。
4. **二进制**：媒体上传需要 multipart，工具调用协议传不了；`MediaService.upload` 也没有文件大小上限。

### 4.4 两个必须特殊处理的工具

**`cms_publish_run`** —— 同步阻塞数秒到数分钟，直接塞进 tool loop 会让 SSE 长时间静默、前端像卡死。

处理方式：工具内部**用独立线程提交发布**，立即返回 `batchId`；模型用 `cms_publish_status` 轮询。提交时必须调

```java
PublishFacade#publish(long siteId, Path siteDir, Mode mode, String trigger)
```

——这是**唯一不需要 `SiteContext` 的重载**（事实 #11），正好适合后台线程。但 `siteId` 与 `siteDir` 的越权校验要自己做：先 `siteService.resolveSiteId(钉死的 siteId)`，再 `siteService.siteDir(收敛后的 id)`。

并发安全不需要额外加锁：`SitePublishService` 内部有 per-site `ReentrantLock`，后台发布与人工发布会正确排队。

另外两条约束（默认值由工具写死，模型不能改）：
- 默认 `mode=incremental`；只有用户确认弹窗里显式勾选才允许 `full`（full 带 GC，会删多余产物）。
- 先 `cms_publish_preview` 拿到页面清单，再允许发布。预演不写盘不写库。

**组合风险：改模板 + 发布连招。** AI 能改模板（§4.6），也能发布全站——它可以先改坏模板、再自己发布上线。而 `publish_run` 的确认弹窗里用户看到的是"发布全站"，**看不到模板刚被改坏**。

缓解：`publish_run` 的确认弹窗要**显式列出"本会话内改过的模板文件"**（连同文件路径与改动行数）。实现很便宜——会话里的 `cms_site_file_replace` 调用记录现成就有。缺了这一步，它就是一个真实的翻车路径。

**`cms_content_create` / `cms_content_update`** —— 同事务重建 3 张派生表（事实 #12），是重操作。工具粒度**必须"一次一条"**，不做批量。批量由模型自己多轮调用，这样每一步都可确认、可中止、可审计。

---

## 4.5 协议范围：一期只支持 OPENAI

### 4.5.1 接缝在哪

**不新建抽象层**，照抄已有的房内模式。`AiChatService` 已经是这么做的：

```java
public AiChatService(List<AiProtocolClient> clients) {
    this.clients = clients.stream().collect(Collectors.toMap(
            c -> c.protocol().name(), Function.identity(), ...));
}
```

Tool 侧镜像一份：

- 接口 `AiToolProtocolClient`，方法 `protocol()` + 一次带工具的流式对话。
- 唯一实现 `@Component OpenAiToolProtocolClient`，`protocol()` 返回 `AiProtocol.OPENAI`。
- `AgentLoopService` 构造器注入 `List<AiToolProtocolClient>`，装配成 `Map<String, AiToolProtocolClient>`。

`AiProtocol` 枚举**已经有** `OPENAI / ANTHROPIC / RESPONSES` 三个值，**不需要预先加任何东西**。

查表落空时抛的错就是"预留"的落点：

```
协议「ANTHROPIC」暂不支持工具调用，请在「AI服务商」里改用 OPENAI 协议的配置
```

**将来加协议要动的文件：只有 1 个** —— 新增 `AnthropicToolProtocolClient.java` 打上 `@Component`。注册表、循环、权限、审计、前端全部不用改。这就是"预留好"的具体含义。

### 4.5.2 智能体可选性：前后端都要拦

协议在 `ai_provider` 上，不在 `ai_agent` 上。`AgentVO` 已经带了 `protocol`（从 provider 取），所以：

- **前端**：`/api/ai/agents/options` 返回 `protocol` 与 `jsonOutput`，下拉里**只列 `protocol=OPENAI` 且 `status=1` 且 `jsonOutput=0`** 的智能体；列表为空时给明确文案（"还没有可用的智能体，请到 AI管理→智能体 新建一个，并绑定 OPENAI 协议的 DeepSeek 服务商"），而不是给一个空下拉。
- **后端**：`CopilotService` 建会话时**硬校验**，不通过就抛 `BizException`。前端过滤不是安全边界。

两条排除规则各自的理由：

| 规则 | 理由 |
| --- | --- |
| `protocol != OPENAI` | 一期没有对应实现（§4.5.1） |
| `jsonOutput == 1` | JSON 输出模式与工具调用互斥：模型要么返回 JSON 对象、要么返回 `tool_calls`，不能同时。`AgentSaveRequest` 已有这个开关，页面上也已有勾选框 |

种子里 `ai_provider` 的 **id=1 就是 OPENAI 协议**（`V20260930224301__ai_provider_agent.sql:64`），所以现成的 DeepSeek 配置直接可用，**不需要改任何数据**。

### 4.5.3 `thinking` 与工具调用：**已实测，兼容**

**结论：`thinking` 照常开启，不需要强制关闭。** 用智能体自己的 `thinking` 设置，思考过程照常通过 `reasoning` 事件推给前端。

实测（`thinking=enabled` + `reasoning_effort=high` + `tools`）→ HTTP 200 且 `finish_reason=tool_calls`。原来担心的"必须关思考模式"整个消失了，这是个纯收益——用户能看到 AI 在想什么。

另有一条**实测挖出来的硬约束**，比上面这条重要得多：

> **多轮历史回灌时，`tool_call_id` 必须原样存取，绝不改写；`reasoning_content` 必须完整落库。**

原因：历史里若缺 `reasoning_content` 而 `tool_call_id` 又不被服务端认可，会返回

```
The `reasoning_content` in the thinking mode must be passed back to the API.
```

——**这个错误信息指向了错误的原因**（真问题是 id，报的是 reasoning）。12 组对照实验才定位到，详见 §10.4。

所以历史拼装有三条规则：

1. `tool_call_id` 原样存取，不截断、不重命名、不重新生成、不排序。
2. `reasoning_content` 完整落库不截断（`ai_chat_message.reasoning_content` 是 `text`，够用）。
3. **默认不回传** `reasoning_content`（省 token、不污染上下文，实测稳定 200），**但加自动降级**：回灌若收到 400 且错误信息含 `reasoning_content`，补上历史里的 `reasoning_content` 重试一次并打 WARN。约 15 行，同时拿到两边的优点，且那行 WARN 就是"某个 id 服务端不认识"的信号。

---

## 4.6 模板编辑（阶段 B，排在内容运营之后）

这是 AI Agent 在这个 CMS 里价值最高的场景，而且**引擎侧的支撑已经齐了**，不用从零造：

| 已有能力 | 出处 |
| --- | --- |
| **20 个模板校验器**：`TagNameValidator`、`FieldPathValidator`、`FilterFieldValidator`、`AnchorOfValidator`、`TagPageTypeMatrixValidator`、`PaginationBodyValidator`、`ReferenceValidator`… | `publish/template/validate/` |
| **不写盘不写库的预演**：preflight 体检 + 页面计划 | `PublishFacade:100` |
| **受边界保护的站点目录读写**：拦绝对路径、`..` 上跳、URL 编码绕过、软链 | `common/site/SitePathBoundary` |
| **语法契约**：§3 语法规格 + §6 标签总表（含"刻意不提供的标签"与"标签×页面类型合法性矩阵"） | `docs/static-publish.md:521,1106` |

`.html/.css/.js/.json` 都在 `SiteFileService.TEXT_EXT` 里，模板文件本来就能编辑。**AI 写模板缺的不是引擎能力，是"看得懂规范"和"改完知道对不对"。** 所以真正要补的是下面四件事。

### 4.6.1 `cms_template_syntax`：语法速查

AI 不知道标签有哪些参数、哪些标签在详情页非法，就会编。做法：把 `static-publish.md` 的 §3 + §6（约 580 行）切片成工具返回值，模型按需调一次，之后整个会话历史里都带着。

**这是本功能真正的成本所在**，不是代码：要人工整理一次，而且 **语法变更时必须同步维护**，否则模型会照着过时规范写。若后续发现漂移严重，再考虑从 `TagRegistry` + 校验器反射生成标签清单。

### 4.6.2 `cms_template_check`：结构化诊断

这里有一个**现成的小缺口**：`PublishFacade.preview()` 把问题拍平成了 `List<String>`（`PublishFacade:124` 那句 `problems.add(e.getMessage())`），而 `PublishException` 里其实带着 `templatePath` / `lineNo` / `actual` / `advice`——`PublishErrors`、`ValidationReport`、`PublishErrorCode.summary()` 也都是现成的。

AI 需要的是"**文件:行号 + 错误码 + 怎么改**"，一句中文串不够。做法：工具直接注入 `List<TemplateValidator>`（Spring 已经按列表收集，`SitePublishService:109` 就是这么用的）自己跑一遍，把 `PublishException` 原样映射成结构化结果。**校验器一个都不用写。**

### 4.6.3 写入用精确替换，不用整文件覆盖

**只提供两个写工具**：`cms_site_file_replace(path, oldText, newText)` 与 `cms_site_file_create(path, content)`。**刻意不提供整文件覆盖**（`SiteFileService.save` 那种）。

| | 整文件覆盖 | **精确替换** |
| --- | --- | --- |
| 乐观锁 | 无，并发/误覆盖无声发生 | **天然有**：`oldText` 不唯一匹配就拒绝 |
| diff 展示 | 全文件重写，用户看不出改了哪 | **diff 就是 oldText/newText**，前端直接渲染，不引 diff 库 |
| token 成本 | 模型要重写整个文件 | 只传改动片段 |

前端确认弹窗**必须让用户看到 diff**——否则"批准 AI 改模板"就是一次盲签。

### 4.6.4 不能往 `template/` 里放备份文件

发布引擎的 `copyStatic` / `copyAssets` 会把主题目录下的资源**拷进 `www/`**。所以往 `template/<theme>/` 放 `.bak` 会跟着发布出去，进产物、进搜索索引。

**"写坏能回滚"不能靠备份文件。** 精确替换方案顺手解决了这件事：旧内容天然留在 `ai_chat_message` 的 `tool_args` 里（因为 `replace` 的 `oldText` 就是原文），要回滚从会话记录里捞，磁盘上不留垃圾。

---

## 5. 接口契约

### 5.1 `POST /api/ai/copilot/chat` → `text/event-stream`

请求：

```json
{ "sessionId": 12, "agentId": 3, "message": "把上个月没发布的草稿都发出来" }
```

`sessionId` 为空表示新建会话（响应首帧回传）。站点取 `X-Site-Id` 头，由拦截器收敛后钉进会话。

**用 POST + `fetch` 流，不用 `EventSource`**：`EventSource` 不能带 `Authorization` 头，而 JWT 只能在头里传。

SSE 事件表：

| event | data | 说明 |
| --- | --- | --- |
| `session` | `{sessionId, siteId, siteName, agentId, agentName, tools:[...]}` | 首帧，钉住上下文 |
| `reasoning` | `{delta}` | 思考内容增量 |
| `delta` | `{delta}` | 正文增量 |
| `tool_call` | `{toolCallId, name, title, args, risk}` | 模型请求调用工具 |
| `tool_result` | `{toolCallId, ok, summary, durationMs, siteId}` | 执行结果摘要（完整结果给模型，前端只给摘要） |
| `confirm` | `{toolCallId, name, title, args, risk, expiresIn}` | 需要人工确认，前端弹窗 |
| `done` | `{messageId, inputTokens, outputTokens, rounds}` | 本轮结束 |
| `error` | `{code, message}` | 业务错误 |

**心跳**：每 15 秒发一个 `:\n\n` 注释帧。上游长思考 + 反向代理容易在 60s 空闲时断连。

### 5.2 `POST /api/ai/copilot/confirm`

```json
{ "sessionId": 12, "toolCallId": "call_abc", "decision": "ALLOW" }
```

`decision` ∈ `ALLOW` / `DENY`。同时支持 `argsOverride`（用户在弹窗里改了参数再批准）。

实现：AgentLoop 在 `confirm` 事件后 `CompletableFuture.get(5, MINUTES)` 阻塞等待；本接口完成该 future。超时按 `DENY` 处理，回灌一条"用户未在时限内确认，操作已取消"给模型，让它换路子或收尾。

被拒绝时回灌的是**工具结果**（`isError: true`），不是协议错误——模型需要看到拒绝原因才能调整策略。

### 5.3 会话查询

- `GET /api/ai/copilot/sessions?page=&size=` —— 本人会话分页
- `GET /api/ai/copilot/sessions/{id}` —— 会话详情（含全部消息，用于回放）
- `GET /api/ai/agents/options` —— 智能体下拉（登录即可读，只回 `id/name/code/model/protocol/thinking/jsonOutput`，**不回 systemPrompt**）

---

## 6. 数据模型

新增迁移 `V<yyyyMMddHHmmss>__ai_copilot.sql`（文件名按 `backend/AGENTS.md`，版本号取创建时的本地时间 14 位；**已执行过的迁移文件保持字节不变**）。

```sql
-- 智能体：工具开关与工具组白名单
alter table ai_agent add column tool_enabled smallint not null default 1;
alter table ai_agent add column tool_scope   varchar(255);   -- 逗号分隔组名，空 = 默认组

-- 会话
create table ai_chat_session (
    id             bigserial    primary key,
    title          varchar(128),                 -- 取首条用户消息前 50 字
    user_id        bigint       not null,
    site_id        bigint       not null,        -- 会话内钉死，工具一律按它执行
    agent_id       bigint       not null,
    status         smallint     not null default 1,  -- 1 进行中 / 0 已结束
    rounds         int          not null default 0,
    tool_call_count int         not null default 0,
    input_tokens   bigint       not null default 0,
    output_tokens  bigint       not null default 0,
    create_by      bigint,
    create_time    timestamp    not null default now(),
    update_by      bigint,
    update_time    timestamp    not null default now(),
    deleted        smallint     not null default 0
);
create index idx_ai_chat_session_user on ai_chat_session (user_id, id desc) where deleted = 0;

-- 消息（追加写，不更新）
create table ai_chat_message (
    id                bigserial   primary key,
    session_id        bigint      not null,
    seq               int         not null,      -- 会话内递增，唯一
    role              varchar(16) not null,      -- user / assistant / tool
    content           text,
    reasoning_content text,
    tool_calls        text,                      -- assistant 发起的工具调用，JSON 数组
    tool_call_id      varchar(64),               -- role=tool 时对应哪次调用
    tool_name         varchar(64),
    tool_args         text,
    tool_status       smallint,                  -- 1 成功 / 0 失败 / 2 被用户拒绝 / 3 无权限
    tool_duration_ms  int,
    site_id           bigint,                    -- 冗余，便于审计
    input_tokens      int,
    output_tokens     int,
    create_time       timestamp   not null default now()
);
create unique index uk_ai_chat_message_seq on ai_chat_message (session_id, seq);
create index idx_ai_chat_message_session on ai_chat_message (session_id, seq);

-- 菜单 / 权限
--   MENU   33  全站agent  /ai/copilot  ai/copilot/index  ai:copilot:chat
--   BUTTON 331 全站agent对话    ai:copilot:chat
--   BUTTON 332 危险操作确认     ai:copilot:confirm
insert into sys_role_menu (role_id, menu_id) select 1, id from sys_menu where id in (33, 331, 332);
select setval('sys_menu_id_seq', (select max(id) from sys_menu));
```

**`ai_chat_message` 没有 `deleted` 列**：它是追加日志，不做逻辑删除（删会话时按 `session_id` 清理）。这一点要在 XML 里注意——实体上不加 `@TableLogic`。

Mapper 一律遵守 `backend/AGENTS.md`：**Java 里不出现 SQL**，语句写在 `src/main/resources/mapper/ai/AiChatSessionMapper.xml` 与 `AiChatMessageMapper.xml`，XML 里该写 `deleted = 0` 的地方自己写。

### 写入时机（这是"会话入库"最容易做砸的地方）

| 时机 | 写库次数 |
| --- | --- |
| 用户发一条消息 | 1 次 INSERT |
| **每收到一个流式 delta** | **0 次** —— 只进内存，绝不逐 token 写库 |
| 一轮模型回复结束 | 1 次 INSERT（正文 + 思考内容 + `tool_calls` JSON） |
| 每次工具执行完 | 1 次 INSERT |
| 会话结束 / 超时 | 1 次 UPDATE（累计 token、轮数、状态） |

内存是热路径，DB 是**追加日志**。只有打开历史会话回放时才从 DB 重放。

**`reasoning_content` 与 `tool_call_id` 必须完整落库、原样取出**——它们是历史回灌能不能被服务端接受的关键（§4.5.3、§10.4）。**不要对这两列做任何截断或加工。**

---

## 7. 高性能设计

### 7.1 上游改流式（首字节的唯一来源）

`AiHttpClient` 整体缓冲，首字节等于全量生成时间。新写 `OpenAiToolProtocolClient`，用 JDK 的 `java.net.http.HttpClient` + `BodyHandlers.ofLines()`。

**不覆盖 `AiHttpClient`** —— `AiAgentController` 的试聊还在用它，保持原样（精准修改）。

一期只需要处理 OPENAI 一种 delta 形状：

| 字段 | 位置 | 处理 |
| --- | --- | --- |
| 正文增量 | `choices[0].delta.content` | 直接推 `delta` 事件 |
| 思考增量 | `choices[0].delta.reasoning_content` | 推 `reasoning` 事件；**是否回传进历史见 §4.5.3** |
| 工具调用 | `choices[0].delta.tool_calls[]` | 按 `index` 分片拼接：`id` 与 `function.name` 通常只在首片给全，`function.arguments` 是**跨片截断的 JSON 字符串**，必须攒完再 `readTree` |
| 结束原因 | `choices[0].finish_reason` | `tool_calls` → 进入执行分支；`stop` → 收尾 |

留作将来（**不实现，只记在这里**，免得下次重新查文档）：

| 协议 | 正文 delta | 思考 delta | 工具调用 delta |
| --- | --- | --- | --- |
| ANTHROPIC | `content_block_delta` → `text_delta` | `thinking_delta` | `input_json_delta`（JSON 字符串分片） |
| RESPONSES | `response.output_text.delta` | reasoning 事件族 | function_call 事件族 |

**验证指标**：真实服务商下首字节 < 1.5s。

### 7.1.1 `tool_calls` 分片拼接是个真实的坑

**已实测确认**：一次 `cms_content_list` 调用的 `arguments`（`{"typeCode": "article", "status": "PUBLISHED", "size": 5}`，48 字符）跨了 **27 个分片**到达。

`function.arguments` 是流式截断的 JSON 片段（如 `{"ti` / `tle":"x` / `"}`），**不能边收边 parse**。做法：按 `index` 维护一个 `StringBuilder`，`finish_reason=tool_calls` 之后再整体 `readTree`。

拼出来的 JSON 仍然可能不合法（模型截断、上游网关掉包）。**必须兜住**：解析失败时回灌一条工具结果 `isError: true` / `"参数不是合法 JSON"` 给模型让它重试，而不是让整个 SSE 流以 500 结束。

### 7.2 SSE 用 `SseEmitter`

Servlet 栈已有，**不引 WebFlux**。前端配 `fetch` + `ReadableStream`。

### 7.3 线程模型：只在 copilot 模块内用虚拟线程

`Executors.newVirtualThreadPerTaskExecutor()`，**不动 `spring.threads.virtual.enabled`**。

理由：发布引擎、`SitePreviewServer`、`ArtifactWriter` 里有 `synchronized`，全局开虚拟线程会撞 pinning；而且改全局配置违反"精准修改"。一个可能挂 5 分钟的 SSE + 一次等用户确认，由虚拟线程承载即可，不占 Tomcat 平台线程。

**上下文捕获是这一步的关键**：

```
CopilotController.chat()  ← 请求线程
  1. LoginUser user = SecurityUtils.user()
  2. Long siteId = siteService.resolveSiteId(SiteContext.siteId())   // 收敛
  3. Set<String> perms = Set.copyOf(user.getPerms())
  4. 建 SseEmitter，交给虚拟线程

AgentLoop 在新线程上    ← SiteContext 与 SecurityContext 都是空的
  SecurityContextHolder.getContext().setAuthentication(auth)   // Service 里可能读它
  SiteContext.set(siteId)                                      // 所有 CMS Service 都读它
  try { ...循环... }
  finally { SiteContext.clear(); SecurityContextHolder.clearContext(); }
```

### 7.4 工具执行：只读并发、写串行

同一轮返回多个 `tool_calls` 时，**只读工具可以并发**，**任何写工具串行**。

**已实测确认模型确实会一次返回多个 `tool_calls`**（§10.2：一次 `cms_stats` + 一次 `cms_category_tree`），所以并发执行不是空想优化——一句话问到两个信息源时，串行就是白等一个来回。

写工具串行的理由：并发写会在不同事务里抢同一行，收益为零风险不小。

并发执行时，每个子线程同样要显式恢复 `SiteContext` + `SecurityContext`——**ThreadLocal 不会自己传播**。封装成 `ToolContext.runWith(ToolContext ctx, Runnable)`，只在这一处处理上下文，别处不许直接 `SiteContext.set`。

### 7.5 并发度跟着数据库连接池走

Hikari 默认 10 连接。工具并发上限设 **4**，agent 循环全局 `Semaphore` 设 **8**。

**否则一次 AI 对话就能把连接池占满，正常后台用户全部超时**——这是最容易忽略的性能回退点。

### 7.6 权限判定 O(1)、提示词按权限裁剪

会话建立时 perms 转 `HashSet`；系统提示词里**只注入当前用户实际有权限的工具**。既省 token，也减少模型"试错式"地调用没权限的工具（每次都是白烧一轮）。

### 7.7 工具返回值必须瘦身

- `cms_content_list` 只回 `{id, title, slug, status, typeCode, updateTime}`，**不回正文**。
- 正文只走 `cms_content_get`，默认截断 8K 字符并标注 `...(已截断)`。
- 列表默认 `size=10`，上限 50。

**验证指标**：列 20 条内容的工具结果 < 2K token。

### 7.8 长任务不进循环

见 §4.4。发布工具提交后台、立即返回 `batchId`，模型轮询 `cms_publish_status`。

### 7.9 可取消

前端 `AbortController` → `SseEmitter.onCompletion/onError/onTimeout` → `AtomicBoolean cancelled` → 循环每次迭代前检查 → 立即关闭上游流。

**用户关掉标签页不该继续烧 token。**

### 7.10 零新增后端依赖

- **不引 Spring AI**：它自带一套 tool/agent 抽象，与现有三个协议适配器职责重复，而且版本耦合。
- **不引 WebFlux**：Servlet 栈已定，`SseEmitter` 够用。
- **不引 Caffeine**：会话 TTL 用一个 40 行的 `ConcurrentHashMap` + 定时清理足够。
- **不引 `jackson-module-jsonSchema`**：从 record 派生 schema 手写约 100 行，换来"schema 与校验同源"。

前端同理：第一期不引 markdown 渲染库，助手文本按现有试聊的做法用 `white-space: pre-wrap` 纯文本（引 `markdown-it` 会多一个 XSS 面）。

---

## 8. 前端改造

| 文件 | 改动 |
| --- | --- |
| `src/router/index.ts` | 加 `{ path: 'ai/copilot', name: 'AiCopilot', component: ..., meta: { title: '全站agent', group: 'AI管理' } }` |
| `src/layout/index.vue` | `<el-sub-menu index="ai">` 里**手加** `<el-menu-item index="/ai/copilot">全站agent</el-menu-item>` —— 没有任何东西会自动生成它 |
| **新建** `src/utils/sse.ts` | 全仓第一个流式 helper。见下 |
| **新建** `src/views/ai/copilot/index.vue` | 对话页 |
| `src/api/ai.ts` | `copilotChatStream` / `copilotConfirm` / `copilotSessions` / `agentOptions` |
| `src/types/index.ts` | AI 管理段加 Copilot 类型（`CopilotEvent` 判别联合、`ChatSession`、`ToolCallCard`） |

**`src/utils/sse.ts` 必须自己处理三件事**（`fetch` 绕过了 axios 的全部拦截器）：

1. 手工加 `Authorization: Bearer` + `X-Site-Id`（从 `localStorage` 读，与 `request.ts:23-33` 同源）。
2. 手工复刻 401 处理：清 `token`/`user`、`ElMessage.error('登录已失效，请重新登录')`、跳 `/login?redirect=`。
3. 暴露 `AbortController`。

**页面沿 `views/ai/agents/index.vue` 的既有形态**：`ChatItem` 带 `error` 标记、`chatSeq` 序列守卫、`event.isComposing` 的 IME 回车保护、`nextTick` 滚底。去掉 dialog 那层壳，加：

- 顶部：智能体下拉（默认选上次用的；**只列 OPENAI 协议且非 JSON 输出的智能体**，见 §4.5.2）+ 当前站点徽标（只读，来自 `session` 首帧）。
- 消息流：工具调用渲染成内联卡片（名称 / 参数 / 结果摘要 / 耗时 / 状态图标）。
- 确认弹窗：`risk=DESTRUCTIVE` 弹窗**高亮参数**（要看清楚删的是哪一条），`full` 发布需二次勾选。
- **`onBeforeUnmount` 里必须 abort** —— 切站点会 remount（事实 #15），不 abort 会留下孤儿流。

**无权限的工具不进提示词，也就不会出现在 UI 上**；`ai:copilot:confirm` 缺失时确认按钮禁用并提示"你没有确认危险操作的权限"。

---

## 9. 分阶段实施与验证标准

| 步 | 内容 | 验证标准 | 可独立回滚 |
| --- | --- | --- | --- |
| **0** | **技术假设验证** ✅ **已完成** | 见 §10：三条假设全部通过，另挖出一条历史回灌的硬约束 | 无代码，只留 `.tmp/` 笔记 |
| 1 | `AiToolProtocolClient` 接口 + `OpenAiToolProtocolClient`：tools 参数 + 流式 delta 解析 | 单测分片拼接与非法 JSON 兜底；真机首字节 < 1.5s | ✅ |
| 1b | 智能体可选性校验（协议白名单 + `jsonOutput` 排除），`/api/ai/agents/options` | 选 ANTHROPIC 的智能体建会话被拒且报错文案明确 | ✅ |
| 2 | `ToolSchemaFactory` + `ToolRegistry` + `ToolPermission` + 6 个只读工具 | 单测：无权限用户调用被拒；有权限放行；`@Size` 超长被拒 | ✅ |
| 3 | 迁移 + 会话存储 + AgentLoop（**先做非流式**） | curl 跑通"这个站点有多少篇文章"，`ai_chat_message` 有 3 行 | ✅ |
| 4 | 接上流式 SSE | 前端能看到打字机效果；心跳帧每 15s | ✅ |
| 5 | 写工具 + 确认闸门 + 取消 | 删除类工具必须弹窗；DENY 后模型换路子；关标签页后 5s 内停止上游请求 | ✅ |
| 6 | 前端页面 + 菜单 + 权限迁移 | 无 `ai:copilot:chat` 的用户访问接口得 403 | ✅ |
| 7 | 发布工具（后台提交 + 轮询） | 发布期间 SSE 不静默；`cms_publish_task` 有对应批次 | ✅ |
| 8 | 审计写 `sys_oper_log` | 操作日志页能看到 AI 的写操作；读操作不出现 | ✅ |
| — | **以下 B1–B5 为阶段 B（模板编辑，§4.6），排在 1–8 之后** | | |
| B1 | `cms_theme_list` / `cms_site_file_list` / `cms_site_file_read` 三个只读工具 | AI 能列出主题并读出模板原文 | ✅ |
| B2 | `cms_template_syntax`：整理 §3+§6 切片 | 用整理出的速查能让 AI 写出一份通过校验的模板 | ✅ |
| B3 | `cms_template_check`：结构化诊断入口 | 故意写错一个标签，返回里带**文件、行号、错误码、建议** | ✅ |
| B4 | `cms_site_file_replace` / `cms_site_file_create` | `oldText` 不唯一匹配时拒绝；旧内容能从 `ai_chat_message` 捞回 | ✅ |
| B5 | 前端 diff 确认弹窗 | 批准前能看到逐行改动；`publish_run` 弹窗列出本会话改过的模板 | ✅ |
| 9 | *（第三阶段）* MCP Streamable HTTP 端点 + `sys_api_token` | MCP Inspector 能 `tools/list` 并 `tools/call` | ✅ |

每步一个 commit（`AGENTS.md` 第 5 条）。

---

## 10. Step 0 技术假设验证：**已完成**

> 实测时间：方案定稿后 · 模型 `deepseek-flash`（DeepSeek-V4.1-Flash，1M 上下文 / 393216 输出）
> 全部结论来自**真实 HTTP 调用**，不是文档推断。完整清单与原始请求响应见
> [`.tmp/step0-tool-calling-findings.md`](../.tmp/step0-tool-calling-findings.md) 与 `.tmp/out/`。

### 10.1 三条假设全部通过

| 假设 | 结论 |
| --- | --- |
| ① `thinking` 开启时能否同时用 `function calling` | **兼容** ✅ `thinking=enabled` + `reasoning_effort=high` + `tools` → 200，`finish_reason=tool_calls` |
| ② 流式 + tools 是否兼容，分片能否拼出完整 JSON | **兼容** ✅ 39 个 SSE 帧，`arguments` 跨 **27 个分片**到达，拼接后解析正常 |
| ③ `tools` 参数形状 | **与 OpenAI Chat Completions 一致** ✅ `parameters` 支持 `enum` / `minimum` / `maximum` / `required`，模型按约束传参 |

**假设①的答案是原方案两种应对里较好的那一种**：不需要强制关思考模式。§4.5.3 的"可能被迫关思考"整条消失，用户能看到 AI 在想什么。

### 10.2 顺带确认的实现细节

| 事实 | 值 |
| --- | --- |
| `function.arguments` 类型 | **String**（JSON 文本），不是对象 |
| `tool_call_id` 形状 | `call_<2位>_<24位字母数字>`，**32 字符**；并行时前缀递增（`call_00_` / `call_01_`） |
| 是否支持并行 tool_calls | **支持**，实测一次返回 2 个 → §7.4 只读并发有了实测依据 |
| `thinking` 默认值 | **默认开启**。不传该字段也会返回 `reasoning_content`，想关必须显式传 `disabled` |
| prompt 缓存 | **生效**：`prompt_cache_hit_tokens=384 / prompt_tokens=590` → 多轮工具循环的历史部分会被缓存命中 |
| `usage` 的额外字段 | `completion_tokens_details.reasoning_tokens`（可计入成本统计） |

### 10.3 数据零改动

种子里 `ai_provider.id=1` 就是 OPENAI 协议，`ai_agent` 里 `thinking=1` + `json_output=0` 的那个智能体可直接用于 copilot。**本次不需要动任何种子数据。**

### 10.4 挖出来的真坑：历史回灌的 `reasoning_content` 规则

这是本次验证最有价值的发现——它**推翻了一个看起来合理的省 token 优化**。

历史里若缺 `reasoning_content` 而 `tool_call_id` 又不被服务端认可，返回：

```
The `reasoning_content` in the thinking mode must be passed back to the API.
```

**这个错误信息指向了错误的原因。** 12 组对照实验才定位到真正的触发条件是 **`tool_call_id` 是否被服务端认可**，不是 `reasoning_content` 缺失：

| 场景（`thinking=enabled`，assistant 带 `tool_calls`） | 结果 |
| --- | --- |
| 无 `reasoning_content` + **API 真实签发过的** id | **200** |
| 无 `reasoning_content` + 伪造 id / 从未签发的 id / 长度不对 / 分隔符不对 | **400** |
| **有** `reasoning_content` + **任意** id | **200** |
| `thinking=disabled` | 200，无所谓 |

同一份请求体跨若干分钟重放，结果完全稳定，**不是偶发**。

**实现规则见 §4.5.3**：id 原样存取、`reasoning_content` 完整落库、默认不回传但配 400 自动降级重试。

> 如果当时按"反正真实 id 下不回传也能过"去做，线上会得到一个随服务端状态漂移的**间歇性误导 400** —— 最难排查的那类 bug。这条规则是这次 Step 0 的主要产出。

### 10.5 另一条可信的错误信息

assistant 的 `tool_calls[].id` 与 tool 消息的 `tool_call_id` 对不上时，返回的是**准确的** 400（会指出哪个 id 没有对应响应）。这条可以放心用于排查。

### 10.6 暂时不用验的

一期不做，等真要做时再查：Anthropic 兼容协议的 `tools` 形状（`input_schema` / `stop_reason:"tool_use"` / `content[]` 里的 `tool_use` 块）、Responses 协议的 function call 事件族。

---

## 11. 刻意不做的事

- **不做 WebSocket / 双向通道**：确认回程用 SSE + 一个 POST 足够。
- **不做工具自动发现**：26~31 个工具手写声明，比扫注解生成更可控，也更容易审权限。
- **不改造 `AiAgentController.chat`**：试聊是无状态单轮，与 copilot 语义不同，混在一起会把两边的约束互相污染。
- **不做 RAG / 知识库**：README 已列为刻意未做，与本次需求无关。
- **不做多智能体编排 / 子 agent**：单 agent + 工具足够。
- **不做批量工具**：一次调用只处理一条记录，保证每一步可确认、可中止、可审计。
- **不做站点级 RBAC**：README 已明确"站点权限目前只到能切到哪些站点这一层"，本次不扩大这个范围。
- **不做 Anthropic / Responses 协议的 tool 与流式支持**：一期只做 OPENAI（§4.5），这两个协议下的智能体**在 copilot 页面上不可选**。接缝留在一个 `@Component` 上，将来加协议不动其它任何文件。现有"试聊"功能三种协议**照常可用**，不受影响。
- **不做站点文件的整文件覆盖写入**（§4.6.3）：只给精确替换 + 新建。整文件覆盖没有乐观锁，会无声覆盖掉并发改动，且用户看不出改了哪几行。
- **不往 `template/` 里放备份文件**（§4.6.4）：会被 `copyStatic`/`copyAssets` 拷进 `www/` 跟着发布出去。回滚靠 `ai_chat_message` 里留存的 `oldText`。
- **不做模板语法速查的自动生成**：一期按 §3+§6 手工整理切片。等真出现"规范与实现漂移"再考虑从 `TagRegistry` + 校验器反射生成。
