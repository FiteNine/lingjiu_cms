-- =============================================================================
-- 批 09（mapper XML 与迁移脚本审查）的库层修复
--
-- 只增不改（backend/AGENTS.md）：下面每一条都是新增的索引 / 列 / 约束或幂等的数据订正，
-- 前面 24 个迁移文件一个字节都不动。审查清单里"必须改旧文件"的部分（文件名不合约定、
-- 硬编码主键、注释与实现不符）不做修改，结论见
-- backend/docs/code-review/fix-reports/09-mapper-xml-and-sql.md。
--
-- 版本号：本机的系统时钟（2026-10-03）落后于库里最后一个迁移（20261006090001，写于 2026-10-06），
--   直接取本机时间会得到一个小于它的版本号（Flyway 未开 out-of-order 时会被判为乱序）。因此沿用
--   既有文件 09:00:0X 的排布，取最后一个迁移之后的下一天：20261007090001。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 缺失的索引（审查：sys_user_site(site_id) / sys_role_menu(menu_id) /
--    cms_content 的类型列表、到期扫描、日榜周榜 / cms_media 派生状态）
-- ---------------------------------------------------------------------------
-- SysUserSiteMapper.deleteBySiteId 按 site_id 删：联合主键 (user_id, site_id) 的最左列用不上
create index idx_sys_user_site_site on sys_user_site (site_id);

-- SysRoleMenuMapper.deleteByMenuId 按 menu_id 删：主键 (role_id, menu_id) 的最左列用不上
create index idx_sys_role_menu_menu on sys_role_menu (menu_id);

-- §6.3 的类型列表查询：site_id + type_code + status + publish_time 排序。
-- 原索引 idx_cms_content_publish 保留给 type='all' 的跨类型聚合。
create index idx_cms_content_type_publish on cms_content (site_id, type_code, status, publish_time desc);

-- §8.8 的每分钟到期扫描：expire_time is not null and expire_time <= now() and status='PUBLISHED'
create index idx_cms_content_expire on cms_content (expire_time)
    where deleted = 0 and status = 'PUBLISHED' and expire_time is not null;

-- §9.4 日榜 / 周榜：按站点与类型过滤后 order by view_count_day|week desc
create index idx_cms_content_rank_day on cms_content (site_id, type_code, view_count_day)
    where deleted = 0 and status = 'PUBLISHED';
create index idx_cms_content_rank_week on cms_content (site_id, type_code, view_count_week)
    where deleted = 0 and status = 'PUBLISHED';

-- 后台按派生状态筛"失败待重试"的媒体
create index idx_cms_media_derive_status on cms_media (derive_status) where deleted = 0;

-- 站点目录必须互不重叠：两个站点填同一个 root_dir 会往同一个物理目录发布、互相覆盖文件。
-- 应用层的 SiteService 只做了 code 唯一校验（checkCodeUnique），这里由数据库兜住。
create unique index uk_cms_site_root_dir on cms_site (root_dir) where deleted = 0;

-- 同一字典类型下 value 不能重复（重复项会让下拉与字典查询出现两条一样的选项）
create unique index uk_sys_dict_item_value on sys_dict_item (type_id, value) where deleted = 0;

-- ---------------------------------------------------------------------------
-- 2. 补齐审计字段（V1__init.sql 第 3 行的约定：所有业务表统一
--    create_by/create_time/update_by/update_time/deleted）
--
-- 只加列：对应的实体（CmsPublishTask / CmsSitePublishOption）目前没有 updateBy/updateTime
-- 字段，MyBatis-Plus 的 MetaObjectHandler 也就填不到它们——Java 侧要同步加字段
-- （@TableField(fill = FieldFill.INSERT_UPDATE)）这两列才会被真正维护。见修复报告。
-- ---------------------------------------------------------------------------
alter table cms_publish_task add column update_by   bigint;
alter table cms_publish_task add column update_time timestamp not null default now();

