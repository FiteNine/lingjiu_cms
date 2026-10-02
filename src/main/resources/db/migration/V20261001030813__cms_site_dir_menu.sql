-- =============================================================================
-- 「站点」一级菜单 + 「站点目录」二级菜单
--
-- 1. 原来的「站点管理」是顶级菜单（id 40）。这里把它改成 DIR「站点」，站点管理另开一行
--    挂到它下面，原按钮（401/402/403）的父节点一起挪过去，角色授权不受影响。
-- 2. 新增「站点目录」菜单与保存按钮权限：在页面上浏览当前站点的网站文件目录，
--    文本文件可编辑保存、图片可预览，见 module/cms/service/SiteFileService。
--
-- 前端路由与左侧菜单是静态定义的（见 admin-ui/src/router、layout），菜单表主要管权限与授权，
-- 两边要保持一致：/sites/manage 站点管理、/sites/dir 站点目录。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 菜单：40 变目录，41 / 42 / 421 是本次新增
-- ---------------------------------------------------------------------------
update sys_menu
   set name = '站点', path = '/sites', component = null, icon = 'Monitor', perms = null, type = 'DIR'
 where id = 40;

insert into sys_menu (id, parent_id, name, path, component, icon, perms, type, sort) values
    (41,  40, '站点管理', 'manage', 'sites/index',     'Monitor', 'cms:site:list',      'MENU',   1),
    (42,  40, '站点目录', 'dir',    'sites/dir/index', 'Folder',  'cms:site:file:list', 'MENU',   2),
    (421, 42, '保存文件', null,     null,              null,      'cms:site:file:edit', 'BUTTON', 1);

-- 站点新增/编辑/删除三个按钮跟着「站点管理」走到 41 下面
update sys_menu set parent_id = 41 where id in (401, 402, 403);

-- 站点相关的菜单只授权给超级管理员（与「站点管理」当初的授权范围一致）
insert into sys_role_menu (role_id, menu_id)
select 1, id from sys_menu where id in (41, 42, 421);

select setval('sys_menu_id_seq', (select max(id) from sys_menu));
