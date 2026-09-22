-- Yak Ops Digital Screen first-release baseline.
-- The aggregate keeps a mutable draft plus immutable published snapshots.
CREATE TABLE IF NOT EXISTS yak_digital_screen (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    template_id VARCHAR(128) NOT NULL,
    template_version INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    bindings_json JSON NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    published_revision BIGINT NULL,
    published_version_id BIGINT NULL,
    published_version_no INT NOT NULL DEFAULT 0,
    published_time DATETIME(6) NULL,
    create_time DATETIME(6) NOT NULL,
    update_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_yak_digital_screen_status_update (status, update_time),
    KEY idx_yak_digital_screen_template (template_id),
    KEY idx_yak_digital_screen_update (update_time),
    KEY idx_yak_digital_screen_published_version (published_version_id),
    KEY idx_yak_digital_screen_project_status_update (
        project_id, status, update_time, id
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS yak_digital_screen_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    screen_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    source_revision BIGINT NOT NULL,
    name_snapshot VARCHAR(200) NOT NULL,
    description_snapshot VARCHAR(2000) NULL,
    template_id_snapshot VARCHAR(128) NOT NULL,
    template_version_snapshot INT NOT NULL DEFAULT 1,
    bindings_json JSON NOT NULL,
    published_time DATETIME(6) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_digital_screen_version_no (screen_id, version_no),
    KEY idx_yak_digital_screen_version_time (screen_id, published_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
