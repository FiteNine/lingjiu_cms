# 后端 CMS 全量评审报告（2026-10-03）

## 1. 评审范围与方法

**目标仓库**：`backend/`（独立仓库，远端 `origin = https://github.com/FiteNine/lingjiu_cms.git`）

**评审对象**：backend 目录下 CMS 后端与后台管理前端，**排除「全站静态化」与「自定义标签」**。

**排除内容**（按功能界定，非按文件）：
- `src/main/java/com/lingjiuw/cms/module/cms/publish/**`（112 个文件 / 约 19400 行）：一键全站静态化引擎、模板编译器、自定义标签（`publish/template/tag/**`）、发布数据 Provider、预览服务；
- 仅供该引擎使用的迁移与 SQL 片段。

**已覆盖**：
- `common/**`（统一响应、异常、JWT、Security、站点上下文、路径边界、MyBatis-Plus 配置）：逐行；
- `config/**`、`annotation/**`、`aspect/**`：逐行；
- `module/system/**`（登录/用户/角色/菜单/字典/操作日志）：逐行；
- `module/cms/**` 非 publish 部分（站点、站点目录、分类、标签、内容类型、字段定义、通用内容、导航菜单、媒体、统计、公开接口、发布选项、主题）：逐行；
- `module/ai/**`（服务商、智能体、协议适配、Copilot 工具层与审计）：逐行，其中 3 个协议适配器与 DTO 为抽样；
- `src/main/resources/mapper/**` 共 16 个 XML：逐条语句；
- `src/main/resources/db/migration/**` 共 28 个迁移：重点核对约束、索引、数据订正；
- `admin-ui/src/**`：按 rule.json 前端约定做定向检查（权限标识一致性、请求层封装、站点请求头、敏感内容处理）。

**未覆盖 / 抽查**：
- publish 引擎本体（按要求排除）；
- `src/test/**` 55 个测试文件（仅作为行为佐证抽查，未评审测试质量）；
- `admin-ui` 约 12000 行未逐行通读，只对权限标识、`api/request.ts`、路由与站点切换做了检查；
- AI 协议适配器（Anthropic / Responses / OpenAI 非工具流式）为抽样阅读。

**评审依据**：`.opencodereview/rule.json` 的 9 组规则 + `backend/AGENTS.md`（SQL 一律在 XML、迁移只增不改）+ `backend/README.md`（统一响应、权限模型、多站点隔离）。

## 2. 结论摘要

| 严重级 | 数量 | 说明 |
| --- | --- | --- |
| 高 | 3 | 影响构建完整性或构成真实攻击面 |
| 中 | 10 | 安全性、数据一致性、性能与错误处理缺口 |
| 低 | 8 | 文档漂移、可读性与体验问题 |

总体判断：分层清晰，`common` 与 `cms` 的边界收口做得比多数同规模项目扎实——Java 侧确实零 SQL、XML 里没有一处 `${}` 拼接、所有业务唯一索引都是 `where deleted = 0` 的部分索引、路径边界统一走 `SitePathBoundary`、前后端权限标识逐条对得上。主要问题集中在**凭据生命周期**（JWT 内嵌权限、上传目录公开）、**内容树约束缺失**与**几处未兜住的数据库异常**。

## 3. 高优先级发现

### H-1 操作日志页面源码未纳入版本控制，新克隆仓库前端构建失败

- 位置：`.gitignore:3`、`admin-ui/src/router/index.ts:112`、`admin-ui/src/layout/index.vue:54`、`admin-ui/src/views/system/logs/index.vue`
- 类别：bug / 构建完整性；严重级：高

`.gitignore` 第 3 行的 `logs/` 是「任意层级名为 logs 的目录」，它同时命中了业务源码目录 `admin-ui/src/views/system/logs/`。实测 `git status --ignored` 显示 `!! admin-ui/src/views/system/logs/`，`git ls-files admin-ui/src/views/system/logs/` 为空——该目录从未入库。

后果：任何人新克隆 backend 后 `npm install && npm run build` 会在 `router/index.ts:112` 的动态 import 处失败；操作日志页在其它机器上直接消失。当前开发机因为文件还在本地磁盘上，问题不会暴露。

