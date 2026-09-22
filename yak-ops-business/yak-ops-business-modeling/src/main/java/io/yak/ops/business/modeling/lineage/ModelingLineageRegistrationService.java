package io.yak.ops.business.modeling.lineage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.lineage.registration.LineageRegistrationService;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.LayerFieldMappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.version.ModelPublishedStructureReader;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.common.bean.po.modeling.ModelingLayerFieldMappingPO;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guided lineage registration (ticket 23): user-confirmed, idempotent
 * (upsert by assetKey). The model becomes a TABLE asset, its columns COLUMN
 * assets with a CONTAINS relation each. Column properties carry the std
 * references (stdProcessFieldId from the 43 mapping table) that power
 * standard-field lineage (45).
 */
@Component
public class ModelingLineageRegistrationService {

  private static final Logger log = LoggerFactory.getLogger(ModelingLineageRegistrationService.class);
  private static final String SOURCE_TYPE = "MODELING";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final LineageRegistrationService registrationService;
  private final LineageQueryService lineageQueryService;
  private final ModelRepository modelRepository;
  private final ModelPublishedStructureReader structureReader;
  private final LayerFieldMappingRepository layerFieldMappingRepository;
  private final ProcessApi processApi;
  private final BusinessAuditService auditService;

  public ModelingLineageRegistrationService(
      LineageRegistrationService registrationService,
      LineageQueryService lineageQueryService,
      ModelRepository modelRepository,
      ModelPublishedStructureReader structureReader,
      LayerFieldMappingRepository layerFieldMappingRepository,
      ProcessApi processApi,
      BusinessAuditService auditService) {
    this.registrationService = registrationService;
    this.lineageQueryService = lineageQueryService;
    this.modelRepository = modelRepository;
    this.structureReader = structureReader;
    this.layerFieldMappingRepository = layerFieldMappingRepository;
    this.processApi = processApi;
    this.auditService = auditService;
  }

