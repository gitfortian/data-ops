-- 02 派生指标结构化生成:限定条件(修饰词)升格为 metric 内结构化条件(裁决见 docs/v1/05 乱象清单 R-11)。
-- qualifiers_json: 结构化限定条件数组 [{"field","op","value"}],派生保存时由
-- DerivedMetricAssembler 编译进 filter_expr(原子 filter AND 限定条件),并继承原子
-- measure_expr/model_id/口径/单位。可空:存量派生指标(自由文本 dim_constraint)不迁移不组装。

ALTER TABLE yak_metric
  ADD COLUMN qualifiers_json JSON NULL COMMENT '结构化限定条件(JSON数组,仅派生指标)' AFTER dim_constraint;