建议：把忽略规则收敛为根级，并在需要时补一条显式反忽略：

```gitignore
/logs/
!admin-ui/src/views/system/logs/
```

### H-2 上传目录对外公开且放行 SVG，构成同源存储型 XSS

- 位置：`src/main/java/com/lingjiuw/cms/common/security/SecurityConfig.java:55`、`src/main/java/com/lingjiuw/cms/module/cms/service/MediaService.java:35-37`、`src/main/java/com/lingjiuw/cms/config/WebConfig.java:25-36`
- 类别：security；严重级：高

`/uploads/**` 被 `permitAll()` 放行，并由 `WebConfig` 直接映射为静态资源目录；`MediaService.ALLOWED_EXT` 明确允许 `svg`。Spring 的资源处理器按扩展名推断 Content-Type，`.svg` 会以 `image/svg+xml` 返回，且没有 `Content-Disposition` 或 CSP 沙箱头。

触发路径：任何持有 `cms:media:upload` 的账号（角色越权场景下即普通编辑）上传一个内嵌 `<script>` 的 SVG，得到形如 `/uploads/2026/10/<uuid>.svg` 的地址；诱导管理员或同站点用户直接打开该地址，脚本即在 CMS 同源下执行，可以读取 `localStorage` 里的 JWT。上传接口只校验扩展名、不校验真实文件类型（见 M-9），所以伪装成本极低。

建议（任选其一或叠加）：
- 不允许 `svg`，或对图片类上传做魔数校验后用服务端生成的名字回写；
- 对 `/uploads/**` 统一加 `Content-Disposition: attachment`（图片场景改走独立域名/端口）；
- 至少加 `X-Content-Type-Options: nosniff` 与 `Content-Security-Policy: sandbox` 响应头。

### H-3 停用/删除用户、回收权限后，旧 token 在有效期内仍完全可用

- 位置：`src/main/java/com/lingjiuw/cms/common/security/JwtAuthenticationFilter.java:29-48`、`src/main/java/com/lingjiuw/cms/module/system/service/UserService.java:101-117`、`src/main/resources/application.yml:63-66`
- 类别：security；严重级：高

鉴权完全基于 token 内的 `perms` claim：`JwtAuthenticationFilter` 拿到 `LoginUser` 后直接用它构造 `GrantedAuthority`，**从不回查数据库**；`application.yml` 的 `cms.jwt.expire-hours` 为 12，README 也明确「无黑名单」。因此：

- 管理员停用某账号（`UserService.updateStatus`）或删除用户后，该账号在最长 12 小时内仍能继续调用全部有权限的接口；
- 回收角色的菜单权限后同样不即时生效；
- 而 `/api/auth/profile` 是实时回查数据库的，于是出现「界面已经看不到按钮了，接口却还能调通」的不一致。

这不是理论问题：`@PreAuthorize` 判定用的就是 token 里的 authorities。

建议（按成本排序）：有效期内每请求回查一次 perms（可加短 TTL 缓存）；或把 `expire-hours` 降到 1~2 并配合刷新；或在 `sys_user` 上引入 `token_version`/`perms_version` 并写进 token，改权限/停用时自增。

## 4. 中优先级发现

### M-1 内容树（TREE 类型）缺少父子环校验与子节点检查

- 位置：`src/main/java/com/lingjiuw/cms/module/cms/service/ContentService.java:220`（`applyRequest` 直接写 `parentId`）、同文件 `184-190`（`delete`）
- 类别：bug；严重级：中

`CategoryService.checkParent` 与 `CmsMenuService.checkParent` 都实现了「不能选自身 / 不能选自身后代」的环校验，内容侧完全没有：`ContentService.applyRequest` 只做 `parentId < 0 ? 0 : parentId` 的归一。后果与分类里已经规避的完全一致——A、B 互指后两项都挂不到根上，`tree` 遍历里整条子树静默消失；把父指向别的站点/别的类型的内容也不会被拒绝。另外 `delete` 不检查子内容，删父后子内容变成孤儿行。

