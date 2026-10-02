-- =============================================================================
-- 「统计」接口的查看权限（cms:stats:list）
--
-- 背景：/api/cms/stats 原来只要求登录，任何账号都能读到内容/分类/标签/媒体
-- 统计与全站用户数（见 docs/code-review/20261003-backend-full-review.md M-5）。
-- 按其它 CMS 模块的同一把尺子补一个权限点：后端 StatsController 用
-- @PreAuthorize("hasAuthority('cms:stats:list')")，前端仪表盘据此决定是否请求。
--
-- 写法沿用 V20261005090002__cms_content_menu.sql：不写死 id、
-- insert ... select ... where not exists 防重、只授权超级管理员（role_id = 1）、
-- 最后 setval 推一下序列；重复执行不会插重。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 按钮权限：挂在 path='/dashboard' 的「仪表盘」MENU 行下
-- （V2__seed.sql 中该行 perms 为 null，所以用 path 定位而不是权限码）
-- ---------------------------------------------------------------------------
insert into sys_menu (parent_id, name, perms, type, sort)
select m.id, '统计查看', 'cms:stats:list', 'BUTTON', 1
  from (select id from sys_menu
         where path = '/dashboard' and type = 'MENU' and deleted = 0
         order by id limit 1) m
 where not exists (select 1 from sys_menu x
                    where x.perms = 'cms:stats:list' and x.deleted = 0);

-- ---------------------------------------------------------------------------
-- 授权：超级管理员拿全量，其他角色到「角色管理」里按需勾选
-- ---------------------------------------------------------------------------
insert into sys_role_menu (role_id, menu_id)
select 1, m.id
  from sys_menu m
 where m.perms = 'cms:stats:list'
   and m.deleted = 0
   and not exists (select 1 from sys_role_menu rm
                    where rm.role_id = 1 and rm.menu_id = m.id);

select setval('sys_menu_id_seq', (select max(id) from sys_menu));
