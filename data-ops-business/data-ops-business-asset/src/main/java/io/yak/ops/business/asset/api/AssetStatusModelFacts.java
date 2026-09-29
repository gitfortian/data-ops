package io.yak.ops.business.asset.api;

import java.util.Optional;

/**
 * 资产状态条(PLATFORM_CORE_FLOW M2-1)需要建模源域提供的只读事实。
 * 由 modeling 实现;asset 经 ObjectProvider 松耦合消费,缺装配即该格降级为 NA。
 */
public interface AssetStatusModelFacts {

  /**
   * @param layerCode       目标分层编码(定标强制口径输入)
   * @param stdMandatory    该分层是否强制字段落标(M2-5 分层配置携带);分层未登记时为 null
   * @param datasourceId    物理落点数据源(来自分层配置)
   * @param databaseName    物理落点库(来自分层配置)
   * @param schemaName      物理落点 schema(方言无 schema 时为 null)
   * @param tableName       物理表名(tableName 空则 modelCode 兜底,与血缘登记同口径)
   * @param columnTotal     模型字段总数
   * @param stdBoundColumns 已绑定标准字段(std_field_id 非空)的字段数
   * @param modelStatus     模型状态 DRAFT/PUBLISHED/DISABLED
   * @param hasTimePartition 是否配置了时间分区(TTL 适用性)
   */
  record ModelFacts(String layerCode, Boolean stdMandatory, Long datasourceId, String databaseName,
      String schemaName, String tableName, long columnTotal, long stdBoundColumns,
      String modelStatus, boolean hasTimePartition) {}

  /** sourceId 为建模模型主键字符串;模型不存在或已删除返回 empty。 */
  Optional<ModelFacts> modelFacts(String sourceId);
}
