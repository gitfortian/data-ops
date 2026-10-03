package io.yak.ops.business.modeling.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
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
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.repository.LayerFieldMappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.modeling.version.ModelPublishedStructureReader;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardField;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 血缘登记单元测试:资产/关系 upsert、stdProcessFieldId 预留透传。 */
class ModelingLineageRegistrationServiceTest {

  private LineageRegistrationService registrationService;
  private LineageQueryService lineageQueryService;
  private ModelRepository modelRepository;
  private ModelPublishedStructureReader structureReader;
  private LayerFieldMappingRepository layerFieldMappingRepository;
  private ProcessApi processApi;
  private BusinessAuditService auditService;
  private ModelingLineageRegistrationService service;

  @BeforeEach
  void setUp() {
    registrationService = Mockito.mock(LineageRegistrationService.class);
    lineageQueryService = Mockito.mock(LineageQueryService.class);
    modelRepository = Mockito.mock(ModelRepository.class);
    structureReader = Mockito.mock(ModelPublishedStructureReader.class);
    layerFieldMappingRepository = Mockito.mock(LayerFieldMappingRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    AuditOperationHandle audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    when(modelRepository.findById(1L))
        .thenReturn(
            Optional.of(
                new Model(
                    1L, "trade_order", "订单", ModelDialect.MYSQL, null, ModelStatus.DRAFT,
                    null, null, null, null, null, null, null, null, null)));
    when(structureReader.publishedStructure(1L)).thenReturn(new StructureView(
        1L, "trade_order", "订单", null, null, null,
        "trade_order", null, List.of(), List.of(), List.of(),
        new StructureView.PartitionView(null, List.of(), null), Map.of()));
    when(structureReader.publishedColumns(1L))
        .thenReturn(
            List.of(
                new ColumnDefinition(1L, "id", "BIGINT", null, null, false, null, null, null, 0),
                new ColumnDefinition(2L, "user_phone", "VARCHAR", 32, null, true, null, null,
                    null, 1, null, null, null, null, null, 6L)));
    // 43 映射:落地字段 user_phone → 标准字段 9
    io.yak.ops.business.modeling.dao.model.ModelingLayerFieldMappingPO mapping =
        new io.yak.ops.business.modeling.dao.model.ModelingLayerFieldMappingPO();
    mapping.setLayerFieldName("user_phone");
    mapping.setProcessFieldId(9L);
    when(layerFieldMappingRepository.listByModel(1L)).thenReturn(List.of(mapping));
    when(registrationService.registerAsset(any()))
        .thenAnswer(
            invocation -> {
              LineageRegistrationService.RegisterAssetCommand command = invocation.getArgument(0);
              long id = command.assetType() == LineageAssetType.TABLE ? 100L : 200L;
              return new LineageAsset(
                  id, command.assetKey(), command.assetType(), command.name(), null, null, null,
                  null, null, null, null, null, null, null, null);
            });
    processApi = Mockito.mock(ProcessApi.class);
    lenient()
        .when(processApi.getField(9L))
        .thenReturn(
            new StandardField(
                9L, "user_phone_std", "用户手机号", "DIMENSION",
                StandardField.STATUS_ENABLED, null, null, null, null, null, null, null,
                StandardField.SOURCE_MANUAL, 1, false, null, null, null));
    service =
        new ModelingLineageRegistrationService(
            registrationService, lineageQueryService, modelRepository, structureReader,
            layerFieldMappingRepository, processApi, auditService);
  }

  @Test
  void registerModelCreatesTableAndColumnAssetsWithContainsRelations() {
    ModelingLineageRegistrationService.RegisterView view = service.registerModel(1L, "tester");

    assertEquals(100L, view.tableAssetId());
    assertEquals(2, view.columnCount());
    ArgumentCaptor<LineageRegistrationService.RegisterAssetCommand> assetCaptor =
        ArgumentCaptor.forClass(LineageRegistrationService.RegisterAssetCommand.class);
    Mockito.verify(registrationService, times(5)).registerAsset(assetCaptor.capture());
    assertEquals("modeling:model:1", assetCaptor.getAllValues().get(0).assetKey());
    assertEquals(LineageAssetType.TABLE, assetCaptor.getAllValues().get(0).assetType());
    assertEquals(
        "modeling:model:1:column:user_phone", assetCaptor.getAllValues().get(2).assetKey());
    ArgumentCaptor<LineageRegistrationService.RegisterRelationCommand> relationCaptor =
        ArgumentCaptor.forClass(LineageRegistrationService.RegisterRelationCommand.class);
    Mockito.verify(registrationService, times(3)).registerRelation(relationCaptor.capture());
    // 前两条:表 CONTAINS 字段;第三条:落地字段 DERIVES_FROM 标准字段(45)。
    assertEquals(LineageRelationType.CONTAINS, relationCaptor.getAllValues().get(0).relationType());
    assertEquals(100L, relationCaptor.getAllValues().get(0).sourceAssetId());
    assertEquals(LineageRelationType.CONTAINS, relationCaptor.getAllValues().get(1).relationType());
  }

  @Test
  void columnPropertiesCarryStdProcessFieldId() {
    service.registerModel(1L, "tester");
    ArgumentCaptor<LineageRegistrationService.RegisterAssetCommand> captor =
        ArgumentCaptor.forClass(LineageRegistrationService.RegisterAssetCommand.class);
    Mockito.verify(registrationService, times(5)).registerAsset(captor.capture());
    JsonNode properties = captor.getAllValues().get(2).properties();
    assertEquals(9L, properties.get("stdProcessFieldId").asLong());
  }

  @Test
  void tableAssetUsesResolvedTableName() {
    service.registerModel(1L, "tester");
    ArgumentCaptor<LineageRegistrationService.RegisterAssetCommand> captor =
        ArgumentCaptor.forClass(LineageRegistrationService.RegisterAssetCommand.class);
    Mockito.verify(registrationService, times(5)).registerAsset(captor.capture());
    assertEquals("trade_order", captor.getAllValues().get(0).tableName());
  }

  @Test
  void standardFieldLineageRegistersSemanticAssetAndDerivesFromRelation() {
    ModelingLineageRegistrationService.RegisterView view = service.registerModel(1L, "tester");
    assertEquals(1, view.standardFieldCount());
    ArgumentCaptor<LineageRegistrationService.RegisterAssetCommand> assetCaptor =
        ArgumentCaptor.forClass(LineageRegistrationService.RegisterAssetCommand.class);
    Mockito.verify(registrationService, times(5)).registerAsset(assetCaptor.capture());
    assertEquals("semantic:field:9", assetCaptor.getAllValues().get(3).assetKey());
    assertEquals("SEMANTIC", assetCaptor.getAllValues().get(3).sourceType());
    assertEquals(
        "modeling:model:1:column:user_phone", assetCaptor.getAllValues().get(4).assetKey());
    ArgumentCaptor<LineageRegistrationService.RegisterRelationCommand> relationCaptor =
        ArgumentCaptor.forClass(LineageRegistrationService.RegisterRelationCommand.class);
    Mockito.verify(registrationService, times(3)).registerRelation(relationCaptor.capture());
    assertEquals(
        LineageRelationType.DERIVES_FROM,
        relationCaptor.getAllValues().get(2).relationType());
  }
}
