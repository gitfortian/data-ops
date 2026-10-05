ALTER TABLE yak_approval_instance
    ADD COLUMN cancel_reason VARCHAR(512) NULL COMMENT '申请人撤销原因';
