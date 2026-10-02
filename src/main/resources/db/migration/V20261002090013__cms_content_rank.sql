-- =============================================================================
-- cms_content 补 v2.2 的到期与运营计数列
-- 契约：docs/static-publish.md §2.3（expire_time / view_count_day / view_count_week /
--       comment_count / rating_avg / rating_count）、§8.8、§12.1 第 13 条
--
-- 这些列同时是 §2.2 的派生字段（viewCountDay / viewCountWeek / commentCount /
-- ratingAvg / ratingCount）与可筛选的内置字段 expireTime 的来源：
--   * 查询恒带 publish_time <= now() 且 (expire_time is null or expire_time > now())（§6.3）；
--   * view_count_day / view_count_week 是滚动 24 小时 / 7 天的有效浏览数，日榜周榜的排序依据（§9.4）；
--   * rating_avg / rating_count 在提交评分时同事务更新（§9.6）。
-- =============================================================================

alter table cms_content add column expire_time       timestamp;                        -- null = 不过期
alter table cms_content add column view_count_day    bigint       not null default 0;  -- 滚动 24 小时
alter table cms_content add column view_count_week   bigint       not null default 0;  -- 滚动 7 天
alter table cms_content add column comment_count     int          not null default 0;  -- 已通过（APPROVED）评论数
alter table cms_content add column rating_avg        numeric(3,2) not null default 0;  -- 评分快照，0.00–9.99
alter table cms_content add column rating_count      int          not null default 0;

comment on column cms_content.expire_time     is '到期时间，null = 不过期；到点由 §8.8 的每分钟任务触发增量下线';
comment on column cms_content.view_count_day  is '滚动 24 小时的浏览数（日榜排序依据）';
comment on column cms_content.view_count_week is '滚动 7 天的浏览数（周榜排序依据）';
comment on column cms_content.comment_count   is '已通过（APPROVED）评论数，与审核动作同事务维护';
comment on column cms_content.rating_avg      is '评分快照（cms.js 用实时值覆盖）';
comment on column cms_content.rating_count    is '评分次数快照';
