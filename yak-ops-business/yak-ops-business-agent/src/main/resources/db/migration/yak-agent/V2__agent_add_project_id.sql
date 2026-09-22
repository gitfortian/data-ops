-- =====================================================================
-- V2: Agent 项目空间隔离：yak_agent_session / yak_agent_report / yak_agent_skill
-- 增加 project_id 列，实现项目级数据隔离。
-- yak_agent_turn 已有 project_id 预留列（V1），此处改为 NOT NULL。
-- =====================================================================

ALTER TABLE `yak_agent_session`
    ADD COLUMN `project_id` BIGINT NOT NULL DEFAULT 0 COMMENT '项目空间ID' AFTER `user_id`,
    ADD KEY `idx_agent_session_project` (`project_id`, `update_time`);

ALTER TABLE `yak_agent_report`
    ADD COLUMN `project_id` BIGINT NOT NULL DEFAULT 0 COMMENT '项目空间ID' AFTER `user_id`,
    ADD KEY `idx_agent_report_project` (`project_id`, `is_deleted`, `id`);

ALTER TABLE `yak_agent_skill`
    ADD COLUMN `project_id` BIGINT NOT NULL DEFAULT 0 COMMENT '项目空间ID' AFTER `skill_id`,
    ADD KEY `idx_agent_skill_project` (`project_id`);

-- yak_agent_turn.project_id 已在 V1 预留（nullable），先回填存量 NULL，再改为 NOT NULL 以强制项目隔离。
UPDATE `yak_agent_turn` SET `project_id` = 0 WHERE `project_id` IS NULL;
ALTER TABLE `yak_agent_turn`
    MODIFY COLUMN `project_id` BIGINT NOT NULL DEFAULT 0 COMMENT '项目空间ID（PROJECT_RUNTIME，异步上下文恢复通道）',
    ADD KEY `idx_agent_turn_project` (`project_id`, `id`);

-- yak_agent_query_log 增加 project_id + user_id 列，实现项目级审计隔离。
ALTER TABLE `yak_agent_query_log`
    ADD COLUMN `project_id` BIGINT NOT NULL DEFAULT 0 COMMENT '项目空间ID' AFTER `id`,
    ADD COLUMN `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '归属用户ID' AFTER `project_id`,
    ADD KEY `idx_agent_query_log_project` (`project_id`, `user_id`, `id`);
