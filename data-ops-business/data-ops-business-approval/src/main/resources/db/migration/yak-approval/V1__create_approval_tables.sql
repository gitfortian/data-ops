-- General approval flow V1 (ticket 101): flow definition, approval instance, step history.
-- See docs/approval/design.md §3. No physical FK; project_id from server-side context only.

CREATE TABLE IF NOT EXISTS yak_approval_flow (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    flow_code VARCHAR(64) NOT NULL COMMENT '流程编码,业务引用常量;软删时改写为 {code}#del#{id} 释放编码',
    flow_name VARCHAR(128) NOT NULL COMMENT '流程名称',
    description VARCHAR(512) NULL COMMENT '说明',
    steps_json TEXT NOT NULL COMMENT '级配置 JSON:[{"level":1,"approvers":["a","b"]}],1~2 级,每级 1~10 人',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '停用后不可再发起,在途单不受影响',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '最后修改人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_flow_code (project_id, flow_code),
    KEY idx_approval_flow_list (project_id, deleted, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批流程定义';

CREATE TABLE IF NOT EXISTS yak_approval_instance (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    flow_code VARCHAR(64) NOT NULL COMMENT '流程编码(发起时校验存在且启用)',
    flow_name VARCHAR(128) NOT NULL COMMENT '发起时快照,流程改名不影响在途单',
    biz_type VARCHAR(64) NOT NULL COMMENT '业务对象类型:MODEL/STANDARD/ACCESS_REQUEST…',
    biz_id VARCHAR(64) NOT NULL COMMENT '业务对象标识(字符串容纳多形态主键)',
    title VARCHAR(256) NOT NULL COMMENT '待办列表展示标题',
    payload_json TEXT NULL COMMENT '审批依据快照 JSON(≤64KB,发起时业务组装,审批人只读)',
    applicant VARCHAR(64) NOT NULL COMMENT '发起人(服务端上下文)',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELED',
    current_level TINYINT NOT NULL DEFAULT 1 COMMENT '当前审批级(终态无意义)',
    active_flag VARCHAR(1) NULL COMMENT 'PENDING 期=Y,终态 NULL(D6 在途唯一)',
    finish_time DATETIME(6) NULL COMMENT '终态时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '最后修改人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删(仅终态可删,留痕)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_inflight (project_id, flow_code, biz_type, biz_id, active_flag),
    KEY idx_approval_inst_applicant (project_id, applicant, status),
    KEY idx_approval_inst_biz (project_id, biz_type, biz_id, id),
    KEY idx_approval_inst_status (project_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批单';

CREATE TABLE IF NOT EXISTS yak_approval_step (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '所属项目空间',
    instance_id BIGINT NOT NULL COMMENT '审批单(无物理外键)',
    level_no TINYINT NOT NULL COMMENT '级序号,1 起',
    approver VARCHAR(64) NOT NULL COMMENT '审批人(发起时快照 D4)',
    status VARCHAR(16) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/PENDING/APPROVED/REJECTED/SKIPPED;终态时剩余全部 SKIPPED→待办查询免 join',
    comment VARCHAR(512) NULL COMMENT '审批意见(拒绝必填 D9)',
    handled_time DATETIME(6) NULL COMMENT '处理时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人(=发起人)',
    updated_by VARCHAR(64) NULL COMMENT '最后修改人',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删',
    PRIMARY KEY (id),
    KEY idx_approval_step_pending (project_id, approver, status, id),
    KEY idx_approval_step_instance (instance_id, level_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批级快照行(=审批记录)';
