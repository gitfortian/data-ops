package io.yak.ops.business.sync.offline.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.yak.ops.business.audit.AuditActor;
import io.yak.ops.business.audit.AuditActorResolver;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobRevisionMapper;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobRevisionPO;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfflineJobRevisionServiceTest {

  @Mock private CurrentProject currentProject;
  @Mock private OfflineJobDefinitionRepository definitionRepository;
  @Mock private OfflineJobRevisionMapper revisionMapper;
  @Mock private BusinessAuditService auditService;
  @Mock private AuditActorResolver auditActorResolver;

  private OfflineJobRevisionService service;

  @BeforeEach
  void setUp() {
    service = new OfflineJobRevisionService(
        currentProject, definitionRepository, revisionMapper, auditService, auditActorResolver);
    when(currentProject.requireProjectId()).thenReturn(7L);
    when(auditService.start(any())).thenReturn(AuditOperationHandle.noop(null));
    when(auditActorResolver.currentActor())
        .thenReturn(new AuditActor(null, "lucas", "USER"));
    when(definitionRepository.update(any())).thenReturn(true);
  }

  @Test
  void firstPublishAppendsV1AndMovesPointer() {
    OfflineJobDefinition definition = draft("digest-1", "{\"a\":1}");
    stubDefinition(definition);
    when(revisionMapper.selectOne(any())).thenReturn(null);
    when(revisionMapper.nextVersionNo(10L)).thenReturn(1);
    when(revisionMapper.insert(any(OfflineJobRevisionPO.class))).thenAnswer(invocation -> {
      invocation.getArgument(0, OfflineJobRevisionPO.class).setId(500L);
      return 1;
    });

    OfflineJobRevisionService.PublishResult result = service.publish(10L);

    assertThat(result.appended()).isTrue();
    assertThat(result.version().versionNo()).isEqualTo(1);
    assertThat(result.version().checksum()).hasSize(64).matches("[0-9a-f]+");
    assertThat(result.version().createdBy()).isEqualTo("lucas");

    ArgumentCaptor<OfflineJobRevisionPO> inserted =
        ArgumentCaptor.forClass(OfflineJobRevisionPO.class);
    verify(revisionMapper).insert(inserted.capture());
    assertThat(inserted.getValue().getProjectId()).isEqualTo(7L);
    assertThat(inserted.getValue().getJobDefinitionId()).isEqualTo(10L);
    assertThat(inserted.getValue().getJobSpecJson()).isEqualTo("{\"job\":\"spec\"}");

    assertThat(definition.getPublishedRevisionId()).isEqualTo(500L);
    assertThat(definition.getLatestVersionNo()).isEqualTo(1);
  }

  @Test
  void publishIsIdempotentWhenDraftMatchesLatestRevision() {
    OfflineJobDefinition definition = draft("digest-1", "{\"a\":1}");
    stubDefinition(definition);
    OfflineJobRevisionPO latest = revision(501L, 3, "digest-1", "{\"a\":1}");
    when(revisionMapper.selectOne(any())).thenReturn(latest);

    OfflineJobRevisionService.PublishResult result = service.publish(10L);

    assertThat(result.appended()).isFalse();
    assertThat(result.version().id()).isEqualTo(501L);
    verify(revisionMapper, never()).insert(any(OfflineJobRevisionPO.class));
    verify(revisionMapper, never()).nextVersionNo(anyLong());
    assertThat(definition.getPublishedRevisionId()).isEqualTo(501L);
    assertThat(definition.getLatestVersionNo()).isEqualTo(3);
  }

  @Test
  void editedDraftPublishAppendsNextVersion() {
    OfflineJobDefinition definition = draft("digest-9", "{\"a\":9}");
    stubDefinition(definition);
    when(revisionMapper.selectOne(any()))
        .thenReturn(revision(501L, 3, "digest-1", "{\"a\":1}"));
    when(revisionMapper.nextVersionNo(10L)).thenReturn(4);

    OfflineJobRevisionService.PublishResult result = service.publish(10L);

    assertThat(result.appended()).isTrue();
    assertThat(result.version().versionNo()).isEqualTo(4);
    verify(revisionMapper).insert(any(OfflineJobRevisionPO.class));
  }

  @Test
  void publishWithoutExecutableSpecIsRejected() {
    OfflineJobDefinition definition = draft("digest-1", "{\"a\":1}");
    definition.setJobSpecJson(null);
    stubDefinition(definition);

    assertThatThrownBy(() -> service.publish(10L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("发布");
    verify(revisionMapper, never()).insert(any(OfflineJobRevisionPO.class));
  }

  @Test
  void publishedRevisionFollowsPointerAndRejectsForeignRows() {
    OfflineJobDefinition definition = draft("digest-1", "{\"a\":1}");

    assertThat(service.publishedRevision(definition)).isNull();

    definition.setPublishedRevisionId(500L);
    when(revisionMapper.selectById(500L)).thenReturn(null);
    assertThat(service.publishedRevision(definition)).isNull();

    when(revisionMapper.selectById(500L)).thenReturn(revision(500L, 10L, 1, "d", "{}"));
    assertThat(service.publishedRevision(definition)).isNotNull();

    when(revisionMapper.selectById(500L)).thenReturn(revision(500L, 999L, 1, "d", "{}"));
    assertThat(service.publishedRevision(definition)).isNull();
  }

  @Test
  void pendingDraftFlagsCompareAgainstLatestRevision() {
    OfflineJobDefinition same = draft(10L, "digest-1", "{\"a\":1}");
    OfflineJobDefinition edited = draft(11L, "digest-2", "{\"a\":2}");
    OfflineJobDefinition neverPublished = draft(12L, "digest-3", "{\"a\":3}");
    when(revisionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
        idOnly(501L, 10L), idOnly(502L, 11L)));
    when(revisionMapper.selectBatchIds(any())).thenReturn(List.of(
        revision(501L, 10L, 1, "digest-1", "{\"a\":1}"),
        revision(502L, 11L, 2, "digest-1", "{\"a\":1}")));

    Map<Long, Boolean> flags =
        service.pendingDraftFlags(List.of(same, edited, neverPublished));

    assertThat(flags).containsEntry(10L, false);
    assertThat(flags).containsEntry(11L, true);
    assertThat(flags).containsEntry(12L, true);
  }

  @Test
  void rollbackOverwritesDraftAndImmediatelyPublishes() {
    OfflineJobDefinition definition = draft("digest-new", "{\"a\":99}");
    definition.setVersion(4);
    definition.setPublishedRevisionId(503L);
    stubDefinition(definition);
    OfflineJobRevisionPO target = revision(501L, 1, "digest-1", "{\"a\":1}");
    OfflineJobRevisionPO latest = revision(503L, 3, "digest-3", "{\"a\":3}");
    when(revisionMapper.selectOne(any())).thenReturn(target, latest);
    when(revisionMapper.nextVersionNo(10L)).thenReturn(4);
    when(revisionMapper.insert(any(OfflineJobRevisionPO.class))).thenAnswer(invocation -> {
      invocation.getArgument(0, OfflineJobRevisionPO.class).setId(504L);
      return 1;
    });

    OfflineJobRevisionService.PublishResult result = service.rollback(10L, 1);

    assertThat(result.appended()).isTrue();
    assertThat(result.version().versionNo()).isEqualTo(4);
    assertThat(definition.getDefinitionJson()).isEqualTo("{\"a\":1}");
    assertThat(definition.getConfigDigest()).isEqualTo("digest-1");
    assertThat(definition.getVersion()).isEqualTo(5);
    assertThat(definition.getPublishedRevisionId()).isEqualTo(504L);
    verify(definitionRepository, times(2)).update(definition);
  }

  @Test
  void versionsMarkTheCurrentPublishedRevision() {
    OfflineJobDefinition definition = draft("digest-1", "{\"a\":1}");
    definition.setPublishedRevisionId(501L);
    stubDefinition(definition);
    when(revisionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
        revision(502L, 2, "digest-2", "{\"a\":2}"),
        revision(501L, 1, "digest-1", "{\"a\":1}")));

    List<OfflineJobRevisionService.VersionSummary> versions = service.versions(10L);

    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).versionNo()).isEqualTo(2);
    assertThat(versions.get(0).current()).isFalse();
    assertThat(versions.get(1).current()).isTrue();
  }

  private void stubDefinition(OfflineJobDefinition definition) {
    when(definitionRepository.findById(any())).thenReturn(Optional.of(definition));
  }

  private OfflineJobDefinition draft(String digest, String definitionJson) {
    return draft(10L, digest, definitionJson);
  }

  private OfflineJobDefinition draft(Long id, String digest, String definitionJson) {
    OfflineJobDefinition definition = new OfflineJobDefinition();
    definition.setId(id);
    definition.setProjectId(7L);
    definition.setJobName("orders-sync");
    definition.setVersion(1);
    definition.setConfigDigest(digest);
    definition.setDefinitionJson(definitionJson);
    definition.setJobSpecJson("{\"job\":\"spec\"}");
    return definition;
  }

  private OfflineJobRevisionPO revision(
      Long id, int versionNo, String digest, String definitionJson) {
    return revision(id, 10L, versionNo, digest, definitionJson);
  }

  private OfflineJobRevisionPO revision(
      Long id, Long jobDefinitionId, int versionNo, String digest, String definitionJson) {
    OfflineJobRevisionPO row = new OfflineJobRevisionPO();
    row.setId(id);
    row.setJobDefinitionId(jobDefinitionId);
    row.setVersionNo(versionNo);
    row.setConfigDigest(digest);
    row.setDefinitionJson(definitionJson);
    row.setJobSpecJson("{\"job\":\"spec\"}");
    return row;
  }

  private OfflineJobRevisionPO idOnly(Long id, Long jobDefinitionId) {
    OfflineJobRevisionPO row = new OfflineJobRevisionPO();
    row.setId(id);
    row.setJobDefinitionId(jobDefinitionId);
    return row;
  }
}