  public record RegisterView(Long tableAssetId, int columnCount, int standardFieldCount) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public RegisterView registerModel(Long modelId, String operator) {
    Model model =
        modelRepository
            .findById(modelId)
            .orElseThrow(
                () -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
    List<ColumnDefinition> columns = structureReader.publishedColumns(modelId);
    Map<String, Long> stdFieldByColumn = stdFieldIdsByColumn(modelId);

    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MODELING_LINEAGE_REGISTER",
                "Register model lineage",
                "MODELING_MODEL",
                String.valueOf(modelId),
                model.code(),
                "APPLICATION",
                Map.of("columns", String.valueOf(columns.size()))));
    try {
      LineageAsset tableAsset =
          registrationService.registerAsset(
              new LineageRegistrationService.RegisterAssetCommand(
                  modelAssetKey(modelId),
                  LineageAssetType.TABLE,
                  model.name(),
                  SOURCE_TYPE,
                  String.valueOf(modelId),
                  null,
                  null,
                  null,
                  null,
                  tableNameOf(modelId, model),
                  null,
                  tableProperties(model),
                  null));
      int columnCount = 0;
      for (ColumnDefinition column : columns) {
        Long stdFieldId = stdFieldByColumn.get(column.columnName().toLowerCase());
        LineageAsset columnAsset =
            registrationService.registerAsset(
                new LineageRegistrationService.RegisterAssetCommand(
                    modelAssetKey(modelId) + ":column:" + column.columnName(),
                    LineageAssetType.COLUMN,
                    column.columnName(),
                    SOURCE_TYPE,
                    modelId + ":" + column.columnName(),
                    tableAsset.id(),
                    null,
                    null,
                    null,
                    tableNameOf(modelId, model),
                    column.columnName(),
                    columnProperties(column, stdFieldId),
                    null));
        registrationService.registerRelation(
            new LineageRegistrationService.RegisterRelationCommand(
                tableAsset.id(),
                columnAsset.id(),
                LineageRelationType.CONTAINS,
                SOURCE_TYPE,
                modelId + ":" + column.columnName(),
                null,
                null,
                "v1",
                Instant.now(),
                null,
                null));
        columnCount++;
      }
      // 45:标准字段级血缘——落地字段 DERIVES_FROM 标准字段(基于 43 映射)。
      int standardFieldCount = registerStandardFieldLineage(modelId, columns);
      // 模型间导入血缘:当 importMode=MODEL 时注册 READS_FROM 关系。
      registerModelToModelLineage(modelId, model, tableAsset);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Model lineage registered",
          Map.of("columns", String.valueOf(columnCount),
              "standardFields", String.valueOf(standardFieldCount)),
          "Model lineage registered");
      return new RegisterView(tableAsset.id(), columnCount, standardFieldCount);
    } catch (RuntimeException exception) {
      audit.failure("MODELING_LINEAGE_REGISTER_FAILED", exception);
      throw exception;
    }
  }

  /** 45:标准字段资产 + 落地字段 DERIVES_FROM 关系(幂等 upsert)。 */
  private int registerStandardFieldLineage(
      Long modelId, List<ColumnDefinition> columns) {
    Map<String, Long> stdByColumn = stdFieldIdsByColumn(modelId);
    if (stdByColumn.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (ColumnDefinition column : columns) {
      Long stdFieldId = stdByColumn.get(column.columnName().toLowerCase());
      if (stdFieldId == null) {
        continue;
      }
      StandardField field = processApi.getField(stdFieldId);
      if (field == null) {
        continue;
      }
      LineageAsset fieldAsset =
          registrationService.registerAsset(
              new LineageRegistrationService.RegisterAssetCommand(
                  "semantic:field:" + stdFieldId,
                  LineageAssetType.COLUMN,
                  field.name(),
                  "SEMANTIC",
                  String.valueOf(stdFieldId),
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  fieldProperties(field),
                  null));
      LineageAsset columnAsset =
          registrationService.registerAsset(
              new LineageRegistrationService.RegisterAssetCommand(
                  modelAssetKey(modelId) + ":column:" + column.columnName(),
                  LineageAssetType.COLUMN,
                  column.columnName(),
                  SOURCE_TYPE,
                  modelId + ":" + column.columnName(),
                  null,
                  null,
                  null,
                  null,
                  tableNameOf(modelId, modelRepository.findById(modelId).orElseThrow()),
                  column.columnName(),
                  null,
                  null));
      registrationService.registerRelation(
          new LineageRegistrationService.RegisterRelationCommand(
              fieldAsset.id(),
              columnAsset.id(),
              LineageRelationType.DERIVES_FROM,
              SOURCE_TYPE,
              modelId + ":" + column.columnName(),
              null,
              null,
              "v1",
              Instant.now(),
              null,
              null));
      count++;
    }
    return count;
  }

  private static ObjectNode fieldProperties(StandardField field) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("fieldCode", field.code());
    node.put("role", field.role());
    if (field.stdTypeId() != null) node.put("stdTypeId", field.stdTypeId());
    return node;
  }

  /** 45 预留:列名 → 标准字段 ID(来自 43 分层映射)。 */
  private Map<String, Long> stdFieldIdsByColumn(Long modelId) {
    Map<String, Long> result = new HashMap<>();
    for (ModelingLayerFieldMappingPO po : layerFieldMappingRepository.listByModel(modelId)) {
      result.putIfAbsent(po.getLayerFieldName().toLowerCase(), po.getProcessFieldId());
    }
    return result;
  }

  private String tableNameOf(Long modelId, Model model) {
    String tableName = structureReader.publishedStructure(modelId).tableName();
    return tableName == null || tableName.isBlank() ? model.code() : tableName;
  }

  private static ObjectNode tableProperties(Model model) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("modelCode", model.code());
    node.put("dialect", model.dialect() == null ? null : model.dialect().name());
    return node;
  }

  private static ObjectNode columnProperties(ColumnDefinition column, Long stdFieldId) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("dataType", column.dataType());
    if (column.stdTypeId() != null) node.put("stdTypeId", column.stdTypeId());
    if (column.stdNamingId() != null) node.put("stdNamingId", column.stdNamingId());
    if (column.stdCodeSetCode() != null) node.put("stdCodeSetCode", column.stdCodeSetCode());
    if (column.stdUnitId() != null) node.put("stdUnitId", column.stdUnitId());
    if (column.stdCaliberId() != null) node.put("stdCaliberId", column.stdCaliberId());
    if (column.stdSecurityId() != null) node.put("stdSecurityId", column.stdSecurityId());
    if (stdFieldId != null) node.put("stdProcessFieldId", stdFieldId);
    return node;
  }

  /** 模型血缘登记键(全仓唯一出处:asset 对账与血缘登记同源复用,禁止在别处复制字面量)。 */
  public static String modelAssetKey(Long modelId) {
    return "modeling:model:" + modelId;
  }

  /** 模型间导入血缘:当 importMode=MODEL 且 sourceModelId 存在时注册 READS_FROM 关系。 */
  private void registerModelToModelLineage(Long modelId, Model model, LineageAsset tableAsset) {
    if (!"MODEL".equals(model.importMode()) || model.sourceModelId() == null) {
      return;
    }
    String sourceKey = modelAssetKey(model.sourceModelId());
    try {
      LineageAsset sourceAsset = lineageQueryService.getAssetByKey(sourceKey);
      registrationService.registerRelation(
          new LineageRegistrationService.RegisterRelationCommand(
              sourceAsset.id(),
              tableAsset.id(),
              LineageRelationType.READS_FROM,
              SOURCE_TYPE,
              modelId + ":import-lineage",
              null,
              null,
              "v1",
              Instant.now(),
              null,
              null));
    } catch (RuntimeException e) {
      log.debug("Skip model-to-model lineage: sourceModelId={}, reason={}",
          model.sourceModelId(), e.getMessage());
    }
  }

}
