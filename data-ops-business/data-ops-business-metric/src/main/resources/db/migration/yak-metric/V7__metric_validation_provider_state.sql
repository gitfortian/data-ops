ALTER TABLE yak_metric_validation_evidence
    ADD COLUMN provider_state VARCHAR(16) NOT NULL DEFAULT 'READY'
    COMMENT '证据覆盖状态:READY/UNAVAILABLE/FORBIDDEN'
    AFTER result;

UPDATE yak_metric_validation_evidence
SET result = CASE result
      WHEN 'READY' THEN 'PASSED'
      WHEN 'BLOCKED' THEN 'FAILED'
      ELSE 'NOT_APPLICABLE'
    END,
    provider_state = 'READY';
