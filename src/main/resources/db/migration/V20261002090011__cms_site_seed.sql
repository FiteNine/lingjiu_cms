-- =============================================================================
-- 站点播种：每个站点自动获得内置内容类型、默认菜单与默认发布选项
-- 契约：docs/static-publish.md §2.1（内置类型表）、§2.4（默认菜单 main）、
--       §2.7（发布选项 47 行）、§12.1 第 11 条
--
-- 用 insert ... select 遍历 cms_site，因此**已有站点**也被补齐，而不只是新建站点。
-- 此后新建的站点由 SiteBootstrapServiceImpl 用同一份默认值播种（两处值必须一致：
-- Java 侧在 SiteBootstrapServiceImpl 的常量里，本文件里写死同样的字面量）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 内置内容类型（§2.1）：article 资讯 / single 单页 / author 作者
-- detail_url_pattern 按 §7.1.1 的占位符白名单取值：
--   article → /{categoryPath}/{slug}.html   （§2.1 自己的例子）
--   single  → /{slug}/                      （SINGLE 路径通常写死，给 {slug} 也可以）
--   author  → /author/{slug}/               （§2.1 明写作者页 URL 就是它）
-- list_url_pattern 只给 article：single 是单页、author 的页面类型由站点选项
-- page.author 控制。用 {typeCode} 而不是写死 /news/，避免与站点选项 url.list 的
-- /{categoryPath}/page-{n}/ 撞车（§7.1.3 冲突检测会报 E4004）。
-- ---------------------------------------------------------------------------
insert into cms_content_type (site_id, code, name, kind, hierarchical,
                              detail_url_pattern, list_url_pattern, per_page, status, sort)
select s.id, v.code, v.name, v.kind, v.hierarchical,
       v.detail_url_pattern, v.list_url_pattern, v.per_page, 1, v.sort
  from cms_site s
  cross join (values
      ('article', '文章', 'CONTENT', 0, '/{categoryPath}/{slug}.html', '/{typeCode}/page-{n}/', 20, 1),
      ('single',  '单页', 'SINGLE',  0, '/{slug}/',                   null,                    20, 2),
      ('author',  '作者', 'CONTENT', 0, '/author/{slug}/',            null,                    20, 3)
  ) as v(code, name, kind, hierarchical, detail_url_pattern, list_url_pattern, per_page, sort)
 where s.deleted = 0
   and not exists (select 1 from cms_content_type t
                    where t.site_id = s.id and t.code = v.code and t.deleted = 0);

-- ---------------------------------------------------------------------------
-- 默认导航菜单 main（§2.4）。只建菜单容器，不建菜单项——导航项是站点自己的数据。
-- ---------------------------------------------------------------------------
insert into cms_menu (site_id, code, name, status, sort)
select s.id, 'main', '主导航', 1, 1
  from cms_site s
 where s.deleted = 0
   and not exists (select 1 from cms_menu m
                    where m.site_id = s.id and m.code = 'main' and m.deleted = 0);

-- ---------------------------------------------------------------------------
-- 默认发布选项（§2.7 那张 47 行的表，展开为 55 个键）
--
-- 三处口径说明（都与契约的写法一致，只是落成可解析的字面量）：
--   * publish.syncTarget / media.host 的默认是"空"，这里存空串；
--   * publish.threads 的默认是 min(4, CPU)，不是常量，因此也存空串表示
--     "未设置，由引擎按 min(4, CPU) 算"（§8.3）；
--   * publish.rankCron 契约写的是描述性的"每小时"，这里落成等价的 cron 0 * * * *。
--   * pages.static / feed.types / facets.combos / i18n.alternates / seo.noindexTypes
--     存 JSON 字面量（契约表格用的是 JS 对象记法，这里是合法 JSON）。
--
-- 判存条件里没有 deleted：本文件执行时 cms_site_publish_option 刚由迁移
-- 20261002090004 建出来、必然为空（deleted 列要到迁移 20261002090014 才补），
-- 按 (site_id, option_code) 判存与本文件执行时的语义等价。
-- ---------------------------------------------------------------------------
insert into cms_site_publish_option (site_id, option_code, value)
select s.id, v.option_code, v.value
  from cms_site s
  cross join (values
      ('page.category',        '1'),
      ('page.tag',             '1'),
      ('page.taglist',         '1'),
      ('page.archive',         '0'),
      ('page.author',          '1'),
      ('page.facet',           '0'),
      ('page.search',          '1'),
      ('page.feed',            '1'),
      ('page.redirect',        '1'),
      ('neighbor.limit',       '1'),
      ('index.shardSize',      '2000'),
      ('publish.keepReleases', '3'),
      ('publish.syncTarget',   ''),
      ('publish.cron',         '0 3 * * *'),
      ('url.tag',              '/tag/{tagSlug}/'),
      ('url.tags',             '/tags/'),
      ('url.archive',          '/archive/{year}/{month}/'),
      ('url.search',           '/search/'),
      ('url.thanks',           '/thanks/'),
      ('url.facet',            '/f/{facetPath}/'),
      ('pages.static',         '[{"code":"thanks","url":"/thanks/","template":"thanks.html","type":"single"}]'),
      ('facets.combos',        '[]'),
      ('facets.maxPages',      '500'),
      ('facets.cardinality',   '50'),
      ('search.mode',          'static'),
      ('search.staticMax',     '50000'),
      ('search.bodyChars',     '1000'),
      ('sitemap.shardSize',    '10000'),
      ('feed.format',          'rss'),
      ('feed.size',            '20'),
      ('feed.types',           '["article"]'),
      ('seo.paginatedIndex',   '0'),
      ('seo.facetIndex',       '0'),
      ('toc.levels',           'h2,h3'),
      ('reading.speed',        '400'),
      ('i18n.alternates',      '[]'),
      ('media.derive',         '320,768,1280'),
      ('media.host',           ''),
      ('url.home',             '/page-{n}/'),
      ('url.list',             '/{categoryPath}/page-{n}/'),
      ('page.tagMinCount',     '1'),
      ('pager.labels',         '首页,上一页,下一页,末页'),
      ('publish.mode',         'incremental'),
      ('publish.threads',      ''),
      ('publish.pageTimeout',  '10'),
      ('publish.debounce',     '5'),
      ('publish.rankCron',     '0 * * * *'),
      ('publish.preview',      '0'),
      ('publish.strict',       '0'),
      ('publish.expireRedirect', '0'),
      ('comment.moderate',     '1'),
      ('comment.snapshot',     '1'),
      ('comment.snapshotSize', '20'),
      ('seo.noindexTypes',     '[]'),
      ('feed.includeBody',     '0')
  ) as v(option_code, value)
 where s.deleted = 0
   and not exists (select 1 from cms_site_publish_option o
                    where o.site_id = s.id and o.option_code = v.option_code);
