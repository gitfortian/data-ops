package io.yak.ops.business.modeling.mapping;

import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.LayerFieldMappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.semantic.api.LayerConfigApi;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.modeling.dao.model.ModelingLayerFieldMappingPO;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Layer-field mapping rules (ticket 43): process field and layer must exist
 * (resolved via semantic SPI), expressions pass the syntax guard, display
 * names are resolved through the SPI (no semantic table joins). Rows are
 * mostly auto-written by derivation (44); manual edits are corrections.
 */
@Component
public class LayerFieldMappingService {

  private final LayerFieldMappingRepository repository;
  private final ModelRepository modelRepository;
  private final ProcessApi processApi;
  private final LayerConfigApi layerConfigApi;

  public LayerFieldMappingService(
      LayerFieldMappingRepository repository,
      ModelRepository modelRepository,
      ProcessApi processApi,
      LayerConfigApi layerConfigApi) {
    this.repository = repository;
    this.modelRepository = modelRepository;
    this.processApi = processApi;
    this.layerConfigApi = layerConfigApi;
  }

  /** 视图:映射 + SPI 解析的展示名。 */
  public record LayerFieldMappingView(
      Long id,
      Long modelId,
      Long processFieldId,
      String processFieldCode,
      String processFieldName,
      Long layerId,
      String layerCode,
      String layerName,
      String layerFieldName,
      String layerDataType,
      String sourceField,
      String transformExpr) {}

  public List<LayerFieldMappingView> listByModel(Long modelId) {
    requireModel(modelId);
    List<LayerFieldMappingView> views = new ArrayList<>();
    for (ModelingLayerFieldMappingPO po : repository.listByModel(modelId)) {
      views.add(toView(po));
    }
    return views;
  }

  /** 按标准字段查看其各层落地(45 向下血缘的数据基础)。 */
  public List<LayerFieldMappingView> listByProcessField(Long processFieldId) {
    List<LayerFieldMappingView> views = new ArrayList<>();
    for (ModelingLayerFieldMappingPO po : repository.listByProcessField(processFieldId)) {
      views.add(toView(po));
    }
    return views;
  }

  /** 补录/修正一条映射(自动写入主路径在 44)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void upsert(
      Long modelId,
      Long processFieldId,
      Long layerId,
      String layerFieldName,
      String layerDataType,
      String sourceField,
      String transformExpr,
      String operator) {
    requireModel(modelId);
    StandardField field = requireProcessField(processFieldId);
    WarehouseLayer layer = requireLayer(layerId);
    if (!StringUtils.hasText(layerFieldName)) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "落地字段名不能为空");
    }
    String expressionError = TransformExpressionValidator.validate(transformExpr);
    if (expressionError != null) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, expressionError);
    }
    ModelingLayerFieldMappingPO po = new ModelingLayerFieldMappingPO();
    po.setModelId(modelId);
    po.setProcessFieldId(field.id());
    po.setLayerId(layer.id());
    po.setLayerFieldName(layerFieldName.trim());
    po.setLayerDataType(layerDataType);
    po.setSourceField(sourceField);
    po.setTransformExpr(transformExpr);
    repository.upsert(po, operator);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    if (!repository.deleteById(id)) {
      throw new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(id));
    }
  }

  private StandardField requireProcessField(Long processFieldId) {
    StandardField field = processApi.getField(processFieldId);
    if (field == null) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "标准字段不存在：" + processFieldId);
    }
    return field;
  }

  private WarehouseLayer requireLayer(Long layerId) {
    try {
      WarehouseLayer layer = layerConfigApi.resolve(layerId);
      if (layer == null) {
        throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "分层不存在：" + layerId);
      }
      return layer;
    } catch (io.yak.ops.business.semantic.exception.SemanticException exception) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN, "分层不存在：" + layerId);
    }
  }

  private void requireModel(Long modelId) {
    modelRepository
        .findById(modelId)
        .orElseThrow(
            () -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
  }

  private LayerFieldMappingView toView(ModelingLayerFieldMappingPO po) {
    StandardField field = processApi.getField(po.getProcessFieldId());
    WarehouseLayer layer = null;
    try {
      layer = layerConfigApi.resolve(po.getLayerId());
    } catch (RuntimeException ignored) {
      // 分层被删时展示 ID,不阻断列表。
    }
    return new LayerFieldMappingView(
        po.getId(),
        po.getModelId(),
        po.getProcessFieldId(),
        field == null ? null : field.code(),
        field == null ? null : field.name(),
        po.getLayerId(),
        layer == null ? null : layer.code(),
        layer == null ? null : layer.name(),
        po.getLayerFieldName(),
        po.getLayerDataType(),
        po.getSourceField(),
        po.getTransformExpr());
  }
}
