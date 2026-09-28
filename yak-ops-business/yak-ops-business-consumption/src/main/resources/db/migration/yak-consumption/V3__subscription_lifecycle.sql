UPDATE yak_ops_consumption_subscription
SET status = 'REVOKED'
WHERE status = 'CANCELLED';

ALTER TABLE yak_ops_consumption_subscription
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE/SUSPENDED/REVOKED';
