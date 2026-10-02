# 批 09 修复报告：Mapper XML 与迁移脚本（SQL）

> 补写说明：本文件由 2026-10-03 全量评审的修复轮补写。迁移脚本 `V20261007090001__cms_integrity_fix.sql` 头部（第 6-7 行）引用了本路径，但当时文件与其父目录都不存在，属悬空引用（评审发现 L-1）。现依据该迁移文件的注释与语句整理本报告，内容不超出该迁移实际包含的范围。

## 背景

批 09 的审查对象是 Mapper XML 与迁移脚本。审查中「必须改旧文件」的部分与迁移约定冲突：`backend/AGENTS.md` 要求已执行的迁移保持提交时的字节不变（只增不改），需要调整就在新文件里写 `ALTER`。因此本批可修项全部收敛到新迁移 `V20261007090001__cms_integrity_fix.sql`：

- 版本号 `20261007090001`：本机时钟（2026-10-03）落后于库里最后一个迁移（`20261006090001`），直接取本机时间会得到更小的版本号；沿用既有 `09:00:0X` 的排布，取最后一个迁移之后的下一天。
- 迁移内容只包含新增索引 / 列 / 约束与幂等的数据订正，前面 24 个迁移文件一个字节都不动。
- 本批未在 Java 侧写任何 SQL（SQL 一律写在 XML / 迁移里）。

## 一、已随迁移落库的修复

### 1. 缺失索引（迁移第 1 节）

| 索引 | 表（列） | 针对的查询 / 原因 |
| --- | --- | --- |
| `idx_sys_user_site_site` | `sys_user_site (site_id)` | `SysUserSiteMapper.deleteBySiteId` 按 site_id 删；联合主键 `(user_id, site_id)` 的最左列用不上 |
| `idx_sys_role_menu_menu` | `sys_role_menu (menu_id)` | `SysRoleMenuMapper.deleteByMenuId` 同理，主键最左列是 role_id |
| `idx_cms_content_type_publish` | `cms_content (site_id, type_code, status, publish_time desc)` | 类型列表查询（static-publish.md §6.3）；原 `idx_cms_content_publish` 保留给 `type='all'` 的跨类型聚合 |
| `idx_cms_content_expire` | `cms_content (expire_time) where deleted = 0 and status = 'PUBLISHED' and expire_time is not null` | 每分钟到期扫描（§8.8） |
| `idx_cms_content_rank_day` | `cms_content (site_id, type_code, view_count_day) where deleted = 0 and status = 'PUBLISHED'` | 日榜（§9.4） |
| `idx_cms_content_rank_week` | `cms_content (site_id, type_code, view_count_week) where deleted = 0 and status = 'PUBLISHED'` | 周榜（§9.4） |
| `idx_cms_media_derive_status` | `cms_media (derive_status) where deleted = 0` | 后台按派生状态筛「失败待重试」的媒体 |

### 2. 新增唯一索引（迁移第 1 节末）

- `uk_cms_site_root_dir`：`cms_site (root_dir) where deleted = 0`。两个站点填同一个 `root_dir` 会往同一物理目录发布、互相覆盖文件；应用层 `SiteService` 原来只校验 code 唯一（`checkCodeUnique`），这里由数据库兜住。
- `uk_sys_dict_item_value`：`sys_dict_item (type_id, value) where deleted = 0`。同一字典类型下 value 不能重复，否则下拉与字典查询会出现两条一样的选项。

### 3. 审计字段补齐（迁移第 2 节）

`V1__init.sql` 约定所有业务表统一带 `create_by` / `create_time` / `update_by` / `update_time` / `deleted`。本迁移只加列：

- `cms_publish_task`：新增 `update_by bigint`、`update_time timestamp not null default now()`。
- `cms_site_publish_option`：新增 `create_by bigint`、`create_time timestamp not null default now()`、`update_by bigint`、`update_time timestamp not null default now()`。

注意：这两张表的实体还没有对应字段，`MetaObjectHandler` 填不进去，见「三、需要 Java 侧同步」。

### 4. 新增约束与注释（迁移第 3 节）

约束原则：① 只加「应用层已经在拦同一件事」的约束，避免把现在能成功的写入变成 500；② 应用层完全没校验的列（`ai_agent.reasoning_effort`、`cms_publish_task.trigger`、`cms_menu_item.visible` 等）不加。

