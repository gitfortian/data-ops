package io.yak.ops.business.modeling.view;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.common.bean.po.modeling.ModelingColumnMappingPO;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.structure.ModelStructureRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Source structure change detection (ticket 27, decision A5): covers only the
 * source tables a model actually references (via 19 source mappings).
 * Detection compares current catalog columns against the model's mapping
 * coverage — detection only, no auto-sync (A5/D9).
 */
@Component
public class SourceChangeDetectionService {

  private final MappingRepository mappingRepository;
  private final ModelRepository modelRepository;
  private final ModelStructureRepository structureRepository;
  private final DataSourceCatalogReader catalogReader;

  public SourceChangeDetectionService(
      MappingRepository mappingRepository,
      ModelRepository modelRepository,
      ModelStructureRepository structureRepository,
      DataSourceCatalogReader catalogReader) {
    this.mappingRepository = mappingRepository;
    this.modelRepository = modelRepository;
    this.structureRepository = structureRepository;
    this.catalogReader = catalogReader;
  }

  /** 单张源表的差异。 */
  public record SourceDiff(
      Long datasourceId,
      String sourceDatabase,
      String sourceTable,
      List<String> addedSourceColumns,
      List<String> removedSourceColumns,
      List<TypeDrift> typeDrift) {}

  /** 类型漂移:源列类型与模型列类型不一致。 */
  public record TypeDrift(String sourceColumn, String sourceType, String modelColumn, String modelType) {}

  /** 模型级报告。 */
  public record ModelDiffReport(
      Long modelId, String modelCode, boolean sourcesRegistered, List<SourceDiff> sources) {}

  public ModelDiffReport detect(Long modelId) {
    var model =
        modelRepository
            .findById(modelId)
            .orElseThrow(
                () ->
                    new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
    List<ModelingColumnMappingPO> mappings = mappingRepository.listByModel(modelId);
    if (mappings.isEmpty()) {
      return new ModelDiffReport(modelId, model.code(), false, List.of());
    }
    // 分组:源表 → 该模型引用它的映射。
    Map<String, List<ModelingColumnMappingPO>> bySource = new HashMap<>();
    for (ModelingColumnMappingPO po : mappings) {
      if (po.getSourceDatasourceId() == null) {
        continue;
      }
      String key =
          po.getSourceDatasourceId()
              + "/"
              + (po.getSourceDatabase() == null ? "" : po.getSourceDatabase())
              + "/"
              + po.getSourceTable();
      bySource.computeIfAbsent(key, key2 -> new ArrayList<>()).add(po);
    }
    List<SourceDiff> diffs = new ArrayList<>();
    for (Map.Entry<String, List<ModelingColumnMappingPO>> entry : bySource.entrySet()) {
      ModelingColumnMappingPO first = entry.getValue().get(0);
      List<CatalogColumn> current =
          catalogReader.listColumns(
              first.getSourceDatasourceId(), first.getSourceDatabase(), null,
              first.getSourceTable());
      Set<String> currentNames = new HashSet<>();
      Map<String, CatalogColumn> currentByLower =
          new HashMap<>();
      for (CatalogColumn column : current) {
        currentNames.add(column.name().toLowerCase(Locale.ROOT));
        currentByLower.put(column.name().toLowerCase(Locale.ROOT), column);
      }
      List<String> removed = new ArrayList<>();
      List<TypeDrift> drift = new ArrayList<>();
      Set<String> mappedSourceColumns = new HashSet<>();
      for (ModelingColumnMappingPO po : entry.getValue()) {
        String sourceColumn = po.getSourceColumn() == null
            ? "" : po.getSourceColumn().toLowerCase(Locale.ROOT);
        mappedSourceColumns.add(sourceColumn);
        if (!currentNames.contains(sourceColumn)) {
          removed.add(po.getSourceColumn());
          continue;
        }
        CatalogColumn live = currentByLower.get(sourceColumn);
        String modelType = modelDataType(modelId, po.getTargetColumn());
        if (live != null && modelType != null
            && !live.typeName().equalsIgnoreCase(modelType)) {
          drift.add(
              new TypeDrift(live.name(), live.typeName(), po.getTargetColumn(), modelType));
        }
      }
      List<String> added =
          current.stream()
              .filter(column -> !mappedSourceColumns.contains(column.name()
                  .toLowerCase(Locale.ROOT)))
              .map(CatalogColumn::name)
              .toList();
      diffs.add(
          new SourceDiff(
              first.getSourceDatasourceId(), first.getSourceDatabase(), first.getSourceTable(),
              added, removed, drift));
    }
    return new ModelDiffReport(modelId, model.code(), true, diffs);
  }

  private String modelDataType(Long modelId, String targetColumn) {
    for (io.yak.ops.business.modeling.domain.ColumnDefinition definition :
        structureRepository.findColumns(modelId)) {
      if (definition.columnName().equalsIgnoreCase(targetColumn)) {
        return definition.dataType();
      }
    }
    return null;
  }
}
