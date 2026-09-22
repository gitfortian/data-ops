package io.yak.ops.business.modeling.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.modeling.domain.Model;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for the modeling catalog. */
public interface ModelRepository {

  Model insert(Model model, String operator);

  /** Finds one live (non-deleted) model. */
  Optional<Model> findById(Long id);

  /** Finds one soft-deleted model (recycle bin). */
  Optional<Model> findDeletedById(Long id);

  /** Uniqueness of the business key among live rows. */
  boolean existsByCode(String code);

  /** Finds one live model by business key(08 逆向导入判定"已有空模型"用)。 */
  Optional<Model> findByCode(String code);

  PageData<Model> page(
      int pageNo, int pageSize, String keyword, Long directoryId, List<Long> tagIds,
      String layerCode, Long processId, java.util.List<Long> processIds, String status,
      Long domainId);

  PageData<Model> pageDeleted(int pageNo, int pageSize, String keyword);

  /** Soft delete: move to recycle bin with operator and time recorded. */
  boolean deleteById(Long id, String operator);

  /** 恢复一个回收站模型；operator 写入 updated_by（最后更新人）。 */
  boolean restoreById(Long id, String operator);

  /** Physically remove one soft-deleted model. Irreversible. */
  boolean purgeById(Long id);

  boolean updateDirectory(Long id, Long directoryId, String operator);

  /** 业务域引用(semantic 松散 ID,新建模型向导写入;null 清空)。 */
  boolean updateDomain(Long id, Long domainId, String operator);

  /** Updates editable basics (name/dialect/description); code is immutable. */
  boolean updateBasics(Long id, String name, String dialect, String description, String operator);

  long countByDirectory(Long directoryId);

  /** 44 派生写入:业务过程/目标分层引用(松散 ID)。 */
  boolean assignProcess(Long modelId, Long processId, String layerCode, String operator);

  /** 08 逆向导入写入:目标分层引用(松散 code,不改业务过程)。 */
  boolean assignLayer(Long modelId, String layerCode, String operator);

  /** 47 主线视图:按业务过程列出模型 ID(项目内)。 */
  List<Long> modelIdsByProcess(Long processId);

  /** 08/44:按来源绑定反查模型(同一源表在项目内一表一模型,返回列表以容错历史数据)。 */
  List<Model> listBySource(Long sourceDatasourceId, String sourceTable);

  /** 51/52:按业务过程 + 分层列出上游候选模型(聚合/应用层的上游是模型而非数据源表)。 */
  List<Model> listByProcessLayer(Long processId, String layerCode);

  /** 51/52:写入统计周期与应用/报表绑定。 */
  boolean assignAggregateMeta(
      Long modelId, String statPeriod, String appCode, String appName, String operator);

  /** 08 逆向导入:写入来源绑定(补 08 票"来源标记"缺口);datasourceId/table 为空则不动。 */
  boolean assignSource(
      Long modelId, Long sourceDatasourceId, String sourceDatabase, String sourceTable,
      String operator);

  /** 血缘追溯:写入字段导入方式和来源模型。 */
  boolean assignImportLineage(Long modelId, String importMode, Long sourceModelId, String operator);

  /** 版本发布:更新状态、发布版本指针和最新版本号。 */
  boolean updatePublishState(
      Long modelId, String status, Long publishedVersionId, int latestVersionNo, String operator);

  /** 仅更新状态(保存结构时重置为 DRAFT)。 */
  boolean updateStatus(Long modelId, String status, String operator);
}
