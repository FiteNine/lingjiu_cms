-- =============================================================================
-- 演示站点：补齐内容类型 + 修正 relatedProducts 的可筛选标记
-- 契约：docs/static-publish.md §2.1、§2.2、§6.3
--
-- 为什么需要这个文件（两个都是"已经发生的状态差"，不是新的语义）：
--   1. V20261002090011__cms_site_seed.sql 只给它执行时**已存在**的站点播种内置类型；
--      演示站点是它之后才建的，于是 demo 少了 article / single / author 三个内置类型，
--      而 V20261003090001 里的补种是后加的（该迁移在本机已经执行过，不会再跑）。
--      没有 article 类型时，模板里的 {cms:query type='article'} 会直接编译失败（E2006）。
--   2. product / article 的 relatedProducts（RELATION）要能被 relate='field:<code>' 使用，
--      而该参数要求字段 indexed=1（§6.3）。原种子里是 0，于是每一个产品详情页都报 E2007。
--
-- 两条都写成幂等：insert ... where not exists、update 只改目标列。
-- 时间戳 20261004090001 > 20261003090001（Flyway 未开 out-of-order，版本必须递增）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 演示站点补齐内置内容类型（article / single / author）
--    取值与 V20261002090011 / V20261003090001 完全一致，站点内 code 唯一。
-- ---------------------------------------------------------------------------
insert into cms_content_type
    (site_id, code, name, kind, hierarchical, detail_url_pattern, list_url_pattern,
     per_page, status, sort)
select s.id, v.code, v.name, v.kind, v.hierarchical,
       v.detail_url_pattern, v.list_url_pattern, v.per_page, 1, v.sort
  from cms_site s
  cross join (values
      ('article', '文章', 'CONTENT', 0, '/{categoryPath}/{slug}.html',     '/news/page-{n}/',   6,  1),
      ('single',  '单页', 'SINGLE',  0, '/{slug}/',                        null,                20, 2),
      ('author',  '作者', 'CONTENT', 0, '/author/{slug}.html',             '/author/page-{n}/', 12, 3)
  ) as v(code, name, kind, hierarchical, detail_url_pattern, list_url_pattern, per_page, sort)
 where s.code = 'demo' and s.deleted = 0
   and not exists (select 1 from cms_content_type t
                    where t.site_id = s.id and t.code = v.code and t.deleted = 0);

-- 补上这三个类型在 §2.1 里需要的排序与模板设置（内置默认值与其余类型保持一致）
update cms_content_type t
   set sort_field       = coalesce(nullif(t.sort_field, ''), 'publishTime'),
       sort_order       = coalesce(nullif(t.sort_order, ''), 'desc'),
       detail_template  = coalesce(t.detail_template, case t.code
                                     when 'article' then 'article_detail.html'
                                     when 'author'  then 'author_detail.html'
                                     else null end),
       list_template    = coalesce(t.list_template, case t.code
                                     when 'article' then 'article_list.html'
                                     else null end)
  from cms_site s
 where s.id = t.site_id and s.code = 'demo' and s.deleted = 0 and t.deleted = 0
   and t.code in ('article', 'single', 'author');

-- ---------------------------------------------------------------------------
-- 2. relatedProducts 必须可筛选：relate='field:<code>' 只接受 indexed=1 的字段（§6.3）
-- ---------------------------------------------------------------------------
update cms_field f
   set indexed = 1
  from cms_site s
 where s.id = f.site_id and s.code = 'demo' and s.deleted = 0
   and f.deleted = 0
   and f.code = 'relatedProducts'
   and f.field_type = 'RELATION';
