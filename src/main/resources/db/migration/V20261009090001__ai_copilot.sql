-- =============================================================================
-- 全站 Agent（AI Copilot）：会话入库 + 智能体工具范围 + 页面权限
--
-- 只增不改（backend/AGENTS.md）：本文件新建表 / 加列 / 加权限，前面 26 个迁移文件一个字节都不动。
-- 版本号：库里最后一个迁移是 20261008090001，Flyway 未开 out-of-order，新版本号必须大于它，
--   沿用既有文件 09:00:0X 的排布取 20261009090001。
--
-- 方案见 backend/docs/ai-copilot.md：工具层按 MCP Tool 语义设计，会话全量入库便于审计与回放。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 智能体：工具开关与工具组白名单
--    tool_enabled = 0 时该智能体退化为现有的"试聊"（只对话、不调工具）；
--    tool_scope 逗号分隔组名（CONTENT / TEMPLATE_READ / SYSTEM_READ / PLATFORM），
--    空 = 只给默认组（前三个），PLATFORM 必须在 tool_scope 里显式打开。
-- ---------------------------------------------------------------------------
alter table ai_agent add column tool_enabled smallint not null default 1;
alter table ai_agent add column tool_scope   varchar(255);

comment on column ai_agent.tool_enabled is '1 允许调用工具（全站agent）/ 0 只对话（试聊语义）';
comment on column ai_agent.tool_scope   is '逗号分隔工具组白名单；空 = CONTENT,TEMPLATE_READ,SYSTEM_READ';

-- ---------------------------------------------------------------------------
-- 2. 会话
--    site_id 在会话创建时钉死：工具一律按它执行，不让 X-Site-Id 的静默回落把内容写错站点。
-- ---------------------------------------------------------------------------
create table ai_chat_session (
    id              bigserial    primary key,
    title           varchar(128),                 -- 取首条用户消息前 50 字
    user_id         bigint       not null,
    site_id         bigint       not null,        -- 会话内钉死，工具一律按它执行
    agent_id        bigint       not null,
    status          smallint     not null default 1,  -- 1 进行中 / 0 已结束
    rounds          int          not null default 0,
    tool_call_count int          not null default 0,
    input_tokens    bigint       not null default 0,
    output_tokens   bigint       not null default 0,
    create_by       bigint,
    create_time     timestamp    not null default now(),
    update_by       bigint,
    update_time     timestamp    not null default now(),
    deleted         smallint     not null default 0
);
create index idx_ai_chat_session_user on ai_chat_session (user_id, id desc) where deleted = 0;

comment on table  ai_chat_session      is '全站agent 会话（site_id 在会话内钉死）';
comment on column ai_chat_session.status is '1 进行中 / 0 已结束';

-- ---------------------------------------------------------------------------
-- 3. 消息（追加写，不更新）
--    刻意没有 deleted 列：它是追加日志，删会话时按 session_id 清理（实体上不加 @TableLogic）。
--    reasoning_content / tool_call_id 必须完整落库、原样取出——它们是历史回灌能否被服务端
--    接受的关键（见 docs/ai-copilot.md §4.5.3、§10.4），任何截断都会导致间歇性 400。
-- ---------------------------------------------------------------------------
create table ai_chat_message (
    id                bigserial   primary key,
    session_id        bigint      not null,
    seq               int         not null,      -- 会话内递增，唯一
    role              varchar(16) not null,      -- user / assistant / tool
    content           text,
    reasoning_content text,
    tool_calls        text,                      -- assistant 发起的工具调用，JSON 数组
    tool_call_id      varchar(64),               -- role=tool 时对应哪次调用（原样存取，不加工）
    tool_name         varchar(64),
    tool_args         text,
    tool_status       smallint,                  -- 1 成功 / 0 失败 / 2 被用户拒绝 / 3 无权限
    tool_duration_ms  int,
    site_id           bigint,                    -- 冗余，便于审计
    input_tokens      int,
    output_tokens     int,
    create_time       timestamp   not null default now()
);
create unique index uk_ai_chat_message_seq on ai_chat_message (session_id, seq);
create index idx_ai_chat_message_session on ai_chat_message (session_id, seq);

comment on table  ai_chat_message                 is '全站agent 消息（追加日志，无逻辑删除）';
comment on column ai_chat_message.seq             is '会话内递增序号，唯一';
comment on column ai_chat_message.tool_status     is '1 成功 / 0 失败 / 2 被用户拒绝 / 3 无权限';

-- ---------------------------------------------------------------------------
-- 4. 菜单 / 权限
--    全站agent 页面 + 两个按钮权限。前端侧边栏是手写的（不做菜单级隐藏），
--    真正的门是 CopilotController 上的 @PreAuthorize。
-- ---------------------------------------------------------------------------
insert into sys_menu (id, parent_id, name, path, component, icon, perms, type, sort) values
    (33,  30, '全站agent',      'copilot', 'ai/copilot/index', 'ChatDotRound', 'ai:copilot:chat',    'MENU',   3),
    (331, 33, '全站agent对话',   null,      null,               null,           'ai:copilot:chat',    'BUTTON', 1),
    (332, 33, '危险操作确认',    null,      null,               null,           'ai:copilot:confirm', 'BUTTON', 2);

-- 只授权给超级管理员：内容编辑角色到「角色管理」里按需勾选
insert into sys_role_menu (role_id, menu_id)
select 1, id from sys_menu where id in (33, 331, 332);

select setval('sys_menu_id_seq', (select max(id) from sys_menu));
