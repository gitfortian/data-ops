ALTER TABLE yak_offline_job_definition
    ADD COLUMN notification_config_json TEXT NULL COMMENT 'Task-level notification policy JSON; NULL keeps the legacy Project OWNER + IN_APP default';
