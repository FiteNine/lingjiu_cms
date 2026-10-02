-- =============================================================================
-- 「发布选项」的删除按钮权限
--
-- 发布选项页原来只能新增与保存（441 cms:publish:option:edit），本次增加删除一条选项
-- （删掉 = 回到引擎默认值）。按内容类型 / 站点目录等模块的同一把尺子，删除单独配一个权限点：
-- 后端见 module/cms/controller/PublishOptionController，前端按钮用 v-permission 挂同一权限码。
-- 菜单 id 沿用 44 号「发布选项」下的编号段（441 已占用），取 442。
-- =============================================================================

insert into sys_menu (id, parent_id, name, path, component, icon, perms, type, sort) values
    (442, 44, '删除选项', null, null, null, 'cms:publish:option:delete', 'BUTTON', 2);

-- 与发布选项其余权限一致：只授权给超级管理员，其他角色到「角色管理」里按需勾选
insert into sys_role_menu (role_id, menu_id)
select 1, id from sys_menu where id in (442);

select setval('sys_menu_id_seq', (select max(id) from sys_menu));
