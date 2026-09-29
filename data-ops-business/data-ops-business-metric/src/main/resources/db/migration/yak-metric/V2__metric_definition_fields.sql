-- Metric definition enhancement: add fields for atomic/derived/composite differentiation.
-- process_id: business process reference (atomic=required, derived=optional)
-- measure_expr: measure expression for atomic metrics, e.g. SUM(order_amount)
-- filter_expr: filter condition for atomic metrics
-- dim_model_ids: DIM model references (JSON array) for atomic metrics
-- ref_metric_id: referenced atomic metric ID (derived metrics only)
-- dim_constraint: dimension constraint expression (derived metrics only)

ALTER TABLE yak_metric
  ADD COLUMN process_id BIGINT NULL COMMENT '业务过程引用(原子必填,派生可选)' AFTER domain_id,
  ADD COLUMN measure_expr VARCHAR(512) NULL COMMENT '度量表达式,如 SUM(order_amount)' AFTER cal_rule,
  ADD COLUMN filter_expr VARCHAR(1024) NULL COMMENT '过滤条件,如 order_status != 已取消' AFTER measure_expr,
  ADD COLUMN dim_model_ids JSON NULL COMMENT 'DIM模型引用(JSON数组)' AFTER filter_expr,
  ADD COLUMN ref_metric_id BIGINT NULL COMMENT '引用的原子指标ID(仅派生指标)' AFTER dim_model_ids,
  ADD COLUMN dim_constraint VARCHAR(1024) NULL COMMENT '维度限定(仅派生指标)' AFTER ref_metric_id;
