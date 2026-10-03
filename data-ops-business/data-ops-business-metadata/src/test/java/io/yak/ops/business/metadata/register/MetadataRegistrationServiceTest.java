package io.yak.ops.business.metadata.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metadata.api.RegisterCommand;
import io.yak.ops.business.metadata.dao.model.CatalogAssetRow;
import io.yak.ops.business.metadata.dao.model.CatalogAssetState;
import io.yak.ops.business.metadata.dao.model.CatalogPresenceRow;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.BatchCommand;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.GoneCommand;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.PresenceScan;
import io.yak.ops.business.metadata.harvest.MetadataAttributeCodec;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry.TypeDefinition;
import io.yak.ops.business.metadata.dao.model.MdFieldDefPO;
import io.yak.ops.business.metadata.dao.model.MdTypeDefPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeCategory;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeStatus;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 登记主通道本体（ticket 130）：校验、保序、与采集共用同一 upsert/软删入口。
 *
 * <p>仓储层全部 mock——本测试盯的是<b>本类的决定</b>（拒什么、拼什么、走哪条通道），
 * SQL 形状由共表守卫与 {@code AssetUpsertRepositoryTest} 各管各的。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetadataRegistrationServiceTest {

  private static final long PROJECT = 7L;
  private static final LocalDateTime NEWER = LocalDateTime.of(2026, 9, 20, 10, 0);
  private static final LocalDateTime OLDER = NEWER.minusMinutes(5);

  private final AssetUpsertRepository upsertRepository = mock(AssetUpsertRepository.class);
  private final MetadataTypeRegistry typeRegistry = mock(MetadataTypeRegistry.class);
  private final MetadataRegistrationService service =
      new MetadataRegistrationService(
          upsertRepository, typeRegistry, new MetadataAttributeCodec(new ObjectMapper()));

  @BeforeEach
  void projectableType() {
    // 投影类型：collectible=0，走登记通道（工单 128 基线的 dataModel 形状）。
    when(typeRegistry.require("dataModel")).thenReturn(dataModelType(false, "MODEL"));
  }

  @Test
  void registerGoesThroughTheHarvestUpsertWithTheRegisteredChannelShape() {
    when(upsertRepository.statesOf(eq(PROJECT), any())).thenReturn(Map.of());
    when(upsertRepository.idsOf(eq(PROJECT), eq(List.of("modeling:model:1"))))
        .thenReturn(Map.of("modeling:model:1", 42L));

    assertThat(service.register(PROJECT, command())).isEqualTo(MetadataRegistrationService.RegistrationResult.APPLIED);

    ArgumentCaptor<BatchCommand> captor = ArgumentCaptor.forClass(BatchCommand.class);
    verify(upsertRepository).write(captor.capture());
    BatchCommand written = captor.getValue();
    // 与采集同一入口、同一 BatchCommand 形状——本票不开第二条共表写路径。
    assertThat(written.providerType()).isEqualTo(ProviderType.REGISTERED);
    assertThat(written.collectRunId()).isNull();
    assertThat(written.dryRun()).isFalse();
    assertThat(written.typeName()).isEqualTo("dataModel");
    assertThat(written.operator()).isEqualTo("alice");

    CatalogAssetRow row = written.rows().get(0);
    assertThat(row.getSourceType()).isEqualTo("METADATA");
    assertThat(row.getSourceId()).isEqualTo("42");
    assertThat(row.getAssetType()).isEqualTo("MODEL");
    assertThat(row.getProviderType()).isEqualTo(ProviderType.REGISTERED.name());
    // 登记通道看不见物理结构：content_hash 伪造一份就是给缺席判定递假证据（plan §3.3 分岔）。
    assertThat(row.getContentHash()).isNull();
    assertThat(row.getSourceHash()).isEqualTo("sha-demo");
    assertThat(row.getSourceUpdatedAt()).isEqualTo(NEWER);
    assertThat(row.getEntityStatus()).isEqualTo("Unprocessed");
    assertThat(row.getParentAssetId()).isEqualTo(42L);
    // 投影归属三列进共表列本身（工单 130 的目录列补齐）。
    assertThat(row.getOwnerUser()).isEqualTo("alice");
    assertThat(row.getDomainIds()).isEqualTo("11,12");
    assertThat(row.getLayerCode()).isEqualTo("DWD");
    // FQN 由 type_def 的 fqn_pattern 渲染，占位符上下文来自 name/属性袋（{modelCode} → 属性值）。
    assertThat(row.getFullyQualifiedName()).isEqualTo("demo-code");
    assertThat(row.getFqnHash()).hasSize(32);
    // 属性只经 MetadataAttributeCodec：未登记字段在本行就该炸（下一条测试）。
    assertThat(row.getMdAttributes()).contains("modelCode").contains("demo-code");
  }

  @Test
  void unregisteredAttributeIsRejectedBeforeAnythingIsWritten() {
    RegisterCommand command = command();
    command.getAttributes().put("secretNote", "x");
    when(upsertRepository.statesOf(eq(PROJECT), any())).thenReturn(Map.of());

    assertCode(() -> service.register(PROJECT, command), MetadataErrorCode.ATTRIBUTE_NOT_DEFINED);
    verify(upsertRepository, never()).write(any());
  }

  @Test
  void aStaleCommandRefreshesPresenceOnlyAndNeverOverwritesContent() {
    // 保序（plan §3.2c 必须 3）：晚到的旧快照改不了新内容，但"实体仍在场"是当下事实。
    CatalogAssetState current = new CatalogAssetState();
    current.setId(9L);
    current.setAssetKey("modeling:model:9");
    current.setSourceUpdatedAt(NEWER);
    when(upsertRepository.statesOf(eq(PROJECT), any())).thenReturn(Map.of("modeling:model:9", current));

    RegisterCommand stale = command();
    stale.setSourceUpdatedAt(OLDER);
    assertThat(service.register(PROJECT, stale))
        .isEqualTo(MetadataRegistrationService.RegistrationResult.STALE_PRESENT);

    verify(upsertRepository, never()).write(any());
    verify(upsertRepository)
        .touchPresence(eq(PROJECT), eq(List.of("modeling:model:9")), any(LocalDateTime.class));
  }

  @Test
  void anIdenticalSnapshotStillReplaysThroughTheUpsert() {
    // 时间戳相等不算"更旧"：重放幂等由指纹守卫兜底，这里不该把重放误判成过期。
    CatalogAssetState current = new CatalogAssetState();
    current.setId(9L);
    current.setAssetKey("modeling:model:9");
    current.setSourceUpdatedAt(NEWER);
    when(upsertRepository.statesOf(eq(PROJECT), any())).thenReturn(Map.of("modeling:model:9", current));

    assertThat(service.register(PROJECT, command()))
        .isEqualTo(MetadataRegistrationService.RegistrationResult.APPLIED);
    verify(upsertRepository).write(any());
  }

  @Test
  void validationRejectsEveryShapeThatWouldCrashLaterDownstream() {
    // 类型不存在 → 49002（require 自己抛）。
    when(typeRegistry.require("ghost")).thenThrow(new MetadataException(MetadataErrorCode.TYPE_NOT_FOUND));
    RegisterCommand ghost = command();
    ghost.setTypeName("ghost");
    assertCode(() -> service.register(PROJECT, ghost), MetadataErrorCode.TYPE_NOT_FOUND);

    // collectible 类型归物理采集通道，登记会造出"同一实体两把指纹"。
    when(typeRegistry.require("table")).thenReturn(dataModelType(true, "TABLE", true));
    RegisterCommand collected = command();
    collected.setTypeName("table");
    assertCode(() -> service.register(PROJECT, collected), MetadataErrorCode.REGISTER_COMMAND_INVALID);

    // lineage_asset_type 缺失：写进去的 asset_type 会让 lineage 读行 valueOf 抛异常（炸别人的查询）。
    when(typeRegistry.require("pending134")).thenReturn(dataModelType(false, null));
    RegisterCommand noAssetType = command();
    noAssetType.setTypeName("pending134");
    assertCode(() -> service.register(PROJECT, noAssetType), MetadataErrorCode.LINEAGE_ASSET_TYPE_INVALID);

    // 键前缀不符 → 49006（§3.2b 硬约束 4：元数据不替源域拼键，只验）。
    RegisterCommand badKey = command();
    badKey.setAssetKey("wrong:prefix:9");
    assertCode(() -> service.register(PROJECT, badKey), MetadataErrorCode.ASSET_KEY_INVALID);

    // sourceUpdatedAt 缺失 → 49015：保序令牌与 outbox 去重键列都吃它。
    RegisterCommand noTimestamp = command();
    noTimestamp.setSourceUpdatedAt(null);
    assertCode(() -> service.register(PROJECT, noTimestamp), MetadataErrorCode.REGISTER_COMMAND_INVALID);

    // sourceHash 缺失 → 49015：CHANGED 判定唯一的依据。
    RegisterCommand noHash = command();
    noHash.setSourceHash(" ");
    assertCode(() -> service.register(PROJECT, noHash), MetadataErrorCode.REGISTER_COMMAND_INVALID);

    verify(upsertRepository, never()).write(any());
  }

  @Test
  void unregisterSoftDeletesOnlyTheMatchingRegisteredRowThroughTheSharedEntry() {
    when(upsertRepository.registeredScan(PROJECT, 104L, "42", 2_000))
        .thenReturn(
            new PresenceScan(
                List.of(presence("modeling:model:9", "sha-9"), presence("modeling:model:8", "sha-8")),
                false));
    when(upsertRepository.markGone(any())).thenReturn(1);

    assertThat(service.unregister(PROJECT, "dataModel", "42", "modeling:model:9")).isEqualTo(1);

    ArgumentCaptor<GoneCommand> captor = ArgumentCaptor.forClass(GoneCommand.class);
    verify(upsertRepository).markGone(captor.capture());
    GoneCommand gone = captor.getValue();
    // 与采集同一个软删入口；候选只剩被点名的那一个。
    assertThat(gone.providerType()).isEqualTo(ProviderType.REGISTERED);
    assertThat(gone.collectRunId()).isNull();
    assertThat(gone.dryRun()).isFalse();
    assertThat(gone.rows()).hasSize(1);
    assertThat(gone.rows().get(0).row().getAssetKey()).isEqualTo("modeling:model:9");
    assertThat(gone.rows().get(0).typeName()).isEqualTo("dataModel");
  }

  @Test
  void aTruncatedCandidatePoolFailsLoudInsteadOfPretendingToBeComplete() {
    when(upsertRepository.registeredScan(PROJECT, 104L, "42", 2_000))
        .thenReturn(new PresenceScan(List.of(presence("modeling:model:9", "sha-9")), true));

    // 候选读不全就撤销 = 漏删，而漏删在目录上没有任何痕迹可查。
    assertCode(
        () -> service.unregister(PROJECT, "dataModel", "42", null),
        MetadataErrorCode.REGISTER_COMMAND_INVALID);
    verify(upsertRepository, never()).markGone(any());
  }

  @Test
  void unregisterOfNothingThatWasNeverRegisteredIsNotAnError() {
    when(upsertRepository.registeredScan(PROJECT, 104L, "42", 2_000))
        .thenReturn(new PresenceScan(List.of(), false));

    assertThat(service.unregister(PROJECT, "dataModel", "42", "modeling:model:999")).isZero();
    verify(upsertRepository, never()).markGone(any());
  }

  private static CatalogPresenceRow presence(String assetKey, String sourceHash) {
    CatalogPresenceRow row = new CatalogPresenceRow();
    row.setId(500L);
    row.setAssetKey(assetKey);
    row.setAssetType("MODEL");
    row.setSourceHash(sourceHash);
    return row;
  }

  private static TypeDefinition dataModelType(Boolean collectible, String lineageAssetType) {
    return dataModelType(collectible, lineageAssetType, false);
  }

  private static TypeDefinition dataModelType(
      Boolean collectible, String lineageAssetType, boolean reuseTypeName) {
    MdTypeDefPO type = new MdTypeDefPO();
    type.setId(104L);
    type.setTypeName(reuseTypeName ? "table" : "dataModel");
    type.setCategory(TypeCategory.ENTITY.name());
    type.setStatus(TypeStatus.ACTIVE.name());
    type.setKeyPrefix("modeling:model:");
    type.setFqnPattern("{modelCode}");
    type.setKeySeparator(".");
    type.setLineageAssetType(lineageAssetType);
    type.setCollectible(collectible);
    MdFieldDefPO modelCode = new MdFieldDefPO();
    modelCode.setTypeId(104L);
    modelCode.setFieldName("modelCode");
    modelCode.setBaseType("STRING");
    modelCode.setOrdinal(10);
    return new TypeDefinition(type, List.of(modelCode));
  }

  private static RegisterCommand command() {
    RegisterCommand command = new RegisterCommand();
    command.setTypeName("dataModel");
    command.setSourceId("42");
    command.setAssetKey("modeling:model:9");
    command.setName("订单域模型");
    command.setDisplayName("订单域模型");
    command.setSummary("one-line");
    command.setOwnerUser("alice");
    command.setDomainIds("11,12");
    command.setLayerCode("DWD");
    command.setParentAssetKey("modeling:model:1");
    command.setSourceHash("sha-demo");
    command.setSourceUpdatedAt(NEWER);
    command.setAttributes(new java.util.LinkedHashMap<>(Map.of("modelCode", "demo-code")));
    command.setOperator("alice");
    return command;
  }

  private interface Call {
    void run() throws Exception;
  }

  private static void assertCode(Call call, MetadataErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(expected);
  }
}
