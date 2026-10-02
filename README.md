# Lingjiuw CMS 企业级内容管理脚手架

简体中文 | [English](README.en.md)

**后台管理界面与 CMS 系统绑定在一起**：启动一个 Spring Boot 进程，浏览器打开 `http://localhost:8081/` 就是后台管理界面，REST API 在 `/api/**`，接口文档在 `/swagger-ui.html`。前端构建产物直接打包进 Spring Boot 静态资源目录，无需 Nginx、无需第二个服务。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言/运行时 | Java 21 (LTS) |
| 框架 | Spring Boot 3.5.x（Web / Validation / Security / AOP） |
| ORM | MyBatis-Plus 3.5.x（分页插件 + 审计字段自动填充 + 逻辑删除） |
| 数据库 | PostgreSQL 16（Flyway 版本化迁移） |
| 认证 | Spring Security + JWT（jjwt，HS256，无状态） |
| 接口文档 | springdoc-openapi 2.8.x（Swagger UI） |
| 前端 | Vue 3.5 + TypeScript + Vite + Element Plus + Pinia + Vue Router + Axios |
| 富文本 | wangEditor 5 |
| 构建 | Maven 3.9+ / npm |

## 目录结构

```
backend/
├── pom.xml                      # Maven 构建
├── docker-compose.yml           # 新机器一键起 PostgreSQL 16
├── admin-ui/                    # 后台管理前端（Vue3 + Element Plus）
│   └── vite.config.ts           # 构建产物输出到 src/main/resources/static
├── src/main/java/com/lingjiuw/cms/
│   ├── common/                  # 统一响应、异常、JWT、Security、MyBatis-Plus 配置
│   ├── config/                  # Web/SPA 回退/Swagger/Jackson 配置
│   ├── annotation/ + aspect/    # @OperLog 操作日志切面
│   └── module/
│       ├── system/              # 系统管理：登录、用户、角色、菜单、字典、操作日志
│       ├── cms/                 # 内容管理：站点、分类、标签、内容、媒体、统计、公开接口
│       └── ai/                  # AI 管理：服务商（三种协议适配）、智能体配置与试聊
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/            # Flyway：V1 建表、V2 种子数据
│   └── static/                  # 前端构建产物（npm run build 生成，不入库）
├── uploads/                     # 媒体上传目录（运行时生成）
└── sites/                       # 站点目录根：站点管理里选择/新建的站点目录都在它下面（运行时生成），
                                 #   每个站点目录下固定有 data/（静态资源）与 template/（站点模板）
```

## 快速开始

### 1. 前置条件

- JDK 21、Maven 3.9+
- Node 20+（构建后台管理前端）
- PostgreSQL 16（已有实例或用 Docker）

### 2. 准备数据库（二选一）

**A. 复用已有 PostgreSQL 16 实例**（本仓库开发机采用的方式）：

```bash
docker exec -it <你的pg16容器> psql -U postgres
```

```sql
create user cms with password 'cms123456';
create database lingjiuw_cms owner cms;
```

**B. 用脚手架自带 compose 起独立实例**（新机器推荐）：

```bash
cd backend
docker compose up -d     # postgres:16，端口 5432，库/用户与 application.yml 一致
```

连接信息在 `src/main/resources/application.yml`，可改配置或用环境变量覆盖：

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lingjiuw_cms
SPRING_DATASOURCE_USERNAME=cms
SPRING_DATASOURCE_PASSWORD=cms123456
```

### 3. 构建后台管理前端

```bash
cd backend/admin-ui
npm install
npm run build            # 产物输出到 backend/src/main/resources/static
```

### 4. 启动

启动前必须提供 JWT 密钥（`cms.jwt.secret` 无默认值，未设置或不足 32 字节会直接启动失败）：

```bash
# 必须设置：CMS_JWT_SECRET，至少 32 字节（示例值仅用于本地开发）
export CMS_JWT_SECRET='local-dev-only-secret-at-least-32-bytes'

