package io.yak.ops.business.modeling.repository;

import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for column source mappings. */
public interface MappingRepository {

  ModelingColumnMappingPO upsert(ModelingColumnMappingPO po, String operator);

  List<ModelingColumnMappingPO> listByModel(Long modelId);

  Optional<ModelingColumnMappingPO> findByTargetColumn(Long modelId, String targetColumn);

  boolean deleteByTargetColumn(Long modelId, String targetColumn);

  int deleteByModel(Long modelId);

  /** 46 影响分析:按来源表(可含源列)反查引用它的映射。 */
  List<ModelingColumnMappingPO> listBySource(
      Long datasourceId, String sourceDatabase, String sourceTable, String sourceColumn);
}
