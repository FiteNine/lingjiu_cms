-- =============================================================================
-- 「内容管理」菜单 + 通用内容（cms_content）的 5 个按钮权限
-- 契约：docs/static-publish.md §12.1 第 10 条、backend/target/research/design-lingjiuw-site.md §7 的 C-3
--
-- 背景：sys_menu 里原本没有任何 cms:content:* 权限位——通用内容在后台是"零入口"
-- （见 backend/target/research/cms-gaps.md §2.4）。这里补上列表 / 新增 / 编辑 / 删除 / 发布
-- 五个动作，并作为「内容管理」（id 由 sys_menu 里 perms='cms:content:list' 那一行定位）的菜单项。
--
-- 写法沿用 V20261002090010__cms_publish_menu.sql：一行一个动作、授权只给超级管理员，
-- 但**不写死 id**：全部 insert ... select ... where not exists，父菜单 id 由子查询取，
-- 最后 setval 推一下序列。重复执行不会插重。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 菜单：内容管理（挂在顶级「内容管理」目录下）
-- ---------------------------------------------------------------------------
insert into sys_menu (parent_id, name, path, component, icon, perms, type, sort)
select p.id, '内容管理', 'contents', 'cms/contents/index', 'Document', 'cms:content:list', 'MENU', 7
  from (select id from sys_menu
         where path = '/cms' and type = 'DIR' and deleted = 0
         order by id limit 1) p
 where not exists (select 1 from sys_menu x
                    where x.parent_id = p.id and x.perms = 'cms:content:list' and x.deleted = 0);

-- ---------------------------------------------------------------------------
-- 按钮权限：新增 / 编辑 / 删除 / 发布，挂在上面那一行菜单下
-- ---------------------------------------------------------------------------
insert into sys_menu (parent_id, name, perms, type, sort)
select m.id, v.name, v.perms, 'BUTTON', v.sort
  from (select id from sys_menu
         where perms = 'cms:content:list' and type = 'MENU' and deleted = 0
         order by id limit 1) m
  cross join (values
       ('内容新增', 'cms:content:add',     1),
       ('内容编辑', 'cms:content:edit',    2),
       ('内容删除', 'cms:content:delete',  3),
       ('内容发布', 'cms:content:publish', 4)
  ) as v(name, perms, sort)
 where not exists (select 1 from sys_menu x
                    where x.perms = v.perms and x.deleted = 0);

-- ---------------------------------------------------------------------------
-- 授权：超级管理员拿全量（与 V20261002090010 对内容类型 / 导航菜单的授权范围一致）
-- ---------------------------------------------------------------------------
insert into sys_role_menu (role_id, menu_id)
select 1, m.id
  from sys_menu m
 where m.perms in ('cms:content:list', 'cms:content:add', 'cms:content:edit',
                   'cms:content:delete', 'cms:content:publish')
   and m.deleted = 0
   and not exists (select 1 from sys_role_menu rm
                    where rm.role_id = 1 and rm.menu_id = m.id);

select setval('sys_menu_id_seq', (select max(id) from sys_menu));
