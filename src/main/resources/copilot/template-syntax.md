# 模板语法速查（全站 agent 的 cms_template_syntax 工具返回值）

> 本文件由 `docs/static-publish.md` 的 §3「语法规格」与 §6「标签清单与参数表」切片而来。
> **语法变更时必须同步维护本文件**，否则模型会照着过时规范写模板。

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