建议：复用分类那套 `checkParent`（校验 parent 属于当前站点、同类型、非自身及后代），删除前增加子节点检查并给出中文提示。

### M-2 分类树/标签页的内容计数是全表聚合

- 位置：`src/main/resources/mapper/cms/CmsCategoryMapper.xml:7-13`、`src/main/resources/mapper/cms/CmsTagMapper.xml:7-13`、`src/main/java/com/lingjiuw/cms/module/cms/mapper/CmsCategoryMapper.java:13-20`
- 类别：performance；严重级：中

两条统计都只按 `category_id` / `tag_id` 分组，**不带 `site_id` 条件**，每次打开分类树、标签列表都要对整张关联表加 `cms_content` 做 `count(distinct)`。代码注释已自认这一点。多站点、十万级内容后，这个页面会先于其它接口变慢。

建议：加 `siteId` 参数并补 `and cc.site_id = #{siteId}`（或先按站点过滤 `cms_content`），顺带给 `cms_content_category(site_id)` 建索引。

### M-3 站点目录字段冲突会以 500 抛出

- 位置：`src/main/java/com/lingjiuw/cms/module/cms/service/SiteService.java:147-164`、同文件 `322-329`；约束见 `src/main/resources/db/migration/V20261007090001__cms_integrity_fix.sql:43`
- 类别：bug / error handling；严重级：中

迁移为 `cms_site(root_dir)` 建了部分唯一索引，但 `SiteService.create/update` 只兜了 `code` 的重复（`checkCodeUnique`），`root_dir` 撞车（两个站点选同一个目录，或编辑时把目录改成别人已用的）会抛 `DuplicateKeyException`，被 `GlobalExceptionHandler` 兜成 500「系统繁忙」，用户拿不到任何可操作信息。这与项目自己的口径不符——`CategoryService.create`、`CmsContentTypeService.create`、`AiProviderService.create` 都显式 `catch (DuplicateKeyException)` 转中文提示。

建议：在 `create/update` 里 catch 并返回「该站点目录已被其它站点占用」。

### M-4 新增用户/角色/内容类型等写接口存在查重竞态

- 位置：`src/main/java/com/lingjiuw/cms/module/system/service/UserService.java:151-158`、`src/main/java/com/lingjiuw/cms/module/system/service/RoleService.java:92-99`、`src/main/java/com/lingjiuw/cms/module/system/service/DictService.java:114-121`
- 类别：bug / error handling；严重级：中

这些地方都是「先 `selectCount` 再插入」，并发下会同时通过检查，随后撞数据库唯一索引抛 `DuplicateKeyException` → 500。同仓库的 `CategoryService.create:57-62`、`ContentTypeService.create:88-93` 已经是正确写法，属实现不一致。

建议：统一改成「先查给出友好提示 + catch `DuplicateKeyException` 兜底」，与分类/类型保持一致。

### M-5 `/api/cms/stats` 无权限校验

- 位置：`src/main/java/com/lingjiuw/cms/module/cms/controller/StatsController.java:18-21`
- 类别：security；严重级：中

控制器缺 `@PreAuthorize`，任何登录用户（哪怕没有任何 CMS 权限）都能读到内容总数、发布/草稿数、分类/标签/媒体数、最近内容列表与全站用户数。rule.json 的 Controller 规则第 1 条要求敏感读必须带权限标识。对比 `ContentTypeController.listOptions`、`AiAgentController.options` 是 README 明确写过的「登录即可读」，stats 没有这个约定。

建议：补 `@PreAuthorize("hasAuthority('cms:stats:list')")` 并在 `sys_menu` 补种子，或并入已有的 `cms:content:list`。

### M-6 删除媒体不检查引用关系

- 位置：`src/main/java/com/lingjiuw/cms/module/cms/service/MediaService.java:95-109`
- 类别：bug / 数据一致性；严重级：中

`delete` 直接逻辑删行并 `deleteQuietly` 删磁盘文件，不检查该 URL 是否仍被 `cms_content.data`（IMAGES/FILES 字段存的是 url 字符串）或站点文件引用。删掉后内容侧静默变成死链，且没有回收站（README 明确未做）。

