package io.yak.ops.business.modeling.version;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.repository.ModelVersionRepository;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 消费方（血缘登记、指标/派生建模、资产投影、DDL 下发）读取模型结构的唯一入口：
 * 一律按 {@code published_version_id} 读发布快照——改草稿不发布，消费侧看到的仍是旧发布版。
 *
 * <p>存量兜底：状态已是 PUBLISHED 但从未有过版本行的老模型，首次读取时自动补发 v1
 * （发布本身幂等，不会重复追加）；从未发布的 DRAFT 模型回退读活表（设计期语义）。
 */
@Component
public class ModelPublishedStructureReader {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String BACKFILL_OPERATOR = "system:legacy-backfill";

  private final ModelRepository modelRepository;
  private final ModelVersionRepository versionRepository;
  private final ModelVersionService versionService;

  public ModelPublishedStructureReader(
      ModelRepository modelRepository,
      ModelVersionRepository versionRepository,
      ModelVersionService versionService) {
    this.modelRepository = modelRepository;
    this.versionRepository = versionRepository;
    this.versionService = versionService;
  }

  public StructureView publishedStructure(Long modelId) {
    Model model = requireModel(modelId);
    Long pointer = model.publishedVersionId();
    if (pointer == null && ModelStatus.PUBLISHED == model.status()) {
      versionService.publish(modelId, BACKFILL_OPERATOR);
      pointer = requireModel(modelId).publishedVersionId();
    }
    if (pointer == null) {
      // 从未发布：无快照可读，消费方维持既有语义读活表（派生/血缘只对已发布模型开放）。
      return liveFallback(modelId);
    }
    StructureView snapshot = versionRepository.findById(pointer, modelId)
        .map(version -> deserialise(version.structureJson()))
        .orElseGet(() -> {
          // 指针悬空（版本行缺失）时自愈：以当前活表补发一次发布。
          versionService.publish(modelId, BACKFILL_OPERATOR);
          Long fixed = requireModel(modelId).publishedVersionId();
          return fixed == null
              ? liveFallback(modelId)
              : versionRepository.findById(fixed, modelId)
                  .map(version -> deserialise(version.structureJson()))
                  .orElseGet(() -> liveFallback(modelId));
        });
    return withModelId(snapshot, modelId);
  }

  public List<ColumnDefinition> publishedColumns(Long modelId) {
    return toColumns(publishedStructure(modelId));
  }

  public List<IndexDefinition> publishedIndexes(Long modelId) {
    return toIndexes(publishedStructure(modelId));
  }

  public static List<ColumnDefinition> toColumns(StructureView structure) {
    return structure.columns() == null ? List.of() : structure.columns().stream()
        .map(column -> new ColumnDefinition(
            column.id(),
            column.columnName(),
            column.dataType(),
            column.length(),
            column.scale(),
            column.nullable(),
            column.defaultValue(),
            column.comment(),
            column.businessDescription(),
            column.sortOrder(),
            column.stdTypeId(),
            column.stdNamingId(),
            column.stdCodeSetCode(),
            column.stdUnitId(),
            column.stdCaliberId(),
            column.stdSecurityId(),
            column.stdFieldId(),
            column.fieldRole(),
            column.aggregateFunc(),
            column.transformExpr()))
        .toList();
  }

  public static List<IndexDefinition> toIndexes(StructureView structure) {
    return structure.indexes() == null ? List.of() : structure.indexes().stream()
        .map(index -> new IndexDefinition(
            index.id(), index.indexName(), index.uniqueIndex(), index.indexType(),
            index.columns()))
        .toList();
  }

  private StructureView liveFallback(Long modelId) {
    return versionService.liveStructure(modelId);
  }

  private StructureView withModelId(StructureView view, Long modelId) {
    if (view.modelId() != null) {
      return view;
    }
    return new StructureView(
        modelId, view.modelCode(), view.modelName(), view.dialect(), view.status(),
        view.modelDescription(), view.tableName(), view.tableComment(), view.columns(),
        view.primaryKey(), view.indexes(), view.partition(), view.tableProperties());
  }

  private Model requireModel(Long modelId) {
    return modelRepository
        .findById(modelId)
        .orElseThrow(
            () -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
  }

  private static StructureView deserialise(String json) {
    try {
      return MAPPER.readValue(json, StructureView.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("版本快照反序列化失败", e);
    }
  }
}
