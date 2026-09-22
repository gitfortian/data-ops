package io.yak.ops.common.constant.metadata;

import java.util.List;

/**
 * {@code LineageAssetType} 常量名的镜像清单。
 *
 * <p><b>为什么是镜像而不是 import</b>：该枚举在 lineage 的 {@code domain} 内部包里
 * （{@code yak-ops-business-lineage/…/lineage/domain/LineageAssetType.java}），
 * 跨模块 import 内部包违反硬性约束（plan §0.3）。而 {@code asset_type} 是 NOT NULL 且 lineage 读行时对每行做
 * {@code LineageAssetType.valueOf(...)}——写进一个不在该枚举里的名字，<b>炸的是别人的血缘查询</b>
 * （plan §2.3 后果 1）。所以校验必须做，且必须防"枚举改了名、这里没跟上"。
 *
 * <p>防线是 {@code LineageAssetTypeMirrorTest}：它读 lineage 的源码解析出真实常量集，
 * 与本清单逐项比对。将来 lineage 增删/改名常量时 <b>CI 先红</b>，而不是线上读行才炸。
 *
 * <p>ticket 134 已给该枚举补上 {@code DATABASE_SERVICE} / {@code DATABASE} / {@code DOMAIN}，
 * {@code yak_md_type_def} 对应三行的 {@code lineage_asset_type} 也随票补齐（metadata V3 迁移）。
 */
public final class MetadataLineageAssetTypes {

  public static final List<String> NAMES =
      List.of(
          "TABLE",
          "COLUMN",
          "SQL_TASK",
          "DATASET",
          "DATASET_FIELD",
          "CHART",
          "DASHBOARD",
          "METRIC",
          "DATABASE_SERVICE",
          "DATABASE",
          "DOMAIN");

  public static boolean isKnown(String name) {
    return name != null && NAMES.stream().anyMatch(name::equals);
  }

  private MetadataLineageAssetTypes() {}
}
