-- =============================================================================
-- V2: 种子数据
-- 账号：admin / admin123（BCrypt），生产环境请务必修改
-- =============================================================================

-- 用户 / 角色 -------------------------------------------------------------
insert into sys_user (id, username, password, nickname, status, remark)
values (1, 'admin', '$2a$10$qvwgdJ5nTCKEW3LlT0PWTOOH6Tjm.kJHf9fWA91UiqUAkQOg3ta5e', '超级管理员', 1, '内置管理员');

insert into sys_role (id, code, name, sort, status, remark) values
    (1, 'admin',  '超级管理员', 1, 1, '拥有全部权限'),
    (2, 'editor', '内容编辑',   2, 1, '内容管理相关权限');

insert into sys_user_role (user_id, role_id) values (1, 1);

-- 菜单 / 权限 -------------------------------------------------------------
insert into sys_menu (id, parent_id, name, path, component, icon, perms, type, sort) values
    (1,   0,  '仪表盘',   '/dashboard', 'dashboard/index',    'Odometer',  null,                  'MENU',   0),
    (10,  0,  '内容管理', '/cms',       null,                 'Document',  null,                  'DIR',    1),
    (11,  10, '文章管理', 'articles',   'cms/article/index',  'Document',  'cms:article:list',    'MENU',   1),
    (111, 11, '文章新增', null,         null,                 null,        'cms:article:add',     'BUTTON', 1),
    (112, 11, '文章编辑', null,         null,                 null,        'cms:article:edit',    'BUTTON', 2),
    (113, 11, '文章删除', null,         null,                 null,        'cms:article:delete',  'BUTTON', 3),
    (114, 11, '文章发布', null,         null,                 null,        'cms:article:publish', 'BUTTON', 4),
    (12,  10, '分类管理', 'categories', 'cms/category/index', 'Folder',    'cms:category:list',   'MENU',   2),
    (121, 12, '分类新增', null,         null,                 null,        'cms:category:add',    'BUTTON', 1),
    (122, 12, '分类编辑', null,         null,                 null,        'cms:category:edit',   'BUTTON', 2),
    (123, 12, '分类删除', null,         null,                 null,        'cms:category:delete', 'BUTTON', 3),
    (13,  10, '标签管理', 'tags',       'cms/tag/index',      'PriceTag',  'cms:tag:list',        'MENU',   3),
    (131, 13, '标签新增', null,         null,                 null,        'cms:tag:add',         'BUTTON', 1),
    (132, 13, '标签编辑', null,         null,                 null,        'cms:tag:edit',        'BUTTON', 2),
    (133, 13, '标签删除', null,         null,                 null,        'cms:tag:delete',      'BUTTON', 3),
    (14,  10, '媒体库',   'media',      'cms/media/index',    'Picture',   'cms:media:list',      'MENU',   4),
    (141, 14, '上传媒体', null,         null,                 null,        'cms:media:upload',    'BUTTON', 1),
    (142, 14, '删除媒体', null,         null,                 null,        'cms:media:delete',    'BUTTON', 2),
    (20,  0,  '系统管理', '/system',    null,                 'Setting',   null,                  'DIR',    2),
    (21,  20, '用户管理', 'users',      'system/user/index',  'User',      'sys:user:list',       'MENU',   1),
    (211, 21, '用户新增', null,         null,                 null,        'sys:user:add',        'BUTTON', 1),
    (212, 21, '用户编辑', null,         null,                 null,        'sys:user:edit',       'BUTTON', 2),
    (213, 21, '用户删除', null,         null,                 null,        'sys:user:delete',     'BUTTON', 3),
    (214, 21, '重置密码', null,         null,                 null,        'sys:user:reset',      'BUTTON', 4),
    (22,  20, '角色管理', 'roles',      'system/role/index',  'UserFilled','sys:role:list',       'MENU',   2),
    (221, 22, '角色新增', null,         null,                 null,        'sys:role:add',        'BUTTON', 1),
    (222, 22, '角色编辑', null,         null,                 null,        'sys:role:edit',       'BUTTON', 2),
    (223, 22, '角色删除', null,         null,                 null,        'sys:role:delete',     'BUTTON', 3),
    (224, 22, '分配权限', null,         null,                 null,        'sys:role:assign',     'BUTTON', 4),
    (23,  20, '菜单管理', 'menus',      'system/menu/index',  'Menu',      'sys:menu:list',       'MENU',   3),
    (231, 23, '菜单新增', null,         null,                 null,        'sys:menu:add',        'BUTTON', 1),
    (232, 23, '菜单编辑', null,         null,                 null,        'sys:menu:edit',       'BUTTON', 2),
    (233, 23, '菜单删除', null,         null,                 null,        'sys:menu:delete',     'BUTTON', 3),
    (24,  20, '字典管理', 'dict',       'system/dict/index',  'Collection','sys:dict:list',       'MENU',   4),
    (241, 24, '字典新增', null,         null,                 null,        'sys:dict:add',        'BUTTON', 1),
    (242, 24, '字典编辑', null,         null,                 null,        'sys:dict:edit',       'BUTTON', 2),
    (243, 24, '字典删除', null,         null,                 null,        'sys:dict:delete',     'BUTTON', 3),
    (25,  20, '操作日志', 'logs',       'system/log/index',   'Tickets',   'sys:log:list',        'MENU',   5);

