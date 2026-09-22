package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.registration.LineageRegistrationService;
import io.yak.ops.business.lineage.registration.LineageRegistrationService.RegisterAssetCommand;
import io.yak.ops.business.lineage.registration.LineageRegistrationService.RegisterRelationCommand;
import io.yak.ops.business.mdm.application.MdmQualityService.LandingContext;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.common.util.metadata.PhysicalTableAssetKey;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** 血缘三段登记单元测试(R3):资产去重、上下流方向、幂等键口径、降级路径。 */
class MdmLineageServiceTest {

  private static final String SOURCE_KEY =
      MdmLineageService.tableKey(9L, "crm_db", null, "crm_customer");
  private static final String LANDING_KEY =
      MdmLineageService.tableKey(3L, "yak_security", null, "mdm_landing_customer_1");
  private static final String LANDING_KEY_2 =
      MdmLineageService.tableKey(3L, "yak_security", null, "mdm_landing_customer_2");
  private static final String RECORD_KEY =
      MdmLineageService.tableKey(3L, "yak_security", null, MdmLineageService.RECORD_TABLE);

  private MdmQualityService qualityService;
  private LineageRegistrationService registration;
  private MdmLineageService service;

  @BeforeEach
  void setUp() {
    qualityService = mock(MdmQualityService.class);
    registration = mock(LineageRegistrationService.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    service =
        new MdmLineageService(
            qualityService, currentProject, provider(registration));
  }

  @Test
  void syncRegistersDedupedAssetsAndDirectedRelations() {
    when(qualityService.landingContexts(1L))
        .thenReturn(List.of(context(1L), context(2L)));
    when(registration.registerAssetsBatch(anyList(), anyInt())).thenAnswer(
        call -> indexByAssetKey(call.getArgument(0)));

    MdmLineageService.LineageSyncReceipt receipt = service.sync(1L);

    // 共享同一源表与记录表：2 源(去重=1) + 2 落地 + 1 记录 = 4 资产
    assertEquals(4, receipt.assetCount());
    assertEquals(4, receipt.relationCount());

    ArgumentCaptor<List<RegisterAssetCommand>> assetsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(registration).registerAssetsBatch(assetsCaptor.capture(), anyInt());
    List<String> keys =
        assetsCaptor.getValue().stream().map(RegisterAssetCommand::assetKey).toList();
    assertEquals(List.of(SOURCE_KEY, LANDING_KEY, LANDING_KEY_2, RECORD_KEY), keys);
    for (RegisterAssetCommand command : assetsCaptor.getValue()) {
      assertEquals(MdmLineageService.SOURCE_TYPE, command.sourceType());
      assertEquals(LineageAssetType.TABLE, command.assetType());
    }

    ArgumentCaptor<List<RegisterRelationCommand>> relationsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(registration).registerRelationsBatch(relationsCaptor.capture(), anyInt());
    List<RegisterRelationCommand> relations = relationsCaptor.getValue();
    Map<String, LineageAsset> byKey = indexByAssetKey(assetsCaptor.getValue());
    // 源 → 落地
    assertEquals(byKey.get(SOURCE_KEY).id(), relations.get(0).sourceAssetId());
    assertEquals(byKey.get(LANDING_KEY).id(), relations.get(0).targetAssetId());
    // 落地 → 记录
    assertEquals(byKey.get(LANDING_KEY).id(), relations.get(1).sourceAssetId());
    assertEquals(byKey.get(RECORD_KEY).id(), relations.get(1).targetAssetId());
    assertEquals(byKey.get(LANDING_KEY_2).id(), relations.get(3).sourceAssetId());
    assertEquals("mdm-entity-1", relations.get(1).sourceId());
    assertEquals("mdm-collect-2", relations.get(2).sourceId());
  }

  @Test
  void syncNormalizesAssetKeysToLowercase() {
    assertEquals(
        PhysicalTableAssetKey.of("9", "crm_db", "", "crm_customer"),
        MdmLineageService.tableKey(9L, " CRM_DB ", null, "CRM_Customer"));
    assertTrue(SOURCE_KEY.startsWith("table:"));
  }

  @Test
  void syncFailsWhenLandedContextMissingAssetResult() {
    when(qualityService.landingContexts(1L)).thenReturn(List.of(context(1L)));
    when(registration.registerAssetsBatch(anyList(), anyInt())).thenReturn(Map.of());
    MdmException exception = assertThrows(MdmException.class, () -> service.sync(1L));
    assertEquals(MdmErrorCode.LINEAGE_REGISTER_FAILED, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("血缘资产登记结果缺失"));
  }

  @Test
  void syncBlocksWhenNoLandingLink() {
    when(qualityService.landingContexts(1L)).thenReturn(List.of());
    MdmException exception = assertThrows(MdmException.class, () -> service.sync(1L));
    assertEquals(MdmErrorCode.SOURCE_NOT_LANDED, exception.getErrorCode());
  }

  @Test
  void syncReportsDisabledLineageModule() {
    MdmLineageService disabled =
        new MdmLineageService(
            qualityService, mock(CurrentProject.class), emptyProvider());
    MdmException exception = assertThrows(MdmException.class, () -> disabled.sync(1L));
    assertEquals(MdmErrorCode.LINEAGE_REGISTER_FAILED, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("未启用"));
  }

  private static LandingContext context(long sourceId) {
    return new LandingContext(
        "customer",
        "yak_security",
        3L,
        "客户库(平台实例)",
        sourceId,
        9L,
        "crm_db",
        null,
        "crm_customer",
        "mdm_landing_customer_" + sourceId,
        "cust_no",
        List.of("cust_mobile"));
  }

  private static Map<String, LineageAsset> indexByAssetKey(List<RegisterAssetCommand> commands) {
    long[] id = {100L};
    Map<String, LineageAsset> assets = commands.stream()
        .map(command -> new LineageAsset(
            ++id[0],
            command.assetKey(),
            command.assetType(),
            command.name(),
            command.sourceType(),
            command.sourceId(),
            null,
            command.dataSourceId(),
            command.databaseName(),
            command.schemaName(),
            command.tableName(),
            null,
            null,
            null,
            null))
        .collect(Collectors.toMap(
            LineageAsset::assetKey, Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new));
    return assets;
  }

  private static <T> ObjectProvider<T> provider(T bean) {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    lenient().when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  private static <T> ObjectProvider<T> emptyProvider() {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    lenient().when(provider.getIfAvailable()).thenReturn(null);
    return provider;
  }
}
