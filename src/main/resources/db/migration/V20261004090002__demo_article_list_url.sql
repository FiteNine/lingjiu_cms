-- =============================================================================
-- 演示站点：article 的类型列表页换一个 URL，避开与 news 分类索引页的冲突
-- 契约：docs/static-publish.md §7.1.3（URL 冲突检测，E4004）、§7.2.1（LIST 的两种来源）
--
-- 为什么必须换：§7.2.1 把 LIST 分成两种来源——
--   · 类型列表页 用类型的 list_url_pattern；
--   · 分类索引页 用站点选项 url.list（默认 /{categoryPath}/page-{n}/）。
-- 原种子里 article.list_url_pattern 写的是 /news/page-{n}/，而 demo 站有一个 slug 为 news
-- 的分类，它的索引页第 1 页算出正好也是 /news/ → 两个来源映射到同一个产物文件。
-- 计划期据此报错（这是对的，宁可不发布也不发布一个被覆盖的页面）：
--     [E4004] URL 冲突：news/index.html
--        LIST（来源 type:article，URL /news/）与 LIST（来源 category:news，URL /news/）
--
-- 选择"改类型列表页"而不是"改分类索引页"：/news/ 是访客对"新闻"这个栏目的直觉 URL，
-- 而 /article/ 作为"文章归档"的入口名同样清楚（类似 /book/ /product/ 的形态）。
--
-- 时间戳 20261004090002 > 20261004090001（Flyway 未开 out-of-order，版本必须递增）。
-- =============================================================================

update cms_content_type t
   set list_url_pattern = '/article/page-{n}/'
  from cms_site s
 where s.id = t.site_id and s.code = 'demo' and s.deleted = 0
   and t.deleted = 0
   and t.code = 'article'
   and t.list_url_pattern = '/news/page-{n}/';
