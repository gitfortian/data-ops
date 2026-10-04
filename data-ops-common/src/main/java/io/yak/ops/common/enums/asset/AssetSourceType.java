package io.yak.ops.common.enums.asset;

/** 资产来源域(provider 路由键);MANUAL 为手工登记,不参与对账。 */
public enum AssetSourceType {
  MODEL,
  METRIC,
  METADATA,
  DATASET,
  DATA_SERVICE,
  DASHBOARD,
  CHART,
  TASK,
  MANUAL
}