建议：至少扫描 `cms_content.data::text like '%url%'` 做一次引用提示；或把磁盘删除改为延迟/软删（保留文件，仅下行）。

### M-7 媒体上传只校验扩展名，不校验内容类型

- 位置：`src/main/java/com/lingjiuw/cms/module/cms/service/MediaService.java:55-74`
- 类别：security；严重级：中

`extensionOf(originalName)` 取后缀做白名单，随后 `file.transferTo` 原样落盘，完全不校验真实字节（无魔数校验、无图片解码）。与 H-2 叠加可上传任意载荷的文件并公开访问。

建议：图片类用 `ImageIO.read` 或魔数校验；非图片类保持白名单并配合 H-2 的响应头策略。

### M-8 登录接口无限流与失败锁定

- 位置：`src/main/java/com/lingjiuw/cms/module/system/service/AuthService.java:33-53`
- 类别：security；严重级：中

`login` 无失败计数、无验证码、无延迟，`sys_oper_log` 只做事后记录。README 把 Redis 列为「刻意未包含」，因此这是已知取舍，但后台管理入口直接暴露在公网时风险实际存在。

建议：应用内内存级失败计数（同一用户名/IP 指数退避）即可，无需引入 Redis；或至少对连续失败写告警日志。

### M-9 站点解析失败静默回落，公开接口无法区分「无权访问」与「未指定」

- 位置：`src/main/java/com/lingjiuw/cms/common/site/SiteInterceptor.java:33-41`、`src/main/java/com/lingjiuw/cms/module/cms/service/SiteService.java:92-130`
- 类别：maintainability / 信息安全；严重级：中

请求头/参数里的 `siteId` 只要解析不出、站点不存在或当前用户不可访问，一律回落默认站点（代码注释说明是有意为之，避免删站点后后台整体卡死）。代价是：`/api/public/articles?siteId=<别的站点>` 不会报错，而是返回默认站点内容；后台也无提示。对公开接口而言，调用方无法区分「参数被忽略」和「参数生效」。

建议：内部请求（后台）保留回落；`/api/public/**` 显式指定的 `siteId` 不可访问时返回业务错误，避免静默串站内容。

### M-10 公开分类接口返回停用分类与状态字段

- 位置：`src/main/java/com/lingjiuw/cms/module/cms/controller/PublicController.java:46-49`、`src/main/java/com/lingjiuw/cms/module/cms/service/CategoryService.java:34-43`
- 类别：bug / 信息暴露；严重级：中

`tree()` 按 `siteId` 过滤但不过滤 `status`，公开接口因此把停用分类连同 `status` 字段一起吐给未登录调用方。文章列表走的是 `status = 'PUBLISHED'` 口径，分类口径与之一致性不足。

建议：为公开接口单独提供「仅启用分类」的读取方法，或给 `tree` 加 `onlyEnabled` 参数。

## 5. 低优先级发现

### L-1 迁移文件引用的评审报告不存在

`src/main/resources/db/migration/V20261007090001__cms_integrity_fix.sql:6-7` 指向 `backend/docs/code-review/fix-reports/09-mapper-xml-and-sql.md`，但仓库内 `backend/docs/` 只有 3 个文件，该路径（及其父目录）并不存在。属悬空引用，后续排查约束来历时会被误导。建议补文件或把注释改为自包含说明。

### L-2 README 与实现不一致（站点目录能力）

`README.md`「设计取舍」写「站点目录页只编辑磁盘上已有的文本文件（不新建、不上传、不删除、不重命名）」，而 `SiteFileController.java:56-79` 已提供新建文件/文件夹与删除，同 README 的接口一览也列了这些端点。建议更新取舍段落。

### L-3 公开详情接口在读路径上写库

`src/main/resources/mapper/cms/CmsContentMapper.xml:741-748` 的 `increaseViewCount` 在 `PublicArticleService.detail` 里被同步调用，公开 GET 请求会写 `cms_content`。XML 注释已说明契约要求改为独立埋点接口但未实现。建议至少改为异步或独立接口，避免读接口被刷导致行锁竞争。

### L-4 仪表盘用户数与站点无关，容易误读