alter table cms_site_publish_option add column create_by   bigint;
alter table cms_site_publish_option add column create_time timestamp not null default now();
alter table cms_site_publish_option add column update_by   bigint;
alter table cms_site_publish_option add column update_time timestamp not null default now();

-- ---------------------------------------------------------------------------
-- 3. 约束：取值区间与列注释 / 应用层校验对齐
--
-- 两条原则：① 只加"应用层已经在拦同一件事"的约束（否则会把现在能成功的写入变成 500）；
-- ② 应用层完全没校验的列（ai_agent.reasoning_effort、cms_publish_task.trigger 来自自由文本、
-- cms_menu_item.visible…）这里不加，避免数据库成为唯一的、报错不可读的关卡，见报告。
-- ---------------------------------------------------------------------------

-- cms_media.derive_status：注释约定 PENDING / DONE / FAILED，但列可空且无默认值，
-- 存量行与本轮之前的写入都是 NULL。先回填再收紧。
update cms_media set derive_status = 'PENDING' where derive_status is null;
alter table cms_media alter column derive_status set default 'PENDING';
alter table cms_media alter column derive_status set not null;
alter table cms_media add constraint ck_cms_media_derive_status
    check (derive_status in ('PENDING', 'DONE', 'FAILED'));

-- 原图宽高是像素尺寸，0 / 负数会让 [field:cover.width/] 渲染出错
alter table cms_media add constraint ck_cms_media_width  check (width  is null or width  > 0);
alter table cms_media add constraint ck_cms_media_height check (height is null or height > 0);

-- ai_provider：AiProviderService 用 AiProtocol.of() 归一化协议名，status 由请求校验成 0/1
alter table ai_provider add constraint ck_ai_provider_protocol
    check (protocol in ('OPENAI', 'ANTHROPIC', 'RESPONSES'));
alter table ai_provider add constraint ck_ai_provider_status check (status in (0, 1));

-- ai_agent：区间与 AgentSaveRequest 的 Bean Validation 逐条对齐
--   temperature 0.00~2.00；max_tokens 1~393216；thinking / jsonOutput / status 只能是 0 / 1。
--   top_p 用「大于 0 且不超过 1」——列注释里的 0.95 只是思考模式的建议区间，不是硬上限，
--   而 AgentSaveRequest 允许 (0, 1] 的任意取值，按 0.95 兜会把现在合法的保存全部拒掉。
alter table ai_agent add constraint ck_ai_agent_temperature
    check (temperature is null or (temperature >= 0 and temperature <= 2));
alter table ai_agent add constraint ck_ai_agent_top_p
    check (top_p is null or (top_p > 0 and top_p <= 1));
alter table ai_agent add constraint ck_ai_agent_max_tokens
    check (max_tokens is null or (max_tokens >= 1 and max_tokens <= 393216));
alter table ai_agent add constraint ck_ai_agent_thinking    check (thinking in (0, 1));
alter table ai_agent add constraint ck_ai_agent_json_output check (json_output in (0, 1));
alter table ai_agent add constraint ck_ai_agent_status      check (status in (0, 1));

-- cms_site.root_dir：注释声明"相对 cms.site.root-dir，如 lingjiuw.cn"，
--   SitePathBoundary.resolveUnder 已拒绝对路径与 .. 上跳，这里做同一条规则的纵深防御。
alter table cms_site add constraint ck_cms_site_root_dir
    check (root_dir <> '' and root_dir !~ '^/' and root_dir !~ '(^|/)\.\.(/|$)');

-- cms_publish_lock.expire_time 是实例崩溃后唯一的锁回收手段，不允许为空
alter table cms_publish_lock alter column expire_time set not null;

comment on column cms_publish_lock.expire_time
    is '过期即视为无效（实例崩溃后的回收）；抢锁时必须写入，为 null 会让该站点永久占锁';
