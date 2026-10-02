-- =============================================================================
-- 「站点目录」新建 / 删除的按钮权限
--
-- 站点目录页（/sites/dir）原来只能浏览与保存文本文件（421 cms:site:file:edit），
-- 本次增加新建（文件与文件夹）和删除（文件与空文件夹），各配一个权限点：
-- 后端见 module/cms/controller/SiteFileController，前端按钮用 v-permission 挂同一权限码。
-- =============================================================================

insert into sys_menu (id, parent_id, name, path, component, icon, perms, type, sort) values
    (422, 42, '新建文件/文件夹', null, null, null, 'cms:site:file:add',    'BUTTON', 2),
    (423, 42, '删除文件/文件夹', null, null, null, 'cms:site:file:delete', 'BUTTON', 3);

-- 与站点目录其余权限一致：只授权给超级管理员，其他角色到「角色管理」里按需勾选
insert into sys_role_menu (role_id, menu_id)
select 1, id from sys_menu where id in (422, 423);

select setval('sys_menu_id_seq', (select max(id) from sys_menu));