`StatsService.java:49` 的 `userTotal` 统计全库用户，却与站点维度的其它指标并列展示。建议改成站点授权用户数，或在前端标注「系统级」。

### L-5 JWT 解析失败只记 debug 日志

`JwtAuthenticationFilter.java:41-44` 对无效/过期 token 只 `log.debug`。fail-safe 行为正确，但线上按 info 级别运行时完全看不到异常 token 的痕迹（可能是攻击尝试）。建议对非过期类失败用 `log.info` 并带上 URI。

### L-6 `cms_content_type` / `cms_field` 的 options 以纯文本存 ENUM 选项，缺少约束

`FieldSaveRequest.options` 是任意字符串，`ContentService.enumValues` 用「逗号分割、冒号分标签」的自定义语法解析（`ContentService.java:627-643`）。选项里若出现多余逗号或全角冒号会静默改变语义。建议在保存字段定义时校验 ENUM/ENUM_MULTI 的 options 可被解析且无重复值。

### L-7 仓储内保留可直接使用的默认凭据

`src/main/resources/application.yml:8-9` 内嵌 `cms/cms123456`，`:63-66` 内嵌 `dev-only-change-me-32-bytes-key!`。注释已提示用环境变量覆盖，但默认值可运行意味着很容易被原样带到生产。建议改为 `${CMS_JWT_SECRET}` 无默认值时启动即失败（`JwtService` 已有长度校验，顺着这个思路即可）。

### L-8 上传文件按时间目录共享，跨站点不隔离

`MediaService.java:66-68` 所有站点共用 `uploads/yyyy/MM/`，`cms_media` 行才带 `site_id`。不同站点复制同一 URL 后，一方删除媒体行会连带删掉共享的物理文件。建议路径里带上 `siteId`。

## 6. 值得肯定之处

- **SQL 约定执行彻底**：Java 侧零 SQL 注解、零 `Wrapper.last/apply`；16 个 XML 全文没有一处 `${}` 拼接，所有能拼的都在 `#{}`。
- **逻辑删除口径统一**：所有业务唯一索引都是 `where deleted = 0` 的部分索引，XML 语句逐条手写 `deleted = 0`，没有踩「逻辑删后无法重建同 slug」这个常见坑。
- **路径边界收口**：`SitePathBoundary` 把「相对路径 → 站点内绝对路径」集中一处，`resolveUnder/checkName/checkSegment/assertNoSymlink/assertPathLength` 复用在站点目录与文件管理，并且注释如实说明了 TOCTOU 的残留窗口。
- **多站点隔离到位**：内容/分类/标签/媒体/菜单/类型的读写全部带 `SiteContext.siteId()`，关联 id 还有 `requireOwnedCategories/requireOwnedTags` 这类「跨站点 id 一律当不存在」的二次校验，`PublicArticleService` 批量取分类/标签时也显式过滤站点。
- **权限标识前后端一致**：后端 70 余个 `hasAuthority(...)` 与前端 `v-permission` 逐条比对无差异，且 `sys_menu` 种子里都能找到对应点；AI 工具层的 `ToolPermission` 明确拒绝照抄前端的 admin 旁路。
- **API Key 不回明文**：`ProviderVO.mask` 只回掩码，编辑时留空表示不修改，`@OperLog` 的脱敏正则覆盖 password/secret/token/apiKey。
- **AI 工具层设计克制**：只读并发、写串行（并发度 4，避免占满 Hikari），危险操作要求人工确认，WRITE/DESTRUCTIVE 才写审计，READ 不污染操作日志。

## 7. 建议的修复顺序

1. H-1（一行 .gitignore）与 H-3（鉴权回查）——影响面最大、成本最低；
2. H-2 + M-7（上传链路）——同一处，合并处理；
3. M-1（内容树环校验）、M-3/M-4（数据库异常兜底）——数据一致性与可运维性；
4. M-2（计数查询加站点条件）、M-5（stats 权限）——性能与权限面；
5. 其余 Low 项可在后续版本顺带清理。

---

评审依据文件：`.opencodereview/rule.json`、`backend/AGENTS.md`、`backend/README.md`。
本次评审仅生成本报告，未改动任何业务代码。