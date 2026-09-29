-- Asset center V1 (ticket 90): ledger, directory tree, tags, assign rules,
-- change records, view stream, health snapshot, settings. See docs/data-asset/design.md §4.

CREATE TABLE IF NOT EXISTS yak_asset_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    asset_key VARCHAR(256) NOT NULL COMMENT '血缘同源键(小写前缀式,复用源域登记键生成器,D6);MANUAL=manual:{code}',
    source_type VARCHAR(16) NOT NULL COMMENT 'MODEL/METRIC/DATASET/DASHBOARD/CHART/TASK/MANUAL',
    source_id VARCHAR(64) NOT NULL COMMENT '源域主键/编码(字符串容纳)',
    asset_type VARCHAR(16) NOT NULL COMMENT '展示类型 TABLE/METRIC/DATASET/DASHBOARD/CHART/TASK/DOC',
    name VARCHAR(128) NOT NULL COMMENT '名称快照,可编辑;META_CHANGED 确认时更新',
    description VARCHAR(1024) NULL COMMENT '描述快照',
    layer_code VARCHAR(32) NULL COMMENT '分层快照(semantic 字典值)',
    domain_code VARCHAR(32) NULL COMMENT '业务域快照(semantic 字典值)',
    directory_id BIGINT NULL COMMENT '主目录(一资产一个)',
    owner VARCHAR(64) NULL COMMENT '统一负责人,唯一事实源(D4);登记时自源域带出',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PUBLISHED/OFFLINE/IGNORED/SOURCE_GONE',
    content_hash VARCHAR(64) NULL COMMENT '源域 descriptor 指纹,META_CHANGED 判定',
    source_updated_at DATETIME(6) NULL COMMENT '源对象最近更新时间(快照)',
    security_level_code VARCHAR(32) NULL COMMENT '定级快照自 security;详情页实时值优先',
    health_score INT NULL COMMENT '健康度派生缓存(每日重算,D7)',
    health_grade VARCHAR(2) NULL COMMENT 'A/B/C/D',
    health_detail TEXT NULL COMMENT '评分明细 JSON(每项得分与缺口)',
    view_count_30d INT NOT NULL DEFAULT 0 COMMENT '近30天浏览派生缓存(每日聚合)',
    access_uri VARCHAR(512) NULL COMMENT 'MANUAL 资产访问入口',
    first_listed_at DATETIME(6) NULL COMMENT '首次上架时间',
    last_listed_at DATETIME(6) NULL COMMENT '最近上架时间',
    last_offline_at DATETIME(6) NULL COMMENT '最近下架时间',
    last_offline_reason VARCHAR(512) NULL COMMENT '最近下架原因(人工下架必填)',
    reconciled_at DATETIME(6) NULL COMMENT '最近一次对账确认存在时间(SOURCE_GONE 窗口判定)',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '最后修改人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_item_key (project_id, asset_key),
    KEY idx_asset_item_status (project_id, status, deleted),
    KEY idx_asset_item_type (project_id, asset_type, deleted),
    KEY idx_asset_item_dir (project_id, directory_id),
    KEY idx_asset_item_owner (project_id, owner),
    KEY idx_asset_item_name (project_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产台账(核心)';

CREATE TABLE IF NOT EXISTS yak_asset_directory (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    dir_code VARCHAR(64) NOT NULL COMMENT '目录编码,自动生成可改,项目内唯一',
    dir_name VARCHAR(128) NOT NULL COMMENT '目录名称,模板初始化自动取层名/域名',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父目录,根=0',
    path VARCHAR(512) NOT NULL COMMENT '物化路径 /1/4/9/,子树查询与移动用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '同级排序',
    icon_key VARCHAR(64) NULL COMMENT '图标',
    description VARCHAR(512) NULL COMMENT '描述',
    builtin TINYINT NOT NULL DEFAULT 0 COMMENT '1=模板初始化产生(可改不可删)',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '最后修改人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_dir_code (project_id, dir_code),
    KEY idx_asset_dir_parent (project_id, parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产目录(树,物化路径)';

CREATE TABLE IF NOT EXISTS yak_asset_tag (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    tag_code VARCHAR(64) NOT NULL COMMENT '标签编码,自动生成 tag_{ts} 可改,项目内唯一',
    tag_name VARCHAR(128) NOT NULL COMMENT '标签名称',
    color VARCHAR(32) NULL COMMENT '颜色(预置色板下拉)',
    description VARCHAR(512) NULL COMMENT '描述',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '最后修改人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_tag_code (project_id, tag_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务标签字典(跨域)';

CREATE TABLE IF NOT EXISTS yak_asset_tag_rel (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    asset_id BIGINT NOT NULL COMMENT '资产(台账 ID,逻辑外键)',
    tag_id BIGINT NOT NULL COMMENT '标签(逻辑外键)',
    created_by VARCHAR(64) NOT NULL COMMENT '打标人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_tag_rel (project_id, asset_id, tag_id),
    KEY idx_asset_tag_rel_tag (project_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产-标签关系(物理删,批量事务内)';

CREATE TABLE IF NOT EXISTS yak_asset_assign_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    rule_type VARCHAR(16) NOT NULL COMMENT 'DIRECTORY(归目录)/TAG(打标签)',
    conditions TEXT NOT NULL COMMENT 'JSON {assetTypes,layerCodes,domainCodes,nameRegex,keyword,sourceTypes};空=通配,多条件 AND',
    target_directory_id BIGINT NULL COMMENT 'rule_type=DIRECTORY 时生效',
    target_tag_id BIGINT NULL COMMENT 'rule_type=TAG 时生效',
    priority INT NOT NULL DEFAULT 100 COMMENT '小者优先;同类型首条命中即停',
    enabled TINYINT NOT NULL DEFAULT 0 COMMENT '默认关,试跑通过后才可启用(D10)',
    last_apply_hit INT NULL COMMENT '最近试跑/重应用命中数',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '最后修改人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删',
    PRIMARY KEY (id),
    KEY idx_asset_rule_type (project_id, rule_type, enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='编目规则(自动归目录/打标签)';

CREATE TABLE IF NOT EXISTS yak_asset_change_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    asset_id BIGINT NOT NULL COMMENT '关联台账(NEW 时即已建行)',
    change_type VARCHAR(16) NOT NULL COMMENT 'NEW/META_CHANGED/SOURCE_GONE/REAPPEARED',
    diff TEXT NULL COMMENT '字段级前后差异 JSON;GONE 记录消失时间',
    handle_status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CONFIRMED/IGNORED',
    snapshot_new TEXT NULL COMMENT '源域新值快照(确认前详情页提示"源域已变")',
    handled_by VARCHAR(64) NULL COMMENT '确认/忽略人',
    handled_at DATETIME(6) NULL COMMENT '处理时间',
    created_by VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '产生方(对账=system)',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删',
    PRIMARY KEY (id),
    KEY idx_asset_change_handle (project_id, handle_status, change_type),
    KEY idx_asset_change_asset (project_id, asset_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点变更记录(对账产生)';

CREATE TABLE IF NOT EXISTS yak_asset_view_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    asset_id BIGINT NOT NULL COMMENT '资产(台账 ID)',
    viewer VARCHAR(64) NOT NULL COMMENT '浏览人(平台用户标识)',
    view_time DATETIME(6) NOT NULL COMMENT '浏览时间',
    entry VARCHAR(64) NULL COMMENT '入口(catalog/search/detail 等)',
    PRIMARY KEY (id),
    KEY idx_asset_view_asset (project_id, asset_id, view_time),
    KEY idx_asset_view_time (project_id, view_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览流水(活跃度;只写,聚合走 SQL;保留90天)';

CREATE TABLE IF NOT EXISTS yak_asset_health_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    snapshot_date DATE NOT NULL COMMENT '快照日期',
    layer_code VARCHAR(32) NOT NULL COMMENT '分层,ALL=全项目汇总行',
    grade_a_count INT NOT NULL DEFAULT 0 COMMENT 'A 级数量',
    grade_b_count INT NOT NULL DEFAULT 0 COMMENT 'B 级数量',
    grade_c_count INT NOT NULL DEFAULT 0 COMMENT 'C 级数量',
    grade_d_count INT NOT NULL DEFAULT 0 COMMENT 'D 级数量',
    published_count INT NOT NULL DEFAULT 0 COMMENT '已上架数量',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_health_snapshot (project_id, snapshot_date, layer_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='健康度按日聚合快照(P2,不存资产级历史)';

CREATE TABLE IF NOT EXISTS yak_asset_setting (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    setting_key VARCHAR(64) NOT NULL COMMENT '键:gone_window_days/reconcile_enabled 等',
    setting_value VARCHAR(256) NULL COMMENT '值',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_setting (project_id, setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产模块设置';
