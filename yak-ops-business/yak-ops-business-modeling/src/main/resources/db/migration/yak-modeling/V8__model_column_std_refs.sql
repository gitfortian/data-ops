-- Data-standard reference reservation (ticket 30, M4 cross-module contract).
--
-- Loose-ID references into yak-ops-business-semantic (no physical FK, per
-- PROJECT_SCOPE convention). Written by tickets 38/39/44; read-path passes
-- them through until then (always NULL in this migration's scope).

ALTER TABLE yak_modeling_model_column
    ADD COLUMN std_type_id BIGINT NULL COMMENT '类型标准引用(semantic 松散 ID)',
    ADD COLUMN std_naming_id BIGINT NULL COMMENT '命名标准引用(semantic 松散 ID)',
    ADD COLUMN std_code_set_code VARCHAR(64) NULL COMMENT '码集编码引用(CODE 类标准的 code_set_code,松散引用)',
    ADD COLUMN std_unit_id BIGINT NULL COMMENT '单位标准引用(semantic 松散 ID)',
    ADD COLUMN std_caliber_id BIGINT NULL COMMENT '口径标准引用(semantic 松散 ID)',
    ADD COLUMN std_security_id BIGINT NULL COMMENT '安全标准引用(semantic 松散 ID)';

CREATE INDEX idx_yak_modeling_model_column_std_type
    ON yak_modeling_model_column (std_type_id);
