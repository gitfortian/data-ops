package io.yak.ops.business.modeling.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.mapping.DwdMappingDraftService.PublicationEvidence;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.repository.ModelVersionRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelVersionServiceTest {

  private ModelRepository modelRepository;
  private ModelVersionRepository versionRepository;
  private ModelStructureService structureService;
  private ModelVersionService service;

  @BeforeEach
  void setUp() {
    modelRepository = mock(ModelRepository.class);
    versionRepository = mock(ModelVersionRepository.class);
    structureService = mock(ModelStructureService.class);
    service = new ModelVersionService(modelRepository, versionRepository, structureService);
    when(modelRepository.findById(1L)).thenReturn(Optional.of(model(ModelStatus.DRAFT, null)));
    when(modelRepository.findByIdForUpdate(1L))
        .thenReturn(Optional.of(model(ModelStatus.DRAFT, null)));
    when(structureService.getForUpdate(1L)).thenReturn(structure("orders_v1"));
    when(modelRepository.updatePublishState(eq(1L), eq(ModelStatus.PUBLISHED.name()),
        anyLong(), anyInt(), eq("tester"))).thenReturn(true);
  }

  @Test
  void publishStoresStructureAndMetaAndMovesPointer() {
    when(structureService.get(1L)).thenReturn(structure("orders_v1"));
    when(versionRepository.findLatestByModelId(1L)).thenReturn(Optional.empty());
    when(versionRepository.nextVersionNo(1L)).thenReturn(1);
    when(versionRepository.insert(eq(1L), eq(1), anyString(), anyString(), eq(1), anyString(),
        eq("tester"))).thenReturn(version(9L, 1, "{\"tableName\":\"orders_v1\"}"));

    ModelVersionService.PublishResult result = service.publish(1L, "tester");

    assertThat(result.created()).isTrue();
    verify(versionRepository).insert(eq(1L), eq(1), anyString(),
        org.mockito.ArgumentMatchers.argThat(meta -> meta != null
            && meta.contains("\"name\":\"订单\"") && meta.contains("\"code\":\"trade_order\"")),
        eq(1), anyString(), eq("tester"));
    verify(modelRepository).updatePublishState(1L, ModelStatus.PUBLISHED.name(), 9L, 1, "tester");
  }

  @Test
  void publishIsIdempotentAndStillHealsMissingPointer() {
    ModelVersion latest = version(9L, 3, null);
    when(structureService.get(1L)).thenReturn(structure("orders_v1"));
    when(versionRepository.findLatestByModelId(1L)).thenReturn(Optional.of(latest));
    when(versionRepository.insert(any(), anyInt(), any(), any(), anyInt(), any(), any()))
        .thenAnswer(invocation -> {
          throw new AssertionError("幂等路径不应追加版本");
        });

    // 指针缺失（存量兜底）：即使内容幂等，也要把发布指针移到最新版。
    ModelVersionService.PublishResult result = service.publish(1L, "tester");

    assertThat(result.created()).isFalse();
    assertThat(result.version()).isSameAs(latest);
    verify(modelRepository).updatePublishState(1L, ModelStatus.PUBLISHED.name(), 9L, 3, "tester");
  }

  @Test
  void newPhysicalVersionFailsClosedWhenPublishPointerUpdateAffectsNoRow() {
    when(versionRepository.findLatestByModelId(1L)).thenReturn(Optional.empty());
    when(versionRepository.nextVersionNo(1L)).thenReturn(1);
    when(versionRepository.insert(eq(1L), eq(1), anyString(), anyString(),
        eq(1), anyString(), eq("tester"))).thenReturn(version(9L, 1, "{}"));
    when(modelRepository.updatePublishState(1L, ModelStatus.PUBLISHED.name(), 9L, 1,
        "tester")).thenReturn(false);

    assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
        () -> service.publish(1L, "tester")))
        .hasMessageContaining("发布指针更新失败");
    verify(versionRepository).insert(eq(1L), eq(1), anyString(), anyString(),
        eq(1), anyString(), eq("tester"));
  }

  @Test
  void idempotentPhysicalVersionFailsClosedWhenPointerRepairAffectsNoRow() {
    when(versionRepository.findLatestByModelId(1L))
        .thenReturn(Optional.of(version(9L, 3, null)));
    when(modelRepository.updatePublishState(1L, ModelStatus.PUBLISHED.name(), 9L, 3,
        "tester")).thenReturn(false);

    assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
        () -> service.publish(1L, "tester")))
        .hasMessageContaining("发布指针更新失败");
    verify(versionRepository, never()).insert(any(), anyInt(), any(), any(),
        anyInt(), any(), any());
  }

  @Test
  void approvedPublishRejectsAChangedStructure() {
    when(structureService.get(1L)).thenReturn(structure("orders_v1"));
    when(structureService.getForUpdate(1L)).thenReturn(structure("orders_v2"));
    String submittedFingerprint = service.structureFingerprint(1L);

    assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
        () -> service.publishApproved(1L, submittedFingerprint, "tester")))
        .hasMessageContaining("偏离送审依据");
    verify(versionRepository, never()).insert(any(), anyInt(), any(), any(), anyInt(), any(), any());
    verify(modelRepository, never())
        .updatePublishState(any(), anyString(), any(), anyInt(), anyString());
  }

  @Test
  void approvedPublishAcceptsAnUnchangedStructure() {
    when(structureService.get(1L)).thenReturn(structure("orders_v1"));
    when(versionRepository.findLatestByModelId(1L)).thenReturn(Optional.empty());
    when(versionRepository.nextVersionNo(1L)).thenReturn(1);
    when(versionRepository.insert(eq(1L), eq(1), anyString(), anyString(), eq(1), anyString(),
        eq("tester"))).thenReturn(version(9L, 1, "{}"));
    String submittedFingerprint = service.structureFingerprint(1L);

    ModelVersionService.PublishResult result =
        service.publishApproved(1L, submittedFingerprint, "tester");

    assertThat(result.created()).isTrue();
    verify(modelRepository).updatePublishState(1L, ModelStatus.PUBLISHED.name(), 9L, 1, "tester");
  }

  @Test
  void identicalStructureButDifferentMappingRevisionMustCreateNewPhysicalVersion() {
    Model dwd = Model.create("trade_order", "订单", ModelDialect.MYSQL,
        null, null, "DWD", null).withPersisted(1L, "alice", null, null);
    when(modelRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(dwd));
    when(versionRepository.findLatestByModelId(1L))
        .thenReturn(Optional.of(version(9L, 3, null)));
    when(versionRepository.nextVersionNo(1L)).thenReturn(4);
    when(versionRepository.insert(eq(1L), eq(4), anyString(), anyString(),
        eq(1), anyString(), eq("tester"))).thenReturn(version(10L, 4, "{}"));
    var evidence = new PublicationEvidence(501L, 201L, 77L, 2,
        "a".repeat(64), "b".repeat(64), "c".repeat(64));

    var result = service.publishWithMapping(1L, null, evidence, "tester");
    assertThat(result.created()).isTrue();
    assertThat(result.version().id()).isEqualTo(10L);
    verify(versionRepository).insert(eq(1L), eq(4), anyString(),
        org.mockito.ArgumentMatchers.argThat(meta -> meta.contains("\"mappingRevisionId\":501")
            && meta.contains("\"mappingContentSha256\":\""+ "c".repeat(64) + "\"")),
        eq(1), anyString(), eq("tester"));
    verify(modelRepository).updatePublishState(1L, ModelStatus.PUBLISHED.name(), 10L, 4, "tester");
  }

  @Test
  void mappedDwdPublishRejectsFailedPointerUpdateInsteadOfClaimingSuccess() {
    Model dwd = Model.create("trade_order", "订单", ModelDialect.MYSQL,
        null, null, "DWD", null).withPersisted(1L, "alice", null, null);
    when(modelRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(dwd));
    when(versionRepository.findLatestByModelId(1L)).thenReturn(Optional.empty());
    when(versionRepository.nextVersionNo(1L)).thenReturn(1);
    when(versionRepository.insert(eq(1L), eq(1), anyString(), anyString(),
        eq(1), anyString(), eq("tester"))).thenReturn(version(9L, 1, "{}"));
    when(modelRepository.updatePublishState(1L, ModelStatus.PUBLISHED.name(), 9L, 1,
        "tester")).thenReturn(false);
    var evidence = new PublicationEvidence(501L, 201L, 77L, 2,
        "a".repeat(64), "b".repeat(64), "c".repeat(64));

    assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
        () -> service.publishWithMapping(1L, null, evidence, "tester")))
        .hasMessageContaining("发布指针更新失败");
    verify(versionRepository).insert(eq(1L), eq(1), anyString(),
        org.mockito.ArgumentMatchers.argThat(meta ->
            meta != null && meta.contains("mappingRevisionId")
                && meta.contains("mappingContentSha256")),
        eq(1), anyString(), eq("tester"));
    verify(modelRepository).updatePublishState(1L, ModelStatus.PUBLISHED.name(), 9L, 1,
        "tester");
  }

  @Test
  void malformedVersionMetaFailsClosedInsteadOfBecomingLegacyUnbound() {
    Model dwd = Model.create("trade_order", "订单", ModelDialect.MYSQL,
        null, null, "DWD", null).withPersisted(1L, "alice", null, null);
    when(modelRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(dwd));
    ModelVersion malformed = version(9L, 3, "{not-json");
    when(versionRepository.findLatestByModelId(1L)).thenReturn(Optional.of(malformed));
    when(structureService.get(1L)).thenReturn(structure("orders_v1"));

    assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
        () -> service.publish(1L, "tester")))
        .hasMessageContaining("元数据损坏");
    verify(versionRepository, never()).insert(any(), anyInt(), any(), any(), anyInt(), any(), any());
  }

  @Test
  void legacyPublishOfSameDwdStructureDoesNotInheritBoundMappingHistory() {
    Model dwd = Model.create("trade_order", "订单", ModelDialect.MYSQL,
        null, null, "DWD", null).withPersisted(1L, "alice", null, null);
    when(modelRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(dwd));
    ModelVersion previous = version(9L, 3, null);
    ModelVersion bound = new ModelVersion(previous.id(), previous.modelId(),
        previous.versionNo(), previous.structureJson(),
        "{\"mappingRevisionId\":501,\"mappingSetId\":201}",
        previous.columnCount(), previous.checksum(), previous.publishedBy(),
        previous.publishTime());
    when(versionRepository.findLatestByModelId(1L)).thenReturn(Optional.of(bound));
    when(versionRepository.nextVersionNo(1L)).thenReturn(4);
    when(versionRepository.insert(eq(1L), eq(4), anyString(), anyString(),
        eq(1), anyString(), eq("tester"))).thenReturn(version(10L, 4, "{}"));

    var created = service.publish(1L, "tester");
    assertThat(created.created()).isTrue();
    verify(versionRepository).insert(eq(1L), eq(4), anyString(),
        org.mockito.ArgumentMatchers.argThat(meta ->
            meta != null && !meta.contains("mappingRevisionId")),
        eq(1), anyString(), eq("tester"));
    verify(modelRepository).updatePublishState(1L, ModelStatus.PUBLISHED.name(), 10L, 4, "tester");
  }

  @Test
  void sameRevisionIdButDifferentMappingSetCannotReusePublishedVersion() {
    Model dwd = Model.create("trade_order", "订单", ModelDialect.MYSQL,
        null, null, "DWD", null).withPersisted(1L, "alice", null, null);
    when(modelRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(dwd));
    ModelVersion previous = version(9L, 3, null);
    String meta = "{\"mappingRevisionId\":501,\"mappingSetId\":999,"
        + "\"logicalModelId\":77,\"logicalVersionNo\":2,"
        + "\"mappingContentSha256\":\"" + "c".repeat(64) + "\","
        + "\"logicalSnapshotSha256\":\"" + "a".repeat(64) + "\","
        + "\"physicalStructureSha256\":\"" + "b".repeat(64) + "\"}";
    ModelVersion bound = new ModelVersion(previous.id(), previous.modelId(),
        previous.versionNo(), previous.structureJson(), meta,
        previous.columnCount(), previous.checksum(), previous.publishedBy(),
        previous.publishTime());
    when(versionRepository.findLatestByModelId(1L)).thenReturn(Optional.of(bound));
    when(versionRepository.nextVersionNo(1L)).thenReturn(4);
    when(versionRepository.insert(eq(1L), eq(4), anyString(), anyString(),
        eq(1), anyString(), eq("tester"))).thenReturn(version(10L, 4, "{}"));
    var evidence = new PublicationEvidence(501L, 201L, 77L, 2,
        "a".repeat(64), "b".repeat(64), "c".repeat(64));

    assertThat(service.publishWithMapping(1L, null, evidence, "tester").created()).isTrue();
    verify(versionRepository).insert(eq(1L), eq(4), anyString(), anyString(),
        eq(1), anyString(), eq("tester"));
  }

  @Test
  void approvedMappingRejectsChangedPhysicalFingerprintBeforeVersionInsert() {
    Model dwd = Model.create("trade_order", "订单", ModelDialect.MYSQL,
        null, null, "DWD", null).withPersisted(1L, "alice", null, null);
    when(modelRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(dwd));
    var evidence = new PublicationEvidence(501L, 201L, 77L, 2,
        "a".repeat(64), "b".repeat(64), "c".repeat(64));
    assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
        () -> service.publishWithMapping(1L, "f".repeat(64), evidence, "tester")))
        .hasMessageContaining("审批后物理结构发生变化");
    verify(versionRepository, never()).insert(any(), anyInt(), any(), any(),
        anyInt(), any(), any());
  }

  @Test
  void rollbackOnlyRestoresDraftWithoutPublishingOrAppendingVersion() {
    when(modelRepository.findById(1L)).thenReturn(Optional.of(model(ModelStatus.DRAFT, 500L)));
    ModelVersion target = version(501L, 2, null);
    when(versionRepository.findByVersionNo(1L, 2)).thenReturn(Optional.of(target));

    when(structureService.get(1L)).thenReturn(structure("restored"));

    StructureView restored = service.rollback(1L, 2, "tester");

    verify(structureService).save(eq(1L), any(), eq("tester"));
    verify(versionRepository, never()).insert(any(), anyInt(), any(), any(), anyInt(), any(), any());
    verify(modelRepository, never())
        .updatePublishState(any(), anyString(), any(), anyInt(), anyString());
    assertThat(restored.tableName()).isEqualTo("restored");
  }

  private Model model(ModelStatus status, Long publishedVersionId) {
    Model model = Model.create("trade_order", "订单", ModelDialect.MYSQL, null)
        .withPersisted(1L, "alice", null, null);
    return status == ModelStatus.PUBLISHED
        ? model.withPublishState(status, publishedVersionId, 3)
        : model;
  }

  private StructureView structure(String tableName) {
    return new StructureView(
        1L, "trade_order", "订单", "MYSQL", "DRAFT", null,
        tableName, null,
        List.of(new StructureView.ColumnView(null, "id", "BIGINT", null, null, false,
            null, null, null, 0, null, null, null, null, null, null, null, null, null, null)),
        List.of("id"), List.of(),
        new StructureView.PartitionView(null, List.of(), null), Map.of());
  }

  private ModelVersion version(Long id, int versionNo, String structureJson) {
    return new ModelVersion(
        id, 1L, versionNo,
        structureJson == null ? serialisePublishedProbe() : structureJson,
        null, 1, io.yak.ops.common.version.VersionDigests.sha256Hex(
            structureJson == null ? serialisePublishedProbe() : structureJson),
        "alice", LocalDateTime.now());
  }

  private String serialisePublishedProbe() {
    try {
      // 幂等判定走语义等值：latest 快照必须与 structure("orders_v1") 序列化一致。
      return new com.fasterxml.jackson.databind.ObjectMapper()
          .writeValueAsString(structure("orders_v1"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