-- 超级管理员拥有全部菜单权限
insert into sys_role_menu (role_id, menu_id)
select 1, id from sys_menu;

-- 内容编辑：仅内容管理相关
insert into sys_role_menu (role_id, menu_id)
select 2, id from sys_menu where id between 10 and 19 or id = 1;

-- 字典 ---------------------------------------------------------------------
insert into sys_dict_type (id, code, name, remark) values
    (1, 'article_status', '文章状态',   '草稿/已发布/已下线'),
    (2, 'content_format', '内容格式',   '富文本/Markdown'),
    (3, 'yes_no',         '通用是否',   '是/否');

insert into sys_dict_item (id, type_id, label, value, sort) values
    (1, 1, '草稿',     'DRAFT',     1),
    (2, 1, '已发布',   'PUBLISHED', 2),
    (3, 1, '已下线',   'OFFLINE',   3),
    (4, 2, '富文本',   'HTML',      1),
    (5, 2, 'Markdown', 'MARKDOWN',  2),
    (6, 3, '是',       '1',         1),
    (7, 3, '否',       '0',         2);

-- CMS 示例内容 -------------------------------------------------------------
insert into cms_category (id, parent_id, name, slug, description, sort) values
    (1, 0, '公司新闻', 'news',    '公司新闻与动态', 1),
    (2, 0, '产品动态', 'product', '产品发布与更新', 2),
    (3, 0, '技术分享', 'tech',    '技术实践与教程', 3);

insert into cms_tag (id, name, slug) values
    (1, '公告', 'notice'),
    (2, '产品', 'product'),
    (3, '技术', 'tech'),
    (4, '教程', 'tutorial');

insert into cms_article (id, category_id, title, slug, summary, content, content_format, author_id, author_name,
                         status, top, recommend, view_count, publish_time)
values (1, 1, '欢迎使用 Lingjiuw CMS', 'welcome',
        '这是系统内置的示例文章，展示文章的标题、摘要、正文、分类与标签能力。',
        '<h2>欢迎</h2><p>这是一篇示例文章。你可以在后台管理界面的「内容管理 → 文章管理」中编辑或删除它。</p><p>本系统由 Java 21 + Spring Boot 3 + MyBatis-Plus + PostgreSQL 16 驱动。</p>',
        'HTML', 1, '超级管理员', 'PUBLISHED', 1, 1, 0, now());

insert into cms_article (id, category_id, title, slug, summary, content, content_format, author_id, author_name,
                         status, top, recommend, view_count, publish_time)
values (2, 3, '内容管理快速上手', 'getting-started', '三分钟了解分类、标签、文章与媒体库的使用方式。',
        '<h2>快速上手</h2><ol><li>在「分类管理」中建立栏目</li><li>在「标签管理」中维护标签</li><li>在「文章管理」中写作并发布</li><li>在「媒体库」中上传图片并引用</li></ol>',
        'HTML', 1, '超级管理员', 'PUBLISHED', 0, 1, 0, now());

insert into cms_article (id, category_id, title, slug, summary, content, content_format, author_id, author_name,
                         status, top, recommend, view_count)
values (3, 2, '产品路线图（草稿）', 'roadmap', '尚未发布的草稿示例，仅后台可见。',
        '<p>草稿内容示例：发布后才会对外可见。</p>',
        'HTML', 1, '超级管理员', 'DRAFT', 0, 0, 0);

insert into cms_article_tag (article_id, tag_id) values (1, 1), (1, 3), (2, 4), (2, 3), (3, 2);

-- 主键序列对齐（上面显式指定了 id） ------------------------------------------
select setval('sys_user_id_seq',      (select max(id) from sys_user));
select setval('sys_role_id_seq',      (select max(id) from sys_role));
select setval('sys_menu_id_seq',      (select max(id) from sys_menu));
select setval('sys_dict_type_id_seq', (select max(id) from sys_dict_type));
select setval('sys_dict_item_id_seq', (select max(id) from sys_dict_item));
select setval('cms_category_id_seq',  (select max(id) from cms_category));
select setval('cms_tag_id_seq',       (select max(id) from cms_tag));
select setval('cms_article_id_seq',   (select max(id) from cms_article));
