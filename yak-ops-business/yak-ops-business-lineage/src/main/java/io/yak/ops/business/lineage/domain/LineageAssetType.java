package io.yak.ops.business.lineage.domain;

/** First-stage asset types shared by data development, catalog and BI consumption. */
public enum LineageAssetType {
  TABLE,
  COLUMN,
  SQL_TASK,
  DATASET,
  DATASET_FIELD,
  CHART,
  DASHBOARD,
  METRIC,
  // 元数据目录侧的物理/组织节点：asset_type NOT NULL 且读行时 valueOf，缺一个就登记不进来。
  DATABASE_SERVICE,
  DATABASE,
  DOMAIN
}
