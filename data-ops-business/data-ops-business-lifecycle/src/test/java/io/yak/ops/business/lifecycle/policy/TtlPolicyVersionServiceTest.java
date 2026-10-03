package io.yak.ops.business.lifecycle.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.lifecycle.dao.mapper.LifecyclePolicyMapper;
import io.yak.ops.business.lifecycle.dao.mapper.LifecyclePolicyVersionMapper;
import io.yak.ops.business.lifecycle.exception.LifecycleException;
import io.yak.ops.business.lifecycle.dao.model.LifecyclePolicyPO;
import io.yak.ops.business.lifecycle.dao.model.LifecyclePolicyVersionPO;
import io.yak.ops.common.enums.PublishState;
import io.yak.ops.common.version.VersionDigests;
import io.yak.ops.core.project.CurrentProject;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** W1-1 单测:发布幂等/追加、回滚覆盖草稿、消费读路径只读发布快照(契约 C2/C4)。 */
class TtlPolicyVersionServiceTest {

  private CurrentProject currentProject;
  private LifecyclePolicyMapper policyMapper;
  private LifecyclePolicyVersionMapper versionMapper;
  private TtlPolicyVersionService service;

  @BeforeEach
  void setUp() {
    currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(1L);
    policyMapper = mock(LifecyclePolicyMapper.class);
    versionMapper = mock(LifecyclePolicyVersionMapper.class);
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    when(auditService.start(any())).thenReturn(AuditOperationHandle.noop(null));
    service = new TtlPolicyVersionService(
        currentProject, policyMapper, versionMapper, auditService);
  }

  private static LifecyclePolicyPO draftPolicy() {
    LifecyclePolicyPO po = new LifecyclePolicyPO();
    po.setId(10L);
    po.setProjectId(1L);
    po.setPolicyCode("ttl_custom_test");
    po.setPolicyName("测试策略");
    po.setScopeType("CUSTOM");
    po.setPartitionGranularity("DAY");
    po.setHotDays(7);
    po.setColdDays(30);
    po.setDestroyDays(365);
    po.setBuiltin(false);
    po.setStatus("ENABLED");
    po.setPublishState(PublishState.DRAFT.name());
    po.setDraftRevision(0);
    po.setLatestVersionNo(0);
    po.setDeleted(false);
    return po;
  }

  private static LifecyclePolicyVersionPO versionRow(long id, int versionNo,
      LifecyclePolicyPO snapshotSource) {
    LifecyclePolicyVersionPO row = new LifecyclePolicyVersionPO();
    row.setId(id);
    row.setPolicyId(10L);
    row.setProjectId(1L);
    row.setVersionNo(versionNo);
    row.setPayloadJson(VersionDigests.canonicalJson(
        TtlPolicyVersionService.draftSnapshot(snapshotSource)));
    row.setChecksum(VersionDigests.sha256Hex(row.getPayloadJson()));
    return row;
  }

  @Test
  void firstPublishAppendsVersionOneAndPointsPointer() {
    LifecyclePolicyPO po = draftPolicy();
    when(policyMapper.selectOne(any())).thenReturn(po);
    when(versionMapper.selectOne(any())).thenReturn(null);
    when(versionMapper.nextVersionNo(10L)).thenReturn(1);
    AtomicLong seq = new AtomicLong(100);
    when(versionMapper.insert(any(LifecyclePolicyVersionPO.class)))
        .thenAnswer(inv -> {
          inv.getArgument(0, LifecyclePolicyVersionPO.class).setId(seq.getAndIncrement());
          return 1;
        });

    TtlPolicyVersionService.PublishResult result = service.publish(10L, "lucas");

    assertThat(result.appended()).isTrue();
    assertThat(result.version().versionNo()).isEqualTo(1);
    assertThat(po.getPublishState()).isEqualTo(PublishState.PUBLISHED.name());
    assertThat(po.getPublishedVersionId()).isEqualTo(100L);
    assertThat(po.getLatestVersionNo()).isEqualTo(1);
    ArgumentCaptor<LifecyclePolicyVersionPO> captor =
        ArgumentCaptor.forClass(LifecyclePolicyVersionPO.class);
    verify(versionMapper).insert(captor.capture());
    assertThat(captor.getValue().getPayloadJson()).contains("\"hotDays\":7");
    assertThat(captor.getValue().getChecksum()).hasSize(64);
  }

  @Test
  void republishWithoutChangesIsIdempotentAndReusesLatestRow() {
    LifecyclePolicyPO po = draftPolicy();
    po.setPublishState(PublishState.PUBLISHED.name());
    LifecyclePolicyVersionPO latest = versionRow(90L, 3, po);
    po.setPublishedVersionId(90L);
    po.setLatestVersionNo(3);
    when(policyMapper.selectOne(any())).thenReturn(po);
    when(versionMapper.selectOne(any())).thenReturn(latest);

    TtlPolicyVersionService.PublishResult result = service.publish(10L, "lucas");

    assertThat(result.appended()).isFalse();
    assertThat(result.version().id()).isEqualTo(90L);
    verify(versionMapper, never()).insert(any(LifecyclePolicyVersionPO.class));
    verify(versionMapper, never()).nextVersionNo(any());
  }

