ALTER TABLE yak_offline_job_definition
    ADD COLUMN editor_meta_json TEXT NULL COMMENT 'UI-only editor metadata such as the task emoji icon';
