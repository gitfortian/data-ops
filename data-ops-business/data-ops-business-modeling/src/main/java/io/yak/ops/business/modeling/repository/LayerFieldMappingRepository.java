package io.yak.ops.business.modeling.repository;

import io.yak.ops.business.modeling.dao.model.ModelingLayerFieldMappingPO;
import java.util.List;

/** Project-scoped persistence boundary for layer-field mappings. */
public interface LayerFieldMappingRepository {

  ModelingLayerFieldMappingPO upsert(ModelingLayerFieldMappingPO po, String operator);

  List<ModelingLayerFieldMappingPO> listByModel(Long modelId);

  List<ModelingLayerFieldMappingPO> listByProcessField(Long processFieldId);

  boolean existsBy(Long modelId, Long processFieldId, Long layerId, String layerFieldName);

  boolean deleteById(Long id);
}