- `cms_media.derive_status`：先回填存量 NULL → `PENDING`，再设 `default 'PENDING'` + `not null` + `check in ('PENDING','DONE','FAILED')`。
- `cms_media.width` / `height`：`check` 为 null 或大于 0，0 / 负数会让 `[field:cover.width/]` 渲染出错。
- `ai_provider.protocol` ∈ `('OPENAI','ANTHROPIC','RESPONSES')`；`ai_provider.status` ∈ `(0,1)`。
- `ai_agent.temperature` 在 0~2；`top_p` 为 (0,1]（不按列注释的 0.95 上限——那只是建议区间，按 0.95 兜会拒掉现在合法的保存）；`max_tokens` 在 1~393216；`thinking` / `json_output` / `status` ∈ `(0,1)`。
- `cms_site.root_dir`：非空、不以 `/` 开头、不含 `..` 上跳（与 `SitePathBoundary.resolveUnder` 同规则的纵深防御）。
- `cms_publish_lock.expire_time`：收紧为 `not null`；补列注释与索引注释，说明「过期即视为无效，抢锁必须先删除过期行再 insert」（`uk_cms_publish_lock_site` 只对 `deleted = 0` 生效，不能只按 insert 冲突理解）。

### 5. 幂等性与数据订正（迁移第 4 节）

- 4.1 默认站点：`code='default'` 存在但 `is_default=0` 时补回 1，避免全库零默认站点导致 `SiteInterceptor` 回落失败。
- 4.2 editor 角色补按钮权限：按「它已持有的菜单下的按钮」补齐内容管理按钮（分类 / 标签 / 媒体），不写死菜单 id。
- 4.3 demo 站书籍 `author_id` / `author_name`：原迁移按 `slug = data->>'author'` 关联恒不成立（存的是中文姓名），改为按标题（姓名）关联。
- 4.4 demo 站书籍 `data.tags` 与 `cms_content_tag` 对齐：按 article 的口径重算 jsonb 数组，并重建这部分的 `cms_content_index` 行。
- 4.5 demo 站点击排行排序字段：`pages.static` 的 `orderby` 从 `viewCount desc` 改为 `viewCountWeek desc`，与 §7.2.1 契约一致。
- 4.6 demo 站内容类型序号收敛到规范值（article=1 … single=8），干净库上是空操作。
- 4.7 站点管理菜单 41 的授权继承：按「当前持有父菜单 40 的角色」补齐 41，避免历史授权静默丢权限。

## 二、只增不改约束下搁置的项（未修改任何既有迁移）

迁移头部说明：审查清单里「必须改旧文件」的三类问题不做修改——

1. **文件名不合约定**：历史迁移存在不符合 `backend/AGENTS.md` 的 `V<年月日时分秒>__<业务代码>.sql` 约定的文件名（如以纯序号命名的 `V1__init.sql`、`V2__seed.sql`）。已执行的迁移改名会被 Flyway 当成新迁移或对不上校验记录，故保留原样。
2. **硬编码主键**：种子与授权数据里直接写死 id（例如菜单 / 角色授权写死 `role_id=1`）。修正需要改旧文件或重写数据；本批只在新增迁移里避免继续写死（4.2 按关系推导、4.7 按存量授权继承）。
3. **注释与实现不符**：旧迁移中注释描述与语句实际行为不一致的地方。属文档层修正，改注释同样会破坏校验和，故搁置。

以上三项不影响运行正确性；后续若要处理，只能新增迁移做数据 / 结构订正，或另行商议「只增不改」的例外。

## 三、需要 Java 侧同步的部分

迁移只加列，MyBatis-Plus 的 `MetaObjectHandler` 只会填实体上声明了 `@TableField(fill = ...)` 的字段（`strictInsertFill` / `strictUpdateFill`）。当前：

- `CmsPublishTask` 没有 `updateBy` / `updateTime` 字段 → 新增的 `update_by` / `update_time` 列不会被维护（`update_by` 恒为 NULL）。
- `CmsSitePublishOption` 没有审计字段 → 四个新列同样不会被维护。

需要同步加上（`FieldFill.INSERT_UPDATE`，与其它实体一致）；本次修复轮未包含实体改动。

其余修复对 Java 侧透明：索引、唯一索引、CHECK 约束不需要代码同步；数据订正只动数据；应用层原有校验保持不变，数据库只做兜底。

---

依据：`src/main/resources/db/migration/V20261007090001__cms_integrity_fix.sql` 的注释与语句。评审背景见 `docs/code-review/20261003-backend-full-review.md`（2026-10-03 全量评审，L-1）。