cd backend
mvn spring-boot:run
# 或打包后运行：mvn -DskipTests package && java -jar target/lingjiuw-cms-1.0.0.jar
```

> Windows PowerShell 用 `$env:CMS_JWT_SECRET='local-dev-only-secret-at-least-32-bytes'` 设置。

首次启动自动执行 Flyway 迁移（建表 + 种子数据）。

### 5. 访问

| 入口 | 地址 |
| --- | --- |
| 后台管理界面 | http://localhost:8081/ |
| Swagger 接口文档 | http://localhost:8081/swagger-ui.html |
| 公开内容接口 | http://localhost:8081/api/public/articles |
| 默认账号 | `admin / admin123`（生产环境务必修改） |

> 端口占用（如本机 8080 被 Jenkins 占用）时改 `application.yml` 的 `server.port`，并同步改 `admin-ui/vite.config.ts` 的代理目标。

### 6. 前端开发模式（可选）

```bash
cd backend/admin-ui
npm run dev              # http://localhost:5173，/api 与 /uploads 代理到 8081
```

## 功能清单

| 模块 | 功能 |
| --- | --- |
| 认证 | 登录（JWT）、当前用户、修改密码、登出 |
| 系统管理 | 用户（角色分配、站点授权/重置密码/停用）、角色（菜单权限分配）、菜单（目录/菜单/按钮）、字典（类型+项）、操作日志（自动记录） |
| 内容管理 | 分类（树形）、标签、通用内容（草稿/发布/下线、置顶、推荐、分类、标签、封面、富文本）、媒体库（本地上传）、仪表盘统计 |
| 动态建模 | **内容类型**（普通/单页/层级、URL 规则、详情与列表模板、分页、SEO 字段、jsonb 选项）、**字段定义**（19 种字段类型、索引/搜索/原样输出开关、ENUM 选项、排序） |
| 通用内容 | **全部内容的唯一入口，也是引擎真正渲染的那张表**：按类型分页、动态字段表单（按字段定义生成控件）、正文（富文本/Markdown）、分类/标签/置顶/推荐、发布/下线；保存时**同事务重建字段索引表与分类/标签关联**（`where` / `orderby` / `facet` 只读索引表）。文章是它的内置类型 `article`，不再有独立的文章表 |
| 导航菜单 | 站点导航（`cms_menu` / `cms_menu_item`）的菜单与菜单项树 CRUD，8 种菜单项类型（分类/内容/链接/类型/标签/归档/占位/作者）。注意与系统菜单 `sys_menu` 不是一回事 |
| 站点发布 | **一键全站静态化**（预演 → 发布 → 批次记录）、**预览站点**（把已发布的 `www/` 用只读端口原样开出来看）、**发布选项**（站点级键值配置，含 `url.*` / `page.*` / `seo.*` / `pages.static` 与站点自定义项）、主题列表与切换 |
| 站点管理 | 站点配置（名称/标识/域名/Logo/描述/SEO 关键词与描述/ICP 备案号/联系方式/**语言/协议/主题/OG 图/默认封面/统计代码**/状态）、站点目录（在配置的根目录内浏览与新建文件夹，用于存放该站点的网站文件；保存站点时自动补齐 `data`（站点静态资源）与 `template`（站点模板）两个子目录） |
| 站点目录 | 浏览当前站点（右上角切换器选中的站点）的网站文件目录：左侧文件树，点击文件后在右侧编辑或预览；文本文件按 UTF-8 读写并保存，图片以 data URL 预览，其余类型只显示文件信息。入口：`站点 → 站点目录` |
| 多站点 | 系统内置一个「默认站点」，右上角可切换站点（只列出当前用户被授权的站点），分类/标签/内容/媒体/统计全部按当前站点隔离 |
| AI 管理 | 服务商（三种协议、API Key 掩码、连通性测试）、智能体（模型参数、系统提示词、思考模式、试聊） |
| 公开接口 | 已发布文章分页/详情（按 slug，读内置类型 `article` 的内容）、分类树，按 `siteId` 取某个站点的内容，供门户/官网免登录消费 |

> **静态化发布**：`站点 → 发布中心` 里「预演」先算这一批会出哪些页面（不写盘），
> 再「一键全站静态化」把 `sites/<站点>/template/<主题>/` 的模板 + 数据库内容
> 渲染成 `sites/<站点>/www/` 下一套可直接托管的静态站，并顺带产出
> sitemap / feed / robots / 搜索索引 / `cms-site.json`。契约见
> [`docs/static-publish.md`](docs/static-publish.md)，
> 一次真实站点的反推交付见 [`docs/lingjiuw-site-reverse.md`](docs/lingjiuw-site-reverse.md)。

## 多站点

一个 CMS 里放多个站点，内容互不可见。

- **默认站点**：系统初始化时必有一个（`cms_site.is_default = 1`），请求没指定站点时全部落到它上面。默认站点不允许删除；数据库上用 `uk_cms_site_default` 兜住「至多一个默认站点」这个不变量。注意它由迁移直接插入，对应的站点目录（`cms.site.root-dir/default`）要到第一次在「站点管理」里保存该站点时才创建。
- **当前站点怎么定**：请求头 `X-Site-Id`（后台管理界面右上角切换器用）→ 请求参数 `siteId`（公开接口用）→ 默认站点。解析统一在 `common/site/SiteInterceptor`，业务层通过 `SiteContext.siteId()` 取用。站点 id 解析不出来或那个站点已经被删了，都落到默认站点——否则切到某站点后把它删掉，整个后台会一直报错。
- **哪些数据按站点隔离**：`cms_content` / `cms_category` / `cms_tag` / `cms_media`，各自带 `site_id`。用户、角色、菜单、字典、操作日志、AI 服务商与智能体是系统级的，不随站点走。
- **唯一约束**：`slug` 由全库唯一改为**站点内唯一**，所以两个站点可以各有一个 `news` 分类。
- **删除站点**：内容都挂着 `site_id`，站点删掉后这些内容就再也认不回来了，所以默认站点禁止删除、站点下还有内容时也禁止删除。
- **站点访问权限**：`sys_user_site` 绑定「用户 → 可切换访问的站点」，在「用户管理」编辑用户时勾选（不选 = 仅默认站点）。右上角的切换器只列绑定到的站点，`X-Site-Id` 指到没绑定的站点时回落到默认站点，所以前端拿着过期的站点 id 也不会把后台卡住。`admin` 角色不受绑定限制，看得到全部站点；绑定的站点都被删掉时同样退到默认站点，漏绑不会让某个用户没有站点可用。公开接口 `/api/public/**` 不设限，门户仍可按 `siteId` 读任意站点。判定统一在 `module/cms/service/SiteService#accessibleSiteIds`。
- **仍未做**：站点级的角色权限（如「在这个站点只能发文章、不能改分类」）。站点权限目前只到「能切到哪些站点」这一层，切进去之后能做什么仍由菜单权限决定。

## AI 模块（服务商 / 智能体）

后台入口：`AI管理 → AI服务商`、`AI管理 → 智能体`。

| 协议 | 请求 | base_url 示例（DeepSeek） | 鉴权 |
| --- | --- | --- | --- |
| `OPENAI` | `POST {base_url}/chat/completions` | `https://api.deepseek.com` | `Authorization: Bearer <key>` |
| `ANTHROPIC` | `POST {base_url}/v1/messages` | `https://api.deepseek.com/anthropic` | `x-api-key: <key>` |
| `RESPONSES` | `POST {base_url}/responses` | `https://api.deepseek.com` | `Authorization: Bearer <key>` |

- `ai_provider` 一行 = 一个服务商 × 一种协议，迁移里预置了 DeepSeek 三行，**API Key 需要在页面上填写**。
- 接入别家服务商：只要它兼容上述三种协议之一，在「AI服务商」里加一行即可，不用改代码；出现全新协议时，实现一个 `module/ai/protocol/AiProtocolClient` 交给 Spring 自动注册。
- 三种协议的字段差异（思考开关与强度、JSON 输出、system 提示词位置、Anthropic 的 max_tokens 必填）全部收敛在协议适配器里，智能体配置与协议无关。
- 已刻意不做的：`frequency_penalty` / `presence_penalty`（官方已标 deprecated 且传入不生效）、Anthropic 协议下的 JSON 输出（该协议无等价参数）。
- 安全边界：API Key 只回掩码（`sk-****8e3c`），编辑时留空表示不修改，操作日志里的 `apiKey` 自动脱敏，服务商被智能体引用时不允许删除。

三种协议的字段依据均来自 DeepSeek 官方文档，抓取快照与逐条出处见 `.tmp/deepseek-api-notes.md`。

## API 约定

- 统一响应包：`{ "code": 0, "message": "ok", "data": ... }`，`code = 0` 成功；业务错误 `code != 0`，`message` 为中文提示
- 分页请求 `page`（从 1 起）/ `size`；分页响应 `{ "records": [], "total": 0, "page": 1, "size": 20 }`
- 认证：`Authorization: Bearer <token>`；未登录返回 HTTP 401，无权限返回 HTTP 403
- 站点：请求头 `X-Site-Id: <站点 id>`，或请求参数 `siteId`；都不传取默认站点（见「多站点」）
- 时间格式：`yyyy-MM-dd HH:mm:ss`；Long 型 id 序列化为字符串，避免前端 JS 精度丢失

## 权限模型（RBAC）

`sys_user` ↔ `sys_user_role` ↔ `sys_role` ↔ `sys_role_menu` ↔ `sys_menu`

- `sys_menu.perms` 为权限标识（如 `cms:content:add`），按钮级
- 后端：Controller 方法上 `@PreAuthorize("hasAuthority('cms:content:add')")`
- 前端：`v-permission="'cms:content:add'"` 按钮级控制（`admin` 角色放行全部）
- 新增权限点：在 `sys_menu` 插入 BUTTON 记录 → 角色分配勾选 → 前端按钮加指令
- 站点访问独立于此：`sys_user_site` 决定用户能切到哪些站点，与菜单权限正交（见「多站点」）

## 常用配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8081` | 服务端口（同时服务后台管理页面） |
| `cms.jwt.secret` | 无默认值（必须设置） | JWT 密钥，启动前必须用环境变量 `CMS_JWT_SECRET` 提供（≥32 字节）；未设置或过短时启动失败 |
| `cms.jwt.expire-hours` | `12` | token 有效期（小时） |
| `cms.upload.dir` | `./uploads` | 媒体上传目录 |
| `cms.upload.url-prefix` | `/uploads` | 媒体访问 URL 前缀 |
| `cms.site.root-dir` | `./sites` | 站点目录白名单根：站点的网站文件目录只能是它下面的子目录 |
| `cms.preview.port` | `8090` | 「预览站点」的端口基数：真实端口 = 基数 + 站点 id（演示站 8096），只监听 `127.0.0.1` |

## 新增业务模块的步骤

1. `src/main/resources/db/migration/` 加 `V3__xxx.sql`（Flyway 迁移只增不改）
2. `module/<模块>/entity` 建实体（沿用审计字段 + `@TableLogic`），`mapper` 建 Mapper
3. `service` 写业务（抛 `BizException` 由全局处理器转统一响应），`controller` 暴露接口并加 `@PreAuthorize` + `@OperLog`
4. `sys_menu` 插入菜单/按钮记录并给角色授权
5. `admin-ui/src/views` 加页面，`src/api` 加接口封装

## 设计取舍（本期刻意未包含）

保持脚手架精简，以下能力留了扩展点但未实现：

- **Redis**：JWT 无状态，未引入缓存/黑名单；需要强制踢人时再加
- **对象存储**：媒体暂存本地磁盘，`cms_media.storage_type` 已预留 `OSS`
- **工作流/审批、评论、内容版本修订、回收站、定时发布**
- **动态菜单**：前端路由为静态定义，`sys_menu` 主要用于按钮级权限与授权管理；如需按角色动态生成菜单，可在 `/api/auth/profile` 返回菜单树后改造前端路由
- **AI 流式输出**：试聊走非流式（一次性返回），未做 SSE；需要打字机效果时再加工
- **AI 工具调用 / 知识库**：智能体只做模型参数与系统提示词配置，tools 与 RAG 未包含
- **AI 会话持久化**：试聊是零散接口，对话历史由前端持有，不入库
- **站点文件发布**：站点目录页直接操作当前站点的网站文件目录：可浏览、查看文本与图片预览、保存已存在的文本文件（UTF-8、≤2MB）、新建文件与文件夹、删除文件或空文件夹；不提供上传与重命名，图片等非文本文件只能预览不能在线编辑。内容与媒体进入站点目录仍走「发布中心」的一键静态化，不经过这个页面
- **站点级内容导入导出 / 站点间复制**：目前换站点只能用右上角切换后各自维护

## 接口一览（主要）

```
POST   /api/auth/login              登录
GET    /api/auth/profile            当前用户（含 perms）
PUT    /api/auth/password           修改密码
GET    /api/system/users            用户分页
PUT    /api/system/roles/{id}/menus 分配菜单权限
GET    /api/system/menus/tree       菜单树
GET    /api/system/dict/items?code= 字典项
POST   /api/cms/media               上传文件
GET    /api/cms/stats                仪表盘统计
GET    /api/cms/types                内容类型分页
GET    /api/cms/types/options        内容类型下拉（登录即可读）
POST   /api/cms/types                新增内容类型
GET    /api/cms/fields?typeCode=     某类型的字段定义
POST   /api/cms/fields               新增字段
GET    /api/cms/contents             通用内容分页（按类型/状态/关键字）
GET    /api/cms/contents/{id}        通用内容详情（含自定义字段）
POST   /api/cms/contents             新增内容（同事务重建字段索引）
PUT    /api/cms/contents/{id}/status 发布/下线
GET    /api/cms/menus                站点导航菜单（含菜单项树）
POST   /api/cms/menus/{id}/items     新增菜单项
GET    /api/cms/publish/options      站点发布选项（含 valueType 元数据）
PUT    /api/cms/publish/options      批量保存发布选项
DELETE /api/cms/publish/options      删除一条发布选项（删掉 = 回到引擎默认值）
GET    /api/cms/sites                站点列表（含站点目录的服务器绝对路径）
GET    /api/cms/sites/options        站点下拉选项（登录即可读，只含当前用户可访问的站点，右上角切换器用）
GET    /api/cms/sites/dirs           站点目录浏览（只在 cms.site.root-dir 内）
POST   /api/cms/sites/dirs           站点目录新建文件夹
GET    /api/cms/sites/files          当前站点的网站文件目录浏览（文件树）
GET    /api/cms/sites/files/content  读取站点目录下的文件（文本给原文，图片给 data URL）
PUT    /api/cms/sites/files/content  保存站点目录下的文本文件
POST   /api/cms/sites/files          新建站点文件
POST   /api/cms/sites/files/dirs     新建站点文件夹
DELETE /api/cms/sites/files          删除站点文件或空文件夹（文件夹须为空）
GET    /api/cms/themes               当前站点 template/ 下的主题列表
GET    /api/cms/publish/site/preview 预演：这一批会出哪些页面（不写盘）
POST   /api/cms/publish/site         一键全站静态化（当前站点；mode=full|incremental）
GET    /api/cms/publish/site/preview-url 预览站点：已发布产物的只读地址（另起一个端口，只监听 127.0.0.1）
GET    /api/cms/publish/site/tasks    最近的发布批次记录
GET    /api/ai/providers             AI 服务商分页（含协议筛选）
GET    /api/ai/providers/options     AI 服务商下拉（登录即可读）
POST   /api/ai/providers/{id}/test   服务商连通性测试（真实调一次模型）
GET    /api/ai/agents                智能体分页
POST   /api/ai/agents/{id}/chat      智能体试聊（无状态，前端带全量历史）
GET    /api/public/articles         已发布文章（免登录，可带 siteId；读内置类型 article 的内容）
GET    /api/public/articles/{slug}  文章详情（免登录，可带 siteId）
```
