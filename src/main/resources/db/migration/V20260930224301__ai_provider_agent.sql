-- =============================================================================
-- V3: AI 管理（服务商 / 智能体）
--
-- 三种协议均取自 DeepSeek 官方文档（抓取时间 2026-09-30，逐条出处见 .tmp/deepseek-api-notes.md）：
--   OPENAI     POST https://api.deepseek.com/chat/completions        base_url https://api.deepseek.com
--   ANTHROPIC  POST https://api.deepseek.com/anthropic/v1/messages   base_url https://api.deepseek.com/anthropic
--   RESPONSES  POST https://api.deepseek.com/responses               base_url https://api.deepseek.com
-- base_url 存各协议文档给出的取值，协议路径由后端适配器拼接（见 module/ai/protocol）。
--
-- 刻意不建的列：frequency_penalty / presence_penalty（官方已标记 deprecated 且传入不生效）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- AI 服务商：一行 = 一个服务商 × 一种协议
-- ---------------------------------------------------------------------------
create table ai_provider (
    id          bigserial    primary key,
    name        varchar(64)  not null,                     -- 服务商显示名，如 DeepSeek
    code        varchar(64)  not null,                     -- 服务商标识，如 deepseek
    protocol    varchar(16)  not null,                     -- OPENAI / ANTHROPIC / RESPONSES
    base_url    varchar(255) not null,
    api_key     varchar(255),
    status      smallint     not null default 1,           -- 1 启用 / 0 停用
    remark      varchar(255),
    create_by   bigint,
    create_time timestamp    not null default now(),
    update_by   bigint,
    update_time timestamp    not null default now(),
    deleted     smallint     not null default 0
);
create unique index uk_ai_provider_code_protocol on ai_provider (code, protocol) where deleted = 0;

-- ---------------------------------------------------------------------------
-- 智能体：模型参数 + 系统提示词
-- ---------------------------------------------------------------------------
create table ai_agent (
    id               bigserial    primary key,
    name             varchar(64)  not null,
    code             varchar(64)  not null,                -- 业务标识，供后续对外调用
    provider_id      bigint       not null,
    model            varchar(64)  not null,                -- 如 deepseek-flash / deepseek-v4-pro
    system_prompt    text,
    temperature      numeric(3,2),                         -- 0.00 ~ 2.00（思考模式下不生效）
    top_p            numeric(3,2),                         -- 仅思考模式生效，有效范围 0.95 ~ 1.00
    max_tokens       int,                                  -- 1 ~ 393216，留空用官方默认
    thinking         smallint     not null default 1,       -- 1 开启思考（官方默认开启）/ 0 关闭
    reasoning_effort varchar(16)  not null default 'high',  -- low / high / max
    json_output      smallint     not null default 0,       -- 1 要求返回 JSON（Anthropic 协议下忽略）
    status           smallint     not null default 1,
    remark           varchar(255),
    create_by        bigint,
    create_time      timestamp    not null default now(),
    update_by        bigint,
    update_time      timestamp    not null default now(),
    deleted          smallint     not null default 0
);
create unique index uk_ai_agent_code on ai_agent (code) where deleted = 0;
create index idx_ai_agent_provider on ai_agent (provider_id);

-- ---------------------------------------------------------------------------
-- 种子：DeepSeek 三种协议各一行（API Key 由后台「AI服务商」页面填写）
-- ---------------------------------------------------------------------------
insert into ai_provider (id, name, code, protocol, base_url, status, remark) values
    (1, 'DeepSeek', 'deepseek', 'OPENAI',    'https://api.deepseek.com',           1, 'OpenAI 兼容：POST /chat/completions'),
    (2, 'DeepSeek', 'deepseek', 'ANTHROPIC', 'https://api.deepseek.com/anthropic', 1, 'Anthropic 兼容：POST /v1/messages'),
    (3, 'DeepSeek', 'deepseek', 'RESPONSES', 'https://api.deepseek.com',           1, 'Responses 兼容：POST /responses');

select setval('ai_provider_id_seq', (select max(id) from ai_provider));

-- ---------------------------------------------------------------------------
-- 菜单 / 权限
-- ---------------------------------------------------------------------------
insert into sys_menu (id, parent_id, name, path, component, icon, perms, type, sort) values
    (30,  0,  'AI管理',   '/ai',       null,                  'MagicStick', null,               'DIR',    3),
    (31,  30, 'AI服务商', 'providers', 'ai/providers/index',  'Connection', 'ai:provider:list', 'MENU',   1),
    (311, 31, '服务商新增', null, null, null, 'ai:provider:add',    'BUTTON', 1),
    (312, 31, '服务商编辑', null, null, null, 'ai:provider:edit',   'BUTTON', 2),
    (313, 31, '服务商删除', null, null, null, 'ai:provider:delete', 'BUTTON', 3),
    (32,  30, '智能体',   'agents',    'ai/agents/index',     'Cpu',        'ai:agent:list',    'MENU',   2),
    (321, 32, '智能体新增', null, null, null, 'ai:agent:add',    'BUTTON', 1),
    (322, 32, '智能体编辑', null, null, null, 'ai:agent:edit',   'BUTTON', 2),
    (323, 32, '智能体删除', null, null, null, 'ai:agent:delete', 'BUTTON', 3),
    (324, 32, '智能体试聊', null, null, null, 'ai:agent:chat',   'BUTTON', 4);

-- 本次新增的菜单只授权给超级管理员（内容编辑角色不涉及 AI 配置）
insert into sys_role_menu (role_id, menu_id)
select 1, id from sys_menu
where id in (30, 31, 32, 311, 312, 313, 321, 322, 323, 324);

select setval('sys_menu_id_seq', (select max(id) from sys_menu));

-- ---------------------------------------------------------------------------
-- 注释
-- ---------------------------------------------------------------------------
comment on table  ai_provider          is 'AI 服务商（一行 = 一个服务商 × 一种协议）';
comment on table  ai_agent             is '智能体配置';
comment on column ai_provider.protocol is 'OPENAI OpenAI 兼容 / ANTHROPIC Anthropic 兼容 / RESPONSES Responses 兼容';
comment on column ai_agent.thinking    is '1 开启思考模式（官方默认）/ 0 关闭';
comment on column ai_agent.json_output is '1 请求 JSON 输出；Anthropic 协议无对应参数，忽略';
