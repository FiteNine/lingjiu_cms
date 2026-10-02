-- =============================================================================
-- 发布任务与发布锁
-- 契约：docs/static-publish.md §8.3（cms_publish_task）、§8.7、§8.8（cms_publish_lock）
--       §12.1 第 5 条
--
-- 1. cms_publish_task：一次发布 = 一个批次 = 一行任务，后台"发布记录"页与
--    release/<batchId>/ 目录都靠它对应（§8.7）。
-- 2. cms_publish_lock：多实例部署时保证同一站点只有一个实例在发布（§8.8）。
--    抢锁 = insert 一行（唯一索引冲突即"已被占用"），放锁 = 删掉这一行。
--    expire_time 用于实例崩溃后的锁回收：过期的锁视为无效，可被下一个实例接管，
--    否则一次异常退出会让该站点永远不再发布。
-- =============================================================================

create table cms_publish_task (
    id          bigserial    primary key,
    site_id     bigint       not null,
    batch_id    varchar(64)  not null,                          -- 对应 release/<batchId>/
    trigger     varchar(16)  not null,                          -- manual / content / schedule / theme / expire
    mode        varchar(16)  not null default 'incremental',    -- full / incremental / narrow / aggregate
    status      varchar(16)  not null default 'PENDING',        -- PENDING/RUNNING/SUCCESS/PARTIAL/FAILED/CANCELLED
    total       int          not null default 0,
    done        int          not null default 0,
    failed      int          not null default 0,
    message     text,                                           -- 前 N 条错误
    create_by   bigint,
    create_time timestamp    not null default now(),
    start_time  timestamp,
    end_time    timestamp,
    deleted     smallint     not null default 0
);
-- 一个站点内 batch_id 唯一（release/<batchId>/ 是按站点分的）
create unique index uk_cms_publish_task_batch on cms_publish_task (site_id, batch_id) where deleted = 0;
-- 发布记录页：按站点倒序列批次
create index idx_cms_publish_task_site on cms_publish_task (site_id, create_time desc);

create table cms_publish_lock (
    id           bigserial   primary key,
    site_id      bigint      not null,
    holder       varchar(128),                                  -- 持锁实例（host:pid），用于排查卡住的锁
    acquire_time timestamp   not null default now(),
    expire_time  timestamp,                                     -- 过期即视为无效（实例崩溃后的回收）
    deleted      smallint    not null default 0
);
-- 同一站点同时只有一把锁
create unique index uk_cms_publish_lock_site on cms_publish_lock (site_id) where deleted = 0;

comment on table  cms_publish_task        is '发布任务/批次：一次发布一行';
comment on table  cms_publish_lock        is '发布锁：同一站点同时只有一个实例在发布';
comment on column cms_publish_task.trigger is 'manual 手工 / content 内容变更 / schedule 定时 / theme 主题 / expire 到期';
comment on column cms_publish_task.mode   is 'full 全量 / incremental 增量 / narrow 窄批次（只发一篇）/ aggregate 只重建聚合产物';
comment on column cms_publish_lock.holder is '持锁实例标识，如 host:pid';