  @Test
  void editedDraftRepublishAppendsNewVersion() {
    LifecyclePolicyPO po = draftPolicy();
    LifecyclePolicyVersionPO latest = versionRow(90L, 3, po);
    po.setPublishedVersionId(90L);
    po.setLatestVersionNo(3);
    po.setHotDays(14); // 草稿已改
    when(policyMapper.selectOne(any())).thenReturn(po);
    when(versionMapper.selectOne(any())).thenReturn(latest);
    when(versionMapper.nextVersionNo(10L)).thenReturn(4);
    when(versionMapper.insert(any(LifecyclePolicyVersionPO.class))).thenAnswer(inv -> {
      inv.getArgument(0, LifecyclePolicyVersionPO.class).setId(91L);
      return 1;
    });

    TtlPolicyVersionService.PublishResult result = service.publish(10L, "lucas");

    assertThat(result.appended()).isTrue();
    assertThat(result.version().versionNo()).isEqualTo(4);
    assertThat(po.getPublishedVersionId()).isEqualTo(91L);
  }

  @Test
  void offlineRequiresPublishedState() {
    LifecyclePolicyPO po = draftPolicy();
    when(policyMapper.selectOne(any())).thenReturn(po);
    assertThatThrownBy(() -> service.offline(10L, "lucas"))
        .isInstanceOf(LifecycleException.class);

    po.setPublishState(PublishState.PUBLISHED.name());
    service.offline(10L, "lucas");
    assertThat(po.getPublishState()).isEqualTo(PublishState.OFFLINE.name());
    assertThat(po.getPublishedVersionId()).isNull(); // 只退出生效,不动指针
  }

  @Test
  void effectiveOverlaysDraftColumnsWithPublishedSnapshot() {
    LifecyclePolicyPO publishedSource = draftPolicy();
    LifecyclePolicyVersionPO v1 = versionRow(100L, 1, publishedSource);
    LifecyclePolicyPO po = draftPolicy();
    po.setHotDays(99); // 未发布的草稿修改
    po.setPublishState(PublishState.PUBLISHED.name());
    po.setPublishedVersionId(100L);
    when(versionMapper.selectById(100L)).thenReturn(v1);

    LifecyclePolicyPO effective = service.effective(po);

    assertThat(effective).isNotNull();
    assertThat(effective.getHotDays()).isEqualTo(7); // 消费方读到发布版
    assertThat(effective.getId()).isEqualTo(10L);
    assertThat(effective.getPolicyCode()).isEqualTo("ttl_custom_test");
    assertThat(po.getHotDays()).isEqualTo(99); // 原草稿不被篡改
  }

  @Test
  void effectiveReturnsNullForDraftDisabledOrMissingSnapshot() {
    LifecyclePolicyPO po = draftPolicy();
    assertThat(service.effective(po)).isNull(); // DRAFT

    po.setPublishState(PublishState.PUBLISHED.name());
    po.setPublishedVersionId(100L);
    when(versionMapper.selectById(100L)).thenReturn(null);
    assertThat(service.effective(po)).isNull(); // 指针悬空

    po.setStatus("DISABLED");
    po.setPublishState(PublishState.PUBLISHED.name());
    po.setPublishedVersionId(null);
    assertThat(service.effective(po)).isNull(); // 开关关闭
  }

  @Test
  void rollbackRewritesDraftAndRepublishesWithoutErasingHistory() {
    LifecyclePolicyPO po = draftPolicy();
    po.setHotDays(99);
    po.setDraftRevision(5);
    LifecyclePolicyVersionPO v1 = versionRow(100L, 1, draftPolicy());
    when(policyMapper.selectOne(any())).thenReturn(po);
    when(versionMapper.selectOne(any())).thenReturn(v1); // v1 即最新

    TtlPolicyVersionService.PublishResult result = service.rollback(10L, 1, "lucas");

    assertThat(po.getHotDays()).isEqualTo(7); // 草稿被 v1 内容覆盖
    assertThat(po.getRemark()).isNull();
    assertThat(po.getDraftRevision()).isEqualTo(6);
    assertThat(po.getPublishState()).isEqualTo(PublishState.PUBLISHED.name());
    assertThat(po.getPublishedVersionId()).isEqualTo(100L);
    assertThat(result.appended()).isFalse(); // 内容与 v1 相同 -> 复用不追加
    // 两次 updateById:回滚覆盖草稿 + 发布指针(幂等复用 v1 行)
    verify(policyMapper, times(2)).updateById(po);
  }

  @Test
  void hasPendingDraftTracksSemanticEqualityAcrossTypes() {
    LifecyclePolicyPO po = draftPolicy();
    Map<String, Object> snapshot = TtlPolicyVersionService.draftSnapshot(po);
    // MySQL JSON 回填反序列化数字口径可能为 Long,语义等值必须吸收
    Map<String, Object> asLongs = new java.util.LinkedHashMap<>(snapshot);
    asLongs.put("hotDays", 7L);
    assertThat(TtlPolicyVersionService.snapshotsEqual(snapshot, asLongs)).isTrue();
    assertThat(TtlPolicyVersionService.snapshotsEqual(snapshot,
        Map.of("hotDays", 8))).isFalse();
  }

  @Test
  void publishRejectsUnknownPolicy() {
    when(policyMapper.selectOne(any())).thenReturn(null);
    assertThatThrownBy(() -> service.publish(404L, "lucas"))
        .isInstanceOf(LifecycleException.class);
  }
}
