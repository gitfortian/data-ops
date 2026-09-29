-- Warehouse modeling catalog organization (ticket 03): directory tree + tags.
--
-- Directories/tags are PROJECT_ROOT-owned like the model itself. parent_id=0
-- marks a root directory (same convention as yak_dev_directory). A model
-- belongs to at most one directory (directory_id=0 means uncategorized) and
-- can carry multiple tags. Filters combine: directory exact match, tags OR.

CREATE TABLE IF NOT EXISTS yak_modeling_directory (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父目录,0 表示根',
    name VARCHAR(128) NOT NULL COMMENT '目录名称',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_directory_sibling (project_id, parent_id, name),
    KEY idx_yak_modeling_directory_parent (project_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模模型目录';

CREATE TABLE IF NOT EXISTS yak_modeling_tag (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    name VARCHAR(128) NOT NULL COMMENT '标签名称',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_tag_name (project_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模模型标签';

CREATE TABLE IF NOT EXISTS yak_modeling_model_tag_rel (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    model_id BIGINT NOT NULL COMMENT '模型 id',
    tag_id BIGINT NOT NULL COMMENT '标签 id',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_modeling_model_tag (model_id, tag_id),
    KEY idx_yak_modeling_model_tag_tag (project_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数仓建模模型-标签关联';

ALTER TABLE yak_modeling_model
    ADD COLUMN directory_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属目录,0 表示未分类' AFTER status,
    ADD KEY idx_yak_modeling_model_directory (project_id, directory_id);