-- 附注（审查：唯一索引不含 expire_time）：uk_cms_publish_lock_site 只对 deleted = 0 生效。
-- 抢锁必须是"先删除过期行、再 insert"，只按"insert 冲突即已被占用"理解会让崩溃残留行永久占锁。
comment on index uk_cms_publish_lock_site
    is '同一站点同时只有一把锁（仅 deleted = 0）；抢锁必须先清理过期行再 insert';

-- ---------------------------------------------------------------------------
-- 4. 幂等性缺口与数据订正
-- ---------------------------------------------------------------------------

-- 4.1 默认站点：迁移 20261001004746 只在 code='default' 不存在时补建。
--     若该行已存在但 is_default=0（被改过、或历史数据），全库会变成"零默认站点"，
--     SiteInterceptor 回落时找不到站点。这里按同样的幂等口径补一次。
update cms_site
   set is_default  = 1,
       update_time = now()
 where code = 'default' and deleted = 0 and is_default = 0;

-- 4.2 内容编辑角色（code='editor'）：V2__seed.sql 给它授的是 `id between 10 and 19`，
--     只覆盖目录与菜单节点，内容管理下的按钮（分类 121-123 / 标签 131-133 / 媒体 141-142）
--     全漏了 —— 结果"看得到列表，点不了按钮"。按"它已经持有的菜单下的按钮"补齐，
--     不写死 id（菜单 id 会随新增菜单变化），也不碰它没被授权的菜单。
insert into sys_role_menu (role_id, menu_id)
select distinct rm.role_id, b.id
  from sys_role_menu rm
  join sys_role r on r.id = rm.role_id and r.deleted = 0
  join sys_menu m on m.id = rm.menu_id and m.type = 'MENU' and m.deleted = 0
  join sys_menu b on b.parent_id = m.id and b.type = 'BUTTON' and b.deleted = 0
 where r.code = 'editor'
   and not exists (select 1 from sys_role_menu x
                    where x.role_id = rm.role_id and x.menu_id = b.id);

-- 4.3 demo 站：书籍的 author_id / author_name。
--     迁移 20261003090001 第 710-723 行按 `a.slug = c.data ->> 'author'` 关联，但作者的 slug 是
--     lin-jiu / chen-mo / su-qing，书籍 data.author 存的是中文姓名（林久 / 陈默 / 苏晴），
--     条件恒不成立、三本书的 author_id 全是 NULL，书详情的作者栏因此不可点。按标题（姓名）关联。
update cms_content c
   set author_id   = a.id,
       author_name = a.title,
       update_time = now()
  from cms_site s
  join cms_content a on a.site_id = s.id and a.type_code = 'author' and a.deleted = 0
 where s.code = 'demo' and s.deleted = 0
   and c.site_id = s.id and c.type_code = 'book' and c.deleted = 0
   and a.title = c.data ->> 'author'
   and c.author_id is distinct from a.id;

-- 4.4 demo 站：书籍的 data.tags 与 cms_content_tag 对不上。
--     原迁移 6.2 里 data.tags 写死过一组 id，第 726-735 行又统一覆盖成
--     static/template/cms/design 这些标签的 id，而 7.2 给书籍挂的关联是 java、template，
--     于是"字段值"与"标签关联"两边不一致（article 是反查 cms_content_tag 生成的，书籍漏了）。
--     这里按 article 的同一口径重算 books 的 data.tags，并重建这部分的索引行。
update cms_content c
   set data = jsonb_set(coalesce(c.data, '{}'::jsonb), '{tags}',
                        coalesce((select jsonb_agg(ct.tag_id order by ct.tag_id)
                                    from cms_content_tag ct
                                   where ct.content_id = c.id and ct.deleted = 0), '[]'::jsonb)),
       update_time = now()
  from cms_site s
 where s.code = 'demo' and s.deleted = 0
   and c.site_id = s.id and c.type_code = 'book' and c.deleted = 0
   and c.data -> 'tags' is distinct from
       coalesce((select jsonb_agg(ct.tag_id order by ct.tag_id)
                   from cms_content_tag ct
                  where ct.content_id = c.id and ct.deleted = 0), '[]'::jsonb);

