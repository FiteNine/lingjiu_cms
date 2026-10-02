-- =============================================================================
-- 后台菜单与按钮权限：内容类型、发布中心、菜单管理、表单、评论、主题
-- 契约：docs/static-publish.md §12.1 第 10 条
--
-- 写法沿用 V20261001030813__cms_site_dir_menu.sql：显式指定 id、path 写相对段、
-- component 写前端页面路径、每个动作一个 BUTTON 行、最后 setval 推一下序列。
-- 与前端静态路由保持一致：内容管理下 types / menus，站点下 publish / options /
-- forms / comments / themes（前端路由表在 admin-ui，本仓库内无法核对）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 内容管理（10）下新增：内容类型、导航菜单
-- ---------------------------------------------------------------------------
insert into sys_menu (id, parent_id, name, path, component, icon, perms, type, sort) values
    (15,  10, '内容类型', 'types', 'cms/type/index', 'Files',     'cms:type:list',        'MENU',   5),
    (151, 15, '类型新增', null,    null,             null,        'cms:type:add',         'BUTTON', 1),
    (152, 15, '类型编辑', null,    null,             null,        'cms:type:edit',        'BUTTON', 2),
    (153, 15, '类型删除', null,    null,             null,        'cms:type:delete',      'BUTTON', 3),
    (154, 15, '字段新增', null,    null,             null,        'cms:field:add',        'BUTTON', 4),
    (155, 15, '字段编辑', null,    null,             null,        'cms:field:edit',       'BUTTON', 5),
    (156, 15, '字段删除', null,    null,             null,        'cms:field:delete',     'BUTTON', 6),
    (16,  10, '导航菜单', 'menus', 'cms/menu/index', 'Guide',     'cms:menu:list',        'MENU',   6),
    (161, 16, '菜单新增', null,    null,             null,        'cms:menu:add',         'BUTTON', 1),
    (162, 16, '菜单编辑', null,    null,             null,        'cms:menu:edit',        'BUTTON', 2),
    (163, 16, '菜单删除', null,    null,             null,        'cms:menu:delete',      'BUTTON', 3);

-- ---------------------------------------------------------------------------
-- 站点（40）下新增：发布中心、发布选项、表单、评论、主题
-- ---------------------------------------------------------------------------
insert into sys_menu (id, parent_id, name, path, component, icon, perms, type, sort) values
    (43,  40, '发布中心', 'publish',  'sites/publish/index',  'Upload',      'cms:publish:list',         'MENU',   3),
    (431, 43, '立即发布', null,       null,                   null,          'cms:publish:run',          'BUTTON', 1),
    (432, 43, '取消批次', null,       null,                   null,          'cms:publish:cancel',       'BUTTON', 2),
    (433, 43, '回滚批次', null,       null,                   null,          'cms:publish:rollback',     'BUTTON', 3),
    (434, 43, '下载报告', null,       null,                   null,          'cms:publish:report',       'BUTTON', 4),
    (44,  40, '发布选项', 'options',  'sites/options/index',   'SetUp',       'cms:publish:option:list',  'MENU',   4),
    (441, 44, '保存选项', null,       null,                   null,          'cms:publish:option:edit',  'BUTTON', 1),
    (45,  40, '表单管理', 'forms',    'sites/form/index',     'DocumentChecked', 'cms:form:list',        'MENU',   5),
    (451, 45, '表单新增', null,       null,                   null,          'cms:form:add',             'BUTTON', 1),
    (452, 45, '表单编辑', null,       null,                   null,          'cms:form:edit',            'BUTTON', 2),
    (453, 45, '表单删除', null,       null,                   null,          'cms:form:delete',          'BUTTON', 3),
    (454, 45, '提交记录', null,       null,                   null,          'cms:form:entry:list',      'BUTTON', 4),
    (46,  40, '评论管理', 'comments', 'sites/comment/index',  'ChatLineSquare', 'cms:comment:list',      'MENU',   6),
    (461, 46, '评论审核', null,       null,                   null,          'cms:comment:audit',        'BUTTON', 1),
    (462, 46, '评论删除', null,       null,                   null,          'cms:comment:delete',       'BUTTON', 2),
    (47,  40, '主题管理', 'themes',   'sites/theme/index',    'Brush',       'cms:theme:list',           'MENU',   7),
    (471, 47, '导入主题', null,       null,                   null,          'cms:theme:import',         'BUTTON', 1),
    (472, 47, '导出主题', null,       null,                   null,          'cms:theme:export',         'BUTTON', 2),
    (473, 47, '删除主题', null,       null,                   null,          'cms:theme:delete',         'BUTTON', 3);

-- ---------------------------------------------------------------------------
-- 授权：超级管理员全量；内容编辑（2）只拿内容域（内容类型与字段、导航菜单），
-- 发布中心 / 主题 / 站点级配置由超级管理员在「角色管理」里按需勾选。
-- ---------------------------------------------------------------------------
insert into sys_role_menu (role_id, menu_id)
select 1, id from sys_menu where id in (15, 151, 152, 153, 154, 155, 156,
                                        16, 161, 162, 163,
                                        43, 431, 432, 433, 434,
                                        44, 441,
                                        45, 451, 452, 453, 454,
                                        46, 461, 462,
                                        47, 471, 472, 473);

insert into sys_role_menu (role_id, menu_id)
select 2, id from sys_menu where id in (15, 151, 152, 153, 154, 155, 156,
                                        16, 161, 162, 163);

select setval('sys_menu_id_seq', (select max(id) from sys_menu));
