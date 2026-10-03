package io.yak.ops.business.modeling.impact;

import io.yak.ops.business.modeling.mapping.TransformExpressionValidator;
import io.yak.ops.business.modeling.repository.LayerFieldMappingRepository;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.business.modeling.dao.model.ModelingLayerFieldMappingPO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Impact analysis (ticket 46): backend lives in modeling because lineage
 * (45) and std references (43/19 mappings) are modeling-owned data.
 * Standard-field impact comes from the 43 mappings; source-column impact
 * from the 19 source mappings. Analysis is read-only preview.
 */
@Component
public class ImpactAnalysisService {

  private final LayerFieldMappingRepository layerFieldMappingRepository;
  private final MappingRepository mappingRepository;

  public ImpactAnalysisService(
      LayerFieldMappingRepository layerFieldMappingRepository,
      MappingRepository mappingRepository) {
    this.layerFieldMappingRepository = layerFieldMappingRepository;
    this.mappingRepository = mappingRepository;
  }

  /** 影响项。 */
  public record ImpactItem(
      Long modelId,
      Long layerId,
      Long processFieldId,
      String targetColumn,
      String sourceDetail,
      String impactKind) {}

  /** 标准字段变更影响:该标准字段在各模型/各层的全部落地。 */
  public List<ImpactItem> byStandardField(Long processFieldId) {
    List<ImpactItem> items = new ArrayList<>();
    for (ModelingLayerFieldMappingPO po : layerFieldMappingRepository.listByProcessField(processFieldId)) {
      items.add(
          new ImpactItem(
              po.getModelId(),
              po.getLayerId(),
              po.getProcessFieldId(),
              po.getLayerFieldName(),
              po.getLayerDataType() == null ? "" : po.getLayerDataType(),
              "STANDARD_FIELD"));
    }
    return items;
  }

  /** 来源列(或整表)变更影响:引用该来源的模型字段映射。 */
  public List<ImpactItem> bySourceColumn(
      Long datasourceId, String sourceDatabase, String sourceTable, String sourceColumn) {
    List<ImpactItem> items = new ArrayList<>();
    for (ModelingColumnMappingPO po :
        mappingRepository.listBySource(datasourceId, sourceDatabase, sourceTable, sourceColumn)) {
      items.add(
          new ImpactItem(
              po.getModelId(),
              null,
              po.getStdProcessFieldId(),
              po.getTargetColumn(),
              po.getSourceDatabase() + "." + po.getSourceTable() + "." + po.getSourceColumn(),
              "SOURCE_COLUMN"));
    }
    return items;
  }

  /** 表达式守卫复用(46 与 19 同一语法契约)。 */
  public String validateExpression(String expression) {
    return TransformExpressionValidator.validate(expression);
  }
}
