-- Derivation support (ticket 44): model row carries the owning business
-- process and target layer (loose references, no physical FK).

ALTER TABLE yak_modeling_model
    ADD COLUMN process_id BIGINT NULL COMMENT '业务过程(semantic 松散引用,44 派生写入)',
    ADD COLUMN layer_code VARCHAR(32) NULL COMMENT '目标分层编码(37 分层,44 派生写入)';

CREATE INDEX idx_yak_modeling_model_process ON yak_modeling_model (process_id);
