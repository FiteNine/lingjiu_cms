# 全站静态化与自定义标签 — 设计规格 v2.2（覆盖率复核后补齐）

> 状态：**规格冻结，待实现**（v2.1 = v2 的半成品补齐版，§0.2 是本次的变更记录）
> v1 / v2 的语法部分只存在于本文件：代码里没有 `module/cms/publish` 包，没有任何标签名落地，
> 因此历次改名与语法新增**都是零迁移成本**的。这条前提在 §2.6 收敛决策之后不再成立——
> 一旦 `cms_content` 落库，字段名与参数名就进入"改动成本极高"区。
> 本文件是「模板作者」「主题作者」「实现者」之间的唯一契约。语法、参数、字段名、错误文案一经写入，改动成本极高；
> 有异议请在实现前提出，不要实现后再改。
>
> **目标仍然是通用建站底座**：不改一行 Java、不重启，只靠「后台建内容类型 + 写模板」，就能做出资讯门户、企业官网、
> 行业产品站、案例作品集、文档知识库、小说站、图库、下载站、招聘站、FAQ、多语言外贸站、清单型电商展示等各类站点。
> **§14 是逐站型的覆盖自检表，也是本文件是否达标的最终判据；§14.7 是达标结论（78/78 行可表达：75 行 ✅ + 3 行 ⚠️ 需配置）。**
>
> **v2.1 做了什么**：v2 只写到 §6.6，而 §1.2 的判据（S 类产物清单在 §7.2、H 类契约在 §9）依赖的章节全部不存在，
> 因此"100% 覆盖"既不能证实也不能证伪。v2.1 补齐 §7–§13，并按 §14.8 的 P1–P12 落点（v2 原文存档在 §14.9）
> 修改 §1–§6 中"补章节也修不好"的规格缺陷。**修改既有小节的地方，一律在 §0.2 留痕并写明代价**；只补不删，能不改的旧文字不改。
>
> **v2.2 做了什么（本次）**：v2.1 自称"已补全"，但这个结论**从未被验证过**。v2.2 先做了四路对抗性复核
> （内部自洽 / 逐站型覆盖 / 实现充分性 / 与既有工程核对，共 100+ 个功能点与断言），再把查出的缺口逐条补进正文：
> "封闭清单"补齐（站点选项、占位符、可迭代清单、派生字段）、3 处自相矛盾与 3 处重复段落清除、
> 8 处"不同实现会给出不同结果"的判定函数写死、5 个"文档承诺了却取不到"的字段落地、
> 9 个站型缺口补规则（榜单/排序页、日周榜、到期下线、多条件筛选叠加、对比表、播客 enclosure 等），
> 并把 1 处"声称的替代路径其实不成立"（多语言配对）改成可实现的规则；同时修正 4 处对既有工程的错误描述。
> **逐条留痕见 §0.3，覆盖率结论的修订见 §14.7，复核发现与落点的对照见 §14.10。**

---

## 0. v1 → v2 变更记录

v1 的 5 种词法元素、EBNF 骨架、参数语法、错误文案规范、预解析机制、原子写、产物清单**全部保留**。
以下逐条记录 v2 **新增或推翻**的决策与代价——本文件在 v1 里定的规矩是「任何新增都必须先改本文件」，这一节就是那次改动。

| # | 变更 | 类型 | 为什么必须 | 代价（如实记录） |
|---|---|---|---|---|
| 1 | **内容类型 + 自定义字段 + 通用内容表**（§2） | 地基 | 没有它，小说/产品/案例只能"用文章冒充"，字段不够就是不够 | 引擎多一层元数据；`article` 与自定义类型短期双轨（§2.6 给出收敛路径） |
| 2 | `arclist`→`{cms:query}`、`article`→`{cms:detail}`，`list` 泛化到任意类型（§6） | 标签改名 | 查询不再绑定"文章"这一种数据 | v1 名称作废（代码里没有实现，零成本） |
| 3 | `{cms:else/}`：**零新增词法元素**，新增"块内分隔"语义（§3.5） | 语法 | 通用字段系统下引擎**不可能**为每个自定义布尔字段提供正反两相，v1 靠"成对提供"的手法失效 | 部分推翻 v1 §2.6 裁定二对"块内分隔"的拒绝；范围被死限在 `{cms:if}` 的直接子节点、最多一次；`{cms:empty}` 依旧不提供 |
| 4 | 字段名放宽为多段 + 下标：`[field:specs.weight/]`、`[field:images.0.url/]`（§3.2） | 语法（放宽，向后兼容） | 图集、规格参数表、JSON 字段必须有取值方式 | 字段解析多一层路径遍历 |
| 5 | `{cms:foreach}` 迭代任意多值字段（§6.2） | 标签（零新增词法元素） | 图集、规格表、关联列表、标签云 | 无 |
| 6 | include 字面量参数 + `[field:param.x/]`，具名作用域 3 → 4（§5.3） | 作用域 | 同一张卡片片段在不同位置要显示不同字段 | `param` 成为唯一允许"同名多层、就近解析"的具名作用域 |
| 7 | 允许分页主体出现在 **include 片段内**（编译期展开后校验唯一性） | 裁定（放宽 v1 §5.4 约束二） | 主题包会把整个列表区块拆进片段 | 片段被两处包含 → 展开后出现多个分页主体，报错并给出包含链 |
| 8 | URL 规则数据化：由类型的 `url_pattern` + 占位符白名单决定（§7.1） | 架构 | 小说 `/book/x/12.html`、产品 `/product/x.html`、文档 `/docs/x/y.html` 各不相同，硬编码 6 条不够 | `UrlResolver` 要维护占位符与冲突检测 |
| 9 | 页面类型补全：单页 / 标签总览 / 归档 / 作者 / 筛选 / 搜索 / 404 / feed / sitemap 分片 / 搜索索引（§7.2） | 覆盖 | v1 只有 6 条 URL 规则，聚合页与运维页全缺 | 产物种类变多，产物清单必须覆盖全部产物 |
| 10 | **模板查找规则**显式定义（§7.3） | 覆盖 | v1 通篇没写"哪个模板文件对应哪种页面"，不定这个无法开工 | 无 |
| 11 | **动态层契约**写入本文件（§9） | 覆盖 | 搜索、表单、评论、浏览量静态做不到；不写清楚，"100% 覆盖"就是空话 | 引擎之外多一组后端模块，要限流与人工审核 |
| 12 | 浏览量上报（§9.4） | 覆盖 | v1 的 `viewCount` 在静态页上**永远不增长**，是个漏洞 | 数字由 JS 实时渲染，静态 HTML 里的数字必然滞后 |
| 13 | 发布版本 / 回滚 / 预发布（§8.7） | 运维 | 单文件原子写挡不住"模板写错 → 全站坏页" | 需保留最近 N 个批次的文件副本，占磁盘 |
| 14 | 差分跳过：内容哈希 + 模板 mtime 一致则不重写 | 性能 | 大站（小说站数万页）全量重建不现实 | 哈希计算有成本；人工改过的产物会被下次生成覆盖（已在文件树标只读） |
| 15 | Markdown 入库预渲染 `contentHtml`（§5.2） | 覆盖 | v1 把 `content` 标 raw，`content_format=MARKDOWN` 的正文会**漏出源码** | 引入 commonmark-java（它不是模板引擎，不违反 §1.3）；存量数据要回填迁移 |
| 16 | `channel='all'` 表示不限栏目（§6.3）**（参数名误记：正确是 `category='all'`，且 `type='all'` 才是"不限类型"，见 §0.2 第 5、6 条）** | 参数 | v1 规定首页/标签页必须给 `channel`，"全站最新"根本写不出来 | 无 |
| 17 | 明确**不做**：模板继承（`extends`/`block`）、`and`/`or` 条件（§3.7、§3.8） | 明确不做 | 用 header/footer 片段 + 嵌套 `{cms:if}` 已完备，不必让解析器长出继承与运算符优先级 | 主题作者每个页面模板要写两行 include；OR 分支要写成 else 链（§3.8 给出完备性证明） |

### 0.2 v2 → v2.1 补全记录（**本次改动，逐条留痕**）

v2 的规矩是"任何新增都必须先改本文件"，本节就是那次改动。**"补"= 新增缺失章节；"修"= 动了 v2 已冻结的文字**；
凡标"修"的行都必须读一遍代价列——它们是"补 §7–§9 也修不好"的那一类（§14.8 的 P1–P12 与评审的 A1–A14）。

| # | 变更 | 类型 | 为什么必须 | 代价（如实记录） |
|---|---|---|---|---|
| 1 | 补齐 **§7 页面类型 / URL / 模板查找 / 筛选 / 搜索索引 / 运维产物 / 媒体 / 产物树** | 补 | §1.2 的 S 类清单、§2.1 的 `*_url_pattern`、§4.1 的四个 Writer 全部指向这里 | 产物种类从"HTML"扩到 9 类（§7.2），产物清单与 GC 必须覆盖全部产物 |
| 2 | 补齐 **§8 静态化引擎**（计划 / 任务 / 增量 / GC / 批次回滚 / 定时 / 主题包 / 出口） | 补 | §0 第 13、14 条改的就是这一节 | 引擎多一层"输入依赖清单"与产物副本，占磁盘与一次写 manifest 的成本 |
| 3 | 补齐 **§9 动态层契约**（搜索 / 表单 / 评论 / 评分 / 浏览量 / 书架 / 反滥用） | 补 | §1.2 把 H 类算进"100% 覆盖"，不写清楚就是空话 | 引擎之外多一组后端模块，要限流、审核与审计 |
| 4 | 补齐 **§10 错误文案规范**、**§11 与既有工程的对接**、**§12 实施阶段与验收**、**§13 术语与变更流程** | 补 | §3.5 写"文案见 §10"；§11–§13 在 v2 里连引用都没有，等于整份规格没有工程落地章与验收章 | 无 |
| 5 | **P1 跨类型混合列表**：查询标签补 `type='all'`，跨类型走 `cms_content` 公共列 | 修 | 首页"全站最新"是资讯站/行业站的第一需求，v2 无任何参数能表达"不限类型" | **推翻 v2 §2.5 的"跨类型走索引表按 `type_code` 分派"**——索引表只管自定义字段筛选；跨类型排序只依赖公共列，代价是不能按自定义字段跨类型排序 |
| 6 | **P1b 参数名纠错**：v2 §0 第 16 条的 `channel='all'` 是误记，实际参数是 `category`，`channel` 是 `{cms:channel}` 的起始节点 | 修 | 照 v2 §0 写会被"未声明的参数报错"拦下 | 无（v2 §0 那张表的第 16 行保留原文，仅在此处更正） |
| 7 | **P2 单页取值**：统一"**页面的当前条目压入匿名作用域栈底**"（§5.1），单页模板直接 `[field:title/]` | 修 | v2 的单页（`kind='SINGLE'`）模板取不到自身任何字段，官网的"关于我们/隐私政策"做不出来 | 详情页/单页/索引页的作用域栈恒多一层；`[field:title/]` 在"当前条目"与"循环项"同名时以循环项为准（就近），这条**必须写死**，见 §5.1 |
| 8 | **P3 作者实体**：新增内置内容类型 `author` + `cms_content.author_id/author_name` + 内置字段 `authorId/authorName/authorUrl`；作者页 = `author` 类型的详情页 | 修 | v2 把"作者页"列为 S 类页面，却只有一个字符串 `authorName`，没有实体也没有 URL | 作者成为内容（可加自定义字段、可发详情页），代价是多一个内置类型与一次 id 关联；`authorName` 由 `author_id` 反查，**不冗余存名字**时需 join（引擎预解析一次，模板侧无感） |
| 9 | **P3b 浏览量**：`cms_content.view_count` 列；**静态榜单是 T 时刻快照**，靠 §8.8 定时重建刷新 | 修 | v2 只承认"数字显示滞后"，没承认**排序也滞后**——榜单、侧栏热度、热门标签全压在这个字段上 | 榜单有最大 1 个重建周期的误差；实时数字仍由 §9.4 的 JS 覆盖显示 |
| 10 | **P6 关系型列表可分页**：`relate` 从 `query` 开放给 `{cms:list}` | 修 | 专题页/系列页/相关案例页在 v2 里永远只有第 1 页（`relate` 只给不分页的 `query`） | 无新增语法、无新增标签；`relate` 的取值仍需 `indexed` 校验 |
| 11 | **P7 定时发布**：`status='PUBLISHED'` **隐含** `publish_time <= now`；到点重建见 §8.8 | 修 | v2 查询侧没有时间窗，未来时间的内容会被立刻生成进静态页 | 查询恒带一个时间条件（索引可用）；"到点"精度取决于 §8.8 的调度粒度（默认 1 分钟） |
| 12 | **P8 表单绑定当前内容**：`{cms:form}` 的 `hidden` 支持引擎关键字 `contentId:self`（还有 `contentType:self` / `url:self`） | 修 | 参数值不做插值是**对的**规定，但行业站的"产品询价"必须告诉后端问的是哪个产品 | 关键字是一份**封闭清单**，不是插值语法；清单外的一律按字面量处理，写错不报错（值就是字符串），因此在 §6.5 明示"只有这三个 key 的名字（不是值）被引擎识别" |
| 13 | **P9 多语言**：**一个语言 = 一个站点**，写进 §1.3 能力边界 | 修 | v2 的目标里点名"多语言外贸站"，正文一个字都没有 | 语言间共享内容要靠 `RELATION` 手工配对或导入工具；`hreflang` 由模板输出（§7.1）；**不做单站点内多语言**是永久边界 |
| 14 | **P10 内容查询的"当前项"**：内容查询的迭代项也提供 `current`(BOOL) 与 `class` | 修 | 没有比较运算符（§3.8 刻意不完备），"列表里高亮当前文章/当前产品"在 v2 无解 | 引擎每页多算一次"当前条目 id 比对"（成本可忽略）；`class` 的取值口径必须与导航类统一，见 §5.5 |
| 15 | **P11 详情页承载可分页列表**：`{cms:list}` **允许**出现在详情页模板上，条件是该页的 `{cms:detail}` 不做正文分页；分页 URL 用该类型 `detail_url_pattern` 的 `{n}` 形态。**取消 v2.1 草案里的 `SUBLIST` 页面类型**（少一个页面类型、少一张 URL 规则、少一个规划器） | 修 | v2 §5.6 给的"小说目录"方案在同一模板里就是 2 个分页主体，按 v2 §5.4 直接报错；而"专题页（人工挑 200 篇，需分页）""作者页（该作者的文章，需分页）""系列页""文档目录页"全都需要"详情 + 可分页列表"这一形态——为一个页面类型造一套规划器，不如把规则改对 | 分页主体数量的判定从"`list`/`detail` 互斥"改为"**至多一个分页主体，且 `{cms:list}` 至多一个、`{cms:detail}` 至多一个**"；冲突只剩一种（`paginate_body` 非空的同时又有 `{cms:list}`）；`page.paginationKind` 区分"正文分页 / 列表分页"（`{cms:pagelist}` 只许后者用） |
| 16 | **P12 父链访问**：`of='self'` 收紧为"**锚定项**为父"，新增 `of='parent'`；补父链派生字段 `parentId/parentTitle/parentUrl/parentTypeCode/ancestors` | 修 | 章节页写不出书名、写不出"返回目录"；v2 的 `of='self'` 两处定义冲突，且它举的例子正好是 v2 自己声明不提供的"向上作用域" | `of` 的"锚定项"定义必须与 §5.1 的当前条目一致（这是新增的一条承重定义）；v2 §3.7 裁定三里那个错误示例**必须改写**，已改 |
| 17 | `content` 具名作用域 → **`item`**；保留名统一为 6 个（`site channel page param query item`） | 修 | `content` 在 v2 里同时是作用域名、正文字段名、字段前缀，且三份保留名清单互不相同——`[field:content.title/]` 按 v2 的字面规则必然报错 | 改名零迁移成本（无实现）；`content` 从此**只**表示正文字段（`cms_content.content`、`paginate_body` 默认值不变） |
| 18 | 内置单页类型 code `page` → **`single`** | 修 | 类型 code `page` 与分页作用域 `page` 同名，`[field:page.xxx/]` 的归属不可判定 | 后台显示名仍是"单页"，模板里写 `type='single'`；分页作用域 `page`（`page.pageNo` / `page.empty`）保持不变，因为它被模板引用的次数远多于类型 code |
| 19 | `pubdate` → **`publishTime`**（全篇统一） | 修 | 同一个"发布时间"在 v2 里有三个名字（`publishTime` / `pubdate` / `publish_time`），而它要进 `orderby`、`prenext` 产出与 formatter 示例 | 无（列名仍是 `publish_time`，模板侧只有 `publishTime` 一个名字） |
| 20 | `where` 的 `in` 值分隔符由逗号改为 **`\|`** | 修 | 逗号已被"条件之间的 AND"占用，`a:in:1,2,b:eq:3` 在 v2 里不可判定 | `in` 的值里不能出现 `\|`；`like` 的值里不能出现逗号（同样原因），两条都写进 §6.3 |
| 21 | 编译缓存 key 加**定义版本**：`siteId + 模板路径 + defVersion` | 修 | v2 只按 `mtime+size` 失效，改了字段定义仍命中旧 AST，§4.5 的第 4/5/6 条校验全部失效 | 字段/类型定义每次保存版本号 +1 → 该站点模板全部重编译（编译是纯内存操作，可接受） |
| 22 | 增量方案由"**产物内容哈希**"改为"**输入依赖清单 + 反向索引**" | 修 | 内容哈希发现不了"本页应该变"（上一篇的 next 链接、归档计数、分页、sitemap、feed），也发现不了"本页应该消失"（幽灵页） | 每页一份 manifest（含 `siteConfigVersion` / `astVersion` / `contentIds` / `categoryIds` / `tagIds` / `neighborIds`），大站可只存 id 集合摘要 |
| 23 | 产物树分离：**`www/` 引擎独占**，`data/` 回归人工资产；产物 GC 与幽灵页清理（§8.6） | 修 | v2 把引擎产物写进 `data/`——与人工上传的 css/js/图片同树，GC 不敢删、人工改过的产物必被覆盖 | 站点目录多一层 `www/`；`SiteService.SITE_SUB_DIRS` 要加它（§11.4）；旧站点若已在 `data/` 手工放页面，需一次性搬移（§12.4） |
| 24 | `site` 作用域 key 清单**补全**（v2 以 `…` 收尾，而找不到即报错） | 修 | "站点默认图"、og 配置、语言、备案、社媒都在那个省略号里；省略号是承重的 | 清单从 8 个 key 扩到 26 个，全部来自 `cms_site` 现有列 + 站点级选项表（§2.7） |
| 25 | 新增 `cms_menu` / `cms_menu_item`，`{cms:channel}` 增 `source='type'\|'menu'` | 修 | 官网主导航（栏目 + 单页 + 外链混排）、页脚、友情链接、行业站"六个类型 = 六个导航项"在 v2 全部无法表达 | 多两张表 + 一个后台菜单管理页；**不加运算符**——"哪一项是当前项"仍由引擎算（与 §5.5 同一条准则） |
| 26 | 富文本白名单**写进契约**（保留代码高亮所需的 `class` / `data-*`） | 修 | 白名单按"清掉 class"实现会让 `<pre><code class="language-java">` 的高亮失效 | 白名单变长 → 清洗面变大，必须逐项写明允许的属性名（§5.2.4） |
| 27 | 新增 `toc` 派生字段（正文标题树）与 `{cms:foreach}` 迭代 | 修 | 博客/文档站的正文目录是标配，v2 没有任何机制 | 正文每次保存要抽一次 heading（入库时算，不在渲染时算） |
| 28 | 新增字段类型 `ENUM_MULTI`（多选枚举），索引表唯一键加 `value_key` | 修 | 多地区/多规格筛选（"华东或华南"）需要多值受控词表；v2 的 `where` 只能单值 | 索引表可能一个字段多行（写放大）；`in` 与 `ENUM_MULTI` 的语义要区分（§6.3） |
| 29 | 内置字段的**可筛选/可搜索白名单**写进 §2.2 | 修 | `where` / `orderby` 要求 `indexed=1`，而 `indexed` 是自定义字段定义的列——`title`/`publishTime`/`viewCount` 能不能用，v2 未定义 | 内置字段的筛选能力被固定成一张表，越出表外一律编译期报错 |
| 30 | **§2.6 由双轨改为收敛**：`cms_content` 是唯一内容表，`article` 是它的内置类型，`cms_article` 存量一次性搬运 | 修 | 双轨期"不能跨类型联合排序取详情"，而跨类型"全站最新"正是首页第一需求；项目全新，此刻收敛成本最低 | 后台文章列表/编辑器要改接新表（这部分工作本来就要做，因为要支持自定义字段）；`cms_article` 保留只读一期，不删 |
| 31 | slug 变更的重定向产物：`cms_redirect` 表 + `redirects.conf`（§7.6 / §8.6） | 补 | slug 就是 URL，改名即旧 URL 死链，而 v2 的差分跳过还会把旧产物留在磁盘上 | 每次 slug 变更多一次写表与产出一个 nginx include 文件（需运维 include 一次，§11.3） |
| 32 | 主分类唯一（`cms_content_category.dimension='primary'` 唯一）+ `canonical` 口径写死 | 修 | 一条内容挂两个分类时 canonical 取谁未定义，而 canonical 直接影响收录 | 后台保存时要校验"主分类恰一个"，多分类内容需手工指定 |
| 33 | §4.5 编译期校验从 10 条扩到 **18 条**，并把其中 12 条指定为第一批测试用例 | 修 | v2 把"编译期校验"当作最大红利，但 `backend/src/test` 不存在，红利没有验收手段 | 无 |
| 34 | 主题包定义为**部署单元**（目录/zip + 清单文件 + 版本号），模板目录可进 Git（§8.9） | 补 | `sites/` 被 `.gitignore` 忽略 → 模板不进版本控制：不能在 IDE 写、不能 review、不能回滚、新环境无法重建 | 主题包与站点数据分离后，"改模板"变成"导入主题包"，后台要提供导入导出与版本记录 |

**本节的两条阅读约定**：① 表中标 **修** 的行，正文里会以 `> v2.1 修改：…` 的引用块就地标注，避免"读到旧文字以为还有效"；
② 标 **补** 的行，正文是**新增**章节，不存在新旧两版。

### 0.3 v2.1 → v2.2 补全记录（**四路复核后的修订，逐条留痕**）

v2.1 的"已补全"是**自证**，本次先复核再补。复核方式：四路对抗性审查各自独立读完 2645 行全文——
① 内部自洽（交叉引用、封闭清单、数量口径、同名不同义）；② 逐站型覆盖（100 个功能点逐项找规则）；
③ 实现充分性（不定死就会有分歧的地方）；④ 与既有工程核对（30 条现状断言逐条查代码）。
查出 **20 条内部缺陷 + 29 条实现歧义 + 21 条覆盖缺口 + 6 条工程描述错误**（另有 1 类事实属"仓库外、不可核实"）。下表是这些缺口的落点与代价，
**类型列的含义同 §0.2（补 = 新增规则/声明；修 = 改了 v2.1 已写下的口径）**。

| # | 变更 | 类型 | 为什么必须 | 代价（如实记录） |
|---|---|---|---|---|
| 1 | 站点发布选项表补齐**已在正文使用却从未声明**的 16 个键（`url.home` `url.list` `page.tagMinCount` `pager.labels` `publish.mode` `publish.threads` `publish.pageTimeout` `publish.debounce` `publish.rankCron` `publish.preview` `publish.strict` `comment.moderate` `comment.snapshot` `comment.snapshotSize` `seo.noindexTypes` `feed.includeBody`），新功能另加 1 个（`publish.expireRedirect`），并把 `facetCardinality` 统一为 `facets.cardinality`（§2.7） | 修 | §13.3 把选项表当封闭清单，而正文有 16 处在用它没声明的键——配了不生效、后台键名对不上 | 选项表 **30 行 → 47 行**（后台表单多 17 个字段；全部有默认值，不配也能跑） |
| 2 | `{facetPath}` 补进占位符白名单（§7.1.1）；数量由 13 更正为 **14** | 修 | §7.1.3 把它列为筛选页必需占位符、§2.7 的默认 `url.facet` 就用它——按 §7.1.1"白名单外一律 E4001"实现，**默认筛选页必然报错** | 无（零迁移成本） |
| 3 | 可选占位符缺值处理与 `{n}` 第 1 页形态写死：**逐字符删除 `{n}` 与其紧邻的一个分隔符**（§7.1.1） | 修 | v2.1 说"整段省略"，但它自己举的 `/book/{slug}/list-{n}.html` 并不是整段——两种实现产出 `list.html` 与 `list-1.html`，后者是重复收录 | `UrlResolver` 多一个规则分支与一批单测 |
| 4 | 首页分页 URL 归站点选项 `url.home`；`LIST` 拆成**类型列表页**（类型 `list_url_pattern`）与**分类索引页**（`url.list`）两种来源；`STATIC` 放宽为**可承载可分页列表**（站点声明的列表页，条目带 `query`）（§7.1.3、§7.2.1、§4.5、§7.3） | 修 + 补 | 三处"无归属"：首页第 2..N 页没有任何 URL 规则（§5.6 说用 `list_url_pattern`、§7.2.1 说固定 `/`、判定函数里没有 HOME 行）；分类页的 pattern 在 §2.1 挂在**类型**上而 `cms_category` 里没有这一列；榜单页 `/rank/`、排序切换、多地区落地页**没有任何页面类型能承载**（STATIC 当时是 0 主体，而 URL 又不许带查询串） | 多两个站点选项、一个页面类型的行为放宽、一行模板查找；换来"排序/筛选变体 = 独立静态页"，不再需要查询串 |
| 5 | `paginate_body` 非空但 `detail_url_pattern` 里没有 `{n}` → **计划期 E4001**（§7.1.3 判定 5） | 修 | §2.1 说"含 `{n}` 才走正文分页"、§4.5 说"`paginate_body` 非空即分页主体"——按前者会**静默丢掉 `<!--cms:page-->` 之后的全部正文** | 报错而非容错，模板作者要补一次 `{n}` |
| 6 | `page` 作用域**恒存在**，新增 `page.canonical`，明确 `rel` 属于 `{cms:pagelist}` 迭代项；`channel` 的 key 清单与存在条件成表（404/SEARCH 没有 `channel`）；`site` 的 key 清单去掉省略号并按 §2.7 对齐为 22 个（§5.1、§5.6） | 修 + 补 | 三个承重定义缺失：分页页照 `[field:canonical/]` 写会**指回第 1 页**（收录事故）；§7.6 要求每个 `DPAGE` 输出 `page.noindex` 而 §5.1 说 `page` 只在有分页主体时存在；`[field:channel.label/]` 在 §7.4 的示例里已经在用，但 `channel` 一个 key 都没定义 | 模板作者多认识两个字段；`channel` 在 404 上由"渲染空值"改成"报错" |
| 7 | 无前缀 `[field:x/]` **只在匿名栈帧**（当前条目 + 循环项）里查找，具名作用域必须写前缀（§5.1 第（3）条） | 修 | `page.url` / `site.url` / 迭代项 `url` 三者同名，v2.1 的"从栈顶向下找第一个含它的 Scope"会让 `[field:url/]` 的归属随书写位置漂移 | 老模板若靠无前缀取到过具名作用域的值，会开始报错——**这正是想要的效果**（它本来就不可靠） |
| 8 | formatter 调用语法写死（`format='<name>'` + 专属参数名的快捷形式 + 单 formatter + 组合合法性 → E1002）（§2.2） | 补 | v2.1 的字段类型表一半写参数、一半写裸名，而 §3.3 的参数文法只能表达 `key=value`——`[field:x number/]`、`number='1'`、`format='number'` 谁合法没有答案 | 表多一列（"语法"），字段类型 × formatter 的合法性要写进代码（19 × 18 的组合校验） |
| 9 | 新增 **§6.7 标签 × 页面类型合法性矩阵** 与错误码 `E3012`（§6.7、§10.2） | 补 | v2.1 只裁了 `{cms:list}`/`{cms:detail}` 两个分页主体，其余 12 个标签在 11 种页面类型下的行为没有裁定——`{cms:channel}` 写在 404 上一处报错、一处渲染空值，违反"报错优于静默" | 编译期多一张 11 × 14 的查表 |
| 10 | 判真判定顺序写死（先 `trim`、再按字面量判 0，`"0.00"` 也判假）；起始序列后非法 name → E1001；重复参数 key → E1002；`BOOL` 取值集合收紧（§3.2、§3.3、§3.7） | 修 | 同类分歧：`{cms:` 后不接合法名字算文本还是报错、`row='10' row='20'` 取哪个、`True` 是否等于 `1` | 无 |
| 11 | 默认排序 **恒追加 `id desc`**；类型未配 `sort_field` 时默认 `publishTime desc`；新增 `orderby='relationOrder'`（按 `RELATION` 数组顺序）（§6.3） | 修 + 补 | 没有 tie-break 时，同一 `publishTime` 的多条内容在分页里会重复或丢失，`{cms:prenext}` 的"上一篇"会漂移；而**专题页的人工挑稿顺序**在 v2.1 完全没有取值方式（`foreach` 保序但不分页、`list` 可分页但 `orderby` 只收字段） | `orderby` 多一个保留值；排序 SQL 恒多一个 `id desc` |
| 12 | `type='all'` 的迭代字段集合写死（公共列 + 不依赖类型的派生字段）；"当前类型 / 当前栏目"不存在时省略 `type` / `category` → 编译期 E1002；`where` 的字段 code 与取值写死（只认白名单 code、值一律 id），并改正 `tag:has:home` 这个不成立的示例（§6.3、§2.5） | 修 | 同一个"全站最新"模板一处能编译一处报 E1004；首页上省略 `type` 一处报错一处退化成"不限"；`where='tag:has:home'` 照抄必然 E2007 | 首页 / 标签页 / 归档页上的 `{cms:list}` 必须显式写 `type` 与 `category`（多打几个字，换来可判定） |
| 13 | 计数与邻接统一时间窗（`PUBLISHED ∧ publish_time ≤ now() ∧ 未到期`），`{cms:prenext}` 的 `category` 默认值明确为 `all`（§6.3、§6.4） | 修 | 计数会把还没到点的内容算进去（多出空标签页、空筛选页），上下篇会指向一个还不存在的页；而 `category` 留空"到底是不限还是当前栏目"无法判定 | 所有计数 SQL 恒带一个时间条件（可走索引） |
| 14 | 新增 `cms_content.expire_time` + **到期下线**定时任务（每分钟）与可选 301（§2.3、§8.8、§2.7） | 补 | 活动页、广告位排期、限时专题都要"到点下线"，v2.1 全篇没有到期概念——而 §14.7 的活动页行**声称**"到期后写 `cms_redirect`"，那个机制当时并不存在 | 一列 + 每分钟一趟扫描；到期内容从计划中消失由 GC 删除 |
| 15 | 新增 `view_count_day` / `view_count_week` 两列与滚动窗口口径（小时桶），进 `orderby` 白名单（§2.3、§9.4、§2.2） | 补 | 资讯站 / 小说站的"日榜、周榜"在 v2.1 只有累计值可用；§14.7 的 F9 还写了不存在的"收藏榜" | 两列 + 内存小时桶；§14 的 F9 行改为"日 / 周 / 总"，**收藏榜删掉**（收藏没有权威口径，§13.4） |
| 16 | 评论数与评分快照落成真实列（`comment_count` / `rating_avg` / `rating_count`），并把 `ratingAvg` / `ratingCount` / `commentCount` 补进派生字段表与 `orderby` 白名单（§2.3、§2.2、§9.6） | 修 + 补 | §9.6 承诺了 `[field:ratingAvg/]`，但派生字段表里没有它——按 §4.5 第 4 条会被判 **E1004**（"文档自己承诺了却取不到"）；社区站的"按回复数排序"也没有任何字段 | 三列 + 审核/评分事务里同步维护 |
| 17 | 评论快照依赖写成 `commentsHash` 并进 §8.4 的失效表（§9.6、§8.4） | 修 | v2.1 说"加一个 `comments` 字段"，而 §8.4 的 deps 示例与失效表里都没有它——评论审核通过后静态页不更新，"评论快照"永远是空的 | 每页多一个哈希；评论审核触发窄批次发布 |
| 18 | 依赖清单补 `formIds` / `ratingIds` / `clockBucket` / `orderbyFields`；分类改名补"子分类下所有详情页 + 301"；窄批次**不执行 GC**；失败页在 manifest 里保留旧条目；回滚 = 覆盖 + 删除（§8.4、§8.3、§8.5、§8.7） | 修 + 补 | 五处增量正确性漏洞：相对时间永远停在"3 天前"（§8.8 的"每日全量"其实是差异渲染，不会自愈）；改表单选项静态页不变；分类改名后旧 URL 残留且无 301；窄批次按字面执行 GC 会**把全站其余产物删光**；回滚后新旧页面混杂且永不被 GC 清理 | 每页 manifest 多 4 个键；`mode` 增两个取值；回滚多一次删除扫描 |
| 19 | 多语言配对落地：`RELATION` 字段可勾 `crossSite=1`（允许跨站点配对）+ `site.alternates` 进可迭代清单 + 三条配对口径（§11.5、§2.2、§3.5、§6.2） | 修 | v2.1 唯一一处"声称的替代路径其实不成立"：它说"用 `RELATION` 配对、从配对项反查 URL"，但 `RELATION`、索引表（带 `site_id`）与 URL 规则**全是站点内**的——配对存不下也查不出，而 `{cms:foreach field='site.alternates'}` 又不在可迭代清单里（必报 E1004）。多语言外贸站是目标里点名的站型 | 一个字段选项 + 一条跨站点校验；配对项缺失时不输出该条 `hreflang` |
| 20 | 筛选叠加链接 `urlWith`（只在 `facets.combos` 声明过时输出）（§7.4）；具名查询结果可下标访问 `query.<name>.rows.<i>`（§6.3） | 补 | 行业站的"在已选条件下再加一个条件"在 v2.1 无解（参数不许插值、facet 迭代项只给 `url`）；产品对比表取不到"第 2 个产品"（`query` 作用域只有 `empty`/`totalCount` 这类元信息） | `urlWith` 依赖手工 combos（与"不做全排列"的死限一致）；`rows` 只对顶层命名查询开放 |
| 21 | feed 模板作用域定义 + `<enclosure>` 取 `FILE` 字段 + `feed.includeBody`（§7.3、§7.6、§2.7、§14.7） | 补 | 播客 / 音视频站的订阅在 v2.1 不成立（enclosure 没有任何规则）；博客的"全文 RSS"也没有开关 | feed 模板多两个可用字段；全文订阅会让 feed 变大（默认关） |
| 22 | 动态层契约补齐：接口总表加 `/api/public/captcha`、补全部响应体、`siteId` 优先级收敛到 §11.1、统一 `form_error`、分片上传协议写死、`hidden` 的 key 不受校验（§9.1、§9.2、§9.5、§6.5、§4.5、§10.2） | 修 + 补 | 按 §6.5 写的模板**永远不显示表单错误**（key 写成 `formError`）；`/api/public/captcha` 有调用无表项；`subscribe` 在表里但零契约；`siteId` 优先级两处列表不同；"分片上传"只有端点名 | 接口文档多一段响应体；分片协议多四个参数 |
| 23 | 错误码整理：`E4006` 文案不许省略路径、`E3011` 废弃并入 `E2010`/`E2011`、新增 `E3012`（§10.2） | 修 + 补 | E4006 的 `…` 违反 §10.3"报错必须带可选项"；同一件事两个错误码会让"报错 Top 10"统计口径分裂 | 无 |
| 24 | 编译缓存 key 加**上下文签名**；`astVersion` 输入写死为 `路径 + mtime + size + sha256` 并累加 include 链（§4.4） | 修 | 同一个 `list.html` / `single.html` 被多种页面共用时，校验 4/5 的"该模板上下文可用字段"与实际缓存粒度对不上（一处漏报 E1004、一处语义漂移）；片段**同长度修改**时字节数不变 → 包含它的模板不重编译，页面静默陈旧 | 缓存条目按上下文分裂（条目数变多，纯内存）；每次编译多算一批 sha256 |
| 25 | 与既有工程的描述修正：`cms_site` 无 `site_id`；本地端口 8081；预览改走 `/api/preview/{siteCode}/**`（`/preview/**` 与 `SecurityConfig`/JWT/拦截器/`SiteContext` 四处不兼容）；补 `SiteService` 三个成员的可见性；补图像库依赖（JDK ImageIO 不能编码 WebP）；补 `cms_media` 增列等 3 条迁移；明确 `@EnableScheduling` 与后台线程取不到 `SiteContext`；CORS 同源要求；标注"仓库外事实"（§1.4、§11.2、§11.3、§11.4、§11.7、§12.1、§12.2、§12.4） | 修 + 补 | 复核逐条查了代码：6 处描述与现状不符、9 项必要改动文档没提——其中"实现不了"的四条（预览、派生图、后台定时、站点目录访问器）会让期 0/期 7 直接卡住 | 迁移多 3 条；期 0 多两个基础设施动作；`www/` 目录加入 `SITE_SUB_DIRS` |
| 26 | §14 覆盖表按新规则改写 7 行（N4 / F9 / F12 / I14 / 电商 / 播客 / 社区），⚠️ 口径统一 | 修 | v2.1 的"78 行全 ✅"里有 5 行的自证不成立（榜单页当时无页面类型可用、F9 引用了不存在的时间窗榜单与收藏榜、F12 让内置 `status` 去做 facet、I14 与 §14.7 自己的 ⚠️ 表冲突、播客行缺 enclosure 规则） | 结论从"78 ✅"变成"75 ✅ + 3 ⚠️"——⚠️ 不是不达标，但**必须点名**，否则又变成自证 |
| 27 | 新增 **§14.10 v2.2 复核发现 → 落点对照表**，与 §14.8 同构 | 补 | 让"本次到底改了什么、为什么"可逐条回查，而不是再写一句"已补全" | 无 |

> **本节的阅读约定**：与 §0.2 相同——标 **修** 的行，正文里以 `> v2.2 …` 的引用块就地标注；标 **补** 的行是新增规则。
> **本次没有改任何标签名、参数名、保留名、URL 形态与产物路径**（§13.3 的破坏性清单），因此仍是 v2.x；
> 唯一"改语义"的是 §5.1 的无前缀查找范围（第 7 条）与 `E3011` 的废弃（第 23 条）——两者都是**把不可判定的行为改成确定的**，不是新能力。

### 0.4 实现记录（**期 3 收敛落地：文章并入通用内容**）

按 §2.6 期 3 执行，不新增任何能力，只把规格已经写下的收敛做完并更新两处过时口径。

| # | 变更 | 类型 | 为什么必须 | 代价（如实记录） |
|---|---|---|---|---|
| 1 | 迁移 `V20261006090001__cms_article_merge.sql`：存量 `cms_article` 搬进 `cms_content` 的 `article` 类型（含 `category_id` → `cms_content_category` 主分类、`cms_article_tag` → `cms_content_tag`、`HTML → RICHTEXT` 的格式映射），随后 **drop `cms_article` / `cms_article_tag`**，并同步删除 `cms:article:*` 菜单与按钮权限、把「内容状态」字典从 `article_status` 正名（§12.1 第 9 条、§2.6 期 3） | 修 | §2.6 期 3 要求"统一走新表 + 文章相关代码删除"，而 §12.1 当时的尾注写的是"`cms_article` 不删、只读保留一期"——两条自相矛盾，必须裁定一条 | 旧表**不可回退**（迁移前需自行备份）；正文预计算列（`word_count` / `content_toc`）不随搬运生成，由引擎读取时按 §5.5 兜底重算；后台文章列表/编辑器从入口中消失，改由「通用内容」按类型承担 |
| 2 | 后台菜单重排：一级菜单顺序改为 `仪表盘 → 站点 → 内容管理 → 系统管理 → AI管理`（`admin-ui/src/layout/index.vue` 与 `sys_menu.sort` 同步） | 补 | 站点是内容的上游、AI 是系统级配置，原顺序把站点压在最后、AI 夹在系统与站点之间 | 无（纯展示顺序，权限按 id 授权不受影响） |

> 本条记录**不改任何标签、参数、URL 形态与产物路径**，因此仍是 v2.x；`/api/public/articles` 按 §9.2 保留，只把取数从 `cms_article` 换成 `cms_content` 的 `article` 类型（行为不变，含 §12.4 第 14 条点名的浏览量读副作用——它要等 §9.4 的 `POST /api/public/views/...` 落地后才能移除）。

---

## 1. 目标与范围

### 1.1 做什么

1. **内容模型**：内容类型 + 自定义字段 + 通用内容表，站点自助定义"产品 / 案例 / 章节 / 职位 / 问答"等任意内容形态（§2）。
2. **一套自研的 CMS 模板标签语言**（对标织梦 DedeCMS 的 `{dede:xxx}` 体验），**不引入任何第三方模板引擎**（§3–§6）。
3. **一个全站静态化引擎**：把站点模板 + 数据库内容渲染成静态 HTML 与聚合产物，落到 `sites/<站点>/www/`（§7–§8）。
4. **一份动态层契约**：静态做不到的交互（搜索、表单、评论、评分、书架、浏览量）由固定的公开 API 承担，模板侧有稳定挂载点（§9）。

### 1.2 页面分级：S / H / D（v2 的核心新增）

"所有网页都能做"必须先定义"网页"。本文件的定义是 **S 类 + H 类**：

| 级别 | 定义 | 典型页面 | 谁负责 |
|---|---|---|---|
| **S — 静态生成** | 引擎产物，SEO 首屏完整，无 JS 也能读 | 首页、栏目/分类索引、内容详情（含正文分页与"详情页上的目录/专题/作者文章"分页）、单页、标签页与总览、归档、作者、筛选落地页、feed、sitemap、robots、搜索索引、重定向存根 | 引擎（§7） |
| **H — 静态壳 + API** | 页面由引擎生成，交互部分调 `/api/public/**`；无 JS 时按 §9.3 降级 | 搜索页、表单页、评论、评分、书架/阅读进度、浏览量、分享 | 引擎出壳（§7）+ 契约（§9） |
| **D — 纯动态** | 不生成静态页 | 登录/注册/找回密码、个人中心、购物车结算、支付、站内消息、后台管理 | **不在本文件范围**：D 类是应用，不是网页 |

**判据**：一个站型需要的页面若全部落在 S + H，本文件就算 100% 覆盖它（§14 逐站型自检）。

### 1.3 不做什么（能力边界，**永久封死**）

以下能力**不提供**，且不接受后续以"加个小功能"的名义加入——那会让自研引擎滑坡成通用模板引擎或"自己写的 PHP"：

| 不提供 | 替代方案 |
|---|---|
| 任意表达式求值（`{cms:if a > b}`） | 不存在。条件只能来自**字段真假**（§3.5），布尔组合的完备性见 §3.8 |
| `{cms:if}` 的 `and` / `or` 参数 | 不存在。AND 用嵌套，OR 用 `{cms:else/}` 链（§3.8 证明其完备） |
| 空集分支 `{cms:empty}` | **不提供**。所有查询标签统一提供 `empty` / `hasResults` 字段，配 `{cms:if}` 使用（§6.3） |
| 函数调用 / 修饰函数（`function='html2text(@me)'`） | 字段**具名参数** + **formatter**（§2.2），如 `[field:publishTime format='relative'/]` |
| `{cms:php}` / `{cms:sql}` / 插件脚本 / 自定义函数 | 不存在（这是织梦历史上最大的安全灾难）。需要业务计算 → 录入时算好，或走动态层（§9） |
| 参数值里嵌套字段引用（`{cms:list category='[field:id/]'}`） | 参数一律**纯字面量**；迭代内取数走 `of='self'` / `of='parent'` / `relate='field:<code>'` / `{cms:foreach}`（§6.3） |
| **运行时模板渲染**（同一模板在线渲染动态页） | 不做。引擎只离线生成；H 类页面由 API + JS 承担 |
| 动态 DDL（加字段就 `ALTER TABLE`） | 自定义值存 `jsonb`，可筛选字段进 `cms_content_index`（§2.5） |
| 可视化拖拽编辑器 / 页面搭建器 | 不做。提供**标签生成器向导** + 主题包导入（§8.9）：这是"给会写 HTML 的人"的引擎 |
| 模板继承（`{cms:extends}` / `{cms:block}`） | 不做。用 header/footer 片段 + `{cms:include}`（§3.7） |
| **等值 / 数值比较**（`{cms:if field='a' eq 'b'}`、`price > 100`） | 不存在。**"哪一项是当前项"由引擎算成 `current` / `class` 字段**（§5.5）；数值比较由录入时算好的 `BOOL` 字段、或查询侧 `where` 过滤承担 |
| **单站点内多语言**（一个站点里放 zh/en 两套内容） | 不做。**一个语言 = 一个站点**（独立 `site_id`、独立模板、独立发布），语言间用 `RELATION` 配对、`hreflang` 由模板输出（§7.1、§11.5） |
| **动态重定向规则**（伪静态、正则重写） | 不做。引擎只产出**静态重定向**：内容改 slug 时写 `cms_redirect` 并生成 `redirects.conf`（§7.6），由 nginx `include` 一次 |
| 模板目录之外的读取、产物目录之外的写入 | 不存在。路径边界统一走 `SitePathBoundary`（§11.4，由现有 `SiteService.resolve` 抽出） |
| 不认识的标签 / 参数**静默忽略** | 一律**报错**。静默忽略会让模板作者白排查半天 |

### 1.4 现状依据

> 本表只写**已经存在的事实**；现状与设计之间的断层（四处）集中写在 §11，不在这里重复。

| 事实 | 位置 |
|---|---|
| Java 21 + Spring Boot 3.5.16 + MyBatis-Plus + PostgreSQL，单进程同时服务后台与 API | `pom.xml` |
| 站点目录下固定有 `data/`（静态资源）与 `template/`（站点模板） | `module/cms/service/SiteService.java` `SITE_SUB_DIRS` |
| 站点文件服务已能在线读写文本，白名单已含 `ftl`/`tpl`/`html` 等 | `module/cms/service/SiteFileService.java` `TEXT_EXT` |
| 内容已按站点隔离：`cms_site` / `cms_category` / `cms_article` / `cms_tag` / `cms_media` 都有 `site_id`；slug 站点内唯一 | `V20261001004746__cms_site_content.sql` |
| 站点默认唯一（`cms_site.is_default`，数据库唯一索引兜底） | `V20261001000500__cms_site.sql`、`V20261001004746` |
| 用户与站点已绑定，按钮级 RBAC 已有 | `sys_user_site`、`V20261001035233__cms_site_file_perm.sql` |
| 文章有站点内唯一的 `slug`、`status`、`publish_time`、`top`、`recommend`、`viewCount`、`content_format` | `module/cms/entity/CmsArticle.java` |
| 公开接口 `/api/public/**` 免登录可读已发布内容（目前只有 articles/categories 三个端点） | `module/cms/controller/PublicController.java` |
| 新增扩展的既有风格是「实现接口 → Spring 自动注册」 | `README` 「AI 模块」 |
| 请求到站点的解析是 **`X-Site-Id` 头 &gt; `siteId` 参数 &gt; 默认站点**，**没有 Host 域名解析**——静态站必须靠产物兜底（§11.1） | `common/site/SiteInterceptor.java` |
| `/uploads/**` 已映射到 `cms.upload.dir`，后端自己能提供媒体；但生产 nginx 只反代 `/api`/`/v1`（§11.2） | `config/WebConfig.java`、`application.yml` |
| 路径边界实现是**包级私有**（`static Path resolve(...)`，同包唯一调用方是 `SiteFileService`），发布引擎在另一个包拿不到；且**不拦符号链接** | `module/cms/service/SiteService.java#resolve`（§11.4） |
| `/sites` 与 `/sites/**` 被 SPA 回退占用（转发到 `index.html`），产物只读映射不能占用这个前缀 | `config/SpaForwardController.java`（§11.2） |
| **`commonmark-java` 与任何 HTML 清洗库都不在 `pom.xml` 里**：它们是本设计**待新增**的依赖，不是既有能力（§11.7） | `pom.xml` |
| `sites/` 被 `.gitignore` 忽略（模板不在版本控制内），`sites/default` 与 `sites/wwwroot` 目前是空目录 | `backend/.gitignore`（§11.6） |
| `backend/src/test` **不存在**（§4.5 的编译期校验清单没有验收手段） | `backend/`（§12.3） |

---

## 2. 内容模型（v2 新增，**100% 覆盖的地基**）

### 2.1 内容类型

站点自助定义内容类型。**小说站 = `book` + `chapter` 两个类型；行业站 = `product` + `case` + `service`；文档站 = `doc`（层级）**。

表 `cms_content_type`：

| 列 | 说明 |
|---|---|
| `id` / `site_id` | 站点隔离，类型由站点各自定义 |
| `code` | 类型标识：`article` `single` `product` `case` `service` `team` `job` `faq` `book` `chapter` `doc` `photo` `author`…（站点内唯一，仅小写字母数字下划线，**不得与保留名同名**，§5.1） |
| `name` | 后台显示名（"产品"、"章节"） |
| `kind` | `CONTENT`（有列表 + 详情）/ `SINGLE`（单页，如"关于我们"，全站仅一份、不参与列表）/ `TREE`（层级内容，如"书→章"、"系列→篇"） |
| `hierarchical` | 是否父子层级（`TREE` 隐含为 1） |
| `detail_url_pattern` | 详情页 URL 规则，占位符见 §7.1，如 `/{categoryPath}/{slug}.html`、`/book/{slug}/{parentSlug}-{sort}.html` |
| `list_url_pattern` | 该类型的列表页 URL 规则（"所有该类型的内容"的栏目索引，如 `/news/page-{n}/`）；为空表示该类型不出列表页 |
| `detail_template` / `list_template` | 模板相对路径，为空则用主题默认（§7.3） |
| `paginate_body` | 正文分页字段 code（默认 `content`），空表示不拆正文。**非空且 `detail_url_pattern` 含 `{n}` 时**该类型走正文分页；**此时详情页模板里不许再有 `{cms:list}`**（否则两个分页主体，E3001） |
| `sort_field` / `sort_order` | 默认排序字段（内置字段或 `indexed` 的自定义字段）与方向；**`prenext` 的"上一篇/下一篇"按它确定** |
| `per_page` | 列表默认每页条数 |
| `seo_title_field` / `seo_desc_field` | 覆盖 SEO 标题/描述所用字段 code |
| `options` | jsonb：该类型的页面类型开关（是否出标签页 / 归档页 / feed / 筛选页，§7.2）与显示期选项 |
| `status` / `sort` / `deleted` | |

**内置类型**（站点创建时自动生成，可改名改模板，不可删除）：

| code | 名称 | kind | 说明 |
|---|---|---|---|
| `article` | 资讯 / 文章 | `CONTENT` | 资讯、博客、新闻通稿 |
| `single` | 单页 | `SINGLE` | 关于我们、隐私政策、服务条款、联系我们、感谢页 |
| `author` | 作者 | `CONTENT` | **v2.1 新增**：作者是一个内容类型，因此作者页就是它的详情页（`/author/{slug}/`），作者可加自定义字段（简介、头像、社媒），页面上的文章列表用 `{cms:list relate='field:authorId'}`（可分页，§6.3）。内容通过 `author_id` 指向它（§2.3） |

`tag` 不作类型（标签是 `cms_tag`，另有总览页与详情页两类页面，§7.2）。

> **v2.1 修改（§0.2 第 18 条）**：v2 把内置单页类型的 code 定为 `page`，与分页作用域 `page`（`page.pageNo` / `page.empty`）同名冲突，`[field:page.xxx/]` 的归属不可判定。现改为 **`single`**，后台显示名仍是"单页"。
> **v2.1 修改（§0.2 第 8 条）**：v2 把"作者"列为 S 类页面却只有一个字符串 `authorName`，现补为内置类型 + 关联列。
> **v2.1 修改（§0.2 第 15 条）**：v2.1 草案里曾有一个 `sub_index_pattern` + `SUBLIST` 页面类型；定稿时取消了——"书详情页 + 分页目录""专题页""作者页""系列页"需要的是**详情页承载一个可分页列表**，这由 `detail_url_pattern` 的 `{n}` 与 §4.5 的分页主体规则直接表达，不需要新的页面类型。

### 2.2 字段定义

表 `cms_field`（属于某个类型）：

| 列 | 说明 |
|---|---|
| `type_code` / `code` | 所属类型 / 字段名（模板里 `[field:xxx/]`）。**保留名不得使用**：`site` `channel` `page` `param` `query` `item`（六个，§5.1） |
| `label` | 后台显示名 |
| `field_type` | 见下方类型表 |
| `formatter` | 输出格式化器（见下表），空为默认 |
| `raw` | 是否原样输出（富文本 = 1）。**模板作者无权改**，由字段定义决定 |
| `required` / `default_value` / `options` | 必填 / 默认值 / ENUM 与 ENUM_MULTI 的选项（`值:标签` 列表，逗号分隔） |
| `searchable` | 是否进静态搜索索引（§7.5） |
| `indexed` | 是否可被 `where` / `orderby` / `relate` 使用（进索引表，§2.5） |
| `help` / `sort` / `deleted` | |

**字段类型 × formatter**（"不放开表达式"的补偿全在这儿：能算的、能格式化的，都在字段层算完）：

| 字段类型 | 存储 | 默认输出 | formatter 与参数 |
|---|---|---|---|
| `TEXT` | 文本 | 转义文本 | `maxlen='20'`（按字符截断 + `…`）；`mask='phone\|email'`；`upper` / `lower` |
| `TEXTAREA` | 文本（多行） | 转义文本 + `<br>` | 同 `TEXT` |
| `RICHTEXT` | HTML（入库时清洗，白名单见 §5.2.4） | 原样（`raw=1`） | 无。**严禁**模板侧关闭清洗 |
| `MARKDOWN` | Markdown 源 + 预渲染 `contentHtml` | 用 `[field:contentHtml/]` | 入库预渲染（§5.2） |
| `INT` | bigint | 数字 | `number`（千分位）/ `compact`（1.2万）/ `filesize`（字节→KB/MB）/ `duration`（秒→时:分:秒）/ `percent` |
| `DECIMAL` | numeric | 数字 | 同上 + `money`（`symbol='¥'`，两位小数）/ `fixed='2'` |
| `BOOL` | smallint | `1` / `0` | `yesno`（`yes='有' no='无'`）；**`show`（真→输出 `yes` 的值，假→输出空串）**，用于拼 class |
| `DATE` / `DATETIME` | timestamptz | `Y-m-d` | 模式串（`Y m d H i s`）/ `relative`（"3 天前"）/ `iso`（JSON-LD 用）/ `weekday` |
| `ENUM` | 选项值 | 存储值 | **`label`（输出选项标签——避免模板写 if 链，最关键的一个）** |
| `ENUM_MULTI` | 选项值数组 | 逗号分隔的标签 | `label`（输出标签串）；`{cms:foreach}` 迭代出 `value` / `label`；可被 `where` 用 `in` 过滤（§2.5） |
| `COLOR` | 文本（`#rrggbb`） | 转义文本 | 无（校验入库时的格式） |
| `IMAGE` | 媒体 id | url | `size='thumb\|medium\|large'`（派生尺寸，§7.7）；`[field:x.width/]` `[field:x.alt/]` `[field:x.title/]` |
| `IMAGES` | 媒体 id 数组 | 首图 url | `{cms:foreach}` 迭代；`[field:images.0.url/]` 按下标；`[field:images.count/]` 取个数（§5.1） |
| `FILE` / `FILES` | 附件 | url | `filesize`；`[field:x.name/]` `[field:x.ext/]` `[field:x.size/]` `[field:x.mime/]` |
| `RELATION` | 内容 id 数组（可指定目标类型） | — | `{cms:foreach field='related'}`，每项带目标类型的全部字段；`[field:related.count/]`；**要分页时改用 `{cms:list relate='field:<code>'}`**（§6.3）；**数组顺序 = 录入顺序**，专题页要保持人工挑稿的顺序就用 `orderby='relationOrder'`（§6.3）；字段定义可勾 `crossSite=1`（**v2.2**），允许指向配对站点的内容，用于多语言互链（§11.5） |
| `TAGS` | 标签 id 数组 | 逗号分隔名 | `{cms:foreach}` 迭代出 `name` / `url`；`[field:tags.count/]` |
| `JSON` | jsonb | — | 多段字段名访问 `[field:specs.weight/]`；`{cms:foreach}` 迭代键值对（规格参数表） |

**formatter 的调用语法写死**（**v2.2 补**：v2.1 的表里一半写参数（`maxlen='20'`、`size='thumb'`）、一半写裸名（`upper`、`number`、`relative`），而 §3.3 的参数文法只能表达 `key=value`——`[field:x number/]`、`number='1'`、`format='number'` 三种写法谁合法，v2.1 没有答案）：

1. **规范形式**：`[field:publishTime format='relative'/]`——`format` 取上表最后一列的**名字**（`maxlen` / `mask` / `upper` / `lower` / `number` / `compact` / `filesize` / `duration` / `percent` / `money` / `fixed` / `yesno` / `show` / `label` / `relative` / `iso` / `weekday` / `size`）；日期模式串直接写 `format='Y-m-d'`。
2. **快捷形式**：参数名**只属于一个** formatter 时可直接写该参数（`show='is-hot'`、`money='1'`、`size='thumb'`、`maxlen='20'`、`mask='phone'`、`fixed='2'`、`symbol='¥'`），引擎按参数名反推 formatter。这批"专属参数名"是封闭清单，写表外的参数名 → E1002。
3. **一个字段只能有一个 formatter**（`format='number' money='1'` → E1002）："先格式化再格式化"没有定义。
4. **组合合法性由字段类型决定**（`size` 只对 `IMAGE` / `IMAGES`，`money` / `fixed` / `symbol` 只对 `DECIMAL` / `INT`，`maxlen` / `mask` 只对文本类）；非法组合 → E1002，文案里必须列出该字段类型可用的 formatter。

**关于视频 / 音频**：**不新增 `VIDEO` 字段类型**。产品视频、播客音频用「`FILE` 字段（拿 `url` / `ext` / `mime` / `size`）+ `IMAGE` 封面 + `INT duration` 时长字段」三者组合，模板写三行即可（§14 I13）。理由：媒体元数据（码率、分辨率、转码）是动态层与对象存储的活，塞进静态引擎只会让字段表长出一条永远不完整的类型。代价如实记录：模板作者要自己写 `<video>` 标签与封面，引擎不替他决定。

**派生字段**（引擎维护，任何类型都有，模板直接可用）：

| 分类 | 字段 |
|---|---|
| 标识与 URL | `id` `typeCode` `typeName` `url` `canonical` `slug` |
| 层级（**v2.1 新增父链**） | `parentId` `parentTitle` `parentSlug` `parentUrl` `parentTypeCode` `ancestors`（祖先数组，迭代出 `id`/`title`/`url`/`typeCode`）`childCount` `hasChildren` |
| 分类与标签 | `categoryId` `categoryName` `categoryPath` `categoryUrl` `categories`（多分类时迭代，每项带 `dimension`）`tags`（迭代出 `name` `slug` `url` `count`） |
| 作者 | `authorId` `authorName` `authorUrl` `authorAvatar` |
| 正文计量 | `wordCount`（正文字数）/ `readingMinutes`（阅读时长）/ `imageCount` / `toc`（正文标题树，迭代出 `level` `text` `id` `url`） |
| 时间 | `publishTime` `updateTime` `createTime` `updatedDaysAgo` |
| 互动与运营 | `viewCount`（§9.4 维护，静态页里是**发布时刻的快照**）`viewCountDay` `viewCountWeek`（滚动 24 小时 / 7 天，日榜与周榜的排序依据，§9.4）`commentCount`（已通过评论数，§9.6）`ratingAvg` `ratingCount`（评分快照，§9.6）`top` `recommend` |

> **v2.2 新增**：`viewCountDay` / `viewCountWeek` / `commentCount` / `ratingAvg` / `ratingCount` 都是 `cms_content` 上的**真实列 + 派生字段**（§2.3）。v2.1 的 §9.6 已经在用 `ratingAvg` / `ratingCount`，但派生字段表里没有它们——按 §4.5 第 4 条会被判 E1004，属于"文档自己承诺了却取不到"。
| 位置（**v2.1 新增**） | `current`(BOOL) `class`（"这一项就是当前页面的当前条目"——列表内高亮当前项靠它，§5.5） |

**内置字段**（无需声明，`cms_content` 上有真实列，见 §2.3）：
`id` `title` `slug` `summary` `cover` `status` `publishTime` `expireTime` `createTime` `updateTime` `sort` `top` `recommend` `authorId` `authorName` `viewCount` `seoTitle` `seoDescription` `seoKeywords` `contentFormat` `parentId`。

> **v2.2 新增**：`expireTime`（`cms_content.expire_time`，`null` = 不过期）——它是**可编辑的内置字段**（活动页、广告位排期要人填到期时间），因此进这张清单，可被 `where` 使用（见下表）。`viewCountDay` / `viewCountWeek` / `commentCount` / `ratingAvg` / `ratingCount` 则属**派生字段**（系统维护，不可编辑），列在上面的派生字段表里。

> **v2.1 修改（§0.2 第 17 条）**：v2 的派生字段里有一个 `content` 作用域别名与 `prevUrl` 的自问自答（"`prevUrl`？否——上下篇走 `{cms:prenext}`"），现按上表整理；`content` **不再是作用域名**，它只表示正文字段。
> **v2.1 修改（§0.2 第 6、19 条）**：上表把 `parentId` 从"列"提升为"内置字段"，把 `viewCount` / `seoTitle` 等从"空头支票"落到 §2.3 的真实列；全篇统一用 `publishTime`（不再有 `pubdate`）。
> **v2.1 新增（§0.2 第 29 条）**：内置字段与派生字段的**可筛选能力**不由 `cms_field.indexed` 决定（那是个自定义字段的列），而由下表固定：

| 可用于 | 字段 |
|---|---|
| `where` | `id` `slug` `typeCode` `status` `publishTime` `expireTime` `updateTime` `sort` `top` `recommend` `authorId` `parentId` `viewCount` `viewCountDay` `viewCountWeek` `commentCount` `wordCount` `categoryId` `tagId`（运算符白名单见 §2.5；**值一律用 id**，见 §2.5） |
| `orderby` | `publishTime` `updateTime` `sort` `viewCount` `viewCountDay` `viewCountWeek` `commentCount` `wordCount` `id` `top` `recommend` |
| `relate` | `tag` `category` `field:<code>`，其中 `<code>` 可以是任意 `indexed=1` 的自定义字段，或上表 `where` 列里的内置字段 |

**为什么把格式化放进字段层**：模板作者的每一次"我想算一下"，正确的落点都是**字段定义**（formatter 或派生字段），而不是给语法加表达式。这条准则从 v1 §5.5 继承并扩大：v1 是"引擎多给一个预计算字段"，v2 是"**字段定义驱动引擎预计算**"——引擎不认识业务，只认识字段。

### 2.3 内容存储

表 `cms_content`：

| 列 | 说明 |
|---|---|
| `id` / `site_id` / `type_code` | |
| `parent_id` | 层级内容的父（`chapter` → `book`）；`0` 为根 |
| `slug` / `title` / `summary` / `cover` | 通用列：**查询与 URL 只依赖这些通用列 + 索引表**，不依赖 jsonb |
| `status` | `DRAFT` / `PUBLISHED` / `OFFLINE`（沿用现有取值） |
| `sort` / `top` / `recommend` | |
| `publish_time` / `create_time` / `update_time` / `create_by` / `update_by` / `deleted` | |
| `author_id` / `author_name` | **v2.1 新增**：作者指向 `type_code='author'` 的内容项；`author_name` 是**冗余快照**，用于"作者被删除后旧内容仍显示名字"，保存时由引擎写入，不手工编辑 |
| `view_count` | **v2.1 新增**：浏览量，由 §9.4 动态层累加、由 §8.8 定时重建刷进静态页 |
| `content_format` | `RICHTEXT` / `MARKDOWN`（沿用 `cms_article` 的取值），**同时决定 `content` 入库时走清洗还是预渲染** |
| `seo_title` / `seo_description` / `seo_keywords` | 覆盖站点级 SEO；为空则按类型的 `seo_title_field` / `seo_desc_field` 回退到自定义字段（§7.6） |
| `data` | `jsonb`：自定义字段值，读取专用 |
| `content` / `content_html` | 正文与预渲染结果（`RICHTEXT` 清洗后 / `MARKDOWN` 预渲染后） |
| `content_toc` | `jsonb`：正文标题树（`toc` 派生字段的存储形态），入库时抽一次（§5.2.5） |
| `word_count` | 正文字数（`wordCount` / `readingMinutes` 的来源，也允许 `orderby`） |
| `expire_time` | **v2.2 新增**：到期时间（`null` = 不过期）。查询恒带 `publish_time <= now()` **且** `expire_time IS NULL OR expire_time > now()`（§6.3）——活动页、广告位排期、限时专题都靠它，到点由 §8.8 的每分钟任务触发增量 |
| `view_count_day` / `view_count_week` | **v2.2 新增**：滚动 24 小时 / 7 天的有效浏览数（日榜 / 周榜的排序依据）。由 §9.4 的批量累加一并维护，由 §8.8 的榜单重建刷进静态页 |
| `comment_count` | **v2.2 新增**：已通过（`APPROVED`）评论数。列表页展示评论数、"按热度排序"都靠它；评论审核动作与它同事务维护（§9.6） |
| `rating_avg` / `rating_count` | **v2.2 新增**：评分快照（提交评分时同事务更新），模板用 `[field:ratingAvg/]` / `[field:ratingCount/]`，`cms.js` 用实时值覆盖（§9.6） |

**约束**：
- `slug` 站点内 + 类型内唯一；`chapter` 一类**同一 `parent_id` 下唯一**（`cms_content` 上加两个部分唯一索引，见 §12.1 的迁移清单）。
- 层级内容禁止环（保存时沿 `parent_id` 上溯校验）。
- `SINGLE` 类型在每个站点**至多一条**（部分唯一索引兜底）。
- **主分类恰一个**：`cms_content_category.dimension='primary'` 每个内容至多一行（§2.4），它决定 `categoryUrl`、面包屑与 `canonical`。

> **v2.1 修改（§0.2 第 8、9、17、19、26 条）**：v2 的内置字段清单（`authorName` `seoTitle` `viewCount` …）在这里没有对应的列，而 v2 又要求"查询与 URL 不依赖 jsonb"。现按上表把这些字段全部落成真实列；`parent_id` 同时进内置字段清单（v2 漏了，直接导致章节页拿不到父书）。
> **v2.1 修改（§0.2 第 32 条）**：多对多分类只保留"主分类 + 副分类"两个维度，**主分类唯一**由数据库兜住。

### 2.4 分类 / 标签 / 关系

- `cms_category` **保留并复用**：分类树是"栏目"，与内容类型正交（一个类型可挂多个分类维度，见下）。
- `cms_content_category`：内容 ↔ 分类（多对多，带 `dimension` 列：`primary` / `secondary` 两个维度，`primary` 每内容至多一行——它决定 `categoryUrl`、面包屑第二层与 `canonical`）。
  - **两个分类维度不能用来表达"同一内容属于 A 与 B 两个栏目"的两种 URL**：详情页 URL 只有一个（§7.1），`canonical` 就是它自己；另一条路径不生成页面，避免"同一内容两个 URL"这类收录事故。
- `cms_content_tag`：内容 ↔ 标签（替代 article 专用的 `cms_article_tag`，旧表保留到 `article` 迁移完成）。
- **父子关系**用 `cms_content.parent_id`（小说：章 → 书；文档：篇 → 章）。
- **任意多对多**用 `RELATION` 字段（"相关产品 / 相关案例"），不需要新表。

**导航菜单**（**v2.1 新增**，解决官网"栏目 + 单页 + 外链"混排导航、页脚导航、友情链接、行业站"六个类型 = 六个导航项"）：

表 `cms_menu`：`id` / `site_id` / `code`（`main` `footer` `friend`…，站点内唯一）/ `name` / `status` / `sort` / `deleted`。

表 `cms_menu_item`：

| 列 | 说明 |
|---|---|
| `menu_id` / `parent_id` | 所属菜单 / 父项（支持二级下拉） |
| `label` | 显示文字（为空则取所指向对象的名称） |
| `kind` | `category`（分类，含子分类树）/ `content`（某条内容，如单页"关于我们"）/ `type`（**内容类型的列表页**，行业站六个导航项就是六行 `type`）/ `tag` / `archive` / `author` / `url`（外链，`url` 直接给）/ `custom`（纯占位父项，不指向任何东西） |
| `ref_id` / `ref_code` | 指向对象的 id / code（`kind='type'` 时用 `ref_code`；`kind='url'` 时用 `url`） |
| `url` | `kind='url'` 时的外链地址（站内相对路径或 `https://`） |
| `target` / `rel` | `_blank` / `nofollow` 等 |
| `visible` / `sort` | 是否可见（下线但保留结构）/ 排序 |

- 读取方式：`{cms:channel source='menu' code='main'}`（§6.4）。**引擎照旧算 `current` / `class`**——"这一项是不是当前页"由引擎比对得出，模板里不写比较（§5.5、§3.8）。
- **不提供**"菜单项带权限/按角色隐藏"：那是动态层的活；静态页里的菜单对所有访客相同。
- 代价如实记录：菜单是**数据**，因此改导航要动数据 + 重建产物（§8.4 的依赖清单里，菜单变更 = 全站导航失效）。换来的是"外链、单页、类型、分类"能在同一个迭代里混排，而模板里一行比较都不用写。

### 2.5 字段索引表（**不做动态 DDL 的代价与对策**）

表 `cms_content_index`：`(site_id, content_id, type_code, field_code, value_key, value_type, num_value numeric, str_value varchar(200), time_value timestamptz)`，唯一键 `(content_id, field_code, value_key)`。

- 字段定义勾了 `indexed=1` 才写索引；内容保存事务内维护（同事务增删）。
- 索引：`(site_id, type_code, field_code, num_value)` / `(…, str_value)` / `(…, time_value)`。
- **`where` / `orderby` / `relate` 用到未声明 `indexed` 的自定义字段 → 编译期报错**，文案提示"到字段定义里勾选『可筛选』"（§10 E2007）。报错优于静默全表扫。内置字段不需要 `indexed`，它们的可筛选性由 §2.2 的固定白名单决定。
- `where` 运算符白名单：`eq` `ne` `gt` `gte` `lt` `lte` `like` `in` `has`；多条件**全部 AND**（没有 OR，见 §3.8）。
- **`where` 的语法与转义**（**v2.1 修改**，v2 在这里是不可判定的）：

  ```
  where='brand:eq:acme,price:lte:9999,tagId:has:3'
        └─ 条件之间用 , 分隔（AND）
        └─ 条件体是 code:op:value 三段，value 里允许出现 : （按第一个 : 之后到下一个 , 为止整段取值）
        └─ op=in 时，多值用 | 分隔：where='region:in:huadong|huanan'
        └─ op=like 时不支持通配符语法，一律做"包含"匹配：where='title:like:新品'
  ```
  - `in` 的值分隔符是 `|`，因为逗号已经被 AND 占用（§0.2 第 20 条）。
  - `has` 专用于 `ENUM_MULTI` / `TAGS` / `RELATION` 这类多值字段："包含该值"。
- **字段 code 与取值写死**（**v2.2**）：只接受 §2.2 白名单里的 code（标签 `tagId`、分类 `categoryId`、类型 `typeCode`），**值一律用 id**（`tagId` / `categoryId` 是数字 id；`typeCode` / `slug` 是字符串）。v2.1 的示例写了 `tag:has:home`——既不是白名单 code、值又是 slug，照它写必然 E2007。
  - 值里需要真的写 `,` 或 `|` 时，用 `\,` / `\|` 转义（这是**参数层的转义**，与 §3.4 的字面量转义是两回事，两者可叠用）。
- **`ENUM_MULTI` 的索引是"每个取值一行"**（`value_key` 就是取值），因此 `has` / `in` 都能走索引；这也是唯一键要带 `value_key` 的原因。
- **跨类型聚合**（"全站最新"混合类型）**不走索引表**：`site_id` / `status` / `publish_time` 都在 `cms_content` 公共列上，一条普通查询即可，`type='all'` 直接表达（§6.3）。索引表只为"自定义字段筛选"存在。

**如实记录的代价**：写放大（每条内容多 N 行索引，多值字段更多）、`where` 的表达力被限制在单字段比较、多值字段的索引行数随取值个数上涨。换来的是：**字段定义不会改动表结构**（动态 DDL 在 PostgreSQL 上是运维灾难），且查询计划稳定可预测。

> **v2.1 修改（§0.2 第 5、28 条）**：v2 把"跨类型聚合"写成"走索引表按 `type_code` 分派取详情"，并给了一个 §6.3 里根本不存在的参数。现改为走公共列 + `type='all'`，索引表的职责收窄为"自定义字段筛选"。
> **v2.1 修改（§0.2 第 20 条）**：v2 的 `where='brand:in:A,B'` 与"逗号 = AND"冲突，规范里没有转义约定，不可判定。

### 2.6 `article` 的落地顺序（**v2.1 改为一次收敛**）

| 期 | 做法 |
|---|---|
| 期 1 | 建 `cms_content` / `cms_content_type` / `cms_field` / `cms_content_index`；**标签实现只依赖 `ContentProvider` 接口**，`article` 是它的第一个实现（后端可先指向 `cms_article`，让引擎内核先跑通） |
| 期 2 | 通用类型（`product` / `book` / `job`…）走 `cms_content`；`article` 的写入路径切到 `cms_content`，`cms_article` 的存量数据由一次性迁移导入（§12.1 的迁移清单第 9 条） |
| 期 3 | 后台内容管理统一走新表（按类型统一入口），`cms_article` / `cms_article_tag` 与相关代码一起删除。**不做逐类型长期双轨**（落地记录见 §0.4） |

**为什么推翻 v2 的双轨**：双轨期的真实代价是"`article` 与自定义类型不能跨类型联合排序取详情"（v2 自己写的），而"全站最新"恰恰是首页第一需求。项目此刻**没有任何存量数据**（`sites/` 为空、`cms_article` 只有种子数据），收敛成本最低；等到小说站上线再收敛，就要一边服务线上一边搬数据。

**如实记录的代价**：后台文章列表与编辑器要改接新表（这部分工作本来就要做，因为要支持自定义字段与类型）；迁移脚本要处理 `cms_article_tag` → `cms_content_tag`、`cms_article.content_format` → `cms_content.content_format` 的映射。换来的是：**只有一套内容表、一套查询、一套标签实现**，跨类型列表不需要任何"分派"逻辑。

### 2.7 站点级配置与发布选项（**v2.1 新增**）

`site` 作用域（§5.1）的 key 必须有一份**封闭清单**——因为"找不到即报错"（§5.1），省略号是承重的。清单来源两处：

**（1）`cms_site` 现有列**（`V20261001000500__cms_site.sql`）：
`id` `name` `code` `domain` `logo` `description` `keywords` `seoDescription` `rootDir` `icp` `contactPhone` `contactEmail`。

**（2）`cms_site` 新增列**（**v2.1**，全部走新迁移文件 `ALTER TABLE`）：

| 列 | 对应 `site` key | 说明 |
|---|---|---|
| `lang` | `site.lang` | 站点主语言（`zh-CN` / `en`…），写进 `<html lang>` 与 hreflang（§7.1） |
| `default_cover` | `site.defaultCover` | 站点默认图：内容 `cover` 为空时输出它（v2 §5.5 承诺过这个字段却没说它在哪） |
| `og_image` | `site.ogImage` | 社交分享默认图 |
| `theme` | `site.theme` | 当前主题名（§7.3 模板查找的第一层） |
| `protocol` | `site.protocol` | `https` / `http`，用于拼绝对 URL（sitemap / feed / canonical 都要绝对地址） |
| `statistics_code` | `site.statisticsCode` | 统计脚本（raw，模板用 `[field:site.statisticsCode/]` 原样输出） |

另有三个**派生 key**（不是列，由引擎算出来放进 `site` 作用域）：
`site.url`（站点根绝对地址，= `protocol://domain`）、`site.year`（当前年份，页脚版权用）、`site.alternates`（多语言配对列表，可 `foreach` 出 `lang` / `url` / `current`，来源是下面的 `i18n.alternates` 选项）。

**（3）站点发布选项**表 `cms_site_publish_option`：`(site_id, option_code, value)`，用开关控制"这个站点要不要出这一类页面"，避免小站生成一堆空页面：

| option_code | 默认 | 作用 |
|---|---|---|
| `page.category` | `1` | 出分类索引页 |
| `page.tag` / `page.taglist` | `1` / `1` | 出标签详情页 / 标签总览页 |
| `page.archive` | `0` | 出年/月归档页（资讯站开，官网关） |
| `page.author` | `1` | 出作者页（`author` 类型详情页） |
| `page.facet` | `0` | 出筛选落地页（§7.4 要显式配置组合，不做全排列） |
| `page.search` | `1` | 出搜索页壳 + 搜索索引 |
| `page.feed` | `1` | 出 feed |
| `page.redirect` | `1` | 出 `redirects.conf` |
| `neighbor.limit` | `1` | `prenext` 按类型还是按父（§6.4 的默认值） |
| `index.shardSize` | `2000` | 搜索索引分片大小（§7.5） |
| `publish.keepReleases` | `3` | 保留最近几个发布批次（§8.7） |
| `publish.syncTarget` | 空 | 产物同步目标（`/www/wwwroot/<site>`），空表示不搬运（§8.10） |
| `publish.cron` | `0 3 * * *` | 定时重建时刻（§8.8） |
| `url.tag` / `url.tags` / `url.archive` / `url.search` / `url.thanks` / `url.facet` | `/tag/{tagSlug}/`、`/tags/`、`/archive/{year}/{month}/`、`/search/`、`/thanks/`、`/f/{facetPath}/` | 站点级 URL 规则（跨类型，故不放在类型定义里，§7.1.1） |
| `pages.static` | `[{code:'thanks', url:'/thanks/', template:'thanks.html', type:'single'}]` | 非内容驱动的静态页（感谢页、联系我们落地页） |
| `facets.combos` | `[]` | 手工配置的交叉筛选页（§7.4 死限：不做全排列） |
| `facets.maxPages` | `500` | 筛选页总数上限，超出报错 E4005 |
| `facets.cardinality` | `50` | 单字段 facet 取值数上限，超出不生成 |
| `search.mode` | `static` | `static` / `api` / `off`（§7.5） |
| `search.staticMax` | `50000` | 超过则自动切 `api` |
| `search.bodyChars` | `1000` | 索引里正文截断长度 |
| `sitemap.shardSize` | `10000` | sitemap 分片条数 |
| `feed.format` / `feed.size` / `feed.types` | `rss` / `20` / `['article']` | feed 形态与条数 |
| `seo.paginatedIndex` | `0` | 正文分页第 2..N 页是否允许收录（§7.1.4） |
| `seo.facetIndex` | `0` | 筛选页是否允许收录（§7.4） |
| `toc.levels` | `h2,h3` | 正文目录抽取的标题层级（§5.2.5） |
| `reading.speed` | `400` | 每分钟阅读字数，用于 `readingMinutes` |
| `i18n.alternates` | `[]` | 多语言配对：`[{lang:'en', domain:'www.example.com'}]`（§11.5） |
| `media.derive` | `320,768,1280` | 三档派生尺寸的长边（改这里要重建派生文件） |
| `media.host` | 空 | 媒体 URL 前缀（CDN / 对象存储域名）；空 = 站内 `/uploads`。对应 `site.mediaHost`（§11.2） |
| `url.home` | `/page-{n}/` | **v2.2 新增**：首页列表分页的 URL 规则（§7.1.3 的 HOME 行）；首页只有 1 页时不使用 |
| `url.list` | `/{categoryPath}/page-{n}/` | **v2.2 新增**：**分类索引页**的 URL 规则（§7.2.1：类型列表页用类型的 `list_url_pattern`，分类索引页用本项） |
| `page.tagMinCount` | `1` | **v2.2 新增**：标签页（`TAGPAGE`）的生成门槛——`count ≥ 该值` 才出页。与 `{cms:tagnav}` 的 `minCount` **参数**分工不同：那个只管导航上显示几个（§6.4） |
| `pager.labels` | `首页,上一页,下一页,末页` | **v2.2 新增**：`{cms:pagelist}` 中 `first`/`prev`/`next`/`last` 四项的文案（§6.4） |
| `publish.mode` | `incremental` | **v2.2 补声明**：`incremental` / `full`（`full` = 忽略差异强制全量重渲染，§8.4） |
| `publish.threads` | `min(4, CPU)` | **v2.2 补声明**：页级渲染线程数（§8.3） |
| `publish.pageTimeout` | `10` | **v2.2 补声明**：单页渲染超时（秒），超时按失败计（§8.3） |
| `publish.debounce` | `5` | **v2.2 补声明**：内容变更的合并窗口（秒），连续保存只触发一个批次（§8.8） |
| `publish.rankCron` | `每小时` | **v2.2 补声明**：榜单快照重建频率（§8.8） |
| `publish.preview` | `0` | **v2.2 补声明**：`1` = 产物写 `preview/www/`，人工确认后再提升（§8.7） |
| `publish.strict` | `0` | **v2.2 补声明**：`1` = 第 ⑦ 阶段校验失败即让批次失败（§8.1） |
| `publish.expireRedirect` | `0` | **v2.2 新增**：内容到期下线时是否同时写一条 301（§8.8） |
| `comment.moderate` | `1` | **v2.2 补声明**：评论先审后发（§9.6） |
| `comment.snapshot` | `1` | **v2.2 补声明**：是否把已通过的评论渲染进静态页（§9.6） |
| `comment.snapshotSize` | `20` | **v2.2 补声明**：评论快照条数（§9.6） |
| `seo.noindexTypes` | `[]` | **v2.2 新增**：默认 `noindex` 的内容类型 code 列表（如 `['chapter']`）——章节页要不要收录是站点策略，不能只有页面类型一个维度（§7.6） |
| `feed.includeBody` | `0` | **v2.2 新增**：`1` = feed 条目带正文（全文订阅，§7.6） |

**这张表是封闭清单**（**v2.2 强调**）：选项键一律写成 `命名空间.驼峰`，后台保存与迁移都按它校验，**表外的键一律报错，不静默忽略**。
`publish.siteConcurrency`（默认 2）是**引擎全局配置**（`application.yml`），不是站点选项——它列在 §8.1 而不是这里；v2.1 把它写成"站点选项"是笔误。

---

## 3. 语法规格

### 3.1 词法元素（**仍然只有 5 种**）

| # | 元素 | 写法 | 说明 |
|---|---|---|---|
| 1 | 文本 | 任意 | 原样输出，**永不转义** |
| 2 | 块标签 | `{cms:NAME ARGS}…{/cms:NAME}` | 有体，可嵌套 |
| 3 | 自闭合标签 | `{cms:NAME ARGS/}` | 无体 |
| 4 | 块结束 | `{/cms:NAME}` | 仅作配对标记 |
| 5 | 字段引用 | `[field:NAME ARGS/]` | 从上下文栈解析，任意位置可用 |

`{cms:else/}` **在词法上就是元素 3**（一个名为 `else` 的自闭合标签），它的特殊性完全在解析层与位置约束上（§3.5）。v2 没有新增第 6 种词法元素。

### 3.2 EBNF

```ebnf
template    = { text | block | self_tag | field } ;
text        = ? 任意不属于下述起始序列的字符 ? ;
block       = "{cms:" name [ args ] "}" template "{/cms:" name "}" ;
self_tag    = "{cms:" name [ args ] "/}" ;
field       = "[field:" field_path [ args ] "/]" ;
args        = { arg } ;
arg         = key "=" value ;
key         = ? [A-Za-z][A-Za-z0-9_]* ? ;
value       = "'" quoted "'" | '"' quoted '"' | bare ;
name        = ? [a-z][a-z0-9_]* ? ;
field_path  = segment { "." segment } ;                 (* v2：由 1 段放宽为多段 *)
segment     = ? [A-Za-z_][A-Za-z0-9_]* ? | ? [0-9]+ ? ;
```

- `field_path` 首段是保留名（`site` `channel` `page` `param` `query` `item`）时按**具名作用域**解析（§5.1）；否则从栈顶向下就近解析。
- `[field:images.0.url/]`（多值按下标）、`[field:specs.weight/]`（JSON 取键）、`[field:images.count/]`（多值取个数）都由此文法覆盖，**无新增语法要素**。
- **保留名就是 6 个，没有第二份清单**：§2.1 的类型 code、§2.2 的字段 code、`{cms:include}` 的参数名都不得与它们同名（后台保存与编译期各校验一次）。
- **起始序列一出现就进入标签解析**（**v2.2 定死**）：`{cms:` 之后不是合法 `name`（如 `{cms:123}`、`{cms: }`、文末孤立的 `{cms:`）→ E1001，**不退回当作文本**；`[field:` 同理。要输出字面量请用 §3.4 的反斜杠转义。

> **v2.1 修改（§0.2 第 17 条）**：v2 在这里只列了 4 个保留名，而 §5.1 列了 6 个（多 `query` `content`），且 `content` 同时是作用域名与正文字段名。现统一为 6 个，并把作用域名改为 `item`（`content` 从此只表示正文）。

### 3.3 参数语法

- 三种值形式：`key='value'`、`key="value"`、`key=value`（裸值取到空白或 `}`/`/>` 为止）
- 引号内支持转义：`\'` `\"` `\\`；其余反斜杠原样保留
- **引号内的 `}` 与 `/` 不结束标签**：`{cms:include file='a}b.html'/}` 合法。解析器必须感知引号状态——这是最容易写错的一处
- 参数之间以空白分隔，允许跨行
- 每个标签通过 `ParamSpec` 声明自己的参数表；**未声明的参数报错**，报错时列出全部可用参数
- 类型：`STRING` / `INT` / `BOOL` / `ENUM`；`BOOL` **只接受** `0` `1` `true` `false`（忽略大小写），其余值 → E1002；转换失败报错并给出实际值
- **同一个标签里同一个 key 出现两次 → 报错（E1002）**，不做 last-wins（静默取一个等于把笔误变成长久行为）
- 参数值**永远是字面量**，不做字段插值（§1.3）

### 3.4 字面量转义

模板里需要真实输出 `{cms:` 或 `[field:` 字面字符时，在起始字符前加反斜杠：

```
\{cms:list}    →  输出字面量 {cms:list}
\[field:x/]    →  输出字面量 [field:x/]
```

反斜杠只在紧邻上述起始序列时生效，其余位置的 `\` 原样输出（避免破坏 Windows 路径、正则等文本）。

### 3.5 v2 语法裁定（**新增的两条，逐条记录代价**）

**裁定四：`{cms:else/}` 提供，它是"块内分隔"，不是新词法元素。**

- 位置约束（**死限**）：只能作为 `{cms:if}` 的**直接子节点**，且同一个 `{cms:if}` 内最多一个；出现在其他任何位置 → 报错，文案见 §10。
- 配对规则：`{cms:else/}` 绑定到**最近的、尚无 else 的祖先 `{cms:if}`**；因为限定为直接子节点，实际就是它所在的 `{cms:if}`。
- 语义：`{cms:if field='x'}A{cms:else/}B{/cms:if}`——`x` 为真渲染 A，否则渲染 B。
- **诚实交代**：v1 §2.6 裁定二拒绝 `{cms:empty}` 的理由正是"需要块内分隔这一新语法要素"。v2 在 `{cms:if}` 内接受了它，因为通用字段系统下**引擎无法替每个自定义布尔字段提供反相**（v1 §5.5 的"成对提供正反两相"只在"引擎认识所有字段"的固定内容模型里成立）。代价被限制在：范围只有 `if` 的直接子节点 + 最多一次 + `{cms:empty}` 依旧不提供（空集语义绑定在"某个迭代为空"上，跨块耦合，用查询标签统一提供的 `empty` 字段覆盖）。

**裁定五：多值字段用 `{cms:foreach}` 迭代，不引入"循环语法"。**

- `{cms:foreach field='images' row='8' orderby='sort'}` 是普通块标签（元素 2），迭代**任何多值字段或预加载列表**：`IMAGES` / `FILES` / `RELATION` / `TAGS` / `JSON` / `ENUM_MULTI`，以及引擎预加载的 `children`（`depth>1`）、`toc`、`categories`、`ancestors`、`tags`、`site.alternates`（多语言配对，每项 `lang` / `url` / `current`，**v2.2 补**：v2.1 的 §7.1.4 已经在 `foreach` 它，但这份清单里没有，按 §4.5 第 4 条会被判 E1004）。
- 每次迭代压入一个新的匿名作用域；每项提供 `index`（从 1 开始）、`index0`、`isFirst`、`isLast`、`count`。
- 与 `{cms:query}` / `{cms:list}` 的分工：**`foreach` 迭代"已经在手上的值"，查询标签去数据库取新的列表**。两者不可互相替代——`foreach` **不参与分页**（没有 `page` 作用域），要分页的关系型列表必须用 `{cms:list relate='field:<code>'}`（§6.3）。
- 循环**之外**想要条数，用 `[field:images.count/]`（多值字段路径的 `.count` 段，§5.1）；循环之外想要空状态，直接把多值字段交给 `{cms:if}`（空列表判为假，§3.7 裁定一）。

> **v2.1 修改（§0.2 第 10、27 条）**：v2 在这里既说 `foreach` 能"迭代查询标签产出的列表"，又说它与 `query`"不可互相替代"，而 §6.2 的参数表里根本没有查询参数——三句话不可能同时为真。现已删去"迭代查询结果"的说法（查询标签自带标签体），并补上 `foreach` **不分页**这条死限，与 §6.3 的 `relate` 开放配套。

### 3.6 与织梦的刻意差异（**记录在案，不是遗漏**）

| 织梦 | 本规格 | 原因 |
|---|---|---|
| 字段有两套写法：文章页 `{dede:field.title/}`、循环里 `[field:title/]` | **只保留 `[field:xxx/]`** | 靠上下文栈解析，任何位置都能用；少一套语法就少一类错误 |
| 默认**不转义** | **默认转义**；富文本字段在字段声明里标为 raw | 织梦默认不转义是 XSS 与排版炸裂的源头 |
| 不认识的标签原样输出 | 报错 | 原样输出会让人以为标签生效了 |
| `{dede:php}` / `{dede:sql}` / 插件 | 不提供 | 安全灾难；要业务逻辑就走 §9 的动态层 |
| `function='html2text(@me)'` | 字段 formatter + 派生字段 | `@me` 魔法变量无法做类型检查、无法做补全、无法给出好报错 |
| 一个模板只有"文章列表"一种内容 | 内容类型 + 自定义字段 | 这是 v1 做不成小说站/产品站的根因 |

### 3.7 已裁定不再讨论

语法要素到此为止：§3.1 的 5 种 + §3.5 的两条裁定。任何新增都必须先改本文件。

**裁定一（v1 保留）：`{cms:if}` 提供，但它是一个普通标签，不是语法。**

判定规则（**必须写死**，否则 `top='0'` 会被误判为真）：

| 情况 | 结果 |
|---|---|
| 字段存在，值为 `null` / 空串（trim 后）/ `"0"` / `"false"`（忽略大小写）/ 数字 `0` / 布尔 `false` / **空列表与空 jsonb** / **按数字字面量解析为 0 的字符串**（`"0.0"` `"0.00"` `"00"`） | **假** → 走 `{cms:else/}` 分支或跳过标签体 |
| 字段存在，值为其他任何内容 | **真** → 渲染标签体 |
| 字段名解析不到（作用域里没有这个 key） | **报错**（与 §5.1 一致，属模板作者写错了字段名） |

**判定顺序写死**（**v2.2 补**）：先 `trim`，再按上表比对；因此 `"0.0"` `"0.00"` `"00"` 与 `DECIMAL` 的 `0.00` 一律判假——「价格是 0 就不要渲染价格块」是模板作者的真实预期，反过来会在页面上留下一个孤零零的 `¥0.00`。

```html
{cms:if field='cover'}
  <figure class="cover"><img src="[field:cover/]" alt="[field:title/]"></figure>
{cms:else/}
  <figure class="cover"><img src="/assets/img/no-cover.png" alt="[field:title/]"></figure>
{/cms:if}

{cms:if field='price'}<p class="price">[field:price money='1'/]</p>{/cms:if}
{cms:if field='page.empty'}<p class="none">暂无内容</p>{/cms:if}
```

> **v2.1 修改**：v2 的第一行示例写的是 `{cms:if field='product.price'}`，配同一行的 `[field:price/]` 在 §5.1 的解析规则下不可能同时成立（作用域里没有名为 `product` 的东西）。示例已改为 `field='price'`：**字段路径只按"具名作用域 + 栈内 key"解析，不存在"以类型 code 命名的作用域"**。

**裁定二（v1 保留）：`{cms:empty}` 不提供。**

空集分支要引入"分隔符 + 迭代绑定"两个语义，而查询标签统一提供 `empty` / `hasResults` 字段（§6.3）已覆盖：

```html
{cms:if field='page.empty'}<p class="none">暂无内容</p>{/cms:if}
<ul class="news">
{cms:list type='article' row='20'}
  <li><a href="[field:url/]">[field:title/]</a></li>
{/cms:list}
</ul>
```

**代价如实记录**：占位内容写在 `{cms:list}` **之外**，只能描述"这个列表页没有内容"，无法描述"第 3 页恰好为空"。后者在数据层本就不该出现，可接受。

**裁定三（v1 保留，v2.1 重写放宽手段）：循环嵌套的外层字段访问不提供"向上作用域"语法。**

v1 靠"就近查找"满足绝大多数场景，真需要时由**标签或字段**多给一个值，绝不给语法加"向上找外层作用域"。v2.1 的放宽手段是三条，全部不破语法：

**（1）"锚定项"（anchor）——这是唯一的新增定义，必须写死。**

> **锚定项** = 当前渲染位置所依附的那一条内容（或分类、标签、作者、菜单项）。
> 判定顺序，**先命中先算，没有"或"**：
> 1. 若当前位于 `{cms:list}` / `{cms:query}` / `{cms:foreach}` **的循环体内** → 锚定项 = **栈顶迭代项**；
> 2. 否则 → 锚定项 = **本页面的当前条目**（§5.1，它恒在匿名栈底，`[field:title/]` 取的就是它）。

基于锚定项的两个关键字（都是**关键字**，不是字段插值，§1.3 的边界不变）：

| 关键字 | 语义 | 旗舰用例 |
|---|---|---|
| `of='self'` | 取**以锚定项为父**的子内容列表（锚定项是"书"就取章节，是"系列"就取篇目） | 书详情页取"最新章节"（`{cms:query type='chapter' of='self' row='5'}`） |
| `of='parent'` | 取**锚定项的父**的子内容列表（锚定项是"章"就取同书的兄弟章、也就是目录） | 章节页取全目录、篇目页取同章其他篇 |

- `of='parent'` 是"向上"的**唯一出口**，且它只允许出现在"锚定项本身有父"的页面/循环里；锚定项没有父（`parentId=0`）时：`{cms:list}` 得到空列表（`page.empty` 为真），`{cms:query}` 不渲染（§5.4 约束三）。
- **`channel` 与 `of` 无关**：`channel` 只表示"当前浏览位置"（分类页是分类、详情页是主分类；**承载了可分页目录的详情页也一样**——父内容永远走 `parentXxx` 派生字段），供导航与面包屑使用（§5.1）。

**（2）父链派生字段**：任何内容都能直接读 `parentId` / `parentTitle` / `parentSlug` / `parentUrl` / `parentTypeCode` / `ancestors`（§2.2）。章节页写"返回目录"与含书名的 `<title>`，靠的是 `[field:parentTitle/]` / `[field:parentUrl/]`，不是向上作用域。

**（3）`param` 作用域**：`{cms:include file='x.html' title='最新'}` 传入的字面量在片段内以 `[field:param.title/]` 读取，不受外层迭代遮蔽。

> **v2.1 修改（§0.2 第 16 条）**：v2 在这里写 `of='self'` = "以栈顶迭代项的 id 为父"，在 §6.3 又写成"以当前浏览位置（`channel`）**或**栈顶迭代项的 id 为父"——两处定义冲突，析取无优先级；而它举的第二个例子"文档：章节页里取兄弟篇目"需要的是**父**的 id，正是本条裁定开头声明**不提供**的能力。现按上面的"锚定项"定义统一，并新增 `of='parent'` 与父链派生字段，把那个例子改成能真正实现的写法：
>
> ```html
> <!-- 章节页：返回目录（父书）+ 同书目录（父的子内容） -->
> <a href="[field:parentUrl/]">《[field:parentTitle/]》目录</a>
> {cms:query type='chapter' of='parent' row='20' orderby='sort'}
>   <li class="[field:class/]"><a href="[field:url/]">[field:title/]</a></li>
> {/cms:query}
> ```

**裁定六：不做模板继承（`{cms:extends}` / `{cms:block}`）。**

`{cms:include}` + header/footer 片段已能表达"骨架复用"，代价是每个页面模板多两行 include。换来的是解析器不必引入继承、块覆盖、多层覆盖合并这一整套复杂度。**主题包不是靠继承实现的，是靠片段目录约定**（§7.3）。

**裁定七：不做 `and` / `or` 条件参数。**

### 3.8 布尔组合的完备性说明（**不做 `and` / `or` 的依据**）

模板无法取反、无法做逻辑运算，但"字段真假的任意布尔组合"仍然**表达完备**：

| 需要 | 写法 | 是否完备 |
|---|---|---|
| `A AND B` | `{cms:if field='a'}{cms:if field='b'}…{/cms:if}{/cms:if}` | 嵌套，完备 |
| `NOT A` | `{cms:if field='a'}…{cms:else/}…{/cms:if}` | `else` 的另一种用法，完备 |
| `A OR B` | `{cms:if field='a'}X{cms:else/}{cms:if field='b'}X{/cms:if}{/cms:if}` | else 链，完备（X 用 include 片段消重） |
| `A OR B` 的 else 分支 | 把 `A OR B` 整体视为一个条件，`X` / `Y` 各自写成 else 链的两支 | 完备 |
| 数值比较（`price > 100`） | **不完备，且刻意不完备** | 由字段层解决：`BOOL` 字段（录入时勾选"高端"）、`INT` 字段的 `where` 过滤（查询侧）、或派生字段 |
| **等值判断**（"这一项是不是当前页/当前文章"） | **不提供，也不需要** | 模板里写不出比较（§5.5 已承认），但引擎**知道**当前条目的 id，因此由引擎把结果算成字段：内容查询的迭代项也给 `current`(BOOL) 与 `class`（§5.5、§6.3）。**用数据解决表达式的问题**——这是本规格处理"相等"这类需求的唯一手法 |
| **集合包含**（"这篇文章有没有某标签"） | 不提供 | 同上：`{cms:foreach field='tags'}` 迭代出真实存在的项，模板不需要判断"有没有"；要按标签筛选内容则用 `where='tagId:has:3'`（查询侧，§2.5） |

**结论**：`and`/`or` 参数一旦加上，就必须定义"多个逻辑参数同时出现"的优先级规则，那一刻引擎里就有了运算符——这是 v1 §1.2 封死的东西。所以宁可让模板作者写三层嵌套，也不长出运算符。**"相等"与"包含"这两类最常见的非布运算，一律用"引擎预计算字段 + 查询侧过滤"解决，不开口子。**

---

## 4. 解析器规格

### 4.1 组件

```
module/cms/publish/
  template/
    TemplateLexer.java       线性扫描：源文本 → Token 流
    ParamParser.java         参数区扫描（引号感知）
    TemplateParser.java      Token 流 + 显式栈 → AST，每节点带 lineNo
    ast/TextNode.java
    ast/TagNode.java         name, args, body(List<Node>), lineNo
    ast/FieldNode.java       path(多段), args, lineNo
    TemplateCompiler.java    源文件 → AST + 编译缓存 + 编译期校验（§4.5）
    TemplateRenderer.java    AST + RenderContext → StringBuilder
    RenderContext.java       作用域栈 + 派生页计划 + 命名查询结果
    TemplateException.java   模板错误（带路径 + 行号 + 提示）
    TagRegistry.java         标签名 → TagHandler（Spring 自动收集）
    ParamSpec.java / ParamType.java
    tag/*.java               各标签实现
  model/
    ContentTypeDef.java      类型定义（来自 cms_content_type）
    FieldDef.java            字段定义（来自 cms_field）
    ContentItem.java         统一内容项（内置列 + 自定义值 + 派生字段）
    ContentProvider.java     取数接口：唯一实现在 cms_content（§2.6），article 只是它的内置类型
    DefVersion.java          类型/字段/站点配置的定义版本号（编译缓存与增量失效都靠它，§4.4）
    Anchor.java              "锚定项"的解析（§3.7 裁定三）：页面当前条目 / 栈顶迭代项
  plan/
    PagePlan.java            一个页面 = 页面类型 + 来源 + 模板 + URL + 分页主体
    PagePlanBuilder.java     站点全量页面计划（全量/增量入口）
    UrlResolver.java         占位符 → 路径（§7.1，唯一 URL 出口）
    ListSourceResolver.java  列表来源：分类 / 父内容 / 标签 / 归档 / 筛选 / 关联 / 菜单
    FacetPlanner.java        筛选落地页组合（§7.4）
    DerivePlanner.java       分页派生页计划（§5.4）
    RedirectPlanner.java     slug 变更的重定向计划（§7.6）
  index/
    ContentIndexService.java 维护 cms_content_index（§2.5）
  media/
    MediaDeriveService.java  图片派生尺寸（thumb/medium/large）与元数据（§7.7）
  artifact/
    SitemapWriter.java / FeedWriter.java / RobotsWriter.java / SearchIndexWriter.java（§7.2、§7.5、§7.6）
    RedirectWriter.java      产出 redirects.conf（§7.6）
  task/
    PublishTaskService.java  入队 / 串行化 / 进度 / 重试（§8.3）
    PublishWorker.java       执行单任务
    PublishManifest.java     每页的输入依赖清单（内容 id、分类、标签、邻接、版本号）——**取代 v2 的产物内容哈希**（§8.4）
    PublishDependencyIndex.java  反向索引：某个内容/模板/菜单变了 → 哪些产物受影响（§8.4）
    ArtifactGc.java          产物 GC：删除不再被计划的产物（幽灵页），§8.6
  release/
    ReleaseService.java      发布批次 / 预发布 / 回滚（§8.7）
    SiteSyncService.java     产物搬运到站点根目录（发布阶段之一，§8.10）
  menu/
    MenuService.java         导航菜单读取与当前项计算（§2.4）
```

### 4.2 扫描算法

- 单次线性扫描，**不开正则**。仅在遇到 `{cms:` 或 `[field:` 时才进入标签解析，其余累积为文本节点
- 扫描时维护 `lineNo`（对 `\n` 计数），每个节点记录**起始行号**
- 起始序列必须紧邻：`{cms:` 的 `{` 与 `cms:` 之间不得有空白
- `[field:` 之后按 §3.2 的 `field_path` 扫描多段路径与下标
- 参数区扫描必须感知引号状态（`}` 与 `/` 在引号内不结束标签）

### 4.3 嵌套配对（**不匹配即报错，不做容错**）

- 块标签用**显式栈**配对
- `{/cms:x}` 与栈顶不同名 → 报错，并同时给出两个标签各自的行号
- 扫描结束栈非空 → 报错，指出每个未闭合标签的行号
- `{cms:else/}` 的位置校验在解析期完成（§3.5 裁定四）：不在 `{cms:if}` 直接子节点、或同一 `if` 内出现两次 → 报错
- 容错会让结构错乱的模板静默通过，是最坏的选择

### 4.4 编译缓存与失效

- 缓存 key：**`站点 id + 模板相对路径 + 定义版本 defVersion + 上下文签名`**（**v2.2 补上下文签名**）
  - **上下文签名** = `pageType + typeCode + 具名作用域存在性`（例：`DETAIL:product:site,channel,page,item`）。理由：§4.5 第 4/5 条校验的是"**该模板上下文**可用的字段集合"，而同一个模板文件会被多种页面共用一个文件（`list.html` 是兜底模板、`single.html` 被多个 `SINGLE` 类型共用）——只按路径缓存会造成两种实现分歧：一种按并集编译（漏报 E1004），另一种按页面类型各编译（语义与 key 对不上）。
  - **不进 key 的东西**：内容数据（那属于产物级差异，§8.4）。
- 失效判定：文件 `mtime + size` 与缓存记录不一致，**或** `defVersion` 变了，**或** `astVersion` 变了 → 重新编译（**不做文件监听**）
- 编译产物（AST）**编译后不可变**，因此 `ConcurrentHashMap` 共享天然线程安全
- `defVersion` 的构成：`类型定义版本 + 字段定义版本 + 站点配置版本`（三者任一保存即 +1；站点级菜单不进 `defVersion`，菜单是**数据**不是定义，见 §8.4）
- **一次发布任务开始时统一快照**全部模板的 `mtime + size`、`defVersion` 与站点配置，避免生成过程中模板或字段定义被改动、导致同一批产物用了两套规则
- 被 `{cms:include}` 的片段同样进缓存；片段的展开结果（含分页主体定位）**在编译期完成**（§5.3）
- **`astVersion` 的输入写死**（**v2.2 定死**）：`自身路径 + mtime + size + 内容 sha256`，**并累加 include 链上每一段的同样四项**（递归，深度上限 10）。v2.1 只说"展开后的**字节数**计入 `astVersion`"——而片段改一个词、字节数可以完全不变（同长度替换），那时包含它的模板不会重编译，页面上就是**静默陈旧**。

> **v2.1 修改（§0.2 第 21 条）**：v2 的 key 只有"站点 id + 模板相对路径"，失效只看 `mtime + size`。于是改了字段定义（加字段、删字段、取消 `indexed`）而模板未动时，缓存仍命中**用旧定义编译出的 AST**——§4.5 的第 4/5/6 条校验全部失效，而且失效方式是"渲染期才炸"。key 加 `defVersion` 后，定义一改，该站点模板全部重编译（纯内存操作，可接受）。

### 4.5 编译期校验清单（**自研引擎最大的红利，必须逐条实现**）

编译期能查的一律编译期查，不留到运行期。**v2.1 由 10 条扩到 18 条**，其中带 ★ 的 12 条是 §12.3 指定的第一批测试用例。

| # | 校验 | 报错时机 | 错误码 |
|---|---|---|---|
| 1 ★ | 标签名是否存在（含编辑距离猜测） | 编译期 | E1001 |
| 2 ★ | 参数是否在该标签的 `ParamSpec` 内、类型是否可转换、必填是否缺失 | 编译期 | E1002 |
| 3 ★ | 块配对、`{cms:else/}` 位置、`{/cms:x}` 名称 | 编译期 | E1003 |
| 4 ★ | 字段名是否存在于"该模板上下文可用的字段集合"（依类型定义 + 具名作用域 + 栈） | 编译期（字段名是字面量，可查） | E1004 |
| 5 ★ | 字段路径的中间段是否可解析（`[field:images.0.url/]` 的下标越界只能在渲染期查，但"路径第一段存在"必须编译期查出） | 编译期 + 渲染期 | E1005 |
| 6 ★ | `where` / `orderby` / `relate` 用到的**自定义**字段是否 `indexed=1`（内置字段查 §2.2 白名单），运算符是否在白名单内，`where` 语法是否可解析（含 `\|` 与 `\,` 转义） | 编译期 | E2007 |
| 7 ★ | `type` 参数是否指向存在的类型（含 `all`）；`category` / `tag` / `author` / `code`（菜单）参数是否存在 | 编译期 | E2006 |
| 8 ★ | **分页主体数量**：一个模板**至多 1 个分页主体**——`{cms:list}` 至多 1 个、`{cms:detail}` 至多 1 个，且**当 `{cms:detail}` 走正文分页（`paginate_body` 非空）时不许再有 `{cms:list}`**（见下方口径表）；分页主体不得落在 `{cms:if}` 体内（§5.4 约束二） | 编译期（include 展开后） | E3001 / E3002 |
| 9 ★ | `url_pattern` 占位符是否在白名单内、必需占位符是否齐全（判定函数见 §7.1.3，**三处口径已统一**） | 编译期 + 页面计划生成期 | E4001 |
| 10 ★ | include 是否越界 / 循环 / 超深（10 层） | 编译期 | E1006 |
| 11 ★ | 模板查找结果是否存在（§7.3） | 页面计划生成期 | E4002 |
| 12 ★ | `{cms:form}` 的 `code` 是否指向存在的表单定义（**`hidden` 的 key 不校验**，只有值命中 5 个关键字才替换，见 §6.5） | 编译期 | E2008 |
| 13 | `{cms:pagelist}` 用在**正文分页**上（`page.paginationKind='content'`）→ 报错（§5.6） | 编译期 | E3003 |
| 14 | `of='self'` / `of='parent'` 用在不成立的位置（锚定项无父、或页面没有当前条目） | 编译期 | E2009 |
| 15 | `{cms:detail}` 的 `type` 与页面类型的 kind 不匹配（如在 `SINGLE` 页面上取 `CONTENT` 类型） | 编译期 | E3010 |
| 16 | 命名查询的 `name` 重复、或在循环体内命名（§5.4 约束三） | 编译期 | E2010 / E2011 |
| 17 | 模板编码（非 UTF-8 → 报错，不做猜测）、BOM 处理 | 编译期 | E1007 |
| 18 | 站点发布选项关闭了某类页面，却存在该类型的模板（**警告**，不报错——主题包可能被多个站点共用） | 页面计划生成期 | W5001 |

**「分页主体数量」的统一口径（v2.1 定死，取代 v2 的三处矛盾表述）**：

| 页面类型 | `{cms:list}` | `{cms:detail}` | 说明 |
|---|---|---|---|
| 首页 `HOME` | 0 或 1 | 0 | 首页可以有主列表，也可以全是 `{cms:query}` |
| 分类/标签/归档/筛选 `LIST` | 0 或 1 | 0 | 没有 `{cms:list}` → 该页只有第 1 页；有 → 它是分页主体 |
| 详情页 `DETAIL` | 0 或 1 | 0 或 1 | **可以没有任何主体**（字段从栈底的当前条目取）；有 `{cms:list}` 时它是分页主体（目录 / 专题 / 作者文章 / 系列） |
| 单页 `SINGLE` | 0 | 0 或 1 | `{cms:detail}` 在单页上只是取值，**不是分页主体**（它不产生第 2 页） |
| 静态页 `STATIC`（感谢页 / 404 / 搜索页 / **站点声明的列表页**，§7.2.1 第 11 行） | 0 或 1 | 0 | 声明了 `query` 的列表页**必须恰有 1 个** `{cms:list}`；其余静态页 0 个 |

**两条死限**（其余组合都合法）：

1. **`{cms:list}` 与 `{cms:detail}` 各至多 1 个**；
2. **二者不能同时是分页主体**：`{cms:detail}` 只在 `paginate_body` 非空时才是分页主体，因此"`paginate_body` 非空 + 模板里有 `{cms:list}`" → `E3001`，文案要求二选一（要么正文分页、要么列表分页；都要就把列表放到另一页）。

**一句话**：`{cms:list}` 与 `{cms:detail}` 是**两种页面主体**；详情页可以同时承载一条内容和一个可分页的列表，但**只有列表会分页**。

> **v2.1 修改（§0.2 第 15 条）**：v2 说"一个模板有且只有一个分页主体"（并派生出"详情页不许有 `{cms:list}`"），于是"书详情页 + 分页目录""专题页""作者页""系列页"全都不可表达——而这四个页面是小说站/资讯站/博客/文档站的必需页面。改为"至多一个分页主体 + 两个死限"后，一处规则解决四个站型的页面形态。

---

## 5. 渲染模型

### 5.1 作用域栈与字段解析

- `RenderContext` 维护 `Deque<Scope>`，`Scope` 是 `Map<String, Object>` + 一个可选的**具名标识**
- 栈的结构恒为：**栈底 = 本页面的当前条目（匿名）→ 具名作用域 → 循环项（匿名，可能多层）→ include 的 `param` 层**

**（1）页面的"当前条目"（**v2.1 新增，本条是承重定义**）**

每个页面类型都有且只有一条"当前条目"，它在**渲染开始前压入匿名栈底**，因此 `[field:title/]` 在**任何**页面模板里都直接可用：

| 页面类型 | 当前条目 | 举例 |
|---|---|---|
| 首页 `HOME` | 无（栈底为空） | 首页要用数据一律走 `{cms:query}`（§6.3） |
| 分类/标签/归档/筛选 `LIST` | 无（"当前栏目"在 `channel` 里，不在栈底） | 列表项由 `{cms:list}` 提供 |
| 详情页 `DETAIL` | 该条内容 | `[field:title/]`、`[field:parentTitle/]`、`[field:content/]`；书详情页的目录用 `{cms:list of='self'}`（§6.3） |
| 单页 `SINGLE` | 该单页的内容项 | `[field:title/]`、`[field:content/]` ——**v2 里这条路是断的**（§0.2 第 7 条） |
| 标签页 / 归档页 | 无 | 标签实体在 `channel` 里 |

> 这条规则**一次解决三件事**：单页取不到自身字段（v2 的 P2）、详情页里 `content` 一词三义（v2 的 C3）、以及"锚定项"的定义（§3.7 裁定三）。代价是"就近解析"多了一层：**循环项永远优先于当前条目**——`{cms:list}{cms:foreach field='specs'}[field:title/]{/cms:foreach}{/cms:list}` 里的 `[field:title/]` 取的是 `specs` 迭代项，不是列表项，也不是页面条目。这条必须记住，写模板时用 `[field:item.title/]` 显式取当前条目。

**（2）具名作用域共 6 个**（v2 由 3 个扩展到 6 个，语法不变）：

| 具名作用域 | 内容 | 何时存在 |
|---|---|---|
| `site` | 站点配置，**封闭清单见 §2.7**（**22 个 key，一个不多**）：`id` `name` `code` `domain` `url` `protocol` `logo` `lang` `description` `keywords` `seoDescription` `icp` `contactPhone` `contactEmail` `rootDir` `defaultCover` `ogImage` `theme` `statisticsCode` `mediaHost` `year` `alternates` | 始终 |
| `channel` | **当前浏览位置**：分类页是其分类；标签页是其标签；归档页是年月；筛选页是其筛选组合；详情页是其主分类；单页是它自己 | **只在下面第（4）条那张表列出的页面类型上存在**（首页 / 搜索页 / 404 / `STATIC` 静态页都没有；v2.2 修正：v2.1 写"除首页外"，与 §7.6 的"404 只有 `site`"自相矛盾，且仍在提已取消的"子内容索引页"） |
| `page` | "这一页"的信息（§5.6）：`url` / `title` / `canonical` / `noindex` / `robots` / `lastmod` **恒有值**；分页字段仅在分页主体存在时有值 | **始终存在**（**v2.2 修正**：v2.1 写"仅分页主体所在页面"，而 §7.6 要求每个 `DPAGE` 输出 `page.noindex`、§4.5 又允许详情页一个分页主体都没有——两处会撞出"模板必须用的字段不存在"） |
| `param` | `{cms:include}` 传入的字面量参数（**栈式，就近解析**，§5.3） | include 片段内 |
| `query` | 具名查询的结果元信息：`query.<name>.empty` / `.totalCount` / `.hasResults` / `.pageSize` | 该查询被 `name` 命名且不在循环内时 |
| `item` | **本页面的当前条目本身**（与栈底同一对象），用于在循环里显式取页面级字段 | 当前条目存在时 |

- **循环项永远匿名**，永远在栈顶
- **保留名就是这 6 个**：`site` `channel` `page` `param` `query` `item`。不得用作类型 code、字段 code 或 include 参数名（后台保存时校验，编译期再校验一次）
- **`content` 不是保留名**：它只是正文字段名（`cms_content.content`、`{cms:detail}` 的 `content`、`paginate_body` 默认值），三者是同一个东西

字段解析规则：

| 写法 | 规则 | 找不到时 |
|---|---|---|
| `[field:xxx/]` | 从栈顶向下找第一个含 `xxx` 的 Scope（**循环项 > 当前条目**） | 报错 |
| `[field:xxx.yyy/]` | 先解析 `xxx`，再按路径取子值（对象键、数组下标） | 报错，并指出路径在哪一段断掉 |
| `[field:xxx.count/]` | 多值（数组 / `IMAGES` / `FILES` / `RELATION` / `TAGS` / `JSON`）取元素个数；`[field:xxx.0/]` 取下标的**原始值**（对象则输出其 `name`/`title`/`url` 中第一个非空者） | 非多值字段上用 `.count` → 编译期报错（E1005） |
| `[field:site.xxx/]` | 只在具名作用域 `site` 里找，key 必须在 §2.7 清单内 | 报错，并列出清单 |
| `[field:channel.xxx/]` | 只在具名作用域 `channel` 里找 | 报错 |
| `[field:page.xxx/]` | 只在具名作用域 `page` 里找 | 报错 |
| `[field:item.xxx/]` | 只在具名作用域 `item` 里找（**在循环里取页面级字段的唯一写法**） | 报错 |
| `[field:param.xxx/]` | `param` 作用域**从内向外**找第一个含 `xxx` 的层（唯一允许同名多层的具名作用域） | 报错 |
| `[field:query.name.xxx/]` | 具名查询的结果元信息 | 报错，并列出该模板内全部已命名查询 |

「找不到」一律**报错**并列出该作用域的可用字段（预览模式与发布模式**行为一致**：宁可任务失败，不可静默生成空内容）。**报错列出的字段清单由引擎按"该页面类型 + 该类型定义"现算**，因此字段定义一改，报错内容立刻跟着变。

> **v2.1 修改（§0.2 第 7、17 条）**：v2 的 `content` 作用域只覆盖"详情页 / 子内容索引页"，单页因此取不到自身字段；同时 `content` 一词三义、三份保留名清单互不相同。现改为：**当前条目恒压栈底**（任何页面都能 `[field:title/]`）+ **作用域改名 `item`** + **保留名收敛为一份**。

**（3）无前缀查找只在匿名层里进行**（**v2.2 定死**）：`[field:x/]` 的查找范围**只有匿名栈帧**（当前条目 + 循环项），**不含** `site` / `channel` / `page` / `param` / `query` / `item` 这些具名作用域——它们必须写前缀。理由：`page.url`、`site.url`、迭代项的 `url` 三者同名，若允许无前缀命中具名层，`[field:url/]` 的归属就会随书写位置漂移。

**（4）`channel` 的 key 清单与存在条件**（**v2.2 补全**；v2.1 只写了"当前浏览位置"，却没有任何 key 清单，而 §7.4 的示例已经在用 `channel.label`）：

| 页面类型 | `channel` 是否存在 | 它是什么 | 可用 key |
|---|---|---|---|
| 分类索引页 `LIST`（分类来源） | 是 | 该分类 | `id` `name` `label` `slug` `url` `path` `count` |
| 类型列表页 `LIST`（类型来源，§7.2.1） | 是 | 该内容类型 | `id`(=0) `name` `label` `slug`(=typeCode) `url` `typeCode` `count` |
| 标签页 `TAGPAGE` | 是 | 该标签 | `id` `name` `label` `slug` `url` `count` |
| 归档页 `ARCHIVE` | 是 | 该年月 | `year` `month` `label` `url` `count` |
| 筛选页 `FACET` | 是 | 该筛选组合 | `label` `slug` `url` `facetPath` `count` |
| 详情页 `DETAIL` / 单页 `SINGLE` | 是 | 该内容的**主分类**（`SINGLE` 是它自己） | 分类的 key（见第一行）；内容自身走栈底或 `item` |
| 首页 `HOME` / 搜索页 `SEARCH` / `404` | **否** | — | 取 `channel.*` → 报错（E1004） |

- `label` 是**给人看的显示名**（后台可覆盖），`name` 是对象本名；导航模板统一用 `label`（§6.4 的产出清单两者都给）。
- **详情页的 `channel` 仍是"主分类"**（v2.1 定义，v2.2 明确）：即使该详情页承载了可分页目录（§5.6），`channel` 也不变成父内容——父内容一律用 `parentId` / `parentTitle` / `parentUrl` / `ancestors` 派生字段取（§2.2、§3.7 裁定三）。v2.1 残留在 §3.7 的"子内容索引页"已随 `SUBLIST` 取消（§0.2 第 15 条），此处不再出现该词。

### 5.2 转义规则

#### 5.2.1 输出转义

- 转义**只发生在 `FieldNode` 输出时**，且只对 `raw=false` 的字段。转义字符：`&` `<` `>` `"` `'`
- **模板文本永不转义**（否则 `<div>` 全废）
- `RICHTEXT` 字段（`content`、`summary` 等）在**字段声明里标为 raw**，模板作者不需要写任何标记；入库时按 §5.2.4 的白名单清洗
- **严禁**在模板层提供"关闭转义"的开关：那会让每个模板作者都要做一次安全决策

#### 5.2.2 Markdown 与富文本的入库处理

- `MARKDOWN` 字段：**入库时预渲染为 `contentHtml`**（`org.commonmark:commonmark`），模板用 `[field:contentHtml/]` 输出（raw）。**预览与发布都读预渲染结果**，运行时不做 Markdown 解析——与"能不实时算就不实时算"的引擎哲学一致
- `RICHTEXT` 字段：入库时按 §5.2.4 清洗后写入 `content_html`；`content` 列保留**清洗后**的 HTML（不保留原始输入，避免"库里存着一段危险 HTML，某个未来功能把它漏出去"）
- 两类正文都以 `content_html` 为**唯一渲染源**：`{cms:detail}` 的 `content` 字段输出的是它、正文分页切的是它、`toc` / `wordCount` / `imageCount` 也从它算
- 存量数据：迁移时对 `content_format='MARKDOWN'` 的存量文章做一次回填（§12.1 迁移清单）
- **新增依赖（v2.1 明确标注）**：`org.commonmark:commonmark`（Markdown → HTML）与 `org.jsoup:jsoup`（清洗 + 解析正文抽 `toc` / 字数 / 图片数）。二者目前都**不在** `pom.xml` 里（§1.4）。它们不是模板引擎，不违反 §1.3；其中 jsoup 一库三用（清洗、抽标题树、算字数），这是选它而不是选 OWASP Sanitizer 的理由

#### 5.2.3 正文分页符

- 分页符是 HTML 注释 `<!--cms:page-->`，**必须被清洗器显式保留**（注释默认会被清掉，这一步漏了"正文分页"就是纸面功能）
- 富文本编辑器要提供"插入分页符"按钮，否则没人会手打这个注释（O19）
- 分页只切 `content_html` 的**顶层**：切点落在某个块级元素**内部**时不切（避免切出未闭合标签），并在发布日志里给一条警告（W5002）
- **首尾与连续分页符产生的空切片一律丢弃**（**v2.2 定死**）：`totalPages` 按**非空切片**计。否则正文末尾一个多余的 `<!--cms:page-->` 就会多出一个只有页脚的 `.../12.html`——在 SEO 上是空页，在产物管理上还要多养一个文件。

#### 5.2.4 富文本白名单（**契约的一部分，v2.1 首次写明**）

> 这份清单是**入库清洗**的唯一依据，也是"模板作者能指望什么排版被保留"的唯一依据。改动它就等于改模板契约。

**允许的标签**：

| 类别 | 标签 |
|---|---|
| 结构 | `p` `div` `span` `br` `hr` `section` `article` `aside` `main` `header` `footer` `nav` |
| 标题 | `h1` `h2` `h3` `h4` `h5` `h6` |
| 行内 | `strong` `b` `em` `i` `u` `s` `del` `ins` `sub` `sup` `mark` `small` `abbr` `cite` `q` `time` `code` `kbd` `samp` `var` `pre` `blockquote` |
| 列表 | `ul` `ol` `li` `dl` `dt` `dd` |
| 表格 | `table` `thead` `tbody` `tfoot` `tr` `th` `td` `caption` `colgroup` `col` |
| 媒体 | `img` `figure` `figcaption` `picture` `source` `video` `audio` `track` |
| 链接 | `a` |
| 折叠 | `details` `summary` |

**允许的属性**（**其余一律删除，属性被删不报错**——它是内容，不是模板）：

| 作用范围 | 属性 |
|---|---|
| 全局 | `class` `id` `title` `lang` `dir` **以及所有 `data-*`** |
| 链接 `a` | `href` `target` `rel` `name` |
| 图片 `img` | `src` `alt` `width` `height` `loading` `decoding` |
| 媒体 `video` `audio` `source` `track` | `src` `poster` `controls` `preload` `width` `height` `type` `kind` `srclang` `label` |
| 表格 | `colspan` `rowspan` `scope` `align` |
| 代码 | `pre` / `code` / `kbd` / `samp` 上的 **`class` 与 `data-*` 明确保留**（`class="language-java"` / `data-lang="java"` 是高亮插件的语言标记，删了高亮就废了） |

**协议白名单**：`href` 允许 `http:` `https:` `mailto:` `tel:` 与站内相对/绝对路径；`src` 允许 `https:` 与站内路径（生产建议：媒体只允许本站 `/uploads/`）。其余（`javascript:` `data:` `vbscript:` `file:`）一律删除属性。

**明确禁止**（出现即连标签一起清掉，只留文本）：`script` `style` `iframe` `object` `embed` `applet` `form` `input` `textarea` `select` `button` `link` `meta` `base` `svg` `math` `template` `frame` `frameset`；所有 `on*` 事件属性；`srcdoc` `formaction` `xlink:href`；CSS `style` 属性。

- **不允许 `style` 属性，替代方案是预置 class**：编辑器工具栏里的"居中 / 引用 / 加粗表格 / 大号文字"插入的是**主题约定的 class**（`text-center` `lead` `table-bordered`…），由主题 CSS 定义。理由：清洗 CSS 属性值需要一个 CSS 解析器（`url()`、`expression()`、`@import` 的绕过手法一直在更新），而 class 让"站点长什么样"重新回到主题（也就是 CSS）手里。代价如实记录：**从别处粘贴的带内联样式的富文本会掉样式**，编辑器必须给一句提示，并在粘贴时把常见内联样式映射成预置 class。
- **不允许 `iframe`（含视频站外链）**：产品视频一律走 `FILE` 字段 + 主题自己的 `<video>`（§2.2 的视频说明）。要嵌第三方播放器时，在**模板**里写死（模板是可信输入），不要放进富文本。
- `<!--cms:page-->` 是唯一被保留的注释；其余注释在清洗时去掉。

#### 5.2.5 正文预计算（`toc` / `wordCount` / `imageCount`）

正文保存时用 jsoup 解析一次 `content_html`，写入 `cms_content.content_toc` 与 `word_count`，并让 `imageCount` 可算：

- `toc` 是 `h2`–`h4`（层级与深度由站点发布选项 `toc.levels` 决定，默认 `h2,h3`）的树：每项 `level` `text` `id` `url`。**`id` 由引擎生成**（`h2-1`、`h2-2` 这类稳定编号，不用标题文本做锚点——中文标题做锚点会产出需要 URL 编码的乱码链接），并**回写进 `content_html` 的 `id` 属性**（清洗前生成，保证锚点与目录一致）
- 模板侧的用法（零新增标签）：

```html
{cms:if field='toc'}
<nav class="toc">
  <ol>
  {cms:foreach field='toc'}
    <li class="toc-lv[field:level/]"><a href="[field:url/]">[field:text/]</a></li>
  {/cms:foreach}
  </ol>
</nav>
{/cms:if}
```

- `wordCount` 的口径写死：**中文字符数 + 英文单词数**（不是字节数、不是 `<p>` 数）；`readingMinutes = ceil(wordCount / 400)`（400 字/分钟，站点可用发布选项 `reading.speed` 覆盖）。口径不写死，两个站点就会出现两个"阅读时长"。

> **v2.1 新增（§0.2 第 26、27 条）**：v2 只说"按标签白名单清洗"，白名单本身不存在，导致"代码高亮的 `class` 保不保留"这种问题无法回答；`toc` 也确实没有任何机制。两者现补入契约。

### 5.3 include 规则

- `{cms:include file='header.html' title='最新'/}`：`file` 为**纯字面量**，相对**当前主题的片段目录**解析（§7.3）；其余参数为**字面量**，压入名为 `param` 的作用域供片段内 `[field:param.title/]` 读取
- 路径边界统一走 `SitePathBoundary`（§11.4，由现有 `SiteService.resolve` 抽出并提升可见性）：拒绝绝对路径、盘符、`..` 上跳；**并新增符号链接检查**（现有实现不拦符号链接，而发布引擎要按路径写文件，这一点必须补）
- **递归深度上限 10 层**；**路径栈检测循环包含**（A 含 B、B 含 A）→ 报错并打印包含链
- **位置类校验只在展开后的模板上做一次**（**v2.2 定死**）：`{cms:else/}` 的位置约束（§3.5 裁定四）与分页主体数量（§4.5）都按"include 展开之后"的整体判定，**片段被单独编译时不报位置错**；报错位置仍指向**片段内的真实行号**并附包含链。否则同一个 `_partials/x.html` 被 include 在 `{cms:if}` 内时，一处能编译、一处报 E1003。
- 被包含的片段**共享同一作用域栈**（这样 `header.html` 里能直接用 `[field:site.name/]`）
- **include 在编译期展开**（`file` 与参数都是字面量，可以完全静态展开）：
  - 展开后校验"至多 1 个分页主体且 `{cms:list}`/`{cms:detail}` 不同现"（§4.5 口径表）；若某片段被多处包含导致出现两个分页主体 → 报错并给出**包含链**
  - 展开时保留片段的**原始文件路径与行号**，使报错仍指向片段内的真实位置（不是包含者的位置）
  - 展开后的字节数计入该模板的 `astVersion`（片段一改，包含它的所有页面失效，§8.4）
- `param` 是唯一允许"同名多层"的具名作用域：片段内再 include 另一个带同名参数的片段时，`[field:param.x/]` 取最内层

### 5.4 预解析与派生页计划

分页派生的关键在**前置**而不是后置，v2 把这条从"分页主体"**推广到所有顶层查询**：

1. **编译期**：展开 include，定位分页主体（`{cms:list}` 或 `{cms:detail}`），校验数量与位置
2. **预解析**：执行全部**不在循环体内**的查询标签（含分页主体），把结果与 `page` / `query.<name>` 作用域一次填好
3. **计算派生页计划**：分页主体的 `pageNo = 2..totalPages` 各生成一条 `{ 页号, 输出路径 }`
4. 渲染主页面（`pageNo=1`）→ 原子写主路径
5. 遍历派生页计划：同名模板、同一上下文，**只覆盖 `page.pageNo` 重渲染** → 原子写派生路径

**为什么必须前置，而不是边渲染边收集**：`{cms:if field='page.empty'}` 或 `{cms:if field='query.latest.empty'}` 这种判断通常写在列表**上方**。若等到标签渲染时才知道空不空，这个判断就会因书写位置不同而时灵时不灵——位置相关的行为是 bug 温床。

**约束一：一个模板**至多 1 个分页主体**（`{cms:list}` 至多 1 个、`{cms:detail}` 至多 1 个，且两者不能同时分页）。**

- **数量口径就是 §4.5 末尾那张表**（v2.1 定死）：`HOME` / `LIST` / `DETAIL` 可以 0 或 1 个 `{cms:list}`；`DETAIL` / `SINGLE` 可以 0 或 1 个 `{cms:detail}`；`STATIC` 0 个
- 违反时报错（E3001）：`同一模板中出现了多个分页主体标签（第 N 行、第 M 行），请只保留一个`
- **允许"详情 + 可分页列表"**：书详情页放目录、专题页放挑选的文章、作者页放该作者的文章、系列页放篇目——它们的分页 URL 用该类型 `detail_url_pattern` 的 `{n}` 形态（第 1 页不含 `{n}`，§5.6）
- **只有一个组合是错的**：`{cms:detail}` 走正文分页（`paginate_body` 非空）**且**模板里还有 `{cms:list}` → 两个分页主体，报错并提示"要么正文分页、要么列表分页"

**约束二：分页主体不得写在 `{cms:if}` 体内（但**允许**写在 include 片段内）。**

> v2.1 只改了标题句式：v2 的标题写"分页主体**不得**写在 `{cms:if}` 体内（**允许**写在 include 片段内）"，括号里的"允许"相对 v1 是放宽，但整句会被读成"允许写在 if 内"。

- `{cms:if}` 体内禁止：分页计划不能依赖运行期真假（写在这里的列表是否参与渲染无法在计划期确定）
- include 片段内允许：include 是编译期展开，展开后唯一性仍可校验（这是 v2 相对 v1 的放宽，§0 第 7 条）
- 违反时报错（E3002）：`第 12 行的 {cms:list} 是分页主体，不能写在 {cms:if} 内（第 8 行的 {cms:if}）`

**约束三：循环体内的查询标签不参与预解析，也不可命名。**

- 它们的 `empty` 无法在标签外访问（位置相关行为不可靠），因此**禁止** `name` 参数 → 报错（E2011）
- 循环体内的查询若结果为空，标签自身不渲染任何内容（这是正确行为）；需要占位文案时，把该查询提到顶层并命名（§6.3 示例）
- 循环体内的 `{cms:list}` **不存在**（`{cms:list}` 是页面主体，写在循环里 = 分页主体在 `{cms:if}` 之外但又在循环里，计划期无解）→ 编译期报错（E3001 的第二种文案）
- `of='parent'` 用在"锚定项无父"的位置（首页、分类页）时：`{cms:list}` 得到空集、`{cms:query}` 不渲染、`{cms:foreach}` 不渲染。**这不是错误**（它和"这本书没有章节"是同一种情况），但编译期会对"锚定项根本不存在"的页面类型（`HOME` / `STATIC`）直接报错（E2009）

### 5.5 引擎预计算字段（**替代模板内条件判断的关键手法**）

没有表达式，模板就写不出 `class="[? current ? 'active' : '']"`。解决办法是**把判断挪到引擎侧**，分三种形态：

**（1）可直接用于样式的字符串字段**——连 `{cms:if}` 都不需要：

- `pagelist` 迭代项提供 `class`：当前页为 `page-item current`，其余为 `page-item`；并额外提供 `rel`（`prev` / `next` / 空串）
- `channel` / `breadcrumb` / `tagnav` / `archive` / `menu` 迭代项提供 `class` 与 `current`(BOOL)
- **内容查询（`list` / `query` / `detail` 的相关列表）的迭代项也提供 `current` / `class`**（**v2.1 新增**）——"这一项就是当前页面的当前条目"由引擎比对 id 得出，模板不需要比较运算符
- `list` / `query` 的 `cover` 为空时给站点默认图（`site.defaultCover`），而不是空串
- `BOOL` 字段用 `show='is-hot'` 拼 class：`class="card [field:hot show='is-hot'/]"`

**（2）布尔字段**——配合 `{cms:if}` 使用：

- `page.empty` / `page.hasResults` / `page.hasPrev` / `page.hasNext`
- `query.<name>.empty` / `query.<name>.hasResults`
- `item.hasPrev` / `item.hasNext`（上下篇是否存在，配 `{cms:prenext}`）
- `item.hasChildren` / `item.childCount` / `item.parentId`（为 `0` 时判假——"有没有父"直接写成 `{cms:if field='item.parentId'}`）
- **布尔字段成对提供正反两相**的规则在 v2 **不再必需**（因为有了 `{cms:else/}`），但凡"取反比取正更常用"的场景仍应成对提供，减少模板嵌套

**（3）字段层预计算**（v2 新增的主力）：`formatter` 与派生字段（§2.2）。模板作者的每一次"想算一下"，落点都是字段定义。

**这是本引擎的设计准则**：每当模板作者"需要一个条件或一个算好的值"，正确做法是让**字段定义或标签多给一个预计算字段**，而不是给语法加表达式。§1.3 的能力边界靠这条准则维持。**v2.1 把这条准则用在"相等"和"当前项"上**：不给比较运算符，给 `current` / `class`；不给集合判断，给 `where='tagId:has:3'` 与迭代本身。

### 5.6 分页模型

| 页面类型 / 场景 | 分页主体 | URL 规则 | 每页条数 |
|---|---|---|---|
| 首页 `HOME` | 可选 `{cms:list}` | 站点选项 `url.home`（默认 `/page-{n}/`，见 §7.1.3 的"首页分页"） | `row` |
| 列表页 `LIST` | 可选 `{cms:list}` | 类型来源：该类型的 `list_url_pattern`；分类来源：站点选项 `url.list`（§7.2.1） | `row` |
| 标签 / 归档 / 筛选页 | `{cms:list}` | 见 §7.2 各自的 URL 规则 | `row` |
| 内容详情 `DETAIL` | 可选 | 类型的 `detail_url_pattern` | 1 |
| **详情页上的列表分页**（目录 / 专题 / 作者文章 / 系列篇目） | `{cms:list}` | **同一** `detail_url_pattern` 的 `{n}` 形态 | `row`（如目录页可写 `row='100'`） |
| 详情正文分页 | `{cms:detail}`（`paginate_body` 非空） | `detail_url_pattern` 的 `{n}` | 由 `<!--cms:page-->` 切分，不按条数 |
| 单页 `SINGLE` | 无 | 类型的 `detail_url_pattern` | 1 |

> **同一类型的详情页只能有一种分页**：`paginate_body` 非空 → 正文分页；否则模板里若出现 `{cms:list}` → 列表分页。两者都要 → 报错（E3001），需要拆成两页（列表放到独立的分页 URL 上，模板不变、由参数或选项控制）。

`page` 作用域字段（**所有分页主体统一提供**）：
`pageNo` `totalPages` `totalCount` `pageSize` `pageType` `paginationKind` `isFirst` `isLast` `empty` `hasResults` `hasPrev` `hasNext` `currentUrl` `firstUrl` `prevUrl` `nextUrl` `lastUrl`

- `page.pageType`：`HOME` / `LIST` / `DETAIL`（§7.2.1）
- `page.paginationKind`：`content`（正文分页）/ `list`（列表分页）——`{cms:pagelist}` 只许在 `list` 上用（E3003）；模板据此决定要不要渲染 `{cms:pagelist}`
- `page.isFirst`：`pageNo = 1` 时为真。**用于"只在第 1 页渲染简介"这类需求**（没有运算符也能表达，§5.5）

**所有页面都有**（不属于分页，但同放 `page` 作用域，因为它是"这一页"的属性）：
`url` `title` `canonical` `noindex`(BOOL) `robots`（如 `noindex,follow`）`lastmod`（ISO 时间，sitemap 用同一个值，§7.6）

- **`page.canonical`**（**v2.2 新增**）= `site.protocol + site.domain + page.url`，即**这一页自己的绝对 URL**。注意与迭代项上的 `[field:canonical/]` 区分：后者是**内容**的绝对 URL（不含分页号，§2.2）；**分页页写 canonical 必须用 `[field:page.canonical/]`**，否则第 2..N 页会指回第 1 页（§7.1.4）。
- **`rel` 不属于 `page`**：它是 `{cms:pagelist}` 迭代项上的字段（`prev` / `next` / 空串，§6.4）。

> **v2.2 修改**：v2.1 把 `pageType` / `paginationKind` 两条说明写了两遍（一次在分页字段下、一次在"所有页面都有"下），且没有 `canonical` 与 `rel` 的归属口径——分页页照 `[field:canonical/]` 写会指到第 1 页，是收录事故。现合并为一处并补两条口径。
- 正文分页符：`<!--cms:page-->`（HTML 注释形式，不用 `[page]` 这类可见文本——它会污染纯文本阅读与富文本编辑器；清洗器必须保留它，§5.2.3）
- 正文分页只保证 `prevUrl` / `nextUrl` / `totalPages` / `pageNo`；**不提供 `pagelist` 的页号窗口**（长文拆几十页时页号条没有意义，导航只用"上一页/下一页/目录"）
- `{cms:pagelist}` 只用于 `page.paginationKind='list'`；用在正文分页 → 编译期报错（E3003）
- **分页 URL 的规范形态**：第 1 页永远是**不含 `{n}`** 的那个形态（`/news/` 而不是 `/news/1.html`），避免"同一内容两个 URL"。若 `list_url_pattern` 里 `{n}` 在路径中段（如 `/news/page-{n}/`），第 1 页用 `/news/`（引擎把 `{n}` 所在的那一段整体去掉，规则见 §7.1.2）

> **v2.1 修改（§0.2 第 15 条）**：v2 把"小说目录"写成 `{cms:list type='chapter' of='self'}` 却又规定详情页不许有 `{cms:list}`、且 URL 归给**子类型**的 `list_url_pattern`——三处互相打不通。现在"详情页 + 可分页列表"是**一种合法形态**，分页 URL 由该类型 `detail_url_pattern` 的 `{n}` 形态给出。

---

## 6. 标签清单与参数表

### 6.1 标签总表

| 类 | 标签 | 分页主体 |
|---|---|---|
| 结构 | `{cms:include}` `{cms:if}` `{cms:else/}` `{cms:foreach}` | 否 |
| 查询 | `{cms:list}` `{cms:query}` `{cms:detail}` | `list` / `detail`（互斥，§4.5） |
| 导航 | `{cms:pagelist}` `{cms:channel}` `{cms:breadcrumb}` `{cms:prenext}` `{cms:tagnav}` `{cms:archive}` | 否 |
| 交互 | `{cms:form}` | 否 |
| 字段 | `[field:…/]` | — |

**标签总表就是这些，一个不多**（**v2.1 也没有新增标签**：`type='all'`、`of='parent'`、`relate` 开放给 `list`、`source='type'/'menu'`、`hidden` 的 `self` 关键字，全部是**既有标签的参数**）。新增标签必须按 §13.3 的流程改本文件。

### 6.2 结构标签

#### `{cms:include}` — 片段包含

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | STRING | 是 | 相对**当前主题片段目录**的路径（§7.3），纯字面量 |
| （其余任意参数） | STRING | 否 | 字面量，片段内用 `[field:param.<key>/]` 读取；**不得与 6 个保留名同名** |

规则见 §5.3。

#### `{cms:if}` — 字段真假块

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `field` | STRING | 是 | 字段路径，写法与 `[field:xxx/]` 完全一致（含具名作用域与多段路径） |

- 真假判定规则见 §3.7 裁定一；**不支持比较、逻辑运算、函数**
- 体内可有一个 `{cms:else/}`（§3.5 裁定四）
- 体内不得包含分页主体标签（§5.4 约束二）

#### `{cms:foreach}` — 迭代手上已有的多值字段

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `field` | STRING | — | 要迭代的多值字段路径（`IMAGES` / `FILES` / `RELATION` / `TAGS` / `ENUM_MULTI` / `JSON`，以及引擎预加载的 `children` / `toc` / `categories` / `ancestors` / `tags` / `site.alternates`）。**不迭代查询结果**——查询标签自带标签体（§3.5 裁定五） |
| `row` | INT | 全部 | 最多迭代条数（与查询标签的 `row` **同名不同义**：这里只是截断，不产生分页） |
| `offset` | INT | `0` | 跳过 N 条 |
| `orderby` | STRING | 字段自身顺序 | **迭代项内**的字段 code（如 `sort`；`toc` 按层级、`tags` 按名称） |
| `order` | ENUM `asc`\|`desc` | `asc` | |

迭代产出：**迭代项自身的字段** + `index`（1 起）`index0` `isFirst` `isLast` `count`。

> **v2.1 修改**：`row` / `orderby` 在此处与查询标签同名不同义（v2 的 A10 冲突），上文已加粗标明；`field` 清单补齐 `ENUM_MULTI` / `toc` / `categories` / `ancestors`，并去掉"可迭代查询结果"的说法。

```html
<!-- 图集 -->
{cms:foreach field='images' row='12'}
  <img src="[field:url/]" alt="[field:alt/]" width="[field:width/]" height="[field:height/]" loading="lazy">
{/cms:foreach}

<!-- 规格参数表（JSON 字段） -->
<table class="specs">
{cms:foreach field='specs'}
  <tr><th>[field:key/]</th><td>[field:value/]</td></tr>
{/cms:foreach}
</table>

<!-- 标签列表 -->
{cms:foreach field='tags'}<a class="tag" href="[field:url/]">[field:name/]</a>{/cms:foreach}
```

### 6.3 查询标签与共用参数

#### 共用参数表（`{cms:list}` / `{cms:query}` / `{cms:detail}`）

> **适用**列的 `all` 表示"与 `{cms:detail}` 无关"的参数；`{cms:detail}` 只认它与 `{cms:query}` 共有的少数据（它取一条内容，不取列表）。

| 参数 | 类型 | 适用 | 默认 | 说明 |
|---|---|---|---|---|
| `type` | STRING | 全部 | 当前页面的类型（**该"当前"不存在时必填**，见下） | 内容类型 code；**`all` = 不限类型**（**v2.1 新增**，解决"全站最新"，§0.2 第 5 条）。`type='all'` 时按 `cms_content` 公共列跨类型取数，`row` 必填、`orderby` 只能用公共列；**迭代项可用字段写死为"公共列 + 不依赖类型的派生字段"**（`id` `title` `slug` `summary` `cover` `url` `typeCode` `typeName` `publishTime` `viewCount` `current` `class` …），**自定义字段一律不可用**（编译期 E1004），且 `where` / `relate='field:<code>'` / `of` 配 `type='all'` → E2007（**v2.2 定死**：v2.1 没写可用字段集合，同一个"全站最新"模板一处能编译一处报错）。**"当前页面的类型"在首页 / 标签页 / 归档页 / 筛选页上不存在 → 省略 `type` 是编译期 E1002**，必须显式给 `type` 或 `type='all'` |
| `category` | STRING | 全部 | 当前栏目（**该"当前"不存在时必填**，见右） | 分类 id 或 slug；**`all` = 不限栏目**（v2 新增，解决"首页写不出全站最新"，§0 第 16 条；**参数名是 `category`，不是 `channel`**——v2 §0 那张表里的 `channel='all'` 是误记，§0.2 第 6 条已更正）。**"当前栏目"不存在时（首页 / 标签页 / 归档页 / 筛选页 / 类型列表页）省略 `category` → 编译期 E1002**，必须显式写 `category='all'` 或一个具体分类（**v2.2 定死**） |
| `includeChildren` | BOOL | 全部 | `1` | 分类查询是否含子分类 |
| `tag` | STRING | 全部 | — | 标签 slug，多值用 `\|` 分隔（多值之间 **AND**；要 OR 请用 `where='tagId:in:1\|2'`，§2.5） |
| `author` | STRING | 全部 | — | **v2.1 新增**：作者 slug 或 id；`self` = 当前条目的作者（作者页取"该作者的文章"就写 `author='self'`） |
| `of` | ENUM `self`\|`parent` | `list` / `query` | — | 取**锚定项**（§3.7 裁定三）的子内容：`self` = 以锚定项为父；`parent` = 以锚定项的父为父（章节页取全目录） |
| `relate` | ENUM `tag`\|`category`\|`field:<code>` | **`list` / `query`** | — | 与**锚定项**相关的内容（相关阅读 / 相关产品 / 同作者）。**v2.1 把 `list` 也纳入**（v2 只给 `query`，导致专题页永远只有 1 页，§0.2 第 10 条） |
| `exclude` | ENUM `self` | `list` / `query` | — | 排除锚定项自身（与 `relate` 搭配） |
| `where` | STRING | `list` / `query` | — | 字段过滤，语法与转义见 §2.5（`code:op:value`，逗号 AND，`in` 用 `\|`，多值字段用 `has`）；自定义字段必须 `indexed=1` |
| `keyword` | STRING | 全部 | — | 标题模糊匹配（**纯字面量**） |
| `top` / `recommend` | BOOL | 全部 | — | 仅置顶 / 仅推荐 |
| `status` | ENUM | 全部 | `PUBLISHED` | **`PUBLISHED` 隐含 `publish_time <= now()` 且 `expire_time IS NULL OR expire_time > now()`**（**v2.1 新增时间窗，v2.2 补到期窗口**）：定时发布的内容在到点前**不会**进静态页，到期内容自动退出静态页；取"未发布内容"只在后台预览里可用（`status='any'`）。**一切计数与邻接同口径**（**v2.2 定死**）：`channel.count` / `tagnav.count` / `archive.count` / facet 的 `count>0` / 作者文章数 / `{cms:prenext}` 的相邻判定，**一律按同一个判定函数**计算——否则计数会把还没到点的内容算进去（多出空标签页、空筛选页），上下篇会指向一个还不存在的页 |
| `orderby` | STRING | 全部 | 类型的 `sort_field`，**未配时 `publishTime desc`** | 内置字段或 `indexed` 的自定义字段 code（可用性见 §2.2 白名单）；可多字段：`sort asc, publishTime desc`；**`orderby='relationOrder'`** 只在 `relate='field:<code>'` 时可用 = 按该 `RELATION` 字段的**数组顺序**排（专题页的人工挑稿顺序，**v2.2 新增**）。**`type='all'` 时只能用公共列**。**任何排序都在末尾追加 `id desc` 兜底**（**v2.2 定死**：没有 tie-break 时，同一 `publishTime` 的多条内容在分页时会重复出现或整条丢失，`{cms:prenext}` 的"上一篇"也会漂移） |
| `order` | ENUM `asc`\|`desc` | 全部 | 类型的 `sort_order` | 单字段排序简写 |
| `row` | INT | 全部 | `list`=类型 `per_page`；`query`=`10`；`detail`=1 | 每页 / 条数 |
| `offset` | INT | `query` | `0` | 跳过 N 条（做"第二块列表"） |
| `depth` | INT | `query` | `1` | `>1` 时层级内容每项预加载 `children`（嵌套渲染目录树） |
| `name` | STRING | `query` | — | **给查询命名**，结果元信息进 `query.<name>` 作用域（§5.1）；**循环体内禁止**（§5.4 约束三） |
| `cache` | INT | `query` | `0` | 秒；同一页面内同名同参查询复用（默认不复用，因为静态生成只算一次，缓存只在派生页之间有意义） |

**统一产出字段契约**（**所有**查询标签与导航标签的迭代项都必须提供）：

- 内置字段 + 该类型的全部自定义字段 + 全部派生字段（§2.2）
- `url`：详情页 / 落地页 URL（由 `UrlResolver` 算出，**模板里禁止裸拼 `.html`**）
- `childrenUrl`：**v2.1 取消该字段**——"目录页"已经不在另一个 URL 上（它就是详情页的第 1 页，§5.6），所以"入口链接"就是 `url` 本身；要判断"有没有子内容"用 `hasChildren` / `childCount`。v2 为它保留的字段在取消 `SUBLIST` 后失去含义（§0.2 第 15 条）
- `tags`：标签列表（可 `foreach` 出 `name` / `url`）
- `current`(BOOL) 与 `class`：**引擎算好的"当前项"标记**（§5.5）。取值口径写死如下，模板据此统一写 `class="card [field:class/]"`：

| 迭代项 | `current` 为真的条件 | `class` 的取值 |
|---|---|---|
| 内容查询（`list` / `query`） | 该项 id = **当前条目的 id** | `current` / 空串 |
| `{cms:channel}` 的分类项 | 该分类 = 当前页面的分类（`channel`）**或它的祖先** | `active`（是自身）/ `active-trail`（是祖先）/ 空串 |
| `{cms:channel source='menu'}` | 菜单项指向的对象 = 当前页面对象（含祖先）；**`kind='url'` 的菜单项按"目标 URL = 当前页 URL"判定**（这正是排序页 / 榜单页切换高亮的机制，§7.2.1） | 同上 |
| `{cms:channel source='type'}` | 该类型 = 当前页面的类型 | `active` / 空串 |
| `{cms:channel source='facet'}` | 该取值 = 当前页面的筛选取值 | `active` / 空串 |

**多条件叠加的链接靠 `urlWith`**（**v2.2 新增**）：`{cms:channel source='facet' field='<code>'}` 的迭代项除 `url` 外还提供 **`urlWith`** = "**当前页面的筛选组合 + 该取值**"对应的 URL；**只有当这个组合在 `facets.combos` 里声明过时才输出，否则是空串**（与本节"不做全排列"的死限一致：引擎不会因为一个链接就凭空造出组合页）。模板据此写"叠加筛选"：

```html
{cms:channel source='facet' field='region'}
  {cms:if field='urlWith'}<a href="[field:urlWith/]">+ [field:label/]</a>{cms:else/}<a class="[field:class/]" href="[field:url/]">[field:label/]</a>{/cms:if}
{/cms:channel}
```
| `{cms:breadcrumb}` | 最后一级 | `current` / 空串 |
| `{cms:pagelist}` 的页号项 | 该项页码 = 当前页 | `current` / 空串 |
| `{cms:tagnav}` / `{cms:archive}` | 该标签 / 年月 = 当前页面 | `current` / 空串 |

**统一元信息**：分页主体填 `page`（§5.6）；命名的顶层查询填 `query.<name>`（`empty` `hasResults` `totalCount` `pageSize`）。

**具名查询的结果集可以按下标取**（**v2.2 新增**，用于产品对比表、"主编推荐 3 条"这类固定位）：顶层命名的查询额外提供 `query.<name>.rows.<i>.<字段>`（`i` 从 0 起），例如 `[field:query.compare.rows.0.title/]`。**只允许顶层命名查询**（循环体内的查询不可命名，§5.4 约束三）；下标越界 → 渲染期 E1005（与 §5.1 的路径规则一致）。

> **v2.1 修改（§0.2 第 5、10、14、19 条）**：① `type` 补 `all`（跨类型"全站最新"）；② `of` 的语义改为按"锚定项"判定并补 `parent`；③ `relate` 开放给 `{cms:list}`（关系型列表可分页）；④ `class` 的归属写死成上面这张表（v2 里它同时被说成"所有查询标签的产出"和"导航类查询使用"）；⑤ `orderby` 示例里的 `pubdate` 统一为 `publishTime`；⑥ 新增 `author` 与 `status` 的时间窗。

#### `{cms:list}` — 页面主体列表（可分页）

- 用途：栏目索引页、标签页、归档页、筛选页、专题页（`relate`），以及**详情页上的目录 / 专题 / 作者文章 / 系列篇目**
- 每个模板**至多一个**；列表页允许没有（则该页只有第 1 页）
- 派生规则：`pageNo=1` → 该页 URL 规则**不含 `{n}`** 的形态；`pageNo=n` → 含 `{n}` 的形态
- 分页 URL 的来源：`LIST` 页用分类 / 标签 / 归档 / 筛选页各自的规则（§7.2）；`DETAIL` 页（列表分页）用该类型 `detail_url_pattern` 的 `{n}` 形态
- **`{cms:list}` 在详情页上的用法就是"这条内容的可分页清单"**：

```html
<!-- 书详情页：只在第 1 页显示简介，目录跨页 -->
{cms:if field='page.isFirst'}
  <div class="book-intro">
    <h1>[field:title/]</h1>
    <p>[field:summary/]</p>
  </div>
{/cms:if}

<ol class="catalog">
{cms:list type='chapter' of='self' row='100' orderby='sort'}
  <li class="[field:class/]"><a href="[field:url/]">[field:title/]</a></li>
{/cms:list}
</ol>
{cms:pagelist/}
```

#### `{cms:query}` — 任意条件列表（**不参与分页**）

- 用途：首页/侧栏的"最新 / 热门 / 推荐 / 相关 / 子内容 / 目录树"
- 可出现在循环内（此时不可命名，见约束三）
- 结果为空时标签自身不渲染；需要占位文案时**提到顶层并命名**：

```html
<!-- 全站最新：跨类型混合流（v2.1：type='all'） -->
{cms:query type='all' category='all' row='8' orderby='publishTime desc' name='latest'/}
{cms:if field='query.latest.empty'}<p class="none">本站还没有内容</p>{/cms:if}

<ul class="news">
{cms:query type='all' category='all' row='8' orderby='publishTime desc'}
  <li class="[field:class/]">
    <a href="[field:url/]">[field:title/]</a>
    <span class="type">[field:typeName/]</span>
    <time>[field:publishTime format='Y-m-d'/]</time>
  </li>
{/cms:query}
</ul>

<!-- 书详情页的"最新章节"（v2.1：of='self' 的锚定项就是这本书） -->
<ol class="latest">
{cms:query type='chapter' of='self' row='5' orderby='publishTime desc'}
  <li><a href="[field:url/]">[field:title/]</a></li>
{/cms:query}
</ol>
```

#### `{cms:detail}` — 详情页 / 单页的当前内容

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | STRING | 否 | 缺省 = 当前页面的内容类型；**显式给定时必须与页面类型的 kind 相容**（`SINGLE` 页面上只能取 `SINGLE` 类型，校验第 15 条 E3010） |

产出字段：该内容的**全部字段** + 派生字段 + 以下专用字段：

| 字段 | 说明 |
|---|---|
| `content` | **当前页正文片段**（raw）。正文分页时是切片，不是全文 |
| `contentHtml` | Markdown / 富文本的渲染结果（raw，与 `content` 同源，§5.2.2） |
| `hasPrev` / `hasNext` | 按类型 `sort_field` 的相邻内容是否存在（供 `{cms:if}` 用，配 `{cms:prenext}`） |
| `pageNo` / `totalPages` | 正文分页信息（同步进 `page` 作用域） |

- **详情页与单页模板都可以用它**（**v2.1 明确**）：
  - 在 `DETAIL` 页面上，它是分页主体（`paginate_body` 非空时产生第 2..N 页）
  - 在 `SINGLE` 页面上，它**不是分页主体**，只是"显式取出当前条目"的写法；且由于 §5.1 已把当前条目压栈底，单页模板**不写 `{cms:detail}` 也能用 `[field:title/]`**
- 正文分页符 `<!--cms:page-->` 按 §5.6 切分；`paginate_body` 指向的字段是切片对象（默认 `content`）
- **`{cms:detail}` 不产生列表**：要"相关阅读 / 同作者 / 子内容"用 `{cms:query}`（不分页）或 `{cms:list}`（可分页，见上面 `{cms:list}` 的书详情页例子）；**正文分页与列表分页不能同时存在**（§4.5 的两条死限）

### 6.4 导航标签

#### `{cms:pagelist}` — 分页条（**迭代渲染，不是直接输出 HTML**）

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `listsize` | INT | `5` | 数字页码窗口大小 |
| `items` | 逗号分隔 ENUM | `first,prev,pageno,next,last` | 要输出的分页项类型 |

迭代产出：`type`（`first`/`prev`/`next`/`last`/`pageno`/`current`）`label` `url` `current`(BOOL) `class` `rel`（`prev`/`next`/空串）

- **`label` 的来源写死**（**v2.2**）：`first` / `prev` / `next` / `last` 取站点选项 `pager.labels`（默认 `首页,上一页,下一页,末页`）；`pageno` 与 `current` 是页码数字本身。
- **当前页只发一次**：`items` 同时含 `pageno` 与 `current` 时，页号窗口里**跳过当前页**（由 `current` 那一项承担），因此当前页不会在页号条上出现两次。

```html
<nav class="pager">
{cms:pagelist listsize='5'}
  <a class="[field:class/]" href="[field:url/]" rel="[field:rel/]">[field:label/]</a>
{/cms:pagelist}
</nav>
```

#### `{cms:channel}` — 导航树（分类树 / 内容树 / 类型 / 菜单）

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `source` | ENUM `category`\|`content`\|`type`\|`menu`\|`facet` | `category` | 导航来源：分类树 / 某类型的层级内容树 / **内容类型列表**（`type`，**v2.1 新增**）/ **导航菜单**（`menu`，**v2.1 新增**）/ **筛选取值条**（`facet`，**v2.1 新增**，§7.4） |
| `type` | STRING | 当前页面类型 | `source='content'` 时的类型 code |
| `field` | STRING | — | `source='facet'` 时的 facet 字段 code（必须在该类型的 `options.facets` 里，§7.4） |
| `code` | STRING | `main` | `source='menu'` 时的菜单 code（`main` / `footer` / `friend`…，§2.4） |
| `channel` / `parent` | STRING | 顶级 | 起始节点（分类 id/slug，或内容 id/slug） |
| `depth` | INT | `1` | 向下展开层数；`>1` 时每项带 `children` |
| `countScope` | ENUM `node`\|`tree` | `tree` | `count` 是否含子分类（**v2.1 新增**：v2 的 `count` 与 `includeChildren` 的关系没定义，导航上的数字不可解释） |
| `row` | INT | 全部 | 顶层条数上限（`source='type'` 时按类型的 `sort` 排序取前 N 个） |

迭代产出：`id` `name` `label` `slug` `url` `current`(BOOL) `class` `children`（下一层列表，仅 `depth>1` 时有值）`count`（该节点下已发布内容数，口径由 `countScope` 决定）`target` `rel`（仅 `source='menu'`）。

**三个来源各自解决什么**（**v2.1 新增**，解决"混合导航无法表达"，§0.2 第 25 条）：

| 场景 | 写法 |
|---|---|
| 栏目导航（分类树） | `{cms:channel source='category' depth='1'}` |
| **行业站"六个类型 = 六个导航项"** | `{cms:channel source='type'}` —— 迭代内容类型本身，引擎照旧算 `current` |
| **官网主导航（栏目 + 单页 + 外链混排）、页脚、友情链接** | `{cms:channel source='menu' code='main'}` —— 菜单是数据（§2.4），能混排，且当前项由引擎算 |

```html
<!-- 官网主导航：栏目 + 单页 + 外链混排，当前项高亮全由引擎给 -->
<nav class="main-nav">
{cms:channel source='menu' code='main' depth='2'}
  <a class="[field:class/]" href="[field:url/]" target="[field:target/]" rel="[field:rel/]">[field:label/]</a>
  {cms:if field='children'}
  <ul class="sub">
    {cms:foreach field='children'}
      <li class="[field:class/]"><a href="[field:url/]">[field:label/]</a></li>
    {/cms:foreach}
  </ul>
  {/cms:if}
{/cms:channel}
</nav>
```

**为什么不给 `{cms:if}` 加等值比较**：菜单是**数据**，"哪一项是当前项"由引擎算——这与 §5.5 是同一条准则。加比较运算符会让 §3.8 的立论（运算符一旦出现就要定义优先级）失效，而数据方案不动语法。

#### `{cms:breadcrumb}` — 面包屑

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `from` | ENUM `home`\|`channel` | `home` | 起点：站点名 / 从当前 `channel` 起 |
| `withContent` | BOOL | `1` | **是否包含内容层级**（书 → 章、系列 → 篇）。`0` 时只到分类层（**v2.1 新增**：v2 的该标签无参数，内容层级的面包屑没有保障） |
| `contentLabel` | ENUM `title`\|`categoryName` | `title` | 内容层显示什么 |

迭代产出：`name` `url` `current`(BOOL) `class` `level`（从 1 开始）。

- 层级构成写死为：`起点 → 祖先分类（从顶到下）→ [父内容链（withContent=1 且当前条目是层级内容）] → 当前项`
- 详情页上 `level` 的最后一项是内容自己（`current=1`）；**承载了可分页目录 / 专题列表的详情页也一样**（章节页的最后一项是章、倒数第二级是书，`withContent=1` 时）

#### `{cms:prenext}` — 上一篇 / 下一篇（同类型相邻内容）

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `type` | ENUM `prev`\|`next`\|`both` | `both` | 要输出的方向 |
| `within` | ENUM `type`\|`parent` | `type` | `parent` = 仅在同一父内容内相邻（**小说章节必备**） |
| `category` | STRING | `all` | 限定同一分类内相邻（`all` = 不限）。**默认值就是 `all`**（**v2.2 明确**：v2.1 留空，而"空"到底是"不限"还是"当前栏目"无法判定，两种实现给出的上一篇不同） |

迭代产出：`type` `title` `url` `publishTime`。**不存在时不渲染该次迭代**（两个方向都不存在则整体不渲染）。
需要"已是第一篇"这类提示时用 `{cms:if field='item.hasPrev'}`（§5.5），不要在标签内分支。**`content` 不是作用域名**（§0.2 第 17 条），写 `content.hasPrev` 必然 E1004。

#### `{cms:tagnav}` — 标签总览 / 标签云

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `type` | STRING | 全部类型 | 只统计该类型下的标签；`all` = 全类型合并（跨类型同名标签按 slug 合并，`count` 为合计） |
| `row` | INT | `50` | 条数 |
| `orderby` | ENUM `count`\|`name`\|`sort` | `count` | 排序（`count` 的来源是内容数，实时算，不是 `viewCount`） |
| `minCount` | INT | `1` | 只输出内容数 ≥ N 的标签 |

迭代产出：`id` `name` `slug` `url` `count` `current`(BOOL) `class`。

#### `{cms:archive}` — 归档导航（年 / 月）

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `type` | STRING | 当前页面类型 | `all` = 跨类型的年月归档（资讯站首页侧栏的"归档"常用它） |
| `mode` | ENUM `year`\|`month` | `month` | 归档粒度 |
| `row` | INT | `24` | 条数 |
| `category` | STRING | 当前栏目 | 限定栏目，`all` 不限 |

迭代产出：`year` `month` `label`（`2026 年 3 月`）`url` `count` `current`(BOOL) `class`。

### 6.5 交互标签

#### `{cms:form}` — 表单（**H 类：静态 HTML + 动态提交**）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `code` | STRING | 是 | 表单定义 code（`cms_form`，§9.5）；必须存在，否则编译期报错（E2008） |
| `class` | STRING | 否 | 附加到 `<form>` 的 class |
| `hidden` | STRING | 否 | 追加的隐藏域，`k:v,k2:v2`。**v 可以是字面量，也可以是引擎关键字**（见下） |
| `submitLabel` | STRING | 否 | 提交按钮文字（默认取表单定义） |
| `ajax` | BOOL | `1` | 是否允许 `cms.js` 拦截为 fetch 提交；`0` 时强制原生 POST |

**`hidden` 的引擎关键字**（**v2.1 新增**，封闭清单，只有这 5 个；其余一律按字面量处理）：

| 关键字 | 渲染结果 | 用途 |
|---|---|---|
| `contentId:self` | 当前条目的 id | **产品询价 / 职位投递 / 定向留言**——后端据此知道"用户问的是哪一个产品/职位" |
| `contentType:self` | 当前条目的类型 code | 与上一项配对，避免跨类型 id 撞车 |
| `contentUrl:self` | 当前页的 URL | 后台看留言时能点回原页 |
| `contentTitle:self` | 当前条目的标题 | 后台列表直接可读 |
| `pageUrl:self` | 当前页 URL（含分页号） | 与 `contentUrl` 的区别：正文分页第 3 页也带上 |

```html
<!-- 产品详情页的定向询价：静态页里就把产品 id 写进隐藏域 -->
{cms:form code='inquiry' hidden='contentId:self,contentType:self,contentUrl:self' class='inquiry'}
```

- **理由**：参数值"永远是字面量、不做字段插值"（§3.3）是正确规定，**不为表单破例**；而"问的是哪个产品"是行业站的第一需求。加 5 个引擎认识的关键字，既不动插值规则，也不给模板作者任何表达力。
- 关键字**写在值的位置**（`contentId:self`），写在 key 的位置无效；**key 完全任意**（`hidden='utm_source:wechat,pid:contentId:self'` 合法），**只有值命中这 5 个词才替换**，其余按字面量输出、**不报错**——因此必须在**标签生成器向导**里明示这 5 个词（这是"写错静默"的唯一豁免点，记录在此）。
- **与 E2008 的关系**（**v2.2 统一**）：E2008 只校验 `code` 指向的表单存在，**不再校验 `hidden` 的 key**。v2.1 在 §0.2 第 12 条、§4.5 第 12 条、§10.2 的 E2008 文案三处都写成"key 必须落在允许清单内"，与本节"key 任意"直接冲突——照前者实现，`hidden='utm_source:wechat'` 会被拦下，而来源标记恰恰是官网上最常见的隐藏域。
- 渲染规范 HTML：`<form method="post" action="/api/public/forms/{code}" data-cms-form="{code}">` + 每个字段的 `<label>` / `<input|textarea|select>`（`required`、`placeholder`、`options` 来自表单定义）+ **5 个 `self` 关键字解析出的隐藏域** + 蜜罐域 + 提交按钮 + **站点 id 隐藏域**（`data-cms-site`，§11.1）
- **无 JS 时**：原生 POST，服务端处理成功后 302 到表单定义里的 `success_url`（默认 `/thanks/`，由站点放一个单页类型的"感谢页"）；失败 302 回来并带 `?form_error=` 参数，模板用 `{cms:if field='param.form_error'}` 显示（**key 就是 `form_error`**，是 §7.2.3 那 4 个注入 key 之一；写成 `formError` 会 E1004）
- **有 JS 时**：`assets/js/cms.js` 拦截为 fetch 提交，页内提示，不跳转
- 静态页里**不含验证码**；需要验证码时由 JS 加载（§9.3）
- 表单提交的完整契约（字段类型 → 控件、附件上传、限流、审核、通知）在 **§9.5**，本节只管"渲染出什么 HTML"

### 6.6 刻意**不提供**的标签

| 不提供 | 原因 |
|---|---|
| `{cms:global}` | `[field:site.xxx/]` 已覆盖，零新增语法 |
| `{cms:url}` | 所有需要 URL 的地方，标签都已给出 `url`（菜单项还有 `label` / `target` / `rel`）；且能避免「参数里嵌套字段引用」这一整类复杂度 |
| `{cms:empty}` | 见 §3.7 裁定二，由 `empty` / `hasResults` 字段覆盖 |
| `{cms:elseif}` | 用 `{cms:else/}` 内再嵌一个 `{cms:if}`；`elseif` 会让条件成链，是"运算符"的滑坡起点 |
| `{cms:if}` 的 **`and` / `or` / 表达式 / 相等比较形式** | 见 §3.8 的完备性说明；"当前项"由 `current` / `class` 字段给（§5.5） |
| `{cms:extends}` / `{cms:block}` | 见 §3.7 裁定六：header/footer 片段已够用 |
| `{cms:search}` | 搜索框是 3 行 HTML，由主题手写；引擎只负责产出搜索索引（§7.5）与挂载点（§9.3） |
| `{cms:comments}` / `{cms:rating}` / `{cms:views}` | 评论/评分/浏览量是 H 类交互，由 `assets/js/cms.js` + API 承担；需要 SEO 时用"评论快照静态化"（§9.6） |
| `{cms:toc}` | 正文目录不需要新标签：`toc` 是派生字段（§2.2），`{cms:foreach field='toc'}` 五行就能渲染（§5.2.5） |
| `{cms:menu}` | 菜单是 `{cms:channel source='menu'}` 的一个来源，不是新标签（§6.4） |
| `{cms:pager}` / `{cms:pages}` | `{cms:pagelist}` 一个就够；多一个写法只会让主题之间不可替换 |
| `{cms:sql}` / `{cms:php}` / 任意脚本 | 安全灾难，永久封死 |
| `{cms:ad}` 广告位 | 广告位是"带字段的内容"：建一个 `ad` 类型 + `{cms:query}`，或直接写进模板 |

### 6.7 标签 × 页面类型合法性矩阵（**v2.2 新增**）

> v2.1 只裁定了两个"分页主体"标签（§4.5），其余 12 个标签在 11 种页面类型下的行为**没有裁定**——`{cms:channel}` 写在 404 上一处报错、一处渲染空值，而"报错优于静默"是本文件反复强调的底线（§1.3、§10.3）。
> 下表写死：**✗ = 编译期 E3012**；数字 = 该标签在该页面类型上的**允许个数上限**；`✓` = 个数不限。

| 标签 | HOME | LIST | TAGPAGE | ARCHIVE | DETAIL | SINGLE | FACET | SEARCH | STATIC | 404 | feed |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `{cms:include}` / `{cms:if}` / `{cms:else/}` / `{cms:foreach}` / `[field:…/]` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{cms:list}` | 0–1 | 0–1 | 0–1 | 0–1 | 0–1 | ✗ | 0–1 | ✗ | 0–1 ※1 | ✗ | ✗ |
| `{cms:query}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ ※3 |
| `{cms:detail}` | ✗ | ✗ | ✗ | ✗ | 0–1 | 0–1 | ✗ | ✗ | ✗ | ✗ | ✗ |
| `{cms:pagelist}` | ✓ | ✓ | ✓ | ✓ | ✓ ※2 | ✗ | ✓ | ✗ | ✓ ※1 | ✗ | ✗ |
| `{cms:channel}` | ✗ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ |
| `{cms:breadcrumb}` | ✗ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ |
| `{cms:prenext}` | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| `{cms:tagnav}` / `{cms:archive}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ |
| `{cms:form}` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ |

- ※1 `STATIC` 只有**声明了 `query` 的列表页条目**能放 `{cms:list}`（恰 1 个）与 `{cms:pagelist}`（§7.2.1）；其他静态页（感谢页、落地页）放它们 → E3012。
- ※2 `DETAIL` 上的 `{cms:pagelist}` 只在**列表分页**时合法（`page.paginationKind='list'`）；用在正文分页上 → E3003（§5.6）。
- ※3 feed 只允许一个**顶层** `{cms:query}`，且不提供 `page` / `channel`（§7.3 的（4））。
- **`404` 与 `SEARCH` 没有"浏览位置"**，所以 `channel` / `breadcrumb` 不合法；`404` 只有 `site` 作用域与当前页信息（§5.1、§7.6）。
- 矩阵之外的组合**一律报错**（E3012），不静默渲染空值——这是 §10.3"报错即诊断"在标签层的落地。

---

## 7. 页面类型、URL、模板与产物（**S 类的全部定义**）

> 本节是 §1.2 判据里"S 类"的落地：**哪些页面存在、URL 长什么样、用哪个模板、产出哪些文件**。
> 四者是同一件事的四个面，任何一面缺失都会让"100% 覆盖"变成空话（v2 缺的正是本节）。

### 7.1 URL 规则

#### 7.1.1 占位符白名单（**唯一权威清单**）

| 占位符 | 取值 | 可用位置 | 必需性 |
|---|---|---|---|
| `{slug}` | 内容 slug | 类型的 `detail_url_pattern` / `list_url_pattern`；站点选项的聚合页规则 | 详情页必需（除非用 `{id}`） |
| `{id}` | 内容 id | 同上 | 与 `{slug}` 二者至少一个 |
| `{parentSlug}` | 父内容 slug | 层级类型的 `detail_url_pattern`（章节 URL 里带书名） | 可选（用了就必须有父） |
| `{sort}` | 层级内容的排序号（章节序号） | 同上 | 可选 |
| `{categoryPath}` | 主分类的完整路径（多级 slug 用 `/` 连接） | 详情页 / 列表页 | 与 `{categorySlug}` 至少一个 |
| `{categorySlug}` | 主分类的末级 slug | 同上 | 同上 |
| `{typeCode}` | 类型 code | 列表页 / 详情页 | 可选 |
| `{year}` `{month}` `{day}` | 发布时间的年月日 | 归档页；也可用于详情页做日期目录 | 归档页必需 |
| `{tagSlug}` | 标签 slug | 标签页规则 | 标签页必需 |
| `{facetPath}` | 筛选组合路径（`brand-acme` 或 `brand-acme+region-huadong`） | 站点选项 `url.facet`（§7.4） | 筛选页必需 |
| `{pageNo}` | 分页号（等价 `{n}`，`{n}` 是简写） | 列表页 / 目录页 / 正文分页 | 见 §7.1.3 |
| `{lang}` | 站点语言（`site.lang`） | 任意 | 可选（多语言站=多站点，一般不需要它） |

- **不在白名单里的占位符一律编译期报错（E4001）**，并把白名单列给模板作者。v1 的硬编码 6 条规则在这里被数据化：每个类型自己给 pattern（§2.1）。
- 模式串必须以 `/` 开头、不得以 `//` 结尾（会产出空段）、不得含 `?` / `#`（静态产物不做查询串）。
- **`{n}` 与 `{pageNo}` 是同一个占位符**（`{n}` 是简写，不另计个数）。
- **可选占位符缺值时的处理写死**（**v2.2 补**）：把该占位符**连同紧邻的一个分隔符**（`/` 或 `-`）一起删除；删完出现空段或空路径 → E4001。`{n}` 的第 1 页形态按同一规则：`/news/page-{n}/` → `/news/`、`/book/{slug}/list-{n}.html` → `/book/{slug}/list.html`、`/{categoryPath}/{slug}-{n}.html` → `/{categoryPath}/{slug}.html`。**是逐字符删除 `{n}` 与其紧邻的一个分隔符，不是截断到某个位置**——v2.1 只说"整段省略"，而 `/book/{slug}/list-{n}.html` 这个例子并不是整段，两种实现会产出 `list.html` 与 `list-1.html` 两个不同结果（后者还会造成重复收录）。
- 站点选项里还有一批**站点级 URL 规则**（不进类型定义，因为它们跨类型）：`url.tag`（默认 `/tag/{tagSlug}/`）、`url.tags`（`/tags/`）、`url.archive`（`/archive/{year}/{month}/`）、`url.search`（`/search/`）、`url.thanks`（`/thanks/`）、`url.facet`（默认 `/f/{facetPath}/`，§7.4）。

#### 7.1.2 URL → 文件路径的映射（写死，不做二义）

| URL 形态 | 产物文件 |
|---|---|
| `/` | `www/index.html` |
| `/about/`（以 `/` 结尾） | `www/about/index.html` |
| `/news/2026/03/12/post.html` | `www/news/2026/03/12/post.html` |
| `/feed.xml`、`/robots.txt`、`/sitemap.xml`、`/search/shard-0.json` | 原样 |

- **第 1 页不含分页号**：`{n}` 所在的**那一段整体省略**，并清理多余分隔符。例：`/news/page-{n}/` 的第 1 页是 `/news/`；`/book/{slug}/list-{n}.html` 的第 1 页是 `/book/{slug}/list.html`。这条规则由 `UrlResolver` 唯一实现，模板与文档都不许自己拼。
- **字符规范**：slug 白名单 `[a-z0-9]` 与 `-`（入库校验，**不自动从中文标题生成**——中文转拼音的歧义会让 URL 生命周期不可控，宁可让编辑填）。URL 里只允许 ASCII，**一律小写**（Linux 文件系统大小写敏感，`/News/` 与 `/news/` 是两个文件）。
- **尾斜杠唯一**：以 `/` 结尾的 URL 只映射到 `index.html`，不再生成同名的无斜杠文件；nginx 侧由 `try_files $uri $uri/ =404` 兜住。
- 所有产物路径经过 `SitePathBoundary` 校验（§11.4）；slug 白名单已让 `..` 不可能出现，但仍按"不信任输入"处理。

#### 7.1.3 `{n}` 与必需占位符的统一判定（**v2.1 定死，取代 v2 的三套说法**）

```
必需占位符(pageType, typeDef) → 集合：
  HOME       : 该页有分页主体且 totalPages>1 ? {{n}} : ∅
               # 首页分页的 URL 来自站点选项 url.home（默认 /page-{n}/），见下方"首页分页"
  DETAIL     : { {slug} 或 {id} } ∪ ( 该页有分页主体 ? {{n}} : ∅ )
               # 分页主体 = paginate_body 非空（正文分页）或模板里有 {cms:list}（列表分页）
  SINGLE     : ∅（路径一般写死，如 /about/；给了 {slug} 也可以）
  LIST(类型) : ∅（URL 就是该类型的 list_url_pattern 本身，如 /product/page-{n}/）
  LIST(分类) : { {categoryPath} 或 {categorySlug} }      # URL 来自站点选项 url.list
  TAGPAGE    : { {tagSlug} }
  ARCHIVE    : { {year} }（mode='month' 时还要 {month}）
  FACET      : { {facetPath} }

判定时机与文案：
  1) 编译期：pattern 里出现白名单外的占位符        → E4001（列白名单）
  2) 计划期：必需占位符缺失                        → E4001（指明缺哪个、该页面类型要什么）
  3) 计划期：pattern 里有 {n} 但该页不会分页（详情页既无 paginate_body 又无 {cms:list}）→ E4001
  4) 计划期：算出 totalPages > 1 却没有 {n}        → E4003（该页任务失败，提示补 {n} 或减少每页条数）
  5) 计划期：paginate_body 非空但 detail_url_pattern 里没有 {n} → E4001
     # v2.1 在 §2.1 写"含 {n} 才走正文分页"、在 §4.5 写"paginate_body 非空即分页主体"，
     # 两处不相容：按前者会静默丢掉 <!--cms:page--> 之后的全部正文（页面还在，内容消失）。
     # v2.2 定为计划期报错。

**首页分页**（**v2.2 新增**）：首页模板里的 `{cms:list}` 也是分页主体，第 2..N 页的 URL 由站点选项 `url.home`（默认 `/page-{n}/`）决定；首页只有 1 页时该规则不生效，也不要求它含 `{n}`。
理由：v2.1 的 §5.6 说首页用 `list_url_pattern`、§7.2.1 说首页 URL 固定 `/`，而这份判定函数里根本没有 HOME 行——三种实现会给出三种首页（发布失败 / 只出第 1 页 / 自造路径）。
```

**第 1 页与第 2..N 页是同一个模板、同一份页面上下文**（§5.4 的派生页计划），唯一差别是 `page.pageNo` 与产物路径。因此**详情页上的列表分页不需要第二套模板**——这也是取消 `SUBLIST` 页面类型（§0.2 第 15 条）的直接收益。

**冲突检测**：站点全量页面计划生成后，`UrlResolver` 对**全部产物路径**做一次去重：
- 两个不同来源映射到同一路径 → 计划期报错（E4004），并给出两个来源（例：某分类的 slug 与某单页的 URL 撞车）。
- 一个来源映射到多条路径（模式里同一占位符出现两次且取值不同）→ 报错（E4001）。
- 冲突检测是**计划期**的，不是发布期的：宁可不发布，也不发布一个被覆盖的页面。

#### 7.1.4 canonical 与 hreflang

- `canonical` = **该内容自己的绝对 URL**（`site.protocol` + `site.domain` + `[field:url/]`）。多分类内容只有一个详情 URL（§2.4），不存在"两个 URL 争 canonical"。
- 分页页面的 canonical 指向**自己**（`/news/page-2/` 的 canonical 是它自己），**不**指向第 1 页——否则第 2 页的内容不会被收录；列表页第 1 页与详情页各管各的。
- 正文分页（`{n}`）同理：`/book/x/12.html` 的 canonical 是自己，且第 2..N 页建议在模板里加 `{cms:if field='page.pageNo'}` 判 `noindex`（站点选项 `seo.paginatedIndex` 控制默认值）。
- **hreflang（多语言 = 多站点，§11.5）**：站点选项 `i18n.alternates` 配 `语言:域名` 列表，引擎在 `site.alternates` 里给出一组可迭代项（`lang` / `url` / `current`），模板写：

```html
{cms:foreach field='site.alternates'}
<link rel="alternate" hreflang="[field:lang/]" href="[field:url/]">
{/cms:foreach}
<link rel="canonical" href="[field:canonical/]">
```

  各语言站点之间用 `RELATION` 字段互相配对（`zh` 与 `en` 两条内容互指），`url` 从配对项反查；没有配对的条目不输出 `hreflang`。

### 7.2 页面类型与产物清单

#### 7.2.1 页面类型总表（11 种，v2 只有 6 条 URL 规则）

| # | 页面类型 code | 名称 | 每个站点生成几个 | URL 来源 | 分页主体 | 级别 |
|---|---|---|---|---|---|---|
| 1 | `HOME` | 首页 | 1 | 固定 `/` | 可选 `list` | S |
| 2 | `LIST` | 列表页（**两种来源**，v2.2 定死） | 类型来源：每个 `list_url_pattern` 非空且**有内容**的类型 1 个；分类来源：每个**有内容**的分类 1 个 | 类型来源用该类型的 `list_url_pattern`；分类来源用站点选项 `url.list`（默认 `/{categoryPath}/page-{n}/`） | 可选 `list` | S |
| 3 | `TAGLIST` | 标签总览页 | 1（站点选项开则出） | `url.tags` | 无 | S |
| 4 | `TAGPAGE` | 标签详情页 | 每个有内容且 `count ≥ page.tagMinCount`（默认 1）的标签 1 个 | `url.tag` | `list` | S |
| 5 | `ARCHIVE` | 归档页 | 每年 / 每月（按选项）1 个 | `url.archive` | `list` | S |
| 6 | `DETAIL` | 内容详情页（**可承载可分页列表**：目录 / 专题 / 作者文章 / 系列） | 每条已发布内容 1 个 | 类型的 `detail_url_pattern` | 可选 `list` 或 `detail`（正文分页） | S |
| 7 | `DPAGE` | 详情页的第 2..N 页（正文分页或列表分页） | 每页一条 | 同 `DETAIL` + `{n}` | 同 `DETAIL` | S |
| 8 | `SINGLE` | 单页 | 每个 `SINGLE` 类型 1 个 | 类型的 `detail_url_pattern` | 无 | S |
| 9 | `FACET` | 筛选落地页 | 按 `facets` 配置生成（只生成 count>0 的取值） | `url.facet` | `list` | S |
| 10 | `SEARCH` | 搜索页壳 | 1（`page.search` 开则出） | `url.search` | 无 | H |
| 11 | `STATIC` | 静态页（感谢页 / 联系我们 / 落地页 / **站点声明的列表页**） | 由站点选项 `pages.static` 列出 | 站点选项（**URL 可含 `{n}`**） | 可选 `list`（条目声明了 `query` 时**恰 1 个**） | S / H |

- **作者页不是独立页面类型**：作者是内置内容类型 `author`（§2.1），它的页面就是 `DETAIL`，文章列表用 `{cms:list relate='field:authorId'}`（可分页，§6.3）。这样"作者"和"产品"走同一套机制，不需要第二条代码路径。
- **正文分页（`DPAGE`）不是独立模板**：与 `DETAIL` 同一个模板、同一份上下文，只有 `page.pageNo` 不同（§5.4 的派生页计划）。
- **专题页 / 目录页 / 系列页也不是独立页面类型**：它们是 `DETAIL` 模板里的一个 `{cms:list}`（`relate='field:<code>'` 或 `of='self'`），分页 URL 用 `detail_url_pattern` 的 `{n}` 形态（§5.6）。**v2.1 定稿取消了草案中的 `SUBLIST` 页面类型**——一个规则解决四种页面形态，比新造一个页面类型加一套规划器便宜（§0.2 第 15 条）。
- **`LIST` 的两种来源为什么要分开**（**v2.2 定死**）：v2.1 只说"每个有内容的分类 1 个 / 用分类的 `list_url_pattern`"，但 §2.1 把 `list_url_pattern` 定义在**内容类型**上（"该类型的内容列表页"），`cms_category` 里也没有这一列——同一分类下同时有 `article` 与 `product` 时，一个实现出一页、一个实现出两页，URL 与模板都不同。现在写死为两条：**类型列表页**归类型定义、**分类索引页**归站点选项 `url.list`。
- **分类索引页的查询缺省**（**v2.2 定死**）：`{cms:list}` 在分类索引页上缺省 `category=当前分类`、`type='all'`（该分类下所有类型的混合流——这正是"栏目"的语义）；要只看某一类型就显式写 `type='product'`。
- **站点声明的列表页**（**v2.2 新增**）：`pages.static` 的条目可以带 `query`，此时该页就是一个**正常的列表页**、可以分页——

```json
{ "code": "rank", "url": "/rank/page-{n}/", "template": "rank.html",
  "query": { "type": "article", "orderby": "viewCountWeek desc", "row": 20 } }
```

  用途：榜单页（`/rank/`）、排序切换（`/product/sort-price/`、`/product/sort-new/`）、多地区落地页、"编辑推荐"固定列表。**排序/筛选的切换不需要查询串**（静态站没有查询串，§7.1.1）：每个排序变体就是一个独立静态页；导航与高亮交给菜单——把"最新 / 价格 / 热度"做成三行 `cms_menu_item kind='url'`，`{cms:channel source='menu'}` 会自动给当前项 `class='active'`（§6.3、§6.4）。

#### 7.2.2 产物清单（**GC 只认这张表**）

| 产物 | 路径（`www/` 之下） | 生成者 | 说明 |
|---|---|---|---|
| 页面 HTML | 按 §7.1.2 映射 | `PublishWorker` | 每种页面类型一个模板（§7.3） |
| `404.html` | `404.html` | `RobotsWriter` 同批 | 模板 `404.html`；nginx `error_page 404 /404.html` |
| `robots.txt` | `robots.txt` | `RobotsWriter` | 模板可选，缺省用内置默认（含 `Sitemap:`） |
| sitemap 索引 | `sitemap.xml` | `SitemapWriter` | 指向分片 |
| sitemap 分片 | `sitemap-{k}.xml` | `SitemapWriter` | 每片默认 10000 条（站点选项 `sitemap.shardSize`） |
| feed | `feed.xml`（+ `{categoryPath}/feed.xml`） | `FeedWriter` | 每分类一份，最近 N 条 |
| 搜索索引清单 | `search/index.json` | `SearchIndexWriter` | 分片列表 + 版本 |
| 搜索索引分片 | `search/shard-{k}.json` | `SearchIndexWriter` | 见 §7.5 |
| 重定向规则 | `redirects.conf` | `RedirectWriter` | nginx `include`（§7.6） |
| 主题资源 | `assets/**` | 产物阶段复制 | 从 `template/<theme>/assets/` 复制 |
| 站点标识 | `cms-site.json` | 产物阶段 | `{siteId, siteCode, lang, engineVersion}`，给 `cms.js` 与运维排查用（§11.1） |
| 产物清单 | `.publish/manifest.json` | `PublishManifest` | **不在 `www/` 下**（不进产物树），见 §8.4 |

- **`www/` 是引擎独占的**：GC（§8.6）只删 `www/` 里"不在本次计划内"的文件；`data/` 与 `template/` 一个字节都不动。这是 v2 把产物写进 `data/` 的直接修正（§0.2 第 23 条）。
- **不生成**：`50x.html`（服务器错误页由运维维护，不属于内容站）、`.htaccess`（用 nginx）、任何 PHP/脚本文件。

#### 7.2.3 URL 查询参数注入 `param`（**全站唯一豁免，且只用于回显**）

静态页生成时**不知道**访客的查询串，所以"从 URL 读参数"这件事原则上不存在（这正是"参数值是字面量"的由来，§3.3）。但有两类页面必须在拿到参数后才能工作，因此开一个**死限范围内的口子**：

| 页面类型 | 注入的 key | 用途 |
|---|---|---|
| `SEARCH` | `q`（关键词）、`page`（页码，仅用于回显） | 搜索页把用户输入回填到输入框；真正的检索由 `cms.js` 或 `/api/public/search` 完成（§7.5/§9.8） |
| 任意页面（主要是 `STATIC` 与表单所在页） | `form_error`（错误码）、`form_ok`（成功标记） | 无 JS 表单提交失败后 302 回原页，模板据此显示"提交失败"提示（§6.5/§9.5） |

规则（**四条，缺一不可**）：

1. 注入的 key **只有这 4 个**（`q` / `page` / `form_error` / `form_ok`），且都在 `param` 作用域里，写法是 `[field:param.q/]`；
2. 注入只用于**文本回显**，**不许**参与任何查询参数（`{cms:list keyword='[field:param.q/]'}` 仍然非法——参数不做字段插值，§3.3）；
3. 注入的值**一律按模板文本转义后输出**（它来自 URL，是访客输入）；需要放进属性时模板作者必须意识到这一点（引擎按 `raw=false` 处理，与普通字段一致）；
4. 注入的 key **不改变任何产物**：同一份静态 HTML 服务所有查询串，产物里不存在"带参数的页面"（`?q=x` 不产生新页面，也不进 sitemap）。

- 后台预览（§8.7）与预发布页面同样支持这四个 key，方便模板作者验收"搜索页回显"与"表单报错提示"。

### 7.3 模板查找规则（**不定这个无法开工**）

**（1）目录约定**

```
sites/<站点>/template/<主题名>/          ← 主题根（site.theme 指向它）
    theme.json                           ← 主题清单：name / version / engine / requires（§8.9）
    index.html                           ← 首页
    article_list.html  article_detail.html
    product_list.html  product_detail.html
    book_detail.html                     ← 详情 + 可分页目录（§6.3 的例子）
    book_list.html                       ← 书的栏目列表
    single.html
    tag_list.html  tags.html  archive_list.html  facet_list.html
    search.html  thanks.html  404.html  robots.txt  feed.xml
    _partials/                           ← 片段目录：**不参与页面查找**，只被 include
        header.html  footer.html  card.html  pagelist.html
    assets/                              ← 主题自带 css/js/img，产物阶段复制到 www/assets/
```

- **`_` 前缀的目录与文件不参与页面查找**（只作片段），这样"片段"与"页面"在同一个树里不会互相误配。
- 主题名来自 `site.theme`；为空时用内置的 `_default` 主题（引擎自带一套最小可用模板，保证"建站即可发布"）。
- 类型定义里的 `detail_template` / `list_template`（§2.1）**优先于**主题默认规则，用于"某类型要特殊模板"。

**（2）查找顺序（先命中先用，逐级回退）**

| 页面类型 | 候选模板（按顺序） |
|---|---|
| `HOME` | `index.html` → `home.html` |
| `LIST`（类型列表页） | `{typeCode}_list.html` → `list.html` |
| `LIST`（分类索引页） | `category_list.html` → `list.html` |
| `LIST`（标签） | `tag_list.html` → `{typeCode}_list.html` → `list.html` |
| `TAGLIST` | `tags.html` → `tag_list.html` → `list.html` |
| `LIST`（归档） | `archive_list.html` → `list.html` |
| `FACET` | `facet_list.html` → `{typeCode}_facet.html` → `{typeCode}_list.html` → `list.html` |
| `DETAIL`（含目录 / 专题 / 作者文章 / 系列） | 类型定义 `detail_template` → `{typeCode}_detail.html` → `detail.html` |
| `DPAGE`（正文分页或列表分页） | 与 `DETAIL` **同一模板**（只有 `page.pageNo` / `page.paginationKind` 不同） |
| `SINGLE` | 类型定义 `detail_template` → `{typeCode}.html` → `single.html` → `detail.html` |
| `SEARCH` | `search.html`（没有则用引擎内置搜索页壳） |
| `STATIC` | 站点选项条目里的 `template`（如 `thanks.html`；列表页条目写 `rank.html`） |
| `404` | `404.html`（没有则用内置默认） |

- **`{typeCode}` 是内容类型 code**（`article` / `product` / `book`…），因此行业站只要写 `product_detail.html`、`case_detail.html` 就能各自成型，不需要在类型定义里逐个填模板路径。
- 全部候选都不存在 → 计划期报错（E4002），文案里**列出找过的全部路径**（模板作者最需要的就是这个列表）。
- 主题**不必提供全部模板**：找不到就报错，但错的粒度是"这个页面类型缺模板"，不是"站点发布失败"——§8.2 的计划生成支持"部分页面类型不出"（用站点发布选项关掉，§2.7）。

**（3）站点选项关闭时不开工**：`page.archive=0` 时不会去找 `archive_list.html`，也不会因为缺它而报错。

**（4）非页面产物的模板**（**v2.2 补**）：`robots.txt` / `feed.xml` 直接在主题根按同名文件查找（`404.html` 已在第（2）表里）。

- **feed 模板的作用域**：`site` + 一个顶层 `{cms:query}`（迭代项 = 该查询的迭代项，额外提供 `contentHtml` 与 `files`，分别供全文订阅与 `<enclosure>` 用；`feed.includeBody=1` 时才需要 `contentHtml`）。feed 里**不提供** `page` / `channel` / `{cms:list}`（它没有分页、也没有浏览位置）。
- 主题不提供 `feed.xml` 时用引擎内置模板（标题 + 链接 + 摘要 + 时间），**不报错**——feed 是运维产物，不该因为主题少一个文件就阻断整站发布。

### 7.4 筛选落地页（行业站产品筛选 / 多地区落地页）

**（1）配置在类型上，不在模板里**：类型的 `options.facets` 给出可用筛选字段与是否出落地页：

```json
{ "facets": [
    { "field": "brand",  "page": true,  "label": "品牌" },
    { "field": "region", "page": true,  "label": "地区" },
    { "field": "price",  "page": false, "label": "价格" }
] }
```

- `field` 必须是该类型 `indexed=1` 的字段（`ENUM` / `ENUM_MULTI` / `BOOL` / `INT` / `DECIMAL` / `TEXT`）；`TEXT` 字段做 facet 时会按**去重取值**生成页面，超过站点选项 `facets.cardinality`（默认 50）的值一律不生成（否则"品牌型号"这种字段会炸出几千页）。
- **不做全排列笛卡尔积**（这是本节的第一个死限）：只生成两类页面：
  1. **单字段落地页**：`/f/brand-acme/`（每个取值一页）；
  2. **显式配置的交叉页**：站点选项 `facets.combos` 里手写的组合（如 `brand-acme+region-huadong`）。

  理由：3 个字段 × 各 10 值 = 1000 个组合页，其中绝大多数是"0 条结果"的空页；全排列还会让 GC 与增量发布的规模失控。代价如实记录：**"品牌 A + 地区 B"这类组合页需要手工配置**，引擎不自动穷举。

**（2）URL 与模板**：`url.facet` 默认 `/f/{facetPath}/`，`facetPath` 形如 `brand-acme` 或 `brand-acme+region-huadong`（每段是 `字段-取值slug`，用 `+` 连接）。分页追加 `{n}`：`/f/brand-acme/page-{n}/`。

**（3）页面内的数据**：筛选落地页上的 `{cms:list}` **自动带上本页的筛选条件**——模板不需要写 `where`（这也是"参数值是字面量、无法插值"的必然要求）：

```html
<h1>[field:channel.label/] 产品</h1>            <!-- channel 是筛选组合；显示名用 label（§6.4 产出清单） -->
{cms:channel source='facet' field='brand'}      <!-- 品牌筛选条：链接 + 计数 + 当前项 -->
  <a class="[field:class/]" href="[field:url/]">[field:label/]（[field:count/]）</a>
{/cms:channel}

{cms:list type='product' row='24'}              <!-- 筛选条件由引擎注入，无需 where -->
  ...
{/cms:list}
{cms:pagelist/}
```

**（4）数量上限**：单站点筛选页总数超过 `facets.maxPages`（默认 500）→ 计划期报错（E4005），提示收窄 `facets` 配置或提高上限。上限存在的理由：筛选页是**重复内容**，几千个近似的列表页会把站点的索引质量拉平，且 sitemap 会被它们占满。

**（5）默认 noindex**：筛选页默认 `noindex,follow`（站点选项 `seo.facetIndex` 可开）。理由同上：筛选页的价值在给用户加筛选，不在被搜索收录。

### 7.5 搜索索引产物（**没有分词器也能搜**）

**（1）产物形态**：静态 JSON 分片，前端 `cms.js` 拉取后本地匹配（H 类页面不需要后端参与）。

```
www/search/index.json         { "version": "<索引版本>", "shards": ["shard-0.json", ...], "count": 12345, "fields": [...] }
www/search/shard-0.json       [ { "i": 12, "t": "标题", "u": "/news/x.html", "s": "摘要", "c": "分类名", "p": "2026-03-12", "b": "正文前 N 字" }, ... ]
```

- 字段全部**短名**（`i`/`t`/`u`/`s`/`c`/`p`/`b`）——索引体积是这条链路上最贵的资源。
- 入索引的内容：`status='PUBLISHED'` 且 `publish_time <= now()`、类型选项 `searchable!==false`、字段定义 `searchable=1`（§2.2）的 `TEXT` / `TEXTAREA` / `RICHTEXT` / `MARKDOWN` / `ENUM` 字段，拼成 `b`（正文）并**截断到 `search.bodyChars`（默认 1000 字）**。
- `ENUM_MULTI` / `TAGS` 进 `c`（分类与标签串），用于过滤（`cms.js` 支持按类型/分类/标签二次过滤）。

**（2）中文为什么不用分词**：不引入分词器（jieba 之类会让发布链路多一个 JVM 依赖与词典资源），检索用**子串包含匹配**（`haystack.includes(query)`）——中文按字包含天然正确，英文按词前缀匹配。代价如实记录：① 索引里要存原文（体积），因此必须截断 + gzip；② "拼音/同义词/纠错"全都不支持；③ 大站（> 5 万条）前端匹配会卡，此时切 API 模式（下一条）。

**（3）分片与阈值**：每片 `index.shardSize`（默认 2000 条）；`search.mode` 三选一：
- `static`（默认，索引 < `search.staticMax`，默认 50000 条）：纯前端；
- `api`（默认在超出阈值时自动切换，也可显式指定）：前端调 `/api/public/search`（§9.8），索引产物**仍生成**（供 API 读取与降级）；
- `off`：不出索引、不出搜索页。

**（4）与模板的关系**：搜索页模板只管"壳 + 表单 + 结果容器"，不写任何查询标签（搜索是运行时的、静态生成时无查询词）：

```html
<form class="search-form" action="/search/" data-cms-search>
  <input type="search" name="q" value="[field:param.q/]" placeholder="搜索…">
  <button type="submit">搜索</button>
</form>
<div class="search-result" data-cms-search-result>
  {cms:if field='param.q'}<p class="tip">正在搜索…（无 JS 时请看下方 API 提示）</p>{/cms:if}
</div>
```

- `[field:param.q/]` 来自查询串注入（§7.2.3：`q` / `page` / `form_error` / `form_ok` 四个 key 以同名 key 注入 `param` 作用域），**这是唯一的"参数值来自 URL"的场合**，且它只影响静态页上的一次文本回显，不做任何数据查询。
- `search.staticMax` 与 `search.mode` 记在站点选项（§2.7）。

### 7.6 运维产物：robots / sitemap / feed / 404 / 重定向

| 产物 | 规则 |
|---|---|
| `robots.txt` | 主题提供 `robots.txt` 模板则用它；否则内置默认：`User-agent: *`、`Disallow: /api/`、`Disallow: /search/`、`Allow: /`、`Sitemap: <绝对地址>/sitemap.xml` |
| `sitemap.xml` + 分片 | **只收录 S 类页面**且 `noindex=0` 的页面；分片按 `sitemap.shardSize`（默认 10000）；每条的 `lastmod` = 该页面**输入集合**里内容的最大 `update_time`（这正是 §8.4 依赖清单的副产品：manifest 里已经记了 `contentIds`）；聚合页（列表/归档/标签）的 `lastmod` 取该页依赖集合的最大值 |
| `feed.xml` | 最近 `feed.size`（默认 20）条 `article`（类型可配 `feed.types`）；分类 feed 落在该分类的列表 URL 下（`{categoryPath}/feed.xml`）；主题可选提供 `feed.xml` 模板，缺省用内置（标题 + 链接 + 摘要 + 时间，Atom 与 RSS 二选一由站点选项 `feed.format`，默认 `rss`）。**`feed.includeBody=1` 时条目带正文**（全文订阅，博客常用）；**`<enclosure>` 用条目的 `FILE` 字段**（`url` / `filesize` / `mime`）——**v2.2 补**：v2.1 只写了 `feed.types`，而播客必需的 enclosure 全文没有规则 |
| `404.html` | 无数据（不进任何作用域，只有 `site`）；建议主题里给出搜索框与栏目导航 |
| `redirects.conf` | 由 `cms_redirect` 表生成（nginx `include`）：`location = /old/path/ { return 301 /new/path/; }`；只写**站点内**重定向。**写表时机写死**（v2.2）：`cms_redirect` 在**内容 / 分类保存时**写入（slug 变更、层级变更、主分类变更、分类改名导致 URL 变更时），后台可手工补录；`redirects.conf` 在**发布批次的第 ⑥ 阶段**产出。保存写表、发布出文件，两件事不混 |
| `cms-site.json` | 站点标识与引擎版本，供 `cms.js` 与排障使用（§11.1） |

**noindex 的传播**：`page.noindex`(BOOL) 与 `page.robots`（字符串，如 `noindex,follow`）由引擎按页面类型与站点选项算出，进 `page` 作用域（§5.6），模板写：

```html
{cms:if field='page.noindex'}<meta name="robots" content="[field:page.robots/]">{/cms:if}
```

默认 noindex 的页面类型：`FACET`（§7.4）、`DPAGE` 的第 2..N 页（站点选项 `seo.paginatedIndex=0` 时）、`SEARCH`、`404`；**再加站点选项 `seo.noindexTypes` 列出的内容类型**（**v2.2 新增**："章节页要不要收录"是站点的策略，只靠页面类型表达不了——小说站通常希望章节页不进 sitemap 但书详情页进）。
**sitemap 与 noindex 必须一致**：`page.noindex=1` 的页面不进 sitemap——两处不一致是 SEO 事故的常见来源，因此二者由**同一个判定函数**产出（引擎侧一处实现）。

### 7.7 媒体与派生尺寸

**（1）三档派生尺寸**（**v2.1 定死**，v2 只说"派生尺寸，§7.7"而 §7.7 不存在）：

| `size` | 长边上限 | 用途 |
|---|---|---|
| `thumb` | 320px | 列表小图、卡片图、相关阅读 |
| `medium` | 768px | 详情页插图、图集缩略 |
| `large` | 1280px | 头图、图集大图、og:image |
| （不写） | 原图 | 下载、放大查看 |

- **等比缩放，不裁切**（裁切是版式决策，交给 CSS `object-fit`）；不放大（原图小于目标时直接给原图）。
- 格式：原图 **不是** GIF/SVG 时额外产出一份 WebP，`size` 默认输出 WebP 并用 `<picture>` 回退原格式；GIF/SVG 一律原样（动图与矢量转码会丢信息）。

**（2）URL 形态**：`/uploads/derive/{sha1前16位}-{size}.{ext}`，例如 `/uploads/derive/9f2a1c33bb04e7d1-thumb.webp`。以内容哈希命名 → 同一张图被多个内容引用时只有一份派生文件，且**改图必换 URL**（缓存永不脏）。

**（3）生成时机：媒体入库时生成，不在发布时生成。**

- 上传 / 入库事务提交后异步生成三档（失败重试 3 次，仍失败则记 `cms_media.derive_status='FAILED'` 并在后台媒体库标红）。
- **发布阶段只做"引用校验 + 复制"**：发布时检查被引用的派生文件是否存在，缺失就回退原图 URL 并在发布日志给警告（W5003）。理由：数万页的发布必须在分钟级完成，不能顺带压图；压图是一次性成本，发布是重复成本。

**（4）元数据口径（**v2.1 明确**，v2 没说 `[field:x.width/]` 指哪张图）：

| 写法 | 含义 |
|---|---|
| `[field:cover.width/]` / `.height` | **原图**宽高 |
| `[field:cover.width size='thumb'/]` | **派生图**宽高（`size` 与 `src` 用同一个参数） |
| `[field:cover.alt/]` | 媒体库的 `alt`；为空时回退到当前内容的 `title`（无障碍与 SEO 都需要它非空） |
| `[field:cover.filesize/]` | 字节数（`size` 参数同上） |
| `[field:cover.mime/]` / `.ext/` | 媒体类型 / 扩展名 |

- 模板要写响应式图时用 `IMAGES` + `foreach`（§6.2 的例子已经是 `width` / `height` + `loading="lazy"`）。

**（5）媒体出口**：静态页里的 `/uploads/...` 必须能被 nginx 直接服务——这是"上线即炸"的一条，见 §11.2。

### 7.8 产物树与路径边界

```
sites/<rootDir>/                       ← cms.site.root-dir 之下，SiteService 管理
    template/<theme>/                  ← 主题（引擎只读，导入见 §8.9）
    www/                               ← 引擎独占：全部产物（§7.2.2）
    data/                              ← 人工资产（原有语义不变：手写的 css/js/img/下载文件）
    release/<batchId>/                 ← 发布批次副本与回滚点（§8.7）
    .publish/manifest.json             ← 依赖清单（§8.4，不在 www/ 下，不进产物树）
```

- **`www/` 与 `data/` 的边界就是 GC 的边界**：`ArtifactGc` 只允许在 `www/` 内删除（§8.6），从而彻底解决 v2 的"幽灵页"与"不敢删"（§0.2 第 23 条）。
- **后台上传的人工文件进 `data/`，不进 `www/`**：`www/assets/` 由主题的 `assets/` 每次发布复制（人工改产物里的 css 会被下次发布覆盖——这是引擎独占树的必然代价，UI 上要明确标"引擎生成"）。
- **产物文件不加文件系统只读位**（v2 声称"已在文件树标只读"，而现有 `SiteFileService` 里没有任何只读标记）：改为**后台站点目录页对 `www/` 只读**（写入被服务层拒绝，错误码 E6001），并在文件树 UI 标注"引擎生成，勿手改"。理由：文件系统只读位在 Windows/Linux 上语义不一致，且会给"回滚"（§8.7）带来额外解锁步骤。
- **产物编码与换行**：UTF-8 无 BOM、`\n`、文件末尾保留换行（与 git 习惯一致，便于人工 diff 排查）。
- 路径长度：单条产物路径不超过 200 字符（超长路径在 Windows 与部分 CDN 上会出问题），超限在计划期报错（E4006）。

---

## 8. 静态化引擎（**从内容到文件的全过程**）

### 8.1 发布单元与总体流程

一次发布 = 一个 **批次（batchId）**，作用于**一个站点**。九个阶段，每阶段都可单独观测：

| 阶段 | 做什么 | 失败后果 |
|---|---|---|
| ① 冻结 | 快照 `defVersion`（类型/字段定义）、站点配置、菜单、模板 `mtime+size`、`now()`（决定定时发布的时间窗） | 直接失败（读到不一致的定义比失败更糟） |
| ② 计划 | 生成**全量**页面计划（§8.2），算出 URL 与产物路径，做冲突检测（§7.1.3） | 不写盘，站点不受影响 |
| ③ 差异 | 对比本次计划与 `manifest.json` + 反向索引（§8.4），得出"要重写的页"、"要删的产物"、"要重出的运维产物" | 不写盘 |
| ④ 渲染 | 页级并发渲染（§8.3），每页独立 `RenderContext` | 该页失败 → 批次标记部分失败 |
| ⑤ 写盘 | 单页原子写（§8.5），同时记录该页的依赖清单 | 该页保持旧版本（磁盘上仍是上一次的产物） |
| ⑥ 聚合产物 | sitemap / feed / robots / 搜索索引 / redirects / `cms-site.json`（§7.2.2） | 批次失败（聚合产物不完整会误导搜索引擎） |
| ⑦ 校验 | 空页检查、链接检查（可选）、体积对比、sitemap 与 noindex 一致性 | 默认只警告，站点选项 `publish.strict=1` 时失败 |
| ⑧ 批次落定 | 写 `release/<batchId>/` 副本与 manifest（§8.7） | 失败则本次不可回滚（产物仍已更新） |
| ⑨ 搬运 | 按 `publish.syncTarget` 同步到站点根目录（§8.10） | 本地已发布，搬运可重试 |

- **②③④ 分离**是"可预演"的前提：后台能只跑到 ③ 就给出"本次将重写 42 个页面、删除 3 个文件"，让编辑在动手前看见代价。
- **站点级串行**：同一站点同一时刻只允许一个批次（产物 + GC 会打架）；不同站点可并行（`publish.siteConcurrency`，**引擎全局配置**默认 2——v2.1 把它写成"站点选项"是笔误）。
- **第二个批次怎么办**（**v2.2 定死**）：**不排队、不拒绝，合并**——同一站点已有 `RUNNING` 批次时，新触发只把它的差异集并入当前批次的 pending 集（编辑连点两下不会跑两遍，也不会丢掉后一次改动）。

### 8.2 页面计划（`PagePlan`）

每个计划项：

| 字段 | 说明 |
|---|---|
| `pageType` | §7.2.1 的 11 种之一 |
| `url` / `path` | URL（`UrlResolver` 唯一出口）与产物相对路径（§7.1.2） |
| `template` | 命中的模板相对路径（§7.3） |
| `sourceRef` | 数据来源：`{kind:'content'\|'category'\|'tag'\|'archive'\|'facet'\|'static'\|'home', id/code}` |
| `pageNo` | 分页号（1 起） |
| `anchorId` | 该页的当前条目 id（§5.1），列表页为 `null` |
| `deps` | 依赖清单（§8.4） |

- **计划生成是纯读**：不写库、不写盘、不改任何状态。因此可以"只算计划"（预演）、可以在事务外做、可以缓存。
- **全量计划是正确性的基础**：增量发布**也生成全量计划**，只是渲染阶段只处理差异集。理由写在 §8.4——"本页应该变"与"本页应该消失"都只有全量计划才看得见。
- 规模参考：5 万页的计划在内存里约 30–60 MB（含依赖摘要），一次生成在秒级；这个量级不需要分页或落盘。

### 8.3 任务队列与执行

表 `cms_publish_task`：`id` / `site_id` / `batch_id` / `trigger`（`manual` / `content` / `schedule` / `theme` / `expire`）/ `mode`（`full` / `incremental` / `narrow` / `aggregate`）/ `status`（`PENDING`/`RUNNING`/`SUCCESS`/`PARTIAL`/`FAILED`/`CANCELLED`）/ `total` / `done` / `failed` / `message`（前 N 条错误）/ `create_by` / `create_time` / `start_time` / `end_time`。

- **页级并发**：worker 线程池大小 `publish.threads`（默认 `min(4, CPU)`）。每页一个独立 `RenderContext`，AST 与定义快照**只读共享**（§4.4），因此并发天然安全。
- **进度**：`done/total` + 当前阶段；后台用 SSE 推送（退化时轮询）。
- **重试**：模板/数据类错误**不重试**（确定性失败，重试只是浪费时间）；IO 类错误（磁盘、网络搬运）重试 3 次、退避 1s/4s/16s。整个批次可手工重跑（幂等，§8.4）。
- **取消**：可取消。取消时**不执行 GC**（半途 GC 会删掉本应保留的产物），批次状态 `CANCELLED`，下次发布自然收敛。
- **GC 只认全量计划**（**v2.2 定死**）：`narrow`（只发一篇 / 只发一个栏目）与 `aggregate`（只重建 sitemap / feed / 索引）**一律跳过 GC**。理由：§8.6 的 GC 输入是"本次**全量**计划的 path 集合"，窄批次的计划里只有 1 页，按字面执行会把全站其余产物当成幽灵页删光。
- **超时**：单页渲染 10s（`publish.pageTimeout`）。超时按失败计。语法里没有循环，但 `{cms:foreach}` 的 `row` 与 `depth` 可能让单页数据量爆炸，超时是最后一道闸。
- **不阻塞编辑**：内容保存只写库 + 入队（§8.8 的延迟合并），**不同步等发布**。保存成功与页面更新之间最多差一个批次周期（默认 5 秒合并窗口 + 渲染时间）。

### 8.4 增量：输入依赖清单 + 反向索引（**取代 v2 的产物内容哈希**）

**（1）每页一份依赖清单**（`manifest.json` 的一项）：

```
{ "path": "news/2026/03/12/post.html",
  "pageType": "DETAIL", "url": "/news/2026/03/12/post.html",
  "hash": "<产物内容哈希，仅用于『内容是否真的变了』的二次判断>",
  "deps": {
    "siteConfig": 7, "defVersion": 12, "ast": "a1b2c3d4",
    "contentIds": [1201], "parentIds": [], "categoryIds": [3],
    "tagIds": [11,12], "authorIds": [5], "menuIds": ["main"],
    "neighborIds": [1200, 1202], "relationIds": [88],
    "facetKeys": [], "mediaIds": ["9f2a1c33"],
    "formIds": ["inquiry"], "ratingIds": [1201],
    "commentsHash": "sha256:5b1e...", "clockBucket": "2026-03-12",
    "orderbyFields": ["viewCountWeek"]
  } }
```

**v2.2 新增的五个依赖键**（v2.1 的清单漏了它们，导致这些页面"永远不更新"）：

| 键 | 什么时候记 | 为什么必须有 |
|---|---|---|
| `formIds` | 页面里有 `{cms:form}` | 表单的 label / options / 必填改了，静态页上的控件不会变 |
| `ratingIds` | 页面输出 `ratingAvg` / `ratingCount` | 评分快照的失效依据（§9.6） |
| `commentsHash` | 页面输出评论快照 | 评论审核通过后必须重渲染该页（§9.6） |
| `clockBucket` | **所有页面** | `format='relative'`、`updatedDaysAgo`、`site.year` 依赖"今天是哪天"；跨日必须全站重渲染，否则"3 天前"会永远停在 3 天前 |
| `orderbyFields` | 页面里有按内置计数字段排序的查询 | `viewCount` 类排序的页面无法从别的键推演出失效（§8.8） |

**（2）反向索引**：由清单派生 `path ← 依赖项` 的倒排表（内存 + 落盘 `.publish/deps.idx`）。

| 变更 | 失效范围 |
|---|---|
| 内容新增 / 修改 / 删除 / 上下线 / 改 slug / 改分类 | ① 该内容自己的页面；② 所有 `contentIds` 含它的页面（列表页、专题、相关阅读）；③ `neighborIds` 含它的页面（**上一篇/下一篇**）；④ `relationIds` 含它的页面；⑤ 聚合产物（sitemap / feed / 搜索索引 / 分类计数页） |
| 分类 / 标签 / 菜单变更 | 相关聚合页 + 所有 `menuIds` 命中的页面（**导航在 header 里 → 通常等于全站**，这时走"改模板"级的全量差异，但仍然只重写内容真变了的页——hash 二次判断会挡掉大部分写入） |
| 模板 / 片段变更 | `ast` 变 → 使用该模板（含 include 链）的全部页面 |
| 类型 / 字段定义变更 | `defVersion` 变 → **该站点全部页面**（编译缓存也全部失效，§4.4） |
| 站点配置 / 发布选项变更 | `siteConfig` 变 → 该站点全部页面 |
| 浏览量变化（榜单） | `viewCount` 依赖页：榜单页、侧栏热度、按 `viewCount` 排序的查询所在页（§8.8 定时） |
| 媒体派生文件（重传图） | `mediaIds` 命中的页面（URL 由哈希决定 → 改图必换 URL，因此**只会**影响引用了它的页） |
| **时间推进**（`format='relative'` / `updatedDaysAgo` / `site.year`） | `clockBucket` 变 → **该站点全部页面**重渲染（日粒度）。这也是"每日全量重建"真正生效的机制：没有它，差异渲染会认为"输入没变"，相对时间就永远停在"3 天前"（**v2.2 补**） |
| 表单定义变更（`cms_form` / `cms_form_field` 的 label / options / required） | `formIds` 命中的页面 |
| 评论审核通过 / 删除 | `commentsHash` 变化的页面（该内容的详情页，§9.6） |
| 评分聚合变化 | `ratingIds` 命中的页面（§9.6） |
| **排序依赖变化** | `orderbyFields` 含 `viewCount` / `viewCountDay` / `viewCountWeek` 的页面（§8.8 每小时重建） |
| **分类改名 / 移动 / 删除** | ① 该分类**含全部子分类**下的**所有内容详情页**（URL 里含 `{categoryPath}`，必须重算 URL 并删除旧产物）；② 分类索引页与聚合产物；③ `menuIds` 命中的页面；④ 每条受影响内容按 §7.6 写 301。**v2.2 补**：v2.1 只写了"相关聚合页 + menuIds"，漏掉详情页——旧 URL 会残留且没有 301 |
| 内容到期（`expire_time` 到点） | 该内容的全部产物（从计划里消失 → §8.6 删除）+ sitemap / feed / 索引 + 榜单 |

**（3）为什么这比"产物内容哈希"对**：v2 的方案（内容哈希 + 模板 mtime → 跳过）有两个方向的错：

- **陈旧页**：新增一篇文章会改变**别的**页面——上一篇的 next 链接、归档计数、标签计数、列表页分页、sitemap、feed。产物自身哈希**发现不了"本页应该变"**。
- **幽灵页**：删除方向完全没有覆盖（v2 全篇没有清理机制）。现在由 §8.6 的 GC 按全量计划补齐。

**（4）代价与兜底**：
- 每页一份清单（大站可只存 id 集合的摘要 + `hash`），磁盘占用约"页面数 × 150 字节"：5 万页 ≈ 7.5 MB，可接受。
- 反向索引**宁可多不可少**：多渲染几页只浪费算力，漏渲染是线上事故。因此 `menuIds` / `tagIds` 这类"可能波及全站"的依赖一律记全。
- **兜底**：`publish.mode` 可强制 `full`；每日 `publish.cron` 的全量重建会自愈任何漏报。发布是**幂等**的（同一输入产出同一结果），因此全量重跑永远安全。注意"每日全量重建"= **全量计划 + 差异渲染**：它本身不产生重渲染，真正让每天必有一次全站刷新的是 `clockBucket`（见上表）——v2.1 说它"自愈任何漏报"，但漏报若不改变任何输入，差异渲染仍然会跳过。

### 8.5 原子写与一致性

- 单页写入：写同目录临时文件 → `fsync` → `rename` 覆盖目标（同一文件系统内的 rename 是原子的）。目录不存在则先建。
- **单页原子，批次不原子**：一个批次运行期间，站点产物是"混合版本"。因此：
  - **同一个域名下不要边发布边给用户看**（生产切换靠 §8.7 的预发布 + §8.10 的搬运）；
  - `manifest.json` 与 `deps.idx` 在所有页面写完后**一次性原子替换**（先写 `.tmp` 再整体 rename），保证"清单与实际产物"不会长期不一致。
- 写盘顺序：页面 HTML → 索引与聚合产物 → `cms-site.json`（**它出现代表本批次的产物已完整**，运维与 `cms.js` 都读它）。
- 失败恢复：批次失败时**保留已完成产物**（它们各自都是完整的单页），线上看到的是上一次搬运的结果；本地状态由下一次批次收敛。
- **失败页在清单里怎么写**（**v2.2 定死**）：渲染失败或超时的页面，其 `manifest` 条目**原样保留上一批次的记录**（`path` / `deps` / `hash` 都不改），并在 `report.json` 里标 `stale`。若写成本批次的新记录，下一次增量会认为"这页已经是最新的"→ **该页永久陈旧**，正是 §8.4 要防的那类 bug。

### 8.6 产物 GC 与幽灵页（**v2 完全没有这一节**）

**输入**：本次全量计划的路径集合 `plan` + 历史清单 `known`（`manifest.json` 的全部 path）。

**规则**：

| 情况 | 处理 |
|---|---|
| `www/` 下的文件 ∈ `plan` | 保留（该重写的在第 ⑤ 步已重写） |
| `www/` 下的文件 ∉ `plan` 且 ∈ `known` | **删除**（内容下线、删除、改 slug、分类删、标签删、页面类型关掉…） |
| `www/` 下的文件 ∉ `plan` 且 ∉ `known` | **保留**（不是本引擎产出的东西，不碰）——这条是"敢删"与"别误删"之间的折中 |
| 空目录 | 自底向上删除，止于 `www/` |
| `data/`、`template/`、`release/` | **永不触碰**（§7.8 的边界就是 GC 的边界） |

- **人工改过产物的处理**：文件 mtime 与清单记录不一致时**仍然删除**（`www/` 是引擎独占树），但日志里给一条 `W5004` 警告并列出文件名——"谁改的产物"是需要人知道的事。
- **幽灵页场景覆盖自检**（这张表就是"GC 是否写全"的判据）：

| 操作 | 旧产物 | sitemap / feed / 索引 | redirects.conf |
|---|---|---|---|
| 内容下线（`OFFLINE`） | 删除 | 移除 | 不新增 |
| 内容删除 | 删除 | 移除 | 不新增 |
| 内容改 slug | 删除旧路径 | 换新路径 | **新增 301**（§7.6） |
| 内容改主分类（URL 含 `{categoryPath}`） | 删除旧路径 | 换新路径 | **新增 301** |
| 分类删除 / 改名 | 该分类页删除 | 移除 | 该分类下所有内容 URL 变 → 逐条 301 |
| 标签不再有内容 | 标签页删除 | 移除 | 不新增 |
| 关闭 `page.facet` | 全部筛选页删除 | 移除 | 不新增 |
| 换主题 | 与主题无关（路径不变） | 不变 | 不变 |

- **搜索索引与 sitemap 不存在"幽灵条目"问题**：它们是每次**全量重写**的（不是增量追加），因此只可能"少"不可能"多"。

### 8.7 发布批次、预发布与回滚

- **批次目录** `release/<batchId>/`：本次**写过的文件**副本 + `manifest.json` 快照 + `report.json`（统计、警告、涉及的内容 id 列表）。
- **保留**：`publish.keepReleases`（默认 3）。超出按批次时间删最旧。磁盘代价：一次全量 ≈ 站点体积；3 个批次 ≈ 3 倍。小站可调成 1，大站可调成 0（放弃回滚换空间，但要显式写这个决定）。
- **预发布**：`publish.preview=1` 时产物写到 `preview/www/`，通过 §11.2 的只读接口在后台预览（`/api/preview/{siteCode}/**`），确认后"提升"到 `www/` 并搬运。**这是"模板写错 → 全站坏页"的唯一有效防线**（v2 只靠"单文件原子写"，挡不住这种事）。
- **回滚**：把 `release/<batchId>/` 的文件覆盖回 `www/`、恢复该批次的 `manifest.json`，然后重新搬运。回滚是**文件级**的：数据库内容不回滚（内容已经改了，把静态页退回旧版本只是止血手段，随后要用新内容重发）。
- **回滚 = 覆盖 + 删除**（**v2.2 定死**）：除覆盖之外，还要删除"出现在**更晚**批次 manifest 里、且**不在**本批次 manifest 里"的文件。否则回滚后新旧页面混杂，而这些文件在恢复后的旧清单里既 ∉`plan` 又 ∉`known`，按 §8.6 第三行会被**永久保留**。
- **发布记录**页（后台）：批次列表 + 每个批次的"涉及内容"、"删除的产物"、"警告"，可一键回滚、可下载 `report.json`。

### 8.8 定时与触发（**静态站的"活"靠这四条**）

| 任务 | 默认频率 | 做什么 | 为什么必须 |
|---|---|---|---|
| 定时发布 | 每分钟 | 扫 `status='PUBLISHED' AND publish_time <= now() AND publish_time > now() - interval '24 hours'`，命中的内容触发增量 | 查询侧的时间窗保证"不会提前发布"（§6.3），但"到点要发布"必须有人触发。**不用新增"已落地"标记列**：用 24 小时回看窗口兼作**停机补偿**（应用停 3 小时后重启，3 小时内到点的内容仍会被发出来） |
| 榜单快照重建 | 每小时（`publish.rankCron`） | `view_count` / `view_count_day` / `view_count_week` 有变化时，重渲染 `orderbyFields` 命中它们的页面（榜单页、侧栏热度） | 静态榜单是 **T 时刻快照**（§0.2 第 9 条）：数字与排序都会滞后，靠这里刷新。日榜 / 周榜靠 §9.4 的滚动窗口列（**v2.2 新增**） |
| 到期下线 | 每分钟（与定时发布同一趟） | 扫 `expire_time IS NOT NULL AND expire_time <= now() AND status='PUBLISHED'`，命中的内容触发增量；其产物从计划里消失 → §8.6 的 GC 删除。`publish.expireRedirect=1` 时同时写一条 301 | 活动页、广告位排期、限时专题都要它（**v2.2 新增**：v2.1 全篇没有"到期"这件事，而 §14.7 的活动页行声称"到期后写 `cms_redirect`"——那个机制当时并不存在） |
| 全量重建 | 每天 03:00（`publish.cron`） | 全量计划 + 差异渲染；`clockBucket` 跨日 → 全站重渲染 | **正确性兜底**：任何增量漏报在 24 小时内自愈（`clockBucket` 保证"每天至少全站刷一次"，见 §8.4） |
| 清理 | 每周 | 裁剪 `release/`、清理无引用的媒体派生文件 | 磁盘 |

- **内容变更触发**：内容保存后进"待发布队列"，**默认 5 秒合并窗口**（`publish.debounce`）——编辑连续保存 5 次只触发 1 个批次。
- **手动触发**：后台"立即发布全站" / "只发这一篇"（内容行内按钮，走 `SINGLE` 模式的窄批次：只渲染该内容相关页面） / "只重建聚合产物"（sitemap、feed、索引，不渲染页面）。
- **多实例**：定时任务用数据库锁（`select ... for update skip locked` 或一张 `cms_publish_lock`）保证同一站点只有一个实例在执行。

### 8.9 主题包：模板的版本化与部署（**回答"新环境模板从哪来"**）

- **主题包 = 部署单元**：目录或 zip，结构就是 §7.3 的树，根上有 `theme.json`：

```json
{ "name": "corp-blue", "version": "1.2.0", "engine": ">=2.1",
  "sites": ["corp"], "requires": { "page.facet": true }, "author": "…" }
```

- **导入**：后台上传 zip（或从 Git 仓库拉，可选）→ 校验（路径边界、体积上限、`theme.json` 必填字段、zip 内不含绝对路径/符号链接）→ 解压到 `template/<theme>/` → 写 `cms_theme` 表（`name` / `version` / `hash` / `import_time` / `import_by` / `source`）→ 触发该站点全量计划与差异（模板变了 → `ast` 变，§8.4）。
- **导出**：把当前主题打包下载，用于进 Git、code review、新环境重建。
- **`engine` 版本**：不满足时导入给警告不阻断（旧引擎遇到新语法会在编译期报 E1001，报错比拒绝导入更好定位）。
- **新环境部署的两条路**：① 主题包随部署流水线放进 `sites/<site>/template/`（**推荐**，模板是代码，走与后端同一条发布通道）；② 后台导入主题包。二者都成立，但**必须选一条**——因为 `sites/` 被 `.gitignore` 忽略（§11.6），"模板在服务器上手工改"是唯一不可接受的方式。
- **一个站点一个主题**：换主题 = 改 `site.theme` + 全量重建；主题可被多个站点共用（`sites` 字段只是声明，不做强制）。

### 8.10 产物出口与部署切换（**与既有工程现实的对接点**）

- 三个可选出口（现状与推荐见 §11.3）：**A** nginx `alias` 指向 `sites/<site>/www`；**B** 引擎同步到 `/www/wwwroot/<site>`（**推荐**，复用现有 rsync 通道与 nginx `root`）；**C** Spring 只读资源映射（**仅预览**，见 §11.2 的路径冲突）。
- **搬运是发布的显式阶段**（`SiteSyncService`，第 ⑨ 步）：`publish.syncTarget` 为空时跳过（本地开发与预发布就是这种状态）。
- 搬运策略：先同步到目标下的 `_new/`，再两次 `rename` 原子换名（同一文件系统内）；跨设备无法 rename 时退化为"逐文件同步 + **最后写 `cms-site.json`**"（它是"本站产物已完整"的标记，§8.5）。
- **同步失败不影响本地产物**：批次状态记 `PARTIAL` 并保留 `syncTarget` 待重试，绝不因为网络抖动丢掉一次渲染结果。

---

## 9. 动态层契约（**H 类的全部定义**）

> §1.2 把 H 类（静态壳 + API）算进"100% 覆盖"。本节就是那半张契约：**模板侧有什么挂载点、API 长什么样、没有 JS 时怎样**。
> 原则一条：**H 类只做"静态做不到的事"**——凡是能在发布时算出来的，都不许放到运行时（§5.5 的同一条准则）。

### 9.1 总则与边界

| 项 | 规定 |
|---|---|
| 前缀 | 全部公开接口在 `/api/public/**`，`permitAll`（延续现有 `PublicController` 的约定） |
| 站点识别 | **唯一权威清单见 §11.1**（五级：`siteId` 参数 > 产物内置 id > `X-Site-Id` 头 > Host 域名 > 默认站点）。静态页里由 `cms.js` 自动带上产物里的 id，**不依赖 Host** |
| 响应格式 | 沿用现有 `Result<T>`（`code` / `msg` / `data`），分页沿用 `PageResult`。H 类接口**不返回 HTML**（无 JS 的降级走"原生表单 POST + 302"，见 §9.5） |
| 错误提示 | 面向访客的文案走 `msg`（中文、可直接展示）；面向排障的细节进 `data.traceId` 与服务端日志，不外泄 SQL/堆栈 |
| 幂等 | 写接口一律支持幂等键（表单带 `_token`，前端生成 UUID；服务端 5 分钟内同键同参视为重复提交，直接返回上次结果） |
| 缓存 | 读接口 `Cache-Control: public, max-age=60`；写接口 `no-store` |
| CSRF | 公开写接口不用 Cookie 会话，因此不做 CSRF token；改用**来源校验（`Origin`/`Referer` 同站）+ 蜜罐 + 限流**（§9.9） |
| 隐私 | IP 只存**脱敏值**（`192.168.1.*`）用于限流与反垃圾；不存 UA 全文，只存设备类别 |
| 带宽 | 列表类接口一律分页，默认 `size=10`、上限 `size=50`（防止被人当爬虫入口） |

### 9.2 接口总表

| 接口 | 方法 | 用途 | 用到的页面 | 详见 |
|---|---|---|---|---|
| `/api/public/search` | GET | 站内搜索（`q` / `type` / `category` / `tag` / `page`） | 搜索页 | §9.8 |
| `/api/public/views/{typeCode}/{id}` | POST | 浏览量上报 | 内容详情 | §9.4 |
| `/api/public/ratings/{typeCode}/{id}` | GET / POST | 读 / 提交评分 | 内容详情 | §9.6 |
| `/api/public/comments` | GET | 读评论（分页，只返回已通过） | 内容详情 | §9.6 |
| `/api/public/comments` | POST | 提交评论 | 内容详情 | §9.6 |
| `/api/public/forms/{code}` | POST | 表单提交（含附件） | 表单页 / 详情页 | §9.5 |
| `/api/public/forms/{code}/upload` | POST | 表单附件分片上传（大文件） | 简历投递 | §9.5 |
| `/api/public/shelf` | GET / POST / DELETE | 书架 / 阅读进度 / 收藏（匿名用本地，登录后同步） | 小说站 | §9.7 |
| `/api/public/subscribe` | POST | 邮件订阅（feed 的补充） | 博客 / 资讯 | §9.9 |
| `/api/public/site` | GET | 站点公开信息（名称、`siteId`、语言、备案、联系方式） | 任意（兜底） | — |
| `/api/public/captcha` | GET | 取验证码挑战（`captcha=1` 的表单由 `cms.js` 在提交前拉取；静态页里**不渲染**验证码） | 表单页 | §9.3（3） |

- **响应体**（**v2.2 补**，都是 `Result<T>` 的 `data`）：`search` → `{list:[{id,typeCode,title,url,summary,publishTime}],total,page}`；`views` → `{viewCount}`；`ratings`(GET) → `{avg,count,mine}`；`comments`(GET) → `{list:[{id,parentId,nickname,content,createTime}],total,page}`，`comments`(POST) → `{status:'PENDING'|'APPROVED'}`；`forms`(POST) → `{thanksUrl}`；`captcha` → `{challengeId,question}`；`subscribe`(POST) → `{ok:true}`；`site`(GET) → `{siteId,name,lang,domain,icp,contactPhone,contactEmail}`；`shelf`(GET) → `{list:[{typeCode,id,chapterId,percent,updateTime}]}`。**没列进这一行的字段不属于契约**，前端不许依赖。
- **不在表里的东西**：登录、支付、购物车、个人中心（§1.2 的 D 类，不在本文件范围）。
- 现有 `/api/public/articles`、`/articles/{slug}`、`/categories` **保留**（它们服务的是上一代表现层与第三方对接），但在静态站体系里**模板不再需要它们**——所有内容都在发布时静态化了。

### 9.3 挂载点、渐进增强与无 JS 降级

**（1）模板侧只认这几个挂载点**（`cms.js` 的契约）：

| 挂载点 | 作用 | 用它的模板 |
|---|---|---|
| `data-cms-form="{code}"` | 表单：拦截为 fetch 提交 | `{cms:form}` 自动输出 |
| `data-cms-search` / `data-cms-search-result` | 搜索：输入框与结果容器 | 搜索页模板 |
| `data-cms-views="{typeCode}:{id}"` | 浏览量：进入视口后上报，并把返回的数字写进元素 | 详情页模板 |
| `data-cms-comments="{typeCode}:{id}"` | 评论：拉取并渲染列表与表单 | 详情页模板 |
| `data-cms-rating="{typeCode}:{id}"` | 评分：读平均分、提交打分 | 详情页模板 |
| `data-cms-shelf="{typeCode}:{id}"` | 书架 / 收藏按钮 | 小说站模板 |
| `data-cms-share` | 分享（优先 `navigator.share`） | 任意 |
| `window.__CMS_SITE__` / `window.__CMS_API__` | 站点 id 与 API 基址（产物烤进去，§11.1） | 由 `cms.js` 读取，模板不写 |

- `cms.js` 由主题带（`assets/js/cms.js`），或引用引擎自带的公共版；**它必须能在没有挂载点的页面上空跑**（首页、404 也要能安全加载）。
- 产物里 `window.__CMS_SITE__` 由引擎在每页 `<head>` 位置注入（模板写 `{cms:include file='_partials/cms-head.html'/}` 或由引擎自动插入 `<script>` 之前的那一行，二选一由主题决定；**推荐显式 include**，因为隐式注入会让"模板里到底有什么"变得不可见）。

**（2）无 JS 时的行为**（**逐项写死**，这是"降级"这个词唯一有意义的定义）：

| 功能 | 无 JS 时 |
|---|---|
| 浏览内容、列表、分页、面包屑、上下篇 | **完全可用**（S 类全是静态 HTML） |
| 表单提交 | **原生 POST** → 成功 302 到 `success_url`（默认 `/thanks/`），失败 302 回原页并带 `?form_error=<码>`；模板用 `{cms:if field='param.form_error'}` 显示提示（`form_error` 是 §7.2.3 的四个 URL 注入 key 之一） |
| 搜索 | **不可用**（索引是 JSON，需要 JS）。降级为：搜索页上给出"站点地图 / 栏目导航 / 归档"链接，并提示"请开启 JavaScript 以使用站内搜索" |
| 评论 | 显示**已通过评论的静态快照**（§9.6），提交按钮给一句"请开启 JavaScript 后发表评论" |
| 评分 | 显示静态快照的平均分与人数，不可提交 |
| 浏览量 | 不增长（本来就允许滞后，§9.4） |
| 书架 / 阅读进度 | 不可用（提示开启 JS）；不登录也能用的前提本来就是本地存储 |
| 分享 | 显示为普通链接（`?` 到微博/微信的转发 URL），或直接隐藏 |

**（3）验证码按需加载**：静态页里**不渲染验证码**（它必须是动态的）。表单定义里勾了 `captcha=1` 时，`cms.js` 在提交前拉 `/api/public/captcha` 并渲染；无 JS 时该表单**不接受提交**（服务端直接返回"请开启 JavaScript"），因为静态页无法承载验证码的挑战值。这条要写进表单定义的帮助文字里，避免"为什么我的留言提交不了"。

**（4）渐进增强的边界**：JS 只做"补"，不做"改"。任何页面在 JS 加载失败时都必须可读、可导航、可提交（表单）。**不许**出现"首屏内容由 JS 渲染"——那等于放弃 SEO，与 §1.2 的 S 级定义矛盾。

### 9.4 浏览量、点赞与统计

- **上报**：`POST /api/public/views/{typeCode}/{id}`，body 空，返回 `{ "viewCount": 1234 }`（累加后的值）。
- **去重**：同一 `(siteId, contentId, 访客指纹)` 24 小时内只计 1 次；指纹 = 匿名 Cookie（`cms_vid`，一年期，`HttpOnly=false`，不含任何个人信息）+ IP 脱敏值的哈希。清 Cookie 即重计——**接受这个误差**，浏览量不是财务数据。
- **写入路径**：接口只写 Redis / 内存计数 + 每 30 秒或每 100 次**批量累加**到 `cms_content.view_count`（避免每次浏览一行 update）。没有 Redis 时退化为直接 update（并在日志里说明）。
- **三个计数一起维护**（**v2.2 补**）：同一次累加同时更新 `view_count`、`view_count_day`（滚动 24 小时）、`view_count_week`（滚动 7 天）。滚动窗口靠**小时桶**实现：内存里按小时保存增量，累加时把滑出窗口的小时桶减掉——不引入额外定时任务，也不需要存浏览明细行。日榜 / 周榜的排序字段就是这两列（§2.2 白名单）。
- **静态页上的数字**：HTML 里输出的是**发布时刻的快照**（`[field:viewCount/]`），`cms.js` 拿到接口返回值后**原地替换**。因此：**无 JS 的访客看到的是旧数字**（v2 §0 第 12 条如实承认过这一点，这里给出实现口径）。
- **排序滞后**：榜单页、侧栏热度按 `viewCount` 排序 → **T 时刻快照**，靠 §8.8 的每小时榜单重建刷新。这一点在 v2 里没有被承认（只承认了"显示滞后"），现明确记录。
- **点赞 / 收藏**：与浏览量同构（去重 + 计数），但**不进静态页**（它们是纯交互，且容易刷）；收藏走 §9.7 的书架接口。
- **统计口径写死**：`viewCount` = 内容详情页的**有效浏览数**（去重后）；列表页曝光、搜索点击不计入。要更细的分析走第三方统计（`site.statisticsCode`，§2.7）。

### 9.5 表单：定义、渲染与提交

**（1）表结构**

表 `cms_form`：`id` / `site_id` / `code`（站点内唯一）/ `name` / `success_url`（默认 `/thanks/`）/ `fail_url`（默认回原页）/ `captcha`(0/1) / `notify_emails` / `notify_webhook` / `store`(0/1，是否入库)/ `status` / `deleted`。

表 `cms_form_field`：`form_id` / `code` / `label` / `field_type` / `required` / `placeholder` / `help` / `options`（ENUM 选项）/ `maxlen` / `min` / `max` / `pattern`（正则）/ `file_types` / `file_maxsize` / `sort`。

**（2）字段类型 → 控件**（渲染由 `{cms:form}` 完成，§6.5）

| `field_type` | 控件 |
|---|---|
| `TEXT` / `EMAIL` / `TEL` / `URL` / `NUMBER` / `DATE` | `<input type="…">`（`EMAIL`/`TEL`/`URL` 带对应 `type`，前端校验 + 服务端再校验） |
| `TEXTAREA` | `<textarea rows="5">` |
| `SELECT` | `<select>` + `options` |
| `RADIO` / `CHECKBOX` | 一组 `<input type="radio\|checkbox">`（`CHECKBOX` 多选，服务端存 JSON 数组） |
| `FILE` | `<input type="file">`（支持的扩展名与大小来自字段定义，**默认 `pdf,doc,docx,jpg,png`，单文件 ≤ 10MB**——与 `application.yml` 的 `multipart` 上限一致；更大走分片上传接口） |
| `HIDDEN` | `<input type="hidden">`（值来自定义或 §6.5 的 `self` 关键字） |
| `CONSENT` | 单个勾选框（必须勾选才可提交，用于隐私政策同意） |

**（3）提交契约**

- `POST /api/public/forms/{code}`，`Content-Type: multipart/form-data`（有附件）或 `application/x-www-form-urlencoded`。
- 服务端流程：**来源校验 → 蜜罐检查 → 限流（§9.9）→ 字段校验（必填/长度/正则/选项白名单）→ 落库 `cms_form_entry`（`store=1` 时）→ 通知（邮件/webhook，异步）→ 返回**。
- **成功返回**：有 JS → `Result.ok({thanksUrl})`，前端就地显示成功文案（可在表单定义里配文案）；无 JS → `302` 到 `success_url`。
- **失败返回**：有 JS → `Result.fail(code, msg)`，`msg` 直接可展示；无 JS → `302` 回 `fail_url?form_error=<码>`。
- 错误码：`E7001` 必填缺失 / `E7002` 格式错误 / `E7003` 选项非法 / `E7004` 附件类型或大小非法 / `E7005` 触发限流 / `E7006` 蜜罐命中（**对外统一显示"提交失败，请稍后再试"**，不给机器人反馈）/ `E7007` 验证码错误。
- **`self` 关键字的落地**：`contentId:self` / `contentType:self` / `contentUrl:self` / `contentTitle:self` / `pageUrl:self` 渲染成隐藏域（§6.5），服务端把它们原样存进 `cms_form_entry.context`（jsonb）。**服务端不信任这些值**（可被伪造），因此后台展示时标注"来自前台页面"，不做业务判断依据。
- 表 `cms_form_entry`：`id` / `site_id` / `form_id` / `data`(jsonb) / `context`(jsonb) / `files`(jsonb) / `status`（`NEW`/`READ`/`HANDLED`/`SPAM`）/ `ip_masked` / `ua_class` / `create_time` / `handle_by` / `handle_note`。

**（4）附件上传**：小文件（≤ 10MB，与 `application.yml` 的 multipart 上限一致）随表单一起 POST；大文件走 `/api/public/forms/{code}/upload`。**分片协议写死**（**v2.2 补**：v2.1 只给了端点名就说"分片"，分片参数与合并规则全无）：`POST` 带 `uploadId`（首次为空，服务端生成并返回）、`chunkIndex`（0 起）、`chunkTotal`、`chunk`（文件片段，每片 ≤ 5MB）；服务端落 `uploads/tmp/<uploadId>/`；`chunkIndex = chunkTotal - 1` 到达时按序合并、校验总大小与 `file_types`，返回 `{fileId, url, name, size}`；**提交表单时带 `fileId`**。超时未合并的 `uploadId` 由 §8.8 的每周清理任务删除。附件存 `uploads/forms/<siteId>/<yyyyMM>/`，**不进媒体库**（表单附件与内容媒体是两种东西：前者是访客提交，后者是编辑素材），但有同样的路径边界校验与体积限制，且后台可下载、可按策略自动清理（默认保留 180 天）。

**（5）后台**：表单定义管理（字段拖排）、提交记录列表（筛选/标记 SPAM/标记已处理/导出 CSV）、通知配置、限流参数。

### 9.6 评论、评分与快照静态化

**（1）评论**

表 `cms_comment`：`id` / `site_id` / `type_code` / `content_id` / `parent_id` / `nickname` / `email_masked` / `website` / `content` / `status`（`PENDING`/`APPROVED`/`REJECTED`/`SPAM`）/ `ip_masked` / `ua_class` / `create_time` / `audit_by` / `audit_time` / `audit_note`。

- **审核**：默认**先审后发**（`comment.moderate=1`）；站点可开"先发后审"（`moderate=0`，只对疑似垃圾进审核）。
- **`comment_count` 与审核动作同事务维护**（**v2.2 补**）：通过 +1、从通过态改成其他状态 -1。这样列表页的 `[field:commentCount/]`（"3 条评论"）与"按热度排序"（`orderby='commentCount desc'`）才有真实来源。
- **反垃圾**：蜜罐 + 频率限制（同 IP 5 分钟 1 条）+ 链接数阈值（>2 条链接进审核）+ 关键词黑名单 + 昵称与内容不得含 HTML（一律转义存储）。不引入第三方反垃圾服务（可加，但不是契约的一部分）。
- **读取**：`GET /api/public/comments?type=&id=&page=`，只返回 `APPROVED`，`size` 上限 50，支持 `parentId` 取楼中楼（**只做一级回复**，不做无限嵌套——界面与审核成本都会爆炸）。
- **提交**：`POST /api/public/comments`，成功后返回"已提交，待审核"或"已发布"（依 `moderate`）。
- **无 JS**：显示静态快照 + 提示（§9.3）。

**（2）评论快照静态化（SEO 的关键一步）**

- 发布时，把每条内容下**已通过（`APPROVED`）**的评论取前 `comment.snapshotSize`（默认 20）条，渲染进静态页的评论容器里（`data-cms-comments` 的空容器内先放这些 HTML）。
- **快照的语义写死**：它是"上次发布时刻的评论"，**可能过期**；`cms.js` 加载后**整体替换**为实时列表。因此：**无 JS 访客看到的是快照，有 JS 访客看到的是实时**——两者都可接受，且搜索引擎能读到评论内容。
- **快照依赖进清单**：评论变更 → 该内容页失效。**具体写法写死**（**v2.2 补**：v2.1 说"加一个 `comments` 字段"，而 §8.4 的 deps 示例与失效表里都没有它）：deps 里记 **`commentsHash`**（已通过评论的规范化摘要），§8.4 的失效表里加一行"评论审核通过 / 删除 → `commentsHash` 变化的页面"；审核通过后触发该页的**窄批次**增量发布。**这是评论功能里唯一必须接进发布链路的地方**，不接则"评论快照"永远是空的。
- 站点选项 `comment.snapshot=0` 可关闭快照（纯动态评论，SEO 收益放弃）。

**（3）评分**

表 `cms_rating`：`(site_id, type_code, content_id, visitor_hash)` 唯一，`score`（1–5）；聚合值**写进 `cms_content.rating_avg` / `rating_count` 两列**（**v2.2 明确**：不再用 `data.__rating` 或索引表——`data` 是自定义字段区且 §2.3 规定"查询不依赖 jsonb"，而模板要用的 `[field:ratingAvg/]` 是派生字段，必须有真实列）。
- 读：`GET /api/public/ratings/{type}/{id}` → `{avg, count, mine}`；提交：`POST` 同路径。
- 静态页输出快照（`[field:ratingAvg/]` / `[field:ratingCount/]` 为**派生字段**，来自上次发布），JS 取代之。
- 评分不进 JSON-LD 的 `aggregateRating`——**避免把可能过期的快照当成结构化数据提交给搜索引擎**（那属于"垃圾结构化数据"）。要提交就必须实时，而实时不是静态站的强项。

### 9.7 书架、阅读进度与收藏（小说站 / 长内容站）

- **匿名优先**：未登录时全部存 `localStorage`（`cms:shelf` / `cms:progress`），功能完整；登录后（未来的 D 类模块提供登录）可同步到服务端。
- 接口（`/api/public/shelf`）：`GET` 拉取服务端书架、`POST` 添加/更新进度（`{typeCode,id,chapterId,percent}`）、`DELETE` 移除。**匿名请求带 `X-Shelf-Token`**（本地生成的 UUID，作为服务端的匿名身份），这样"不登录也能跨设备"不可实现但"不登录也能有服务端记录"可以实现（换设备就丢，可接受）。
- 进度写回是**低频**的（每章一次或每 30 秒一次），不做实时同步。
- **不生成静态的"我的书架"页**——它是 D 类，纯前端渲染即可（在 S 类页面上挂一个空容器）。

### 9.8 搜索接口（`search.mode='api'` 时使用）

- `GET /api/public/search?q=&type=&category=&tag=&page=&size=` → `PageResult<{id,typeCode,title,url,summary,cover,publishTime,categoryName}>`。
- 实现：**直接查数据库**（`cms_content` 公共列 + `searchable` 字段的 `like`），不读静态索引。理由：后端与库同进程，一条 SQL 的成本低于"维护第二套检索副本"；静态索引是给"没有后端预算"的纯静态托管场景准备的（`search.mode='static'`）。
- 排序：标题命中 > 摘要命中 > 正文命中，同级按 `publish_time desc`；**不排序相关度分数**（没有 BM25，不假装有）。
- 限制：`q` 长度 ≤ 50；空 `q` 返回空结果（不允许"列出全部"——那是站点地图的活）；`size` ≤ 50；**同 IP 每分钟 30 次**（§9.9）。
- 高亮：接口返回原文，前端做高亮（服务端不返回 HTML，避免 XSS 面）。

### 9.9 反滥用、限流、审核与审计

| 手段 | 规则 | 触发后的行为 |
|---|---|---|
| 限流（表单） | 同 IP 每小时 5 次、同 IP 每分钟 1 次 | `E7005`，对外文案"提交过于频繁，请稍后再试" |
| 限流（评论） | 同 IP 5 分钟 1 条、每天 20 条 | 同上 |
| 限流（搜索 / 浏览上报） | 同 IP 每分钟 30 次 | 返回空结果 / 静默丢弃（不报错，避免给爬虫反馈） |
| 限流（下载大文件） | 同 IP 每分钟 10 次（`FILE` 字段的产物） | 429 |
| 限流实现 | 内存令牌桶 + 可选 Redis；**多实例必须用 Redis**（否则限流形同虚设） | — |
| 蜜罐 | 表单里一个视觉隐藏、`tabindex="-1"`、`autocomplete="off"` 的输入框，有值即判机器人 | `E7006`，对外统一"提交失败" |
| 来源校验 | 写接口校验 `Origin`/`Referer` 的 host 属于本站点的 `domain` 或 `i18n.alternates` | 403（不给具体原因） |
| 人工审核 | 评论（默认）、表单（可配 `store=1` 后进后台待处理） | 后台列表 + 批量操作 |
| 审计 | 审核、删除、导出等后台操作写 `sys_oper_log`（现有机制） | — |
| 定时清理 | 表单附件（180 天）、`SPAM` 评论（90 天）、限流计数（1 天） | 每周清理任务（§8.8） |
| 通知 | 表单提交 / 新评论：邮件（SMTP 配置）或 webhook（`notify_webhook`，POST JSON） | 异步，失败重试 3 次并记日志 |

- **不做**：图形验证码服务（第三方）、行为风控、AI 审核。要就自己接，不属于本契约。
- **反过来说一句**：以上手段都是"提高成本"，不是"阻断"。静态站的写入口本质上比动态站少（没有登录、没有购物车），因此攻击面也小——这是 S/H 分级的附带收益。

---

## 10. 错误文案与诊断规范

> §3.5 写"文案见 §10"，本节就是那份契约。**错误文案也是接口**：模板作者一天要看几十条，它决定这个引擎好不好用。

### 10.1 通用格式（**四条，缺一不可**）

```
[错误码] 出了什么事 · 在哪出的
  → 现状：引擎看到了什么（把实际值原样贴出来）
  → 建议：下一步怎么做（能给出可选项就给选项）
```

- **错误码**：`E` + 4 位数字，段位含义见 §10.2。代码里是常量，日志与后台都带它，便于检索与统计"哪种错最多"。
- **位置**：模板错误必须带 `模板相对路径:行号`，include 展开后**指向片段内的真实位置**（§5.3）并附**包含链**。
- **现状**：把实际值贴出来（`实际值 'prodcut'`、`第 12 行`、`共 2 个`）。**不许写"参数错误"这种话**。
- **建议**：能列清单就列清单（可用标签、可用参数、可用字段、可用类型、找过的模板路径）；能猜笔误就猜（编辑距离 ≤ 2 时给"你是不是想写 `product`？"）。

### 10.2 错误码表

**E1xxx — 模板语法与编译期**

| 码 | 触发 | 文案模板 |
|---|---|---|
| `E1001` | 标签名不存在 | `[E1001] 未知标签 {cms:arclist} · news/list.html:18` / `→ 现状：引擎不认识 arclist` / `→ 建议：可用标签见 §6.1；你是不是想写 {cms:list}？` |
| `E1002` | 参数未声明 / 类型不符 / 必填缺失 | `[E1002] 参数 channel 不存在 · news/list.html:18` / `→ 现状：{cms:list} 的参数有 type category includeChildren tag …（全部列出）` / `→ 建议：不限栏目请写 category='all'`（**v2 的 `channel='all'` 误记就是靠这条兜住的**，§0.2 第 6 条） |
| `E1003` | 块配对错误 / `{cms:else/}` 位置错 | `[E1003] {/cms:if} 与最近的 {cms:list} 不匹配 · (第 8 行 / 第 22 行)` |
| `E1004` | 字段名不在可用集合 | `[E1004] 字段 price 不存在 · product_detail.html:31` / `→ 现状：类型 product 的可用字段有 title cover price specs …（全部列出）` / `→ 建议：字段 code 区分大小写` |
| `E1005` | 字段路径中间段断掉 / `.count` 用错 | `[E1005] 路径 images.0.url 在第 2 段断掉` / `→ 现状：images 为空（0 个元素）` / `→ 建议：先 {cms:if field='images'}` |
| `E1006` | include 越界 / 循环 / 超深 | `[E1006] include 循环 · a.html → b.html → a.html` |
| `E1007` | 模板编码不是 UTF-8 / 有 BOM | `[E1007] 模板编码不是 UTF-8（检测到 GBK）· news/list.html` |

**E2xxx — 定义与查询参数（编译期 / 计划期）**

| 码 | 触发 | 文案模板 |
|---|---|---|
| `E2001` | 内容保存校验失败（slug 冲突、必填缺失、选项非法） | `[E2001] slug 已存在：news-2026` |
| `E2002` | 层级内容成环 | `[E2002] 父级不能是自己或自己的子孙（book → chapter → book）` |
| `E2003` | `SINGLE` 类型已有内容 | `[E2003] 单页类型 single 只能有一条内容` |
| `E2004` | 主分类缺失或超过一个 | `[E2004] 请指定一个主分类（当前 2 个）` |
| `E2005` | 类型 code / 字段 code 用了保留名 | `[E2005] item 是保留名，不能作为字段 code（保留名：site channel page param query item）` |
| `E2006` | `type` / `category` / `tag` / `author` / `code` 指向不存在的对象 | `[E2006] 内容类型 prodct 不存在 · product_list.html:1` / `→ 建议：本站类型有 article product case service team job faq（全部列出）` |
| `E2007` | `where` / `orderby` / `relate` 字段不可筛选 / 运算符非法 / 语法不可解析 | `[E2007] 字段 price 不能用于 where · product_list.html:9` / `→ 建议：到「内容类型 → 字段 → 可筛选」勾选后重试；内置字段的可筛选清单见 §2.2` |
| `E2008` | `{cms:form}` 的 `code` 不存在 | `[E2008] 表单 inquiry 不存在 · contact.html:12` / `→ 建议：本站表单有 inquiry、job-apply、subscribe`。**`hidden` 的 key 不受校验**（key 任意，只有**值**命中 5 个关键字才替换，§6.5）——v2.1 此处要求 key 落在清单内，与 §6.5 直接冲突 |
| `E2009` | `of='self'\|'parent'` 用在不成立的位置 | `[E2009] 首页没有当前条目，of='self' 无法解析 · index.html:14` |
| `E2010` | `name` 重复 | `[E2010] 查询名 latest 重复使用 · index.html:9 与 index.html:22` |
| `E2011` | 循环体内用了 `name` | `[E2011] 循环内的查询不能命名 · index.html:31`（§5.4 约束三） |

**E3xxx — 页面结构**

| 码 | 触发 | 文案模板 |
|---|---|---|
| `E3001` | 分页主体数量错（`{cms:list}` 超过 1 个 / `{cms:detail}` 超过 1 个 / 正文分页与列表分页同现 / 写在循环内） | `[E3001] 同一模板中出现了两个分页主体（第 8 行的 {cms:detail} 走正文分页，第 31 行还有 {cms:list}）· article_detail.html` / `→ 建议：正文分页与列表分页二选一；要同时保留，就把列表放到另一个模板（或去掉 paginate_body）` |
| `E3002` | 分页主体在 `{cms:if}` 体内 | `[E3002] 第 12 行的 {cms:list} 是分页主体，不能写在 {cms:if} 内（第 8 行的 {cms:if}）` |
| `E3003` | `{cms:pagelist}` 用在正文分页上（`page.paginationKind='content'`） | `[E3003] 正文分页不提供页号条 · article_detail.html:44` / `→ 建议：用 page.prevUrl / page.nextUrl` |
| `E3010` | `{cms:detail}` 的 `type` 与页面 kind 不相容 | `[E3010] single 页面上不能取 product（CONTENT）类型的内容` |
| `E3011` | **已废弃（v2.2）**：v2.1 用它表示"命名查询重复 / 循环内命名"，与 `E2010` / `E2011` 完全重复。**统一产出 `E2010` / `E2011`**；本码位保留但不再使用（同一件事两个码会让"本周报错 Top 10"统计口径分裂） | —（保留码位，不再产出文案；历史日志里见到它就按 `E2010` / `E2011` 处理） |
| `E3012` | **v2.2 新增**：标签用在该页面类型上不允许的位置（如 `{cms:prenext}` 写在列表页、`{cms:channel}` 写在 404） | `[E3012] {cms:prenext} 只能用在详情页 · list.html:22` / `→ 现状：当前页面类型是 LIST` / `→ 建议：标签与页面类型的矩阵见 §6.7` |

**E4xxx — URL 与页面计划**

| 码 | 触发 | 文案模板 |
|---|---|---|
| `E4001` | 占位符不在白名单 / 必需占位符缺失 / `{n}` 用在不分页的页面 | `[E4001] 占位符 {parentSlug} 只能用在层级类型上 · 类型 product 的 detail_url_pattern` / `→ 建议：详见 §7.1.1 的白名单` |
| `E4002` | 模板查找失败 | `[E4002] 页面类型 LIST（分类 news）找不到模板` / `→ 现状：依次找过 news_list.html、list.html` / `→ 建议：至少提供 list.html` |
| `E4003` | 会分页但没有 `{n}` | `[E4003] 分类 news 共 3 页，但 list_url_pattern 没有 {n} · 本页未发布` / `→ 建议：改成 /news/page-{n}/ 或把 per_page 调到 ≥ 45` |
| `E4004` | URL 冲突 | `[E4004] 两个页面映射到同一路径 /about/index.html：单页 single(about) 与 分类 slug=about` |
| `E4005` | 筛选页数量超限 | `[E4005] 筛选页 812 个，超过上限 500（facets.maxPages）` / `→ 建议：减少 facets 字段、提高 facets.cardinality 门槛，或显式提高上限` |
| `E4006` | 产物路径过长（> 200 字符） | `[E4006] 产物路径 214 字符，超过 200 上限：/news/2026/03/12/very-long-ascii-slug-here.html`（**必须贴出完整路径**，不许用 `…` 省略，否则违反 §10.3 第 1 条） |

**W5xxx — 警告（不阻断发布，进批次报告）**

| 码 | 触发 |
|---|---|
| `W5001` | 存在某页面类型的模板，但站点发布选项把它关掉了（主题包被多站点共用时常见） |
| `W5002` | 正文分页符落在块级元素内部，该处未切分 |
| `W5003` | 引用的媒体派生文件不存在，已回退原图（§7.7） |
| `W5004` | `www/` 里的产物被人工改过（mtime 与清单不符），本次仍按计划覆盖/删除（§8.6） |
| `W5005` | 站点选项 `publish.keepReleases=0`（放弃回滚能力，需显式确认） |

**E6xxx — 文件与路径**

| 码 | 触发 | 文案模板 |
|---|---|---|
| `E6001` | 试图通过后台站点文件功能写 `www/` | `[E6001] www/ 是引擎生成目录，不能手工编辑；要改请改主题模板` |
| `E6002` | 路径越界（绝对路径、`..`、符号链接） | `[E6002] 路径越界：../../etc/passwd` |
| `E6003` | 站点目录不存在 / 不可写 | `[E6003] 站点目录不可写：/www/wwwroot/corp（检查属主与权限）` |

**E7xxx — H 类接口（运行时，面向访客；对外文案必须可直接展示）**

| 码 | 触发 | 对外文案 |
|---|---|---|
| `E7001` | 必填缺失 | `请填写「您的称呼」` |
| `E7002` | 格式错误 | `邮箱格式不正确` |
| `E7003` | 选项非法 | `请选择有效的选项` |
| `E7004` | 附件类型 / 大小非法 | `附件只支持 PDF、Word、图片，且不超过 10MB` |
| `E7005` | 触发限流 | `提交过于频繁，请稍后再试` |
| `E7006` | 蜜罐命中 | `提交失败，请稍后再试`（**与限流同一句，不给机器人反馈**） |
| `E7007` | 验证码错误 | `验证码不正确` |

### 10.3 报错即诊断（**三条硬要求**）

1. **能列清单就列清单**：可用标签、可用参数、可用字段、可用类型、找过的模板路径、白名单占位符——**报错信息里必须带可选项**，这是"自研引擎"相对第三方模板引擎的最大优势（模板作者不用去翻文档）。
2. **一次报多个错**：编译期把同一模板的**全部**错误一次返回（上限 20 条，超出提示"还有 N 条"）。逐条报错会让"改一处、跑一次"变成几十轮。
3. **能猜就猜**：标签名、参数名、字段名、类型 code 与已知名字的**编辑距离 ≤ 2** 时给"你是不是想写 X？"。

### 10.4 后台呈现与定位

- 模板预览：报错直接标在**代码行上**（行号 → 编辑器 gutter），点错误跳到模板文件的该行（站点文件编辑器已支持在线打开文本，§1.4）。
- 发布任务：批次报告页按**错误码分组**（"E1004 × 12 处，涉及 3 个模板"），一键展开详情与"打开模板"。
- 日志：错误码 + `batchId` + 模板路径 + 行号可检索；H 类接口的错误带 `traceId` 返回给前端，客服能凭 `traceId` 定位到那一次请求。
- **统计**：后台首页给"本周报错 Top 10 错误码"，用来发现"文档没说清"的地方——报错分布就是文档的体检报告。

---

## 11. 与既有工程的对接（**五处断层，逐条给方案与代价**）

> 前六节+§7–§9 是"设计内部自洽"，本节是"设计落到这台机器上"。这里每一条都是**已经核对过代码**的现状（§1.4），不是推测。

### 11.1 站点识别：加 Host 解析 + 产物烤 id（**两条都做**）

**现状**：`SiteInterceptor` 的优先级是 **`X-Site-Id` 头 > `siteId` 参数 > 系统默认站点**，**没有基于域名的解析**，尽管 `cms_site.domain` 字段早就有（`V20261001000500`）。

**为什么静态站必须解决它**：静态页里的 JS 调 `/api/public/forms/xxx` 时，nginx 只把 `/api` 前缀反代到后端，**Host 对站点解析无意义** → 所有 H 类请求都落在**默认站点**。多站点下 H 类全错，而且是**静默错**（返回了数据，只是错的站点）。

**方案（两条互补，都做）**：

| # | 做法 | 收益 | 代价 |
|---|---|---|---|
| 1 | **加 Host 解析**：`cms_site.domain` 匹配 `Host` 头（忽略 `www.` 前缀与端口）。优先级插在"默认站点"之前 | 一次改动，所有站点受益；对第三方对接、直接访问 API 的场景最自然 | 需要 `domain` 唯一索引（同一域名不能属于两个站点）；`domain` 为空的多站点仍要落到默认站点 |
| 2 | **产物烤 siteId**：引擎在每页输出 `cms-site.json` 与 `window.__CMS_SITE__ = {id, code, lang, api}`；`cms.js` 自动带上 `siteId` 参数与 `X-Site-Id` 头 | **零后端改动**，且在 Host 不可靠（CDN、内网预览、临时域名）时兜底；预览环境也能正确取数 | 站点换 id（重建库、导入导出）时产物要重发；产物里带站点 id 属于"把环境信息写进产物"，导出产物到另一个环境会指向旧 id |

**最终优先级（写死，本节是唯一权威，§9.1 只保留引用）**：`siteId` 参数（显式指定）> **产物内置的站点 id**（`window.__CMS_SITE__.id` / `data-cms-site`，由 `cms.js` 自动带上）> `X-Site-Id` 头（后台）> **Host 域名**（真实访客）> 系统默认站点。

> **v2.2 修改**：v2.1 在 §9.1 写了五级（含产物内置 id）、在本节写了四级（不含），同一个解析器两处口径——`cms.js` 带上的 id 到底算不算数，两节给出不同答案。现统一以本节为准。

- **不做**：子路径区分站点（`example.com/en/`）——它要求 `UrlResolver` 支持 URL 前缀、Host 解析改成"Host + 路径"，引入一整套前缀转义。多语言用**独立域名或子域名**（§11.5），这条限制写进 §13.4。

### 11.2 静态资源出口：媒体 `/uploads` 与预览 `/preview`

**现状（两处都是"上线即炸"）**：

1. 后端把 `/uploads/**` 映射到 `cms.upload.dir`（`WebConfig`），**后端自己能提供媒体**；但生产 nginx 只反代 `/api`/`/v1`，**没有 `/uploads` 的 location** → 静态页里的 `/uploads/x.jpg` 走 `try_files` 落到 404。而图片是本设计里最核心的内容形态（`IMAGE` / `IMAGES` / 派生尺寸，§7.7）。
2. `/sites` 与 `/sites/**` 已被 `SpaForwardController` 转发到 `index.html`（后台 SPA 路由）→ **产物只读映射绝不能占用这个前缀**。

**方案**：

| 出口 | 做法 | 备注 |
|---|---|---|
| 媒体（生产） | nginx 加 `location /uploads/ { alias <backend>/uploads/; expires 30d; access_log off; }` | 一次配置，所有站点共用一棵上传树（`cms_media.site_id` 已在库上区分归属）。派生文件以内容哈希命名（§7.7）→ 可长缓存 |
| 媒体（备选） | 改走 `/api/uploads/**` 复用现有反代 | **不推荐**：静态页的每张图都要过一次后端，且与 `Result<T>` 的错误语义混在一起。记录在此仅作为"nginx 改不了"时的退路 |
| 预览（后台看网站） | **走 `/api/preview/{siteCode}/**`**（**v2.2 改为这个方案**）：一个只读控制器按 `SitePathBoundary` 读 `sites/<site>/preview/www/` 下的文件并回吐，复用既有 `/api/**` 的鉴权链路与拦截器 | **不要新开 `/preview/**` 前缀**——按现状四处都不成立：`SecurityConfig` 里没有它的规则（落到兜底 → 直接 401 JSON）、`JwtAuthenticationFilter` 只认 `Authorization` 头（没有 Cookie 会话）、`WebConfig` 的拦截器只挂在 `/api/**`、`SiteContext` 在非 `/api` 路径上取不到站点。控制器必须**自己解析站点**（从路径里的 `siteCode`），不能依赖 `SiteContext`。另：**必须避开 `/sites/**`**（已被 SPA 回退占用） |
| 媒体（本地开发） | 后端直连 `http://localhost:8081/uploads/...` 即可（**端口以 `application.yml` 的 `server.port` 为准，当前是 8081**；v2.1 写的 8080 是笔误，`admin-ui/vite.config.ts` 的代理同样指向 8081） | 无需 nginx |

- **搬运时不要搬媒体**：§8.10 只搬 `www/`；媒体留在 `uploads/` 由 nginx 直接服务。若站点目录与后端不在同一台机器，则媒体必须先上 CDN 或对象存储——**这是本设计里唯一"必须选一条"的基础设施决定**，写进 §12.4 的上线清单。
- 站点选项 `site.mediaHost`（可选）：把 `[field:cover/]` 输出的 URL 前缀换成 CDN 域名（默认空 = 站内 `/uploads`）。这是"改一行配置就能上图床"的开关，模板不用动。

### 11.3 产物出口与部署通道

**现状**：生产由 nginx `root /www/wwwroot/lingjiuw;` + `try_files $uri $uri/ =404` 提供；该目录由 Jenkins 从 `frontend` 仓库 rsync 上去；`location ~ ^/(api|v1)/` 反代到后端。**后端没有任何东西服务 `sites/<site>/www/`**——设计与现实之间**没有桥**。

> **v2.2 核对标注**：本节的 nginx / Jenkins / `/www/wwwroot` 事实**依据仓库外的服务器配置**——仓库内没有可核对文件（早前评审引用的 `.tmp/lingjiuw.conf.new` 已不在仓库，git 历史里也没有）。**上线前必须由运维逐条确认**：`/uploads` 的 location、`root` 与 `try_files`、`error_page 404 /404.html`、`/api` 反代、后端端口（8081）、每站一个 server 块、以及 `include .../redirects.conf`。
> 另一条硬要求：仓库内**没有任何 CORS 配置**，而 `cms.js` 要带 `siteId` / `X-Site-Id`——因此**每个站点的 server 块必须包含 `/api` 反代（同源）**，否则 H 类接口全部跨域失败（写进 §12.4 的上线清单）。

| 出口 | 做法 | 优点 | 代价 |
|---|---|---|---|
| **A** | nginx `alias` 指向 `sites/<site>/www` | 产物即上线，零搬运；发布 = 磁盘写入 | 多站点要多 server 块；与现有 Jenkins/nginx 通道解耦（部署与发布变成两套机制）；后端与 web 必须同机同文件系统 |
| **B（推荐）** | 引擎把 `www/` 同步到 `/www/wwwroot/<site>` | 复用现有 nginx `root` 与 rsync 通道；天然支持"一站一域名"；搬运是**显式阶段**，天然成为回滚切换点（§8.7/§8.10） | 多一次搬运与一个远端写入凭据；产物落两份（本地 `www/` + 线上） |
| **C** | Spring 只读映射（仅预览） | 零运维改动，适合开发/验收 | 静态站走应用服务器 = 放弃静态站最大的好处（不占应用资源）；`/sites/**` 前缀已被占用（§11.2）；**不可用于生产** |

- **一站一域名**：每个站点一个 server 块，`server_name` 取自 `cms_site.domain`。域名与站点的绑定关系由后台维护，**运维不需要为加站点改配置**（这是"通用建站底座"的必要条件；加站点只加 DNS + 一条 server 块模板）。
- **`redirects.conf` 要 include 一次**：`include /www/wwwroot/<site>/redirects.conf;`（§7.6），站点内的 301 由引擎产出，运维不改配置。
- **两条写入通道绝不能指向同一目录**：现有 `frontend` 仓库（Jenkins rsync）与引擎产物如果都写 `/www/wwwroot/lingjiuw`，会互相覆盖。切换步骤见 §12.4。

### 11.4 路径边界组件 `SitePathBoundary`（**先把地基抽出来**）

**现状**：`SiteService.resolve(root, relativePath)` 是**包级私有**（同包唯一调用方是 `SiteFileService`），发布引擎在另一个包**拿不到它**；而且它**不拦符号链接**（javadoc 明说），对"按路径写文件"的引擎不够。

**方案**：抽出 `common/site/SitePathBoundary`（`public`），三个方法，行为与原实现**逐条一致**（先保不变，再加严）：

| 方法 | 行为 |
|---|---|
| `resolveUnder(root, relativePath)` | 拒绝绝对路径、盘符、`..` 上跳；返回规范化后的绝对路径（**与现有 `SiteService.resolve` 同逻辑**） |
| `assertNoSymlink(root, target)` | **新增**：对 `root → target` 的每一段做 `Files.isSymbolicLink` 检查，命中即 `E6002`。`www/` 树内禁止符号链接 |
| `assertWritable(path)` | **新增**：发布前置检查（`E6003`），避免"渲染 3 万页之后才发现目录不可写" |

- `SiteService` 与 `SiteFileService` 改为调用它（**行为不变**，只换实现位置）；发布引擎、媒体派生、主题包导入全部复用它。
- **还要一并提升可见性的三个成员**（**v2.2 补**：只抽 `resolve` 还不够——引擎在别的包连站点目录都拿不到）：`SiteService.siteDir()`（包级私有）、`siteRoot()`（`private`）、`SITE_SUB_DIRS`（`private` 常量，唯一使用点在同类的目录检查里）。统一改成 `public` 访问器（`siteDir(site)` / `siteRoot(site)` / `subDirs()`），并把 `relative()` / `checkName()` 一并纳入 `SitePathBoundary`——它们被 `SiteFileService` 调用 10 处，改签名要一起动。
- `SITE_SUB_DIRS` 加 `www` 之后**没有副作用**（唯一使用点只做"这些子目录必须存在"的检查），但要在 §12.4 里说明：`www/` 由引擎创建，人工不要往里放东西（§7.2.2、E6001）。
- **测试用例**（§12.3）：`..`、绝对路径、盘符、URL 编码绕过（`%2e%2e%2f`）、符号链接、长路径、Windows 保留名（`CON`、`NUL`）。

### 11.5 多语言 = 多站点

**方案（v2.1 采纳，改动面为零）**：一个语言 = 一个站点。复用已有的 `site_id` 隔离、独立 `root_dir`、独立模板与独立发布；语言之间没有共享内容，**要配对就手工用 `RELATION` 字段配对**（字段 code 约定 `translation`），`hreflang` 由模板按 §7.1.4 输出。

| 事项 | 做法 |
|---|---|
| 域名 | 独立域名或子域名（`en.example.com`）；**不支持子路径**（§11.1 的死限） |
| 语言标记 | `cms_site.lang`（§2.7）→ `[field:site.lang/]` 写进 `<html lang>`；`site.alternates` 输出 hreflang |
| 内容配对 | `RELATION` 字段（`translation`，字段定义勾 **`crossSite=1`**，§2.2），两侧都配 → 双向 `hreflang`；只配一侧就只出一个方向 |
| 模板 | 各站点独立主题（英文站与中文站可以完全不同的版式）；共用部分用主题包复制（§8.9） |
| 菜单 / 分类 | 各站点独立（同一套结构要复制，或用"站点复制"工具，属后台功能） |
| 发布 | 各站点独立批次；**没有"多语言一起发布"**（要一起发就连续发两次） |

- **跨站点配对的三条实现口径**（**v2.2 补**：v2.1 说"用 `RELATION` 配对、从配对项反查 URL"，但 `RELATION`、索引表（带 `site_id`）与 URL 规则**全是站点内**的，配对既存不下也反查不出——这是 v2.1 唯一一处"替代路径其实不成立"的缺口）：
  1. `RELATION` 字段勾 `crossSite=1` 后才允许指向**另一个站点**的内容 id；配对站点由 `i18n.alternates` 声明，写入时校验目标内容确实属于其中一个配对站点。
  2. `site.alternates` 是**引擎算出来的**可迭代项（每项 `lang` / `url` / `current`）：`url` = 当前内容在目标语言站点里**配对项的绝对 URL**；没有配对项的内容不出现在列表里（也就不会输出那一条 `hreflang`）。
  3. 模板只写 §7.1.4 的三行 `foreach`，**模板里不需要任何跨站查询**（跨站查询在语法上也不存在，§1.3）。
- **不做的替代方案（记录理由）**：给 `cms_content` 加 `lang` 列。代价：`slug` 唯一性变成 `(site, type, lang, slug)`，URL 规则、查询、索引、菜单全多一维，且与 §7.1 的占位符体系纠缠——**改动面比"复制一个站点"大得多**。
- 代价如实记录：**跨语言的内容一致性要靠人**（改中文忘了改英文，引擎不会提醒）。需要提醒就在后台加一个"翻译缺口"报表（按 `translation` 关系查缺），属可选功能。

### 11.6 模板与主题的版本化（**回答"新环境模板从哪来"**）

**现状**：`backend/.gitignore` 忽略 `sites/`，而模板正是 `sites/<site>/template/`。后果：**模板不进版本控制**——不能在 IDE 里写、不能 code review、不能回滚、不能在新环境重建；`sites/default` 与 `sites/wwwroot` 目前是空目录。

**方案（三层，从推荐到备选）**：

| 层 | 做法 |
|---|---|
| 1（推荐） | **主题源码进后端仓库**：`backend/themes/<theme>/`（进 Git，可 review、可回滚）→ 后台"应用主题"把它**复制**到 `sites/<site>/template/<theme>/`（运行副本，仍被忽略，随时可从源码重建）。这样"新环境模板从哪来"= 从仓库来，一条 `cp -r` 的事 |
| 2 | **主题包**（§8.9）：目录/zip + `theme.json`，后台导入导出。适合"主题由外部团队交付、不进本仓库"的场景 |
| 3（兜底） | 部署流水线把主题目录同步进 `sites/<site>/template/`（与产物搬运同一条通道，§8.10） |

- `.gitignore` **保持忽略 `sites/`**（它是运行数据：产物、人工资产、发布批次），只新增不忽略 `themes/`。
- **明确不接受**："模板只存在于生产服务器的 `sites/` 里，靠人手备份"。这是 v2 现状的默认结果，也是最容易丢资产的方式。
- 现有 `sites/wwwroot` 空目录：迁移时确认用途（若曾是手工站点目录），把其中的文件搬进该站点的 `data/`，然后删掉这个空目录（§12.4）。

### 11.7 新增依赖清单（**v2 把两个待新增依赖写成了既有能力**）

| 依赖 | 用途 | 体积/影响 | 不引入的替代方案（与代价） |
|---|---|---|---|
| `org.commonmark:commonmark`（+ `commonmark-ext-gfm-tables` 等按需） | `MARKDOWN` 字段入库预渲染（§5.2.2） | 纯 Java、无传递依赖 | 自己写 Markdown 解析器（不现实）；运行时用前端渲染（放弃 SEO 与"能不实时算就不实时算"） |
| `org.jsoup:jsoup` | 富文本清洗（§5.2.4）+ 抽 `toc` / `wordCount` / `imageCount`（§5.2.5） | 纯 Java、单 jar | OWASP Java HTML Sanitizer（更专一，但还要再引一个 HTML 解析器来抽标题）；正则清洗（**绝对不行**，HTML 不能靠正则处理） |
| `spring-boot-starter-mail`（**可选**） | 表单 / 评论通知邮件（§9.9） | 官方 starter | 不要邮件通知就只留 webhook |
| Redis 客户端（**可选**） | 限流令牌桶、浏览量计数批量累加（§9.4/§9.9） | 多一个中间件 | 单实例用内存；**多实例必须加**，否则限流失效 |
| **图像处理库**（`net.coobird:thumbnailator` 或 `com.twelvemonkeys.imageio:imageio-webp`，**v2.2 补**） | 派生尺寸与 WebP 产出（§7.7） | 纯 Java、单 jar | **JDK 自带的 ImageIO 不能编码 WebP**（只能读），而 §7.7 要求默认输出 WebP——没这个依赖，派生图在第一步就卡住；替代方案是"只出 JPEG/PNG、放弃 WebP"，代价是体积与评分（`pom.xml` 现有依赖里没有任何图像库） |

- **明确不引入**：任何模板引擎（Thymeleaf / FreeMarker / Velocity）。§1.3 的边界不变——"自研标签语言"是这套设计的核心资产，不是可替换件。
- 依赖引入要按既有工程习惯写进 `pom.xml` 的 `<dependencies>`，并在 §12.1 的迁移清单里标注版本（由 Spring Boot BOM 管版本的两个除外）。

### 11.8 与现有 `frontend` 站点的所有权交接（**同一 URL 的两个所有者**）

**现状**：`frontend/` 是 git submodule，内容是**手写单页官网**（纯 HTML/CSS/原生 JS，无构建、无 API 调用），由 Jenkins rsync 到 nginx 根目录；`robots.txt`、`sitemap.xml`、`404.html`、`50x.html` 都是手写的。而本设计要由引擎生成 404 / robots / sitemap / feed——**同一条 URL 上会出现两个所有者**。

**交接三步（写进 §12.4 的执行清单）**：

| 步 | 做什么 | 判据 |
|---|---|---|
| 1 | 引擎只生成到 `sites/<site>/www/`，**不搬运**；后台用 `/preview/**` 验收 | 预览站与线上站可以并排比较，线上零风险 |
| 2 | 开启 `publish.syncTarget` 搬运；**同时**从 Jenkins 流水线里移除 `robots.txt` / `sitemap.xml` / `404.html` / `index.html` 的 rsync（或整体停止该 job） | 线上 `index.html` 的 mtime 与 `.publish-synced` 记录一致；`curl -I` 能看到引擎产物的特征（如 `cms-site.json`） |
| 3 | `frontend` 只保留"引擎不生成的纯静态资源"（若有），或整体退休 | 仓库 README 明确"这个仓库不再提供站点页面" |

- **在完成第 2 步之前，绝不能让两条通道同时写同一个目录**——这是本文件里唯一一条"顺序错了会直接导致线上错页"的规则。

---

## 12. 实施阶段与验收判据

> 本设计最容易验证、收益最高的部分是**编译期校验**（§4.5）与**增量正确性**（§8.4），因此这两项在下面的阶段里被前置，并且各有一个**可执行的判据**（不是"做完即可"）。

### 12.1 数据模型与迁移清单

迁移文件按后端既有约定命名：`V<创建时的年月日时分秒 14 位>__<业务代码>.sql`，**只新建、不改已执行过的文件**（`backend/AGENTS.md`）。下表的"业务代码"是建议名，时间戳按创建时刻取。

| # | 迁移 | 内容 |
|---|---|---|
| 1 | `__cms_content.sql` | `cms_content_type`、`cms_field`、`cms_content`、`cms_content_index`、`cms_content_category`、`cms_content_tag`；唯一索引与部分索引（§2.3 的约束） |
| 2 | `__cms_menu.sql` | `cms_menu`、`cms_menu_item` |
| 3 | `__cms_redirect.sql` | `cms_redirect`（`site_id` / `old_path` / `new_path` / `reason` / `create_time`；唯一键 `(site_id, old_path)`） |
| 4 | `__cms_site_publish.sql` | `cms_site` 增列（`lang` `default_cover` `og_image` `theme` `protocol` `statistics_code`）+ `cms_site_publish_option`；`cms_site.domain` 唯一索引 |
| 5 | `__cms_publish_task.sql` | `cms_publish_task`、`cms_publish_lock` |
| 6 | `__cms_form.sql` | `cms_form`、`cms_form_field`、`cms_form_entry` |
| 7 | `__cms_comment.sql` | `cms_comment`、`cms_rating` |
| 8 | `__cms_theme.sql` | `cms_theme`（主题包导入记录） |
| 9 | `__cms_article_migrate.sql` | `cms_article` → `cms_content` 的**数据搬运**（含 `cms_article_tag` → `cms_content_tag`、`content_format`、`view_count`、`author_id` 映射），随后把 `cms_article` / `cms_article_tag` **删除**（即 §2.6 期 3 的收敛） |
| 10 | `__cms_publish_menu.sql` | 后台菜单与按钮权限（内容类型、发布中心、菜单管理、表单、评论、主题）；沿用 `sys_menu` / `sys_role_menu` 的既有写法 |
| 11 | `__cms_site_seed.sql` | 每个站点自动获得内置类型（`article` / `single` / `author`）、默认菜单 `main`、默认发布选项（§2.7）；用 `insert ... select` 保证已有站点也被补齐 |
| 12 | `__cms_media_columns.sql` | `cms_media` 增列：`alt` / `width` / `height` / `derive_status`（**v2.2 补**：§7.7 的派生尺寸与 `[field:cover.alt/]` / `.width` / `.height` 全靠它们，v2.1 的迁移清单漏了这条，而现有 `MediaService` 也不写这些字段） |
| 13 | `__cms_content_rank.sql` | `cms_content` 增列：`expire_time` / `view_count_day` / `view_count_week` / `comment_count` / `rating_avg` / `rating_count`（§2.3，v2.2 新增） |
| 14 | `__cms_subtable_pk.sql` | 给 `cms_redirect` / `cms_menu_item` / `cms_form_field` / `cms_content_index` 等**明细表**补齐既有工程约定：`id` 主键、`deleted` 逻辑删除列、部分唯一索引（`where deleted = 0`）——`V1__init.sql` 的约定如此，MyBatis-Plus 的内置方法（如 `deleteById`）也需要主键 |

- **表 1–14 的列定义以 §2–§9 为准**，本文档是唯一契约；实现时**不许**顺手加"以后可能有用"的列（§1.3 的边界精神）。
- **工程约定（`backend/AGENTS.md`）**：本设计涉及的原生 SQL（依赖反向索引的批量查询、`view_count` 累加、到点扫描、`insert ... select` 播种）**一律写进 `src/main/resources/mapper/cms/*.xml`**；Java 里不出现 SQL，也不许用 `Wrapper.last(...)` / `Wrapper.apply(...)` 塞原生片段。XML 里的语句不会自动带逻辑删除条件，要自己写 `deleted = 0`。
- `cms_site.domain` 的唯一索引要写成**部分唯一索引**：`where deleted = 0 and domain is not null`（多站点留空域名时不能互相冲突）。
- 迁移文件的时间戳必须**全部大于现有最后一个**（`V20261001035233`）且互不相同——`application.yml` 没开 `out-of-order`。
- `cms_article` / `cms_article_tag` **已删除**（迁移 `V20261006090001__cms_article_merge.sql`）：先把存量行搬进 `cms_content` 的 `article` 类型（含分类、标签、`HTML → RICHTEXT` 的格式映射），再 drop 两张表，"只读保留一期"的过渡到此结束（§2.6 期 3）。

### 12.2 实施阶段

| 期 | 内容 | 验收判据（**可执行**） |
|---|---|---|
| **期 0** 地基 | 抽 `SitePathBoundary`（§11.4）；引入依赖（§11.7）；迁移 1/4/5/10/11/12/13/14；**加 `@EnableScheduling` 与一个站点遍历器** | 路径边界用例全绿；新建站点后自动有 3 个内置类型与默认菜单；`mvn test` 通过；**定时任务在没有 HTTP 请求时也能取到站点**——现有 `SiteContext` 是 `ThreadLocal`，只由 `SiteInterceptor` 在请求内写入，后台线程里是空的，因此 §8.8 的五个定时任务必须**按 `cms_site` 列表逐站点显式设置上下文**，不能依赖 `SiteContext`（仓库内目前也没有 `@EnableScheduling` / `@Scheduled`） |
| **期 1** 引擎内核 | 词法 / 解析 / AST / 编译缓存 / **§4.5 的 18 条校验** / 渲染器 / 作用域栈与"当前条目" / `include` `if` `else` `foreach` / `list` `query` `detail` `pagelist` | §4.5 的 12 条★**各有一个失败用例与一个通过用例**；能发布一个最小资讯站（首页 + 分类 + 详情 + 分页 + 上下篇 + 面包屑） |
| **期 2** 计划与产物 | `PagePlan` / `UrlResolver` / 模板查找 / 原子写 / 产物清单 / **依赖清单与反向索引** / GC | **增量正确性测试通过**（§12.3 的第 3 条）；小说站 5 万页计划生成 < 30s；增量批次耗时 < 全量的 10% |
| **期 3** 聚合与运维产物 | sitemap（含分片）/ feed / robots / 404 / `redirects.conf` / 搜索索引 / `cms-site.json` | sitemap 与 noindex **一致**（自动化用例）；改 slug 后旧 URL 出 301 且旧产物被删（§8.6 的场景表逐行有用例） |
| **期 4** 动态层 | Host 解析 + 产物烤 id（§11.1）；表单 / 评论 / 评分 / 浏览量 / 搜索 API / 限流（§9） | 多站点下 H 类请求落**正确站点**（自动化用例）；**关闭 JS** 能提交表单、能读评论快照（自动化用例） |
| **期 5** 后台 | 内容类型与字段管理；按类型的内容管理（含层级、图集、规格、关联）；发布中心（任务 / 批次报告 / 回滚 / 预发布）；菜单管理；表单定义与提交记录；评论审核；主题导入导出 | **不改一行 Java**，在后台建出行业站六类型（产品/案例/服务/团队/招聘/FAQ）并成功发布；发布中心能一键回滚到上一批次 |
| **期 6** 主题与迁移 | `_default` 内置主题；主题包（§8.9）；`cms_article` 数据搬运（§12.1 第 9 条）；`frontend` 交接（§11.8） | **新环境从仓库重建站点成功**（空库 + 空 `sites/` + 一条命令）；搬运后旧文章 URL 不变或 301 正确 |
| **期 7** 运维 | 五个定时任务（§8.8：定时发布 / 到期下线 / 榜单重建 / 每日全量 / 每周清理）；产物出口 B 的搬运（§8.10）；监控指标（批次耗时、失败页数、报错码 Top N） | **连续 7 天无人值守**：每日全量重建零失败；定时发布到点误差 ≤ 2 分钟；榜单每小时刷新；到期内容在 ≤ 2 分钟内从静态页消失 |

### 12.3 测试策略（`backend/src/test` 目前不存在，这是第一批要补的）

| # | 测试 | 为什么它排第一 |
|---|---|---|
| 1 | **编译期校验的表驱动测试**：每条★校验给"触发模板 + 期望错误码 + 文案关键字" | §4.5 是自研引擎最大的红利，也是**最容易写测试**的部分（纯函数式：源码进、错误出） |
| 2 | **黄金文件测试**：固定主题 + 固定内容 → 期望 HTML 逐字节比较 | 同时验证三件事：渲染确定性（§8.5 幂等）、作用域解析、转义策略。产物一变就 red，改动的副作用无所遁形 |
| 3 | **增量正确性测试（最重要）**：随机做 N 次内容变更（新增 / 修改 / 删除 / 改 slug / 上下线 / 改分类 / 改标签 / 改菜单），把"增量发布后的产物树"与"全量发布后的产物树"**逐字节比较** | 它把 §8.4 的正确性从"论证"变成"判据"；任何漏报（陈旧页）与漏删（幽灵页）都会立刻暴露 |
| 4 | **路径边界与安全测试**：§11.4 的用例 + 富文本清洗的绕过用例（`<script>`、`on*`、`javascript:`、`<img src=x onerror=…>`、大小写与编码绕过） | 这两处是"上线即事故"的地方，且用例可以穷举 |
| 5 | **无 JS 降级测试**：关掉 JS 走表单提交、评论快照、分页、搜索页提示 | H 类的"降级"承诺必须可验证，否则就是文档里的一句话 |
| 6 | **性能测试**：5 万页全量 / 增量的耗时与内存；单页渲染 P95 | 给 §12.5 的预算定基线；小说站的可行性全压在这一条上 |

### 12.4 上线与切换清单（含旧站处理）

1. 备份数据库与 `sites/` 目录（含 `template/`）。
2. 跑迁移（§12.1 的 1–14），确认 Flyway 校验和正常。
3. 建生产站点记录（`cms_site`：`domain` / `root_dir` / `lang` / `theme`），确认 `cms_site.domain` 唯一索引生效。
4. 应用主题（§11.6 的方案 1 或 2），后台"预览计划"看到页面数量与 URL 清单。
5. 导入 / 授权内容（或跑 `cms_article` 搬运）；核对 URL 与旧站是否一致（**不一致的逐条补 `cms_redirect`**）。
6. 发布到 `sites/<site>/www/`，用 `/api/preview/{siteCode}/**` 逐页验收（重点：首页、列表分页、详情、404、robots、sitemap、feed、表单提交、搜索、**榜单页与排序页**）。
7. nginx 一次性改齐：`location /uploads/`（§11.2）、`include .../redirects.conf`（§11.3）、`error_page 404 /404.html`、`location ~ ^/(api|v1)/`（已有，**每个站点的 server 块都要有——H 类接口同源全指望它**）；同时逐条核对 §11.3 的"仓库外事实"标注（这些配置在仓库里无法核实，必须由运维确认）。
8. 开启 `publish.syncTarget` 搬运；**同时**停掉 `frontend` 仓库里写站点页面的 Jenkins 步骤（§11.8 的第 2 步）——**这一步必须与上一步同批执行**，否则两条通道会互相覆盖。
9. 线上抽查：`curl -I` 首页 / 一个详情 / 一张图 / `robots.txt` / `sitemap.xml`；确认 `cms-site.json` 存在且 `siteId` 正确。
10. 回滚预案：保留旧站目录副本；发布中心留住上一个批次（`publish.keepReleases ≥ 1`）；nginx 配置改回只需注释两行。
11. 清理：确认 `sites/wwwroot` 里的内容已归位后删除该空目录（§11.6）。
12. **删站点的完整性**（**v2.2 补**）：站点删除校验要加上新表（内容、菜单、表单、评论、评分、重定向、发布任务、主题记录），否则收敛到 `cms_content` 之后会留下孤儿内容——现有实现只查 `article` / `category` / `tag` / `media` 四张表。
13. **multipart 上限对齐**（**v2.2 补**）：`application.yml` 当前是单文件 10MB / 单请求 20MB，与 §9.5 的表单附件默认值一致；改表单默认值时必须同时改这里，否则大附件会在容器层被拦下（媒体库允许 zip 上传，同样受这个上限约束，§8.9 的"体积上限"要落成具体数字）。
14. **旧公开端点的浏览量副作用**（**v2.2 补**）：现有 `/api/public/articles` 每次读取都会给 `cms_article.view_count` +1，而 §9.4 规定浏览量走 `POST /api/public/views/...` + 去重 + 批量累加。旧端点既然保留，就要**去掉读接口的写副作用**，否则同一个数字有两个来源、两套口径。

### 12.5 性能与容量预算（**用来判断"小说站能不能做"**）

| 指标 | 预算 | 依据 / 超限时的对策 |
|---|---|---|
| 单页渲染（编译缓存命中） | 简单页 P95 < 5ms；复杂页（3 个 `query` + `foreach`）P95 < 20ms | 超限先查 N+1：`{cms:list}` / `{cms:query}` 必须批量取数（一页一次查询，不许每项一次） |
| 全量发布 | **5 万页 < 5 分钟**（4 线程；纯渲染约 60–90s，其余是 IO 与聚合） | 超限：提高 `publish.threads`、减少聚合产物（关 feed / 筛选页） |
| 增量发布 | 单篇内容变更影响 20–200 页 → **< 5 秒**（不含 5 秒合并窗口） | 超限：检查反向索引是否把 `menuIds` 之类记得过宽（宁可多，但要能解释） |
| 计划生成 | 5 万页 < 30s，内存 < 512MB | 超限：计划项瘦身（依赖只存 id 摘要） |
| `manifest.json` | 5 万页 ≈ 7.5MB；`.publish/deps.idx` ≈ 2× | 超限：分片存储（按路径前缀） |
| 搜索索引 | 5 万条 × ≤1KB ≈ 50MB（gzip 后 ~15MB，25 个分片） | 这正是 `search.staticMax=50000` 的由来；超过就切 `api` 模式（§7.5） |
| 站点体积 | 5 万页 × 平均 30KB ≈ 1.5GB；`release` ×3 ≈ +4.5GB | 磁盘不够就 `publish.keepReleases=1`（回滚能力换空间，**必须显式确认**，见 `W5005`） |
| 媒体 | 派生文件 ≈ 原图总和的 1.5–2 倍（三档 + WebP） | 图多的大站必须上 CDN/对象存储（§11.2） |

---

## 13. 术语、固定清单与变更流程

### 13.1 术语表（**本文件里出现的每个专有名词，只有这一个意思**）

| 术语 | 定义 |
|---|---|
| **页面类型** | §7.2.1 的 11 种之一（`HOME`/`LIST`/`TAGLIST`/`TAGPAGE`/`ARCHIVE`/`DETAIL`/`DPAGE`/`SINGLE`/`FACET`/`SEARCH`/`STATIC`）。一个页面类型的实例 = 一个产物 |
| **内容类型** | 站点自助定义的业务形态（`article` / `product` / `book` / `chapter`…），`kind` ∈ `CONTENT`/`SINGLE`/`TREE` |
| **当前条目** | 页面所依附的那一条内容（§5.1），恒压入匿名作用域栈底；详情页、单页有，首页与各种列表页没有 |
| **锚定项** | 查询/迭代发生位置的依附对象：在循环内 = 栈顶迭代项；否则 = 当前条目（§3.7 裁定三） |
| **页面主体** | `{cms:list}` 与 `{cms:detail}` 的合称：一个模板**至多一个**，二者互斥（§4.5 口径表） |
| **分页主体** | 会产生第 2..N 页的主体（列表分页 / 正文分页 / 目录分页），它决定 `{n}` 与派生页计划 |
| **派生页** | 同一模板、同一上下文、只有 `page.pageNo` 不同的页（第 2..N 页） |
| **依赖清单（manifest）** | 每个产物记录了"它由哪些输入算出来"（§8.4），增量发布与 sitemap `lastmod` 都靠它 |
| **批次（batch）** | 一次发布 = 一个批次，对应一个 `release/<batchId>/` 副本与一份报告 |
| **主题包** | 模板的部署单元（目录/zip + `theme.json`），§8.9 |
| **快照** | 静态页里"发布时刻的值"（`viewCount`、评论、评分）——**会滞后，且这是已知代价**（§9.4/§9.6） |
| **S / H / D** | §1.2 的页面分级：静态生成 / 静态壳 + API / 纯动态（不在本文件范围） |
| **结论性清单** | 保留名、页面类型、标签、占位符、字段类型、错误码段——§13.2 汇总 |

### 13.2 固定清单总表（**改任何一张都要走 §13.3**）

| 清单 | 数量 | 在哪 |
|---|---|---|
| 词法元素 | **5** | §3.1 |
| 标签 | **14**（结构 4 + 查询 3 + 导航 6 + 交互 1，另加 `[field:…/]`） | §6.1 |
| 标签 × 页面类型合法性 | **11 种页面类型 × 14 个标签**（越界 → E3012） | §6.7 |
| 具名作用域 / 保留名 | **6**：`site` `channel` `page` `param` `query` `item` | §5.1 |
| 字段类型 | **19** | §2.2 |
| 内置字段 | **21**（v2.2 加 `expireTime`） | §2.2 |
| 派生字段 | 8 组（见 §2.2 表） | §2.2 |
| 页面类型 | **11** | §7.2.1 |
| URL 占位符 | **14**（`{year}` / `{month}` / `{day}` 三个占一行；`{n}` 是 `{pageNo}` 的简写，不另计） | §7.1.1 |
| 站点发布选项 | **47** | §2.7 |
| 内容类型 kind | 3：`CONTENT` / `SINGLE` / `TREE` | §2.1 |
| 不支持清单（永久边界） | 见 §1.3、§6.6 | §1.3 |
| 错误码段 | 7 段：`E1xxx`–`E7xxx`，另 `W5xxx` | §10.2 |

### 13.3 变更流程（**spec 冻结之后怎么改**）

1. **先改本文件，再改代码**——本文件是唯一契约。改代码不改文档，视为没改。
2. 任何"新增"都要在 **§0.2 追加一行**，并填满四列（变更 / 类型 / 为什么必须 / 代价）。**代价列不许写"无"**（真无代价就写"无（零迁移成本，无实现）"并说明为什么）。
3. **破坏性变更的定义**（须升级为 v3 并给迁移方案）：改标签名、改参数名、改字段名、改 URL 形态、改 `{n}` 语义、改保留名、改错误码含义、改产物路径、改 `www/` 与 `data/` 的边界。
4. **非破坏性变更**（可在 v2.x 内追加）：新增可选参数、新增派生字段、新增站点选项、新增错误码、新增页面类型（`SEARCH` 这类可有可无的页面类型）。
5. **先例原则**：任何"给语法加表达式/运算符/函数"的提案，一律按 §1.3 拒绝，并指向 §5.5 的准则（**把判断挪到引擎侧，或挪到字段定义**）。
6. 提异议的窗口是**实现之前**；实现之后改名要付迁移成本（内容表落库、模板散落在各站点时就晚了）。

### 13.4 明确不做与未决事项

**明确不做（v2.1 定稿，永久或长期）**：

| 不做 | 理由 | 要做的替代 |
|---|---|---|
| 单站点内多语言、URL 路径前缀区分站点（`/en/…`） | 见 §11.5 的代价对比 | 一个语言 = 一个站点（独立域名或子域名） |
| 可视化拖拽建页 | §1.3 的边界 | 主题包 + 标签生成器向导 |
| 无限层级评论 / 富文本评论 | 审核与渲染成本都不成比例 | 一级回复 + 纯文本评论 |
| 运行时模板渲染（在线渲染动态页） | §1.3 的边界 | H 类 = 静态壳 + API |
| 搜索引擎级全文检索（BM25 / 分词 / 同义词） | 静态索引 + 子串匹配已覆盖"站内找到内容"的需求 | `search.mode='api'` 时的 `like` 查询；真需要就外部接 ES/Meilisearch，属可选扩展 |
| 评论 / 评分的结构化数据（`aggregateRating`） | 快照可能过期，提交过期结构化数据属于垃圾数据 | 只展示，不提交（§9.6） |
| **整书导出（TXT / EPUB / MOBI）** | 排版转换与设备兼容是另一个产品，静态引擎不该内建排版器 | **编辑用工具导出后作为 `FILE` 字段上传**（§2.2），模板用 `[field:bookFile/]` 给下载链接；要自动化就写一个后台扩展（不属本文件范围） |
| **点赞 / 收藏进静态页（收藏榜、点赞榜）** | 极易刷，且没有像浏览量那样的去重口径 | 榜单只做"浏览榜"（`viewCount` / `viewCountDay` / `viewCountWeek`）；收藏是本地 / 账号行为（§9.7），不进静态页（§9.4） |

**未决事项（需要在实现前定，或允许先按默认值走）**：

| # | 未决 | 当前默认 | 谁定 |
|---|---|---|---|
| 1 | 产物出口选 A 还是 B（§11.3） | **B**（同步到 `/www/wwwroot/<site>`） | 运维（取决于机器与权限现状） |
| 2 | 媒体出口（§11.2）：nginx `alias` 还是 CDN | 先 `alias`，量大再上 CDN | 运维 |
| 3 | 是否引入 Redis（§11.7） | 单实例不用；多实例必须用 | 部署形态 |
| 4 | `article` 存量数据是"搬运"还是"重新录入" | 搬运（§12.1 第 9 条） | 取决于种子数据量（目前只有种子数据，搬运成本极低） |
| 5 | 内联样式（`style`）是否加入富文本白名单（§5.2.4） | 不加（用预置 class） | 主题作者的实际抱怨量 |
| 6 | 每日全量重建的时刻（§8.8） | 03:00 | 站点访问低峰 |
| 7 | 表单附件保留天数（§9.9） | 180 天 | 合规要求 |
| 8 | 站点导入导出的完整工具（内容 + 主题 + 选项一键搬站点） | 不做（v2.1 只做主题包） | 有第二个同构站点需求时 |

---

## 14. 逐站型覆盖自检表（**验收判据**）

> 本节是判断"本文件是否达标"的最终判据。**一个站型只有全部行都判 ✅，才算被 100% 覆盖。**
>
> **v2.1 的结论：全部站型达标**（§14.7 统计：78/78 行 ✅）。v2 的这张表是 67 行 / 19 ✅ / 10 ⚠️ / 38 ⛔，
> 因为那时 §7–§13 全部不存在、且 §14.8 列着 12 项"补章节也修不好"的规格缺陷（P1–P12）。
> v2.1 补齐了章节并把 P1–P12 全部落进 §1–§9，**修复对照见 §14.8**（每一项都给出落点，可逐条回查；v2 的原文留在 §14.9 存档）。
>
> **状态口径**：
> - ✅ **本文件已有明确的规则可以表达**（规则必须能被指向具体小节，不接受"应该可以"）
> - ⚠️ 可表达但**需要站点做额外配置或取舍**（例如关掉某类页面、提高某个上限）——**不算不达标，但要在 §14.7 里点名**
> - ⛔ 本文件无法表达 → 不允许出现在表里；出现即视为本文件未完成
>
> **模板文件列**按 §7.3 的查找顺序写（`{type}` 是内容类型 code）；**URL 形态列**按 §7.1 的占位符与 §7.2 的页面类型写。

### 14.1 通用页面（所有站型共有）

| # | 页面 | URL 形态（§7.1 规则） | 模板文件（§7.3 查找顺序） | 页面主体 | 数据来源 | 标签组合 | 级别 | 状态 |
|---|---|---|---|---|---|---|---|---|
| G1 | 首页 | `/` | `index.html` → `home.html` | 可选 `list` | 命名 `query` × N（可 `type='all'` 跨类型） | `query` `if`/`else` `foreach` | S | ✅ |
| G2 | 栏目 / 分类索引 | 分类 `list_url_pattern`，第 2 页起含 `{n}` | `{type}_list.html` → `list.html` | `list` | 分类（`includeChildren`） | `list` `pagelist` `channel` `breadcrumb` | S | ✅ |
| G3 | 内容详情 | `detail_url_pattern` | `{type}_detail.html` → `detail.html` | 可选 | 单条内容（栈底当前条目） | `breadcrumb` `prenext` `foreach` `if` | S | ✅ |
| G4 | 详情正文分页 | 同上 + `{n}`（`paginate_body` 非空） | 同 G3 | `detail` | 正文按 `<!--cms:page-->` 切片 | `page.prevUrl/nextUrl/totalPages` | S | ✅ |
| G5 | 单页（`kind='SINGLE'`） | `detail_url_pattern`（如 `/about/`） | `{type}.html` → `single.html` → `detail.html` | 无 | 该类型的唯一一条 | 直接 `[field:title/]` 等（当前条目在栈底，§5.1） | S | ✅ |
| G6 | **详情页上的可分页清单**（目录 / 专题 / 作者文章 / 系列篇目） | 同详情页 + `{n}` | 同 `{type}_detail.html` | `list` | 子内容（`of='self'`）/ 关联（`relate='field:<code>'`）/ 过滤（`where`） | `list` `pagelist` `foreach` `if`(`page.isFirst`) | S | ✅ |
| G7 | 标签总览 | `url.tags`（`/tags/`） | `tags.html` → `tag_list.html` → `list.html` | 无 | `tagnav` | `tagnav` | S | ✅ |
| G8 | 标签详情 | `url.tag`（`/tag/{tagSlug}/`）+ `{n}` | `tag_list.html` → `{type}_list.html` → `list.html` | `list` | 标签 | `list` `pagelist` `tagnav` | S | ✅ |
| G9 | 归档页 | `url.archive`（`/archive/{year}/{month}/`）+ `{n}` | `archive_list.html` → `list.html` | `list` | 年月（`{cms:archive}` 迭代项的 `url`） | `list` `pagelist` `archive` | S | ✅ |
| G10 | 作者页 | `author` 类型的 `detail_url_pattern`（`/author/{slug}/`）+ `{n}` | `author_detail.html` → `detail.html` | `list` | 作者是内容（§2.1），文章用 `relate='field:authorId'` | `list` `pagelist` `channel` | S | ✅ |
| G11 | 筛选落地页 | `url.facet`（`/f/{facetPath}/`）+ `{n}` | `facet_list.html` → `{type}_facet.html` → `{type}_list.html` → `list.html` | `list` | 该页面的 facet 条件（**引擎自动注入，模板不写 `where`**） | `list` `pagelist` `channel source='facet'` | S | ✅ |
| G12 | 搜索页 | `url.search`（`/search/`） | `search.html`（缺省用内置壳） | 无 | 静态索引（§7.5）或 `/api/public/search`（§9.8） | 手写 `<form>` + `param.q` + `if` | H | ✅ |
| G13 | 404 | `/404.html` | `404.html` | 无 | 无数据（仅 `site`） | `site` 字段 | S | ✅ |
| G14 | robots.txt | `/robots.txt` | `robots.txt`（缺省用内置） | — | 站点配置 + 页面计划 | — | S | ✅ |
| G15 | sitemap（含分片） | `/sitemap.xml` + `sitemap-{k}.xml` | 内置 Writer（§7.6） | — | 全部计划页面 + manifest 的 `lastmod` | — | S | ✅ |
| G16 | feed | `/feed.xml`、`{categoryPath}/feed.xml` | `feed.xml`（缺省用内置） | — | 最近 N 条 | — | S | ✅ |
| G17 | 搜索索引产物 | `/search/index.json` + 分片 | 内置 Writer（§7.5） | — | `searchable=1` 字段 | — | S | ✅ |
| G18 | 主导航 / 页脚导航 / 友情链接 | — | 任意模板 | — | `cms_menu`（§2.4） | `channel source='menu'` | S | ✅ |
| G19 | 类型作为导航项（行业站六类型） | — | 任意模板 | — | 内容类型列表 | `channel source='type'` | S | ✅ |
| G20 | 面包屑 | — | 任意模板 | — | `channel` + 分类祖先 + **内容父链**（`withContent`） | `breadcrumb` | S | ✅ |
| G21 | 站点标识产物（给 `cms.js` 与排障） | `/cms-site.json` | 内置 | — | 站点配置 | — | S | ✅ |
| G22 | 重定向（改 slug / 改分类） | `/old/` → 301 `/new/` | `redirects.conf`（内置 Writer） | — | `cms_redirect` | — | S | ✅ |

### 14.2 资讯门户（通用页见 14.1）

| # | 页面 | URL 形态 | 模板文件 | 页面主体 | 数据来源 | 标签组合 | 级别 | 状态 |
|---|---|---|---|---|---|---|---|---|
| N1 | 首页要闻 / 推荐 / 最新区 | `/` | `index.html` | 可选 `list` | 命名 `query`（`top` / `recommend` / `type='all'` 最新） | `query` `if` | S | ✅ |
| N2 | 图集频道 | 同 G2 | `photo_list.html` → `list.html` | `list` | `IMAGES` 内容 | `list` `pagelist` `foreach` | S | ✅ |
| N3 | 专题 / 聚合页（人工挑选，200 篇需分页） | `/topic/{slug}/` + `{n}` | `topic_detail.html` → `detail.html` | `list` | `topic` 类型 + `relate='field:picked'`（§6.3） | `list` `pagelist` `if`(`page.isFirst`) | S | ✅ |
| N4 | 点击排行 | `/rank/page-{n}/`（站点选项 `pages.static` 声明 `query`，§7.2.1） | `rank.html` | `list` | `list orderby='viewCountWeek desc'`（**T 时刻快照**，§9.4） | `list` `pagelist` | S | ✅ |
| N5 | 评论（含 SEO 快照） | 详情页内 | 详情模板内 | 无 | 评论 API + **发布时快照** | `data-cms-comments` + §9.6 | H | ✅ |
| N6 | 浏览量 | 详情页内 | 详情模板内 | 无 | `viewCount` 快照 + 上报接口 | `[field:viewCount/]` + `data-cms-views` | H | ✅ |
| N7 | 广告位 | 任意 | 任意模板 | 无 | `ad` 类型 | `query` | S | ✅ |
| N8 | 定时发布 | — | — | — | `status='PUBLISHED'` 隐含 `publish_time<=now()`（§6.3）+ 每分钟到点触发（§8.8） | — | S | ✅ |
| N9 | 正文分页的 SEO 处理 | 详情页 `{n}` | 详情模板内 | `detail` | `page.noindex`（站点选项 `seo.paginatedIndex`，§7.6） | `if`(`page.noindex`) | S | ✅ |

### 14.3 企业官网（通用页见 14.1）

| # | 页面 | URL 形态 | 模板文件 | 页面主体 | 数据来源 | 标签组合 | 级别 | 状态 |
|---|---|---|---|---|---|---|---|---|
| W1 | 关于我们 / 隐私政策 / 服务条款 | `/about/`、`/privacy/` | `single.html`（或 `about.html`） | 无 | `SINGLE` 类型 | 直接 `[field:title/]` / `[field:content/]`（当前条目在栈底，§5.1） | S | ✅ |
| W2 | 服务 / 案例 / 团队 列表 + 详情 | 同 G2 / G3 | `{type}_list.html` / `{type}_detail.html` | `list` / 可选 | 各类型 | `list` `pagelist` `foreach` | S | ✅ |
| W3 | 招聘列表 + 详情 | 同 G2 / G3 | `job_list.html` / `job_detail.html` | `list` / 可选 | `job` | `list` `pagelist` `foreach`(要求/福利) | S | ✅ |
| W4 | FAQ | `/faq/` | `faq_list.html` | `list` | `faq`（问 / 答两个字段） | `list` `foreach` + `<details>` | S | ✅ |
| W5 | 联系我们 / 在线留言 | `/contact/` | `single.html` + 表单 | 无 | `cms_form`（§9.5） | `form` | H | ✅ |
| W6 | 详情页定向询价（带内容 id） | 详情页内 | `{type}_detail.html` | — | `cms_form` + `hidden='contentId:self'`（§6.5） | `form` | H | ✅ |
| W7 | 感谢页 | `url.thanks`（`/thanks/`） | `thanks.html`（`STATIC`） | 无 | 静态 + `param.form_error` 回显 | `if` | S | ✅ |
| W8 | 主导航（栏目 + 单页 + 外链） | — | `_partials/header.html` | — | `cms_menu`（§2.4） | `channel source='menu' depth='2'` | S | ✅ |
| W9 | 多语言外贸站 | 独立域名或子域名（一站一语言） | 各站点自己的主题 | — | 独立站点（§11.5） | `site.lang` + `site.alternates` + `RELATION` 配对 | S | ✅ |
| W10 | 页脚（友情链接 / 备案 / 联系方式） | — | `_partials/footer.html` | — | `cms_menu code='footer'` + `site` 字段 | `channel source='menu'` + `[field:site.icp/]` | S | ✅ |
| W11 | 资质 / 证书展示 | 同 G2 | `cert_list.html` → `list.html` | `list` | `IMAGE` 类型内容（或 `IMAGES` 字段） | `list` `foreach` | S | ✅ |

### 14.4 博客（通用页见 14.1）

| # | 页面 | URL 形态 | 模板文件 | 页面主体 | 数据来源 | 标签组合 | 级别 | 状态 |
|---|---|---|---|---|---|---|---|---|
| B1 | 首页文章流 | `/` | `index.html` | `list` | `article` | `list` `pagelist` | S | ✅ |
| B2 | 文章详情（TOC / 代码高亮） | 同 G3 | `article_detail.html` | 可选 | `article` | `toc` 派生字段 + `foreach`（§5.2.5）；`class` / `data-*` 在清洗白名单内（§5.2.4） | S | ✅ |
| B3 | 系列 / 合集 | `/series/{slug}/` + `{n}` | `series_detail.html` | `list` | `TREE` 类型的子内容（`of='self'`）或 `relate` | `list` `pagelist` `if`(`page.isFirst`) | S | ✅ |
| B4 | 作者页 / 作者归档 | `author` 的 `detail_url_pattern` + `{n}` | `author_detail.html` | `list` | `author` 类型 + `relate='field:authorId'` | `list` `pagelist` `foreach` | S | ✅ |
| B5 | 列表内高亮当前文章 | — | 任意列表模板 | — | 引擎算出的 `current` / `class`（§5.5） | `class="card [field:class/]"` | S | ✅ |
| B6 | 评论 | 详情页内 | 详情模板内 | 无 | 评论 API + 发布时快照 | `data-cms-comments` + §9.6 | H | ✅ |
| B7 | RSS / Atom | `/feed.xml`、`{categoryPath}/feed.xml` | `feed.xml`（缺省内置） | — | 最近 N 条 | — | S | ✅ |
| B8 | 站内搜索 | `url.search` | `search.html` | 无 | 索引 + API | `data-cms-search` + §9.8 | H | ✅ |

### 14.5 小说站（通用页见 14.1）

| # | 页面 | URL 形态 | 模板文件 | 页面主体 | 数据来源 | 标签组合 | 级别 | 状态 |
|---|---|---|---|---|---|---|---|---|
| F1 | 首页（推荐 / 分类 / 榜单） | `/` | `index.html` | 可选 `list` | `book` + `query` | `query` `list` `pagelist` | S | ✅ |
| F2 | 书详情页（简介 + 目录，可分页） | `/book/{slug}/` + `{n}` | `book_detail.html` | `list` | `book` + 子内容 `of='self'` | `list` `pagelist` `if`(`page.isFirst`) | S | ✅ |
| F3 | 目录（第 2 页起） | 同 F2 的 `{n}` 形态（`/book/{slug}/page-{n}/`，由 `detail_url_pattern` 定） | 同 F2 | `list` | `chapter`（`parent_id` = 该书） | `list` `pagelist` | S | ✅ |
| F4 | 章节阅读页 | `/book/{parentSlug}/{slug}.html` | `chapter_detail.html` | 可选 | `chapter` | 栈底当前条目 + `parentTitle` | S | ✅ |
| F5 | 章节正文分页 | 同上 + `{n}`（`paginate_body` 非空） | 同 F4 | `detail` | 正文按 `<!--cms:page-->` 切片 | `page.prevUrl/nextUrl` | S | ✅ |
| F6 | 上一章 / 下一章 | 章节页内 | 同 F4 | 无 | 同父相邻（按类型 `sort_field`） | `prenext within='parent'` | S | ✅ |
| F7 | 返回目录 / 含书名的 `<title>` | 章节页内 | 同 F4 | 无 | 父链派生字段（§2.2） | `[field:parentTitle/]` `[field:parentUrl/]` | S | ✅ |
| F8 | 书详情页"最新章节" | 书详情内 | 同 F2 | 无 | 子内容最新 N 条 | `query type='chapter' of='self' orderby='publishTime desc'` | S | ✅ |
| F9 | 榜单（日 / 周 / 总） | `/rank/page-{n}/` | `rank.html` | `list` | `view_count` / `view_count_day` / `view_count_week`（快照 + 每小时重建，§8.8） | `list` `pagelist` | S | ✅ |
| F10 | 书架 / 阅读进度 | 任意 | 任意模板（挂 `data-cms-shelf`） | 无 | `localStorage` + `/api/public/shelf`（§9.7） | `data-cms-shelf` | H | ✅ |
| F11 | 站内搜索 | `url.search` | `search.html` | 无 | 索引 + API | §7.5 / §9.8 | H | ✅ |
| F12 | 完本 / 连载筛选 | 同 G11 | `facet_list.html` | `list` | `facets` 配置 + **自建 `ENUM` 字段**（如 `serialStatus`：连载 / 完结，勾 `indexed=1`）。**不能用内置 `status`**：它是 `DRAFT`/`PUBLISHED`/`OFFLINE`，而且内置列没有 `cms_field` 定义、进不了 facet（§7.4） | `list` `pagelist` `channel source='facet'` | S | ⚠️ |
| F13 | 同作者其他作品 | 书详情内 | 同 F2 | 无 | `relate='field:authorId'` | `query` | S | ✅ |

### 14.6 行业站（产品 / 案例 / 服务 / 团队 / 招聘 / FAQ；通用页见 14.1）

| # | 页面 | URL 形态 | 模板文件 | 页面主体 | 数据来源 | 标签组合 | 级别 | 状态 |
|---|---|---|---|---|---|---|---|---|
| I1 | 首页六类型分区 | `/` | `index.html` | 可选 | 六个类型的命名 `query` | `query` `if` | S | ✅ |
| I2 | 产品列表（含筛选） | 同 G2 / G11 | `product_list.html` / `facet_list.html` | `list` | `product`（栏目页）或 facet 条件（引擎注入） | `list` `pagelist` `channel source='facet'` | S | ✅ |
| I3 | 产品详情（规格参数表） | 同 G3 | `product_detail.html` | 可选 | `product` + `JSON` 字段 | `foreach field='specs'` | S | ✅ |
| I4 | 案例 / 服务 列表 + 详情 | 同 G2 / G3 | `case_list.html` / `case_detail.html`… | `list` / 可选 | `case` / `service` | `list` `pagelist` | S | ✅ |
| I5 | 团队 | `/team/` | `team_list.html` | `list` | `team` | `list` `foreach`（头像 / 职位 / 社媒） | S | ✅ |
| I6 | 招聘列表 + 详情 | 同 G2 / G3 | `job_list.html` / `job_detail.html` | `list` / 可选 | `job` | `list` `pagelist` | S | ✅ |
| I7 | 简历投递 / 询价表单 | 详情页内 | 详情模板内 | — | `cms_form` + `hidden='contentId:self'` + `FILE` 附件（§9.5） | `form` | H | ✅ |
| I8 | FAQ | `/faq/` | `faq_list.html` | `list` | `faq` | `list` `foreach` + `<details>` | S | ✅ |
| I9 | 资料下载 | `/download/` | `download_list.html` | `list` | `FILE` / `FILES` | `list` `foreach` `[field:x.filesize/]` | S | ✅ |
| I10 | 相关产品 / 相关案例（可多页） | 详情页内 + `{n}` | `product_detail.html` | `list` | `RELATION` 字段 | `list relate='field:related'` `pagelist` | S | ✅ |
| I11 | 多地区 / 多门店落地页 | 同 G11 | `facet_list.html` | `list` | `region` 字段做 facet（`ENUM_MULTI`） | `list` `pagelist` `channel source='facet'` | S | ✅ |
| I12 | 导航（六类型 = 六导航项） | — | `_partials/header.html` | — | 内容类型列表 | `channel source='type'` | S | ✅ |
| I13 | 产品视频 | 详情页内 | `product_detail.html` | — | `FILE` + `IMAGE` 封面 + `INT duration`（§2.2 的视频说明） | `if` + 原生 `<video>` | S | ✅ |
| I14 | 按规格选型（多条件筛选） | 同 G11（组合页需先在 `facets.combos` 声明） | `facet_list.html` | `list` | 多个 `indexed` 规格字段做 facet；**叠加链接用 `urlWith`**（§7.4） | `list` `pagelist` `channel source='facet'` | S | ⚠️ |
| I15 | 询价 / 投递记录（访客不可见） | — | — | — | `cms_form_entry`（后台，§9.5） | — | D（后台） | ✅ |

### 14.7 达标结论

| 站型 | 总行数 | ✅ | ⚠️（可表达，但需配置或取舍） | ⛔ |
|---|---|---|---|---|
| 通用（所有站型共有） | 22 | 22 | 0 | 0 |
| 资讯门户 | 9 | 9 | 0 | 0 |
| 企业官网 | 11 | 11 | 0 | 0 |
| 博客 | 8 | 8 | 0 | 0 |
| 小说站 | 13 | 12 | 1（F12） | 0 |
| 行业站 | 15 | 13 | 2（I11、I14） | 0 |
| **合计** | **78** | **75** | **3** | **0** |

**结论：六个站型全部达标，78/78 行可表达**（其中 **75 行开箱即可**、**3 行需要在后台做配置**：F12 / I11 / I14 的多条件筛选页要求勾选字段的"可筛选"并在 `facets.combos` 里声明组合——按本节口径，⚠️ 不算不达标）。

> **v2.2 修订**：v2.1 这里写的是"78 行全 ✅"，但复核发现其中 5 行的自证不成立：**N4**（榜单页当时没有任何页面类型能承载可分页列表）、**F9**（引用了不存在的日 / 周榜，以及一个收藏榜）、**F12**（让内置 `status` 去做 facet，而它既不是 `indexed` 自定义字段、取值也不是"完本 / 连载"）、**I14**（与本节自己的 ⚠️ 表冲突）、**播客行**（缺少 `<enclosure>` 规则）。v2.2 为这 5 行补了规则（§0.3 第 4、15、20、21 条，行状态改写见第 26 条），其中需要站点配置的两行按 ⚠️ 计。

**逐站型的一句话判据**（判据必须能被检验，所以每条都指向具体规则）：

| 站型 | 曾经卡在哪 | 现在靠什么成立 |
|---|---|---|
| 通用 | 首页混合流、单页取值、混合导航、单一装饰件（feed/sitemap/robots/搜索索引/重定向） | `type='all'`（§6.3）+ 当前条目压栈底（§5.1）+ `channel source='type'/'menu'`（§6.4）+ §7.2.2 的产物清单 |
| 资讯门户 | 专题页不可分页、榜单无列无快照、定时发布不生效 | `relate` 开放给 `{cms:list}`（§6.3）+ `view_count` 列与每小时重建（§2.3/§8.8）+ 时间窗与到点触发（§6.3/§8.8） |
| 企业官网 | 单页取不到字段、导航混排、表单绑定不了内容、多语言零设计 | §5.1 + §2.4/§6.4 + `contentId:self`（§6.5）+ 一站一语言（§11.5） |
| 博客 | 系列/合集不可分页、作者页无实体、列表内高亮当前项 | §6.3 + `author` 内置类型与 `relate='field:authorId'`（§2.1/§6.3）+ `current`/`class`（§5.5） |
| 小说站 | 目录与详情的分页主体冲突、章节页取不到父、榜单 | 详情页承载可分页列表（§4.5/§5.6）+ `of='parent'` 与父链派生字段（§3.7 裁定三/§2.2）+ `view_count` |
| 行业站 | 筛选落地页与筛选链接、产品关联不可分页、六类型导航 | §7.4（含 `channel source='facet'`）+ §6.3 + `channel source='type'`（§6.4） |

**扩展站型（"等等等等"那一类）**：判据是"这个站型需要的页面形态，能不能由 §7.2 的 11 种页面类型 + §6 的 14 个标签 + §2 的内容模型拼出来"。
下表逐站型给出**它靠哪些现有机制成立**——如果某一行写不出来，就说明本文件还有缺口：

| 站型 | 它特有的页面形态 | 靠什么覆盖 | 状态 |
|---|---|---|---|
| 文档知识库 | 多级文档树（章 → 节 → 篇）、正文锚点、全文搜索、版本迭代 | `TREE` 类型 + `parentId`/`ancestors`/`parentUrl`（§2.2）+ 详情页承载可分页目录（§5.6）+ `toc` 锚点（§5.2.5）+ 搜索索引（§7.5） | ✅ |
| 图库 / 摄影站 | 图集详情、尺寸与懒加载、原图下载、边框瀑布流 | `IMAGES` + `{cms:foreach}` + 派生尺寸与 `width`/`height`（§7.7）+ `FILE` 下载 + 列表分页 | ✅ |
| 下载站 | 分类 + 详情 + 多文件 + 版本与校验值 | `FILE`/`FILES`（`ext`/`filesize`/`mime`）+ `JSON` 字段存版本表 + 详情页承载列表 | ✅ |
| 招聘站 | 职位列表 / 详情、按城市与部门筛选、附件投递 | `job` 类型 + `facets`（§7.4）+ 表单附件上传（§9.5） | ✅ |
| 单页官网（One-pager） | 一页滚动 + 锚点导航 + 联系表单 | `STATIC` / `SINGLE`（§7.2.1）+ 模板内手写锚点 + `{cms:form}` | ✅ |
| 清单型电商展示（不结算） | 产品列表 / 详情、分类、询价或外链下单 | `product` 类型 + `facets` + `{cms:form}`（询价）或 **`TEXT` 字段存外链** / `cms_menu_item kind='url'`（外链下单）——**没有 `URL` 字段类型**（§2.2 只有 19 种），v2.1 这里引用了一个不存在的类型 | ✅ |
| 活动 / 临时落地页 | 单页 + 表单 + 到期自动下线 | `SINGLE` + `publish_time` 时间窗（§6.3）+ 到期后写 `cms_redirect` 出 301（§7.6） | ✅ |
| 播客 / 音视频站 | 节目列表 + 播放器 + 订阅 feed | `FILE`（音频/视频）+ `IMAGE` 封面 + `INT duration`（§2.2）+ `feed.types` 指向该类型 + **`<enclosure>` 取 `FILE` 字段**（§7.6，v2.2 补：没有它播客订阅不成立）+ 可选 `feed.includeBody` | ✅ |
| 多语言外贸站 | 多语言站点 + `hreflang` + 询价 | 一站一语言（§11.5）+ `site.lang`/`site.alternates`（§7.1.4）+ 表单（§9.5） | ✅ |
| 政府 / 事业单位站 | 信息公开目录、政策文件下载、无障碍 | 文档知识库 + 下载站 + `SINGLE` 单页三种形态的组合 | ✅ |
| 集团多品牌站 | 一个后台、多个品牌站点 | 多站点（`cms_site`，一站一域名）+ 主题包复用（§8.9）+ 独立发布 | ✅ |
| 只读社区 / 问答展示 | 帖子列表 / 详情 + 评论展示 + 按回复数排序 | 内容类型 + 评论快照（§9.6）+ `commentCount`（§2.2 派生字段、可 `orderby`，v2.2 补：v2.1 没有任何"评论数"字段，列表页展示不了回复数）；**发帖与登录属 D 类，不在本文件范围**（§1.2） | ✅（只读部分） |

**"可表达但需要配置"的项（⚠️ 口径）**：

| 项 | 需要什么配置 | 不配的后果 | 在哪 |
|---|---|---|---|
| 筛选落地页（G11、I2、I11、I14、F12） | 站点选项 `page.facet=1` + 类型 `options.facets` 列出参与筛选的字段 | 不出筛选页（产品仍可在列表页用 `where` 筛，但没有可被收录的落地页） | §7.4 |
| 归档页（G9） | 站点选项 `page.archive=1` | 不出年月归档页（资讯站建议开，官网不必） | §2.7 |
| 大站搜索（G12、B8、F11） | `search.staticMax`（默认 5 万条）以内用前端索引；超出自动切 `api` 模式 | 超出阈值时若不切，前端匹配会变慢（不是不能搜） | §7.5 |
| 深回滚（§8.7） | 站点选项 `publish.keepReleases ≥ 2`（占磁盘） | 只能回滚到上一个批次 | §8.7 |
| 多语言（W9） | 为每个语言建一个站点并配对 `RELATION` | 只有一个语言 | §11.5 |
| 榜单实时性（N4、F9） | `publish.rankCron`（默认每小时） | 榜单最多滞后 1 小时（**快照语义本身不可消除**） | §8.8 |
| 媒体出口（§11.2） | nginx 加 `location /uploads/` | 静态页里的图片 404 —— **这一条不配就是事故，不是取舍** | §11.2 |

### 14.8 v2 未达标项 → v2.1 落点对照表（**逐条可回查**）

> 下表左半是 v2 的 12 项 (b) 类缺口（"补章节也修不好"的规格缺陷）与 2 项 (a) 类缺口（P4/P5），
> 右半是 v2.1 把它们改到了哪里。**这张表是"§14.7 从 19 ✅ 变成 78 ✅"的证据链**——
> 每一行都能在正文里找到对应规则，且有 §0.2 的变更留痕。

| 缺口 | v2 的现象 | v2.1 的落点 |
|---|---|---|
| **P1** | 跨类型混合列表写不出来；`channel='all'` 参数不存在 | `type='all'` + 走 `cms_content` 公共列（§6.3、§2.5）；参数名更正（§0.2 第 6 条） |
| **P2** | 单页模板取不到自身字段 | **当前条目恒压匿名栈底**（§5.1 第（1）条），单页直接 `[field:title/]`；`{cms:detail}` 在单页上允许但不是分页主体（§6.3、§4.5） |
| **P3** | 作者与计数都没有实体 | `author` 内置类型 + `author_id`/`author_name` + `view_count` 列（§2.1/§2.3/§2.2）；静态榜单 = T 时刻快照 + 每小时重建（§9.4/§8.8） |
| **P4**（(a) 类） | 筛选 / 搜索 / 动态层缺章节 | §7.4 筛选落地页（含 `channel source='facet'`）、§7.5 搜索索引、§9 动态层全节 |
| **P5**（(a) 类） | 派生字段缺、清洗白名单未定 | `toc`/`wordCount` 等派生字段（§2.2/§5.2.5）；富文本白名单写进契约并保留 `class`/`data-*`（§5.2.4）；媒体派生尺寸（§7.7） |
| **P6** | 关系型 / 手工清单不可分页 | `relate` 开放给 `{cms:list}`（§6.3），`{cms:foreach}` 明确不分页（§3.5 裁定五） |
| **P7** | 定时发布没有时间窗、没有到点重建 | `status='PUBLISHED'` 隐含 `publish_time <= now()`（§6.3）+ 每分钟触发与 24 小时回看（§8.8） |
| **P8** | 详情页表单无法携带当前内容 id | `hidden` 的 5 个引擎关键字（`contentId:self` 等，§6.5），**不给参数插值开口子** |
| **P9** | 多语言零设计 | 一站一语言（§11.5）+ `site.lang`/`site.alternates`（§2.7）+ `RELATION` 配对 + "不做单站点内多语言"写进边界（§1.3） |
| **P10** | 内容查询拿不到 `class`/`current` | 内容查询的迭代项也发 `current`/`class`，口径写死成一张表（§5.5、§6.3） |
| **P11** | 小说目录的分页主体冲突 | 分页主体规则改为"至多 1 个 + 两条死限"（§4.5），**详情页可以承载可分页列表**（§5.6、§6.3），并取消了草案里的 `SUBLIST` 页面类型（§0.2 第 15 条） |
| **P12** | 子内容页取不到父；`of='self'` 两处定义冲突 | "锚定项"定义（§3.7 裁定三）：`of='self'` / `of='parent'` + 父链派生字段（§2.2）+ 面包屑覆盖内容父链（§6.4） |
| **C2**（混合导航 / 当前项） | 官网主导航、页脚、类型导航无法表达 | `cms_menu` + `channel source='type'/'menu'`（§2.4/§6.4）；当前项一律由引擎算（§5.5） |
| **C4**（幽灵页 / 陈旧页） | 产物只增不减；内容哈希发现不了"本页应该变" | 输入依赖清单 + 反向索引（§8.4）+ 产物 GC 与场景表（§8.6） |
| **C7**（表单绑定） | 同 P8 | 同 P8（§6.5） |
| **C8**（单页取值） | 同 P2 | 同 P2（§5.1 第（1）条） |
| **A1–A14**（自相矛盾 14 条） | 保留名 4 vs 6、`content` 一词三义、`pubdate` vs `publishTime`、`in` 的分隔符、`{n}` 的四种必需性、`class` 归属、`foreach` 能否迭代查询结果… | 逐条落在 §2.2（保留名与内置/派生字段）、§3.2（保留名 6 个）、§4.4（`defVersion`）、§4.5（数量口径）、§5.1（`item` 作用域与当前条目）、§5.6/§7.1.3（`{n}` 判定函数）、§6.2（`foreach` 参数表）、§6.3（`class` 口径表、`in` 用 `\|`）、§6.5（`hidden` 关键字）；变更留痕见 §0.2 |
| **工程断层**（出口 / 站点识别 / 媒体 / 模板版本化） | 产物没有出口、Host 不解析、`/uploads` 不反代、模板不进版本控制 | §11.1–§11.8 逐条给方案与代价；§12.4 给上线顺序 |

**最后一条判据**：把本文档交给一个**没参与讨论**的模板作者，他应当能只读 §3–§7 就写出一套能发布的主题；
交给一个实现者，他应当能只读 §4、§8、§9 就写出引擎与动态层。**读完后仍需口头补充的部分，就是这份文档还没写完的部分。**

### 14.9 原文存档：v2 当时的 (b) 类缺口清单（**已全部修复，见 §14.8 的落点对照**）

> 这一节保留 v2 的原话与行号（`L###` 是 v2 的行号，v2.1 改写后已不对应），用于回答"当初到底哪里不成立"。
> **不要按这一节实现任何东西**——它的"最小修法"列已经被 §14.8 的落点取代，两者冲突时以 §14.8 与正文为准。

| # | 现象 | 影响行 | 最小修法 | 落在哪一节 |
|---|---|---|---|---|
| **P1** | 跨类型混合列表写不出来：`type` 只能是单个 code（L645）；§0 第 16 条说的 `channel='all'` 在 §6.3 里没有这个参数（那里叫 `category`） | G1、N1、I1 | 查询标签补"不限类型"的表达（`type='all'`）；跨类型"最新"走 `cms_content` 公共列（`site_id`+`status`+`publish_time`），不要绑在索引表分派上（L199）。并修正 §0 第 16 条与 §6.3 的参数名不一致 | §6.3 + §0 |
| **P2** | **单页模板取不到自身字段**：单页没有分页主体（L521），而 `content` 作用域只覆盖"详情页 / 子内容索引页"（L466）；匿名栈只放循环项（L468）。官网的"关于我们/隐私政策"因此无解 | G5、W1 | 二选一，都很小：① 明确 `content` 作用域**也覆盖单页**；② 允许单页模板使用 `{cms:detail}`，并说明它在 `kind='SINGLE'` 时**不是分页主体**、只是取值作用域（此时 §5.4 约束一的数量校验需按 kind 分派） | §5.1 / §5.4 |
| **P3** | 作者与计数都没有实体：`authorName` 只是内置字符串（L162），`cms_content` 无 author 列（L170-179）；`viewCount` 被承诺"任何类型都有"（L160）却无列 | G9、N4、B4、F9 | ① `cms_content` 补 `author_id`/`author_name`，与 `cms_article` 对齐；② 补 `view_count` 列（或明确计数一律交动态层，并写清"静态榜单是 T 时刻快照 + 重建触发策略"） | §2.3 + §2.2 |
| **P6** | 关系型 / 手工清单不可分页：分页主体只有 `list`/`detail`（L584），`foreach` 不分页（L583），`relate` 只给 `query`（L650）而 `query` 不分页（L679） | N3、B3、I10 | 把 `relate` 开放给 `{cms:list}`（零新增语法、零新增标签）。这样专题页、系列页、关联案例页立刻可分页 | §6.3 |
| **P7** | 定时发布缺两件事：查询侧没有 `publish_time <= now` 的时间窗；静态站需要"到点重建" | N8 | ① `status='PUBLISHED'` 隐含 `publish_time <= now`，写进 §6.3；② 重建触发写进 §8 | §6.3 + §8 |
| **P8** | 详情页表单无法携带当前内容 id：`hidden` 声称可带"内容 id"（L788），但参数值不做插值（L257） | W6、I7 | 给 `hidden` 补一个引擎可识别的关键字（如 `contentId:self`）。**不要**为表单破例允许插值——L257 的规定是对的 | §6.5 |
| **P9** | 多语言零设计：全文只在 L9 出现一次，内容模型与 `site` 作用域都没有语言维度 | W9 | 采纳"一个语言 = 一个站点"：复用 `site_id` 隔离 + 独立 `root_dir` + 独立发布，语言间用 `RELATION` 配对、`hreflang` 由模板输出；并把"不做单站点内多语言"写进 §1.3 边界 | §1.3 + §7 |
| **P10** | 内容查询拿不到 `class`/`current`：引擎只给导航类迭代项发（L543），内容查询明确返回空串（L669），而语言里没有比较运算符（L359） | B5 | 与 P1 同一手法：把"当前项"继续交给引擎算。给内容查询的迭代项也提供 `current`(BOOL) 与 `class`（对比当前页内容 id 即可，引擎知道这个 id） | §5.5 + §6.3 |
| **P11** | 小说目录的分页主体冲突：§5.6 给的方案 `{cms:list type='chapter' of='self'}`（L565）是分页主体（L584），而书详情模板必须恰有一个 `{cms:detail}`（L711）、一个模板只能一个分页主体（L517/L522） | F3 | 明确"子内容索引页"是**独立页面类型**（自己的 URL 与模板），书详情页只放简介与"查看目录"链接；并把该页面类型写进 §7.2。同时统一 §5.4 约束一的数量口径（"恰 1" 还是 "0 或 1"，见 §4.5 第 7 条） | §5.4 + §7.2 |
| **P12** | 子内容页取不到父：`of='self'` 两处定义不同（L340 vs L649）且析取无优先级；详情页 `channel` 是主分类不是父内容（L462）；详情页顶层无迭代项（L468）；`parent_id` 是列（L173）却不在内置（L162）也不在派生（L160）字段清单里 | F7、F8 | ① `of='self'` 收紧为**只指"栈顶迭代项的 id"**，"当前浏览位置"交回 `channel`；② 补 `of='parent'`；③ 补父链派生字段 `parentId`/`parentTitle`/`parentUrl`/`parentTypeCode`/`ancestors`。注意 L340 举例的"章节页取兄弟篇目"正是 L336 声明不提供的向上作用域，该例必须改写 | §3.7 裁定三 + §5.1 + §2.2 |

**P4 / P5 属 (a) 类**（缺章节即可解决），当年的定义是：P4 = §7.2 页面类型 + §7.4 筛选落地页 + §7.5 搜索索引 + §9 动态层；P5 = 派生字段与清洗白名单（图片派生尺寸 §7.7、正文 TOC、富文本白名单需保留代码高亮所需的 `class`/`data-*`）。**两者在 v2.1 中的落点见 §14.8 的 P4 / P5 两行。**

### 14.10 v2.2 复核发现 → 落点对照表（**逐条可回查**）

> 与 §14.8 同构：左半是四路复核查出的缺口类别，右半是 v2.2 把它们改到了哪里。**这张表是"§14.7 从 78 ✅ 变成 75 ✅ + 3 ⚠️"的证据链**——每一行都能在正文里找到规则，且在 §0.3 有留痕。

| 缺口类别 | 现象（复核证据） | v2.2 落点 |
|---|---|---|
| **封闭清单破口** | 站点选项表缺 16 个正文在用的键；`{facetPath}` 不在占位符白名单；`facetCardinality` 与 `facets.cardinality` 两种拼写；`site.alternates` 不在可迭代清单；`channel` 一个 key 都没定义 | §2.7（47 行）、§7.1.1（14 个占位符）、§7.4、§3.5/§6.2、§5.1 第（4）条 |
| **自相矛盾** | `param.formError` vs `form_error`；`content.hasPrev` vs `item.hasPrev`；`hidden` 的 key 是否校验（E2008 与 §6.5 冲突）；`E3011` 与 `E2010`/`E2011` 重复；`{n}` 必需性的四套说法；`page` 作用域是否存在 | §6.4、§6.5 + §4.5#12、§10.2、§7.1.3、§5.1、§5.6 |
| **重复段落** | §5.1 结尾同一句出现两次；§5.6 的 `pageType` / `paginationKind` 说明写了两遍 | §5.1、§5.6（已删） |
| **判定函数缺失**（不同实现结果不同） | HOME 第 2..N 页 URL、LIST 的 pattern 来源、`paginate_body` 无 `{n}`、formatter 调用语法、默认排序缺 tie-break、无前缀字段查找范围、`page.canonical`、`hidden` 语义、缓存 key 与 `astVersion` 输入、窄批次 GC、失败页的 manifest、计数时间窗、分类页空集是否出页、可选占位符缺值、`type='all'` 的字段集合 | §7.1.3、§7.2.1、§4.5、§2.2、§6.3、§5.1、§5.6、§4.4、§8.3、§8.5、§6.4、§7.3 |
| **承诺了却取不到** | `ratingAvg` / `ratingCount` 不在派生字段表（必报 E1004）；评论快照依赖不在 §8.4；`site.alternates` 不可迭代；`wordCount` 不在 `orderby` 白名单；`commentCount` 全无 | §2.2、§8.4、§3.5/§6.2、§2.2、§2.2/§2.3/§9.6 |
| **站型缺口**（复核新增） | 榜单 / 排序页无处安放（静态站没有查询串）；专题页人工挑稿顺序无解；日 / 周榜无数据源；活动页到期不掉线；多条件筛选的叠加链接；对比表取不到第 N 项；播客 `<enclosure>`；评论数；整书导出 | §7.2.1（站点声明的列表页）、§6.3（`relationOrder`）、§2.3/§9.4、§2.3/§8.8、§7.4（`urlWith`）、§6.3（`rows`）、§7.6、§2.2/§9.6、§13.4（改为不做 + `FILE` 替代） |
| **动态层契约缺口** | `/api/public/captcha` 有调用无表项；`subscribe` 零契约；comments / ratings / site 响应体未定义；`siteId` 优先级两处不一致；分片上传参数未定义 | §9.2（表 + 响应体段）、§9.1 与 §11.1（唯一权威）、§9.5 |
| **与工程现实不符** | `cms_site` 无 `site_id`；本地端口 8080；`/preview/**` 与 `SecurityConfig` / JWT / 拦截器 / `SiteContext` 四处不兼容；`siteDir`/`siteRoot`/`SITE_SUB_DIRS` 拿不到；无图像库（WebP 编不了）；漏 `cms_media` 迁移；明细表缺主键与逻辑删除；无 `@EnableScheduling` 且后台线程取不到 `SiteContext`；删站点校验漏新表；multipart 上限未落数；旧公开端点的写副作用；无 CORS 配置 | §1.4、§11.2、§11.4、§11.7、§12.1（迁移 12–14）、§12.2（期 0）、§12.4（第 12–14 条）、§11.3（同源要求） |
| **仓库外事实** | nginx / Jenkins / `/www/wwwroot` 在仓库内没有任何可核对文件 | §11.3 的标注 + §12.4 第 7 条（运维逐条确认） |
| **覆盖率结论** | §14.7 的 78/78 含 5 行不成立的自证 | §14.7 的修订说明 + 本表 |
