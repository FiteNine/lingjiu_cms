-- =============================================================================
-- lingjiuw 站点：修正服务图标 id
--
-- 背景：迁移 20261005090001 里「监控告警」这一项的 icon 写成了 i-monitor，
-- 而主题 _partials/sprite.html 里的 symbol id 是 i-watch（手写原站的图标库也是 i-watch）。
-- 模板写的是 <use href="#[field:icon/]"/>，id 对不上时浏览器不会报错——只是那个图标
-- 静默变成空白，所以必须在数据层改掉。
--
-- 为什么不直接改 20261005090001：那份迁移已经被 Flyway 执行过，改它的字节会让
-- 下一次启动直接 checksum mismatch（backend/AGENTS.md：迁移只增不改）。
--
-- 顺带做一次自检：把本站所有 service 的 icon 与主题 sprite 里的 symbol id 对齐，
-- 对不上的都改回 i-watch（只有这一项，写成通用条件是为了以后新增服务写错 id 时
-- 这条语句仍然能兜住——代价是它只认这一个已知的正确值，不做模糊匹配）。
-- =============================================================================

-- 1. 就事论事：把「监控告警」的 icon 改成 sprite 里真实存在的 i-watch
update cms_content c
   set data        = jsonb_set(c.data, '{icon}', '"i-watch"'),
       update_time = timestamp '2026-10-01 09:00:00'
  from cms_site s
 where s.id = c.site_id
   and s.code = 'lingjiuw' and s.deleted = 0
   and c.type_code = 'service' and c.deleted = 0
   and c.data ->> 'icon' = 'i-monitor';

-- 2. 索引行不涉及 icon（只有 group 进了 cms_content_index），因此不需要动索引表。
--    这里留一条查询式的自检说明：服务图标不在索引表里，改 data 不会让筛选取值失真。
