package io.yak.ops.business.modeling.importer;

import io.yak.ops.business.modeling.api.ModelingStructureApi;
import io.yak.ops.business.modeling.catalog.ModelCatalogService;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.mapping.MappingService;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.Objects;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Transactional write half of reverse import (ticket 08): one table's model row,
 * its physical table structure and its layer reference commit or roll back
 * together, so a failed import never leaves a model without fields behind. Kept
 * as its own bean because Spring's proxy only applies {@code @Transactional} to
 * calls that cross a bean boundary.
 */
@Component
public class ReverseImportWriter {

  private final ModelCatalogService catalogService;
  private final ModelStructureService structureService;
  private final ModelRepository modelRepository;
  private final MappingService mappingService;

  public ReverseImportWriter(
      ModelCatalogService catalogService,
      ModelStructureService structureService,
      ModelRepository modelRepository,
      MappingService mappingService) {
    this.catalogService = catalogService;
    this.structureService = structureService;
    this.modelRepository = modelRepository;
    this.mappingService = mappingService;
  }

  /** 新建模型 + 写入表结构 + 落目标分层,同一事务。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Model createWithStructure(ReverseImportPlan plan, String operator) {
    Model model =
        catalogService.create(
            plan.name(), plan.code(), plan.dialect(), plan.description(),
            operator, plan.directoryId(), plan.layerCode(), null);
    writeStructure(model.id(), plan, operator);
    return model;
  }

  /** 已有空模型补字段 + 落目标分层,同一事务。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void fillStructure(Long modelId, ReverseImportPlan plan, String operator) {
    writeStructure(modelId, plan, operator);
  }

  private void writeStructure(Long modelId, ReverseImportPlan plan, String operator) {
    // Keep the original source of an existing model; never quietly rebind it.
    Model existing = modelRepository.findByIdForUpdate(modelId)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
    if (existing.sourceDatasourceId() != null && plan.sourceDatasourceId() != null
        && (!Objects.equals(existing.sourceDatasourceId(), plan.sourceDatasourceId())
            || !Objects.equals(existing.sourceDatabase(), plan.sourceDatabase())
            || !Objects.equals(existing.sourceTable(), plan.sourceTable()))) {
      throw new ModelingException(ModelingErrorCode.INVALID_COLUMN,
          "已有模型绑定不同来源，不能在导入时覆盖");
    }
    structureService.save(
        modelId,
        new ModelingStructureApi.SaveStructureRequest(
            plan.tableName(),
            plan.description(),
            plan.columns(),
            plan.primaryKey(),
            List.of(),
            plan.partitionExpr() == null
                ? null
                : new ModelingStructureApi.PartitionInput(
                    "LIST", List.of("event_time"), plan.partitionExpr()),
            null),
        operator);
    if (StringUtils.hasText(plan.layerCode())) {
      modelRepository.assignLayer(modelId, plan.layerCode(), operator);
    }
    if (plan.sourceDatasourceId() != null) {
      if (existing.sourceDatasourceId() == null) {
        modelRepository.assignSource(
            modelId, plan.sourceDatasourceId(), plan.sourceDatabase(), plan.sourceTable(), operator);
      }
      // Only real catalog columns become direct source mappings. Generated
      // technical fields have no source unless the catalog actually contains them.
      for (String sourceColumn : plan.sourceColumnNames()) {
        mappingService.setMapping(modelId, sourceColumn, plan.sourceDatasourceId(),
            plan.sourceDatabase(), plan.sourceTable(), sourceColumn, null, operator);
      }
    }
  }
}
