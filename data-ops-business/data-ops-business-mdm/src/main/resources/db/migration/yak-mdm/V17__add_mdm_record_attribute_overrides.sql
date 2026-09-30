-- Persist explicit, reviewed field values so the next source processing run
-- can refresh source-managed attributes without erasing governance decisions.
ALTER TABLE yak_mdm_record
    ADD COLUMN attribute_overrides JSON NULL COMMENT '经审批或清洗锁定的属性值';
