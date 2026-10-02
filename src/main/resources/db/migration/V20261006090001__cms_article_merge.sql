-- =============================================================================
-- 文章并入通用内容：cms_article → cms_content（type_code='article'）的一次性收敛
--
-- 契约：docs/static-publish.md §2.6 期 3「后台内容管理统一走新表，cms_article 相关代码
--       删除」、§12.1 第 9 条的搬运清单。本文件执行后 cms_article / cms_article_tag
--       不再存在——"只读保留一期"的口径由本次收敛取代（见 §12.1 的同款说明）。
--
-- 搬运映射（§12.1 第 9 条逐项落实）：
--   cms_article.content_format 'HTML' → cms_content.content_format 'RICHTEXT'
--     （'HTML' 是旧文章表的取值，新表只有 RICHTEXT / MARKDOWN 两种，§2.3）
--   cms_article.category_id → cms_content_category（dimension='primary'，§2.4）
--   cms_article_tag         → cms_content_tag
--   view_count / author_id / author_name / top / recommend / publish_time 原样带过去
--   正文预计算列（word_count / content_toc）留空：引擎读不到时自己算（§5.5、
--   DbContentProvider 的 plainLength 兜底），不在迁移里重写一套分词逻辑
--
-- 版本号说明：现有最后一个迁移是 V20261005090003，Flyway 未开 out-of-order，
--   新版本号必须大于它，因此取 20261006090001（沿用既有文件 09:00:0X 的排布）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 0. 兜底：有存量文章、却没有 article 内容类型的站点，补上内置类型
--
-- 没有它，第 1 步搬过去的行会指向一个不存在的 type_code，引擎按 §4.5 直接报 E2006。
-- 取值与 V20261002090011__cms_site_seed.sql 的内置 article 类型逐列一致。
-- ---------------------------------------------------------------------------
insert into cms_content_type (site_id, code, name, kind, hierarchical,
                              detail_url_pattern, list_url_pattern, per_page, status, sort)
select distinct a.site_id, 'article', '文章', 'CONTENT', 0,
       '/{categoryPath}/{slug}.html', '/{typeCode}/page-{n}/', 20, 1, 1
  from cms_article a
 where a.deleted = 0
   and not exists (select 1 from cms_content_type t
                    where t.site_id = a.site_id and t.code = 'article' and t.deleted = 0);

-- ---------------------------------------------------------------------------
-- 1. 正文与公共列
-- ---------------------------------------------------------------------------
insert into cms_content (site_id, type_code, parent_id, slug, title, summary, cover, status,
                         sort, top, recommend, publish_time, author_id, author_name, view_count,
                         content_format, content, word_count,
                         create_by, create_time, update_by, update_time, deleted)
select a.site_id, 'article', 0, a.slug, a.title, a.summary, a.cover, a.status,
       0, a.top, a.recommend, a.publish_time, a.author_id, a.author_name, a.view_count,
       case when upper(a.content_format) = 'MARKDOWN' then 'MARKDOWN' else 'RICHTEXT' end,
       a.content, 0,
       a.create_by, a.create_time, a.update_by, a.update_time, 0
  from cms_article a
 where a.deleted = 0
   -- slug 允许为空，用 is not distinct from 才能让"空 slug 对空 slug"也算已存在
   and not exists (select 1 from cms_content c
                    where c.site_id = a.site_id and c.type_code = 'article'
                      and c.slug is not distinct from a.slug and c.deleted = 0);

-- ---------------------------------------------------------------------------
-- 2. 分类关联：旧表一个 category_id，落成一条 primary（§2.4 主分类恰一个）
--
-- 与分类表 join 两个目的：分类已被删除时不留悬空关联；分类属别的站点时不建跨站关联。
-- ---------------------------------------------------------------------------
insert into cms_content_category (content_id, category_id, dimension, deleted)
select c.id, a.category_id, 'primary', 0
  from cms_article a
  join cms_category cat on cat.id = a.category_id
                       and cat.site_id = a.site_id
                       and cat.deleted = 0
  join cms_content c on c.site_id = a.site_id and c.type_code = 'article'
                    and c.slug is not distinct from a.slug and c.deleted = 0
 where a.deleted = 0
   and not exists (select 1 from cms_content_category cc
                    where cc.content_id = c.id and cc.dimension = 'primary' and cc.deleted = 0);

-- ---------------------------------------------------------------------------
-- 3. 标签关联（cms_article_tag 无逻辑删除列，这里是全量关系）
-- ---------------------------------------------------------------------------
insert into cms_content_tag (content_id, tag_id, deleted)
select c.id, at.tag_id, 0
  from cms_article_tag at
  join cms_article a on a.id = at.article_id and a.deleted = 0
  join cms_tag t on t.id = at.tag_id and t.site_id = a.site_id and t.deleted = 0
  join cms_content c on c.site_id = a.site_id and c.type_code = 'article'
                    and c.slug is not distinct from a.slug and c.deleted = 0
 where not exists (select 1 from cms_content_tag ct
                    where ct.content_id = c.id and ct.tag_id = at.tag_id and ct.deleted = 0);

-- ---------------------------------------------------------------------------
-- 4. 删掉「文章管理」菜单与 cms:article:* 全部权限位
--
-- 菜单行本身的 perms 就是 cms:article:list，因此一个 like 条件同时覆盖菜单与 4 个按钮；
-- 先删授权行（sys_role_menu）再删菜单行，不留孤儿授权。
-- ---------------------------------------------------------------------------
delete from sys_role_menu
 where menu_id in (select id from sys_menu where perms like 'cms:article:%');

delete from sys_menu
 where perms like 'cms:article:%';

-- ---------------------------------------------------------------------------
-- 5. 一级菜单排序：站点提到内容管理之上，AI 管理落到系统管理之下
--
-- 左侧菜单是前端静态定义的（admin-ui/src/layout），这里同步菜单表，"菜单管理"里的
-- 树序与页面上的侧边栏才不会各说一套。按 path 定位而不是写死 id。
-- ---------------------------------------------------------------------------
update sys_menu set sort = 1 where parent_id = 0 and type = 'DIR' and path = '/sites'  and deleted = 0;
update sys_menu set sort = 2 where parent_id = 0 and type = 'DIR' and path = '/cms'    and deleted = 0;
update sys_menu set sort = 3 where parent_id = 0 and type = 'DIR' and path = '/system' and deleted = 0;
update sys_menu set sort = 4 where parent_id = 0 and type = 'DIR' and path = '/ai'     and deleted = 0;

-- ---------------------------------------------------------------------------
-- 6. 清理遗留字典：文章状态 → 内容状态；内容格式的取值与 cms_content 对齐
--
-- 字典只是参考数据（无代码引用），但"文章状态"与 'HTML' 都是收敛后不成立的旧口径。
-- ---------------------------------------------------------------------------
update sys_dict_type set code = 'content_status', name = '内容状态'
 where code = 'article_status' and deleted = 0;

update sys_dict_item set value = 'RICHTEXT'
 where type_id = (select id from sys_dict_type where code = 'content_format' and deleted = 0)
   and value = 'HTML' and deleted = 0;

-- ---------------------------------------------------------------------------
-- 7. 删除遗留表（数据已在第 1–3 步搬完）
-- ---------------------------------------------------------------------------
drop table if exists cms_article_tag;
drop table if exists cms_article;
