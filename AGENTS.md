# 后端 CMS（lingjiuw-cms）约定

## 数据库迁移：只增不改

迁移文件放在 `src/main/resources/db/migration/`，由 Flyway 在应用启动时按版本号顺序执行。

- **生成迁移时只新建文件，已执行过的文件保持提交时的字节不变。** 需要调整表结构就在新文件里写 `ALTER`，让旧文件原样留着；Flyway 用校验和比对已执行记录，改动会让下一次启动直接报错。
- **文件名：`V<年月日时分秒>__<业务代码>.sql`。** 版本号是创建该文件时的本地时间，14 位，如 `20250603142233`；业务代码用小写下划线标出本次变更所属的业务。例：`V20250603142233__cms_article.sql`、`V20250604091500__sys_menu_button.sql`。
- 少了 `V` 前缀或 `__` 分隔符，Flyway 会跳过该文件：不执行、也不报错。

## SQL：一律写在 XML 里

**Java 文件里不允许出现 SQL。** 要写 SQL 语句就放到 `src/main/resources/mapper/` 下的 XML 映射文件，由 `mybatis-plus.mapper-locations` 扫描加载。

- **禁止**在 Mapper 接口上用 `@Select` / `@Insert` / `@Update` / `@Delete` 注解写 SQL；也禁止用 `Wrapper.last("...")` / `Wrapper.apply("...")` 往 Java 里塞原生 SQL 片段。需要原生 SQL 就在 XML 里加一条语句。
- Mapper 接口只留方法签名和 `@Param`。XML 的 `namespace` 写接口全限定名，语句 `id` 写方法名，这样调用方一行都不用改。
- XML 按模块分目录、与接口同名：`mapper/system/SysRoleMapper.xml`、`mapper/cms/CmsArticleMapper.xml`。
- `resultType` 写全限定名；结果是 Mapper 内部嵌套类时写 `全限定名$内部类名`，例如 `com.lingjiuw.cms.module.cms.mapper.CmsArticleTagMapper$ArticleTagRow`。
- XML 里 `<`、`>` 必须转义成 `&lt;`、`&gt;`，例如 `m.perms &lt;&gt; ''`。
- **XML 里的语句不会自动带逻辑删除条件。** 实体上有 `@TableLogic deleted` 时，要在 XML 里自己写 `deleted = 0`。
- 不含 SQL 文本的查询照旧：`BaseMapper` 内置方法（`selectById`、`insert`、`selectList(wrapper)` 等）和 `LambdaQueryWrapper` 正常使用。