delete from cms_content_index
 where field_code = 'tags'
   and content_id in (select c.id
                        from cms_content c
                        join cms_site s on s.id = c.site_id
                       where s.code = 'demo' and s.deleted = 0
                         and c.type_code = 'book' and c.deleted = 0);

insert into cms_content_index (site_id, content_id, type_code, field_code, value_key, value_type,
                               num_value, str_value, time_value)
select c.site_id, c.id, c.type_code, 'tags', t.value, 'TAGS', null, t.value, null
  from cms_site s
  join cms_content c on c.site_id = s.id and c.deleted = 0 and c.type_code = 'book'
  cross join lateral jsonb_array_elements_text(c.data -> 'tags') as t(value)
 where s.code = 'demo' and s.deleted = 0;

-- 4.5 demo 站：点击排行页的排序字段与注释 / 契约不一致。
--     同一文件第 65 行写明 rank 页的材料来自第 8 节填的 view_count_week，§7.2.1 也要求
--     点击排行按 viewCountWeek desc（T 时刻快照），而 pages.static 里写的是 viewCount desc
--     （累计浏览量）—— 生成的演示榜单与说明不是一回事。
update cms_site_publish_option o
   set value       = replace(o.value, '"orderby":"viewCount desc"', '"orderby":"viewCountWeek desc"'),
       update_time = now()
  from cms_site s
 where s.id = o.site_id and s.code = 'demo' and s.deleted = 0
   and o.option_code = 'pages.static' and o.deleted = 0
   and o.value like '%"orderby":"viewCount desc"%';

-- 4.6 demo 站：内容类型序号收敛到规范值。
--     迁移 20261003090001 的第二次 update（该文件 179-208 行）把类型重排成
--     article=1 / product=2 / book=3 / chapter=4 / author=5 / about=6 / contact=7 / single=8；
--     而迁移 20261004090001 给"老库"补 article / single / author 三个类型时用的是它自己那份
--     取值表（single=2、author=3），于是升级库会出现 single 与 product 同为 2、author 与 book
--     同为 3，与干净库不一致。这里按规范值统一，干净库上是空操作（sort 已经相等）。
update cms_content_type t
   set sort = v.sort
  from cms_site s
  cross join (values
      ('article', 1), ('product', 2), ('book', 3), ('chapter', 4),
      ('author',  5), ('about',   6), ('contact', 7), ('single', 8)
  ) as v(code, sort)
 where s.id = t.site_id and s.code = 'demo' and s.deleted = 0
   and t.code = v.code and t.deleted = 0
   and t.sort is distinct from v.sort;

-- 4.7 站点「站点管理」菜单（41）的授权继承。
--     迁移 20261001030813 把原来的顶级菜单 40（perms='cms:site:list'）改成了 DIR、
--     权限点搬到新建的 41 上，但授权只写死给了 role_id=1。运行期管理员若已把 40 授给过
--     其它角色，这些角色会留在 40（DIR，perms 已置空）却拿不到 41，静默丢掉站点管理权限。
--     按"当前持有 40 的角色"补齐 41；超级管理员已持有，干净库上是空操作。
insert into sys_role_menu (role_id, menu_id)
select distinct rm.role_id, child.id
  from sys_role_menu rm
  join sys_menu parent on parent.id = rm.menu_id
                      and parent.path = '/sites' and parent.type = 'DIR' and parent.deleted = 0
  join sys_menu child on child.parent_id = parent.id
                     and child.perms = 'cms:site:list' and child.deleted = 0
 where not exists (select 1 from sys_role_menu x
                    where x.role_id = rm.role_id and x.menu_id = child.id);
