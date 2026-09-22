package io.yak.ops.business.sync.offline.definition;

import io.yak.ops.common.enums.PublishState;
import com.fasterxml.jackson.databind.JsonNode;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.definition.OfflineDefinitionSupport.DraftDefinition;
import io.yak.ops.business.sync.offline.definition.OfflineDefinitionSupport.PreparedDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.mapping.OfflineSyncViewMapper;
import io.yak.ops.business.sync.offline.notification.OfflineNotificationPolicyCodec;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.business.sync.offline.schedule.OfflineScheduleLifecycle;
import io.yak.ops.business.sync.offline.schedule.OfflineScheduleSupport;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionQueryDTO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobRevisionPO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobDefinitionVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 离线同步当前定义的稳定 Application Facade；发布快照的读写入口在 {@link OfflineJobRevisionService}。 */
@ConditionalOnOfflineSyncEnabled
@Service
@RequiredArgsConstructor
public class OfflineJobDefinitionService {

  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineScheduleRepository scheduleRepository;
  private final OfflineDefinitionSupport support;
  private final OfflineNotificationPolicyCodec notificationPolicyCodec;
  private final OfflineEditorMetaCodec editorMetaCodec;
  private final OfflineScheduleSupport scheduleSupport;
  private final OfflineScheduleLifecycle scheduleLifecycle;
  private final OfflineSyncViewMapper viewMapper;
  private final OfflineJobRevisionService revisionService;
  private final AtomicLong idSequence = new AtomicLong(System.currentTimeMillis() * 1000L);

  public Long nextId() {
    long floor = System.currentTimeMillis() * 1000L;
    long value = idSequence.updateAndGet(current -> Math.max(current + 1, floor));
    while (definitionRepository.findById(value).isPresent()) {
      value = idSequence.incrementAndGet();
    }
    return value;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public Long saveDraft(OfflineJobDefinitionDTO dto) {
    Long id = ensureDefinitionId(dto);
    OfflineJobDefinition existing = definitionRepository.findById(id).orElse(null);
    ensureEditable(existing);
    ensureCanSaveDraft(existing);

    DraftDefinition draft = support.prepareDraft(dto);
    String notificationConfigJson = resolveNotificationConfig(dto, existing);
    String editorMetaJson = resolveEditorMeta(dto, existing);
    ensureUniqueName(draft.getJobName(), id);

    OfflineJobDefinition definition = existing == null ? new OfflineJobDefinition() : existing;
    applyDraft(definition, existing, id, draft);
    definition.setNotificationConfigJson(notificationConfigJson);
    definition.setEditorMetaJson(editorMetaJson);
    persist(existing, definition);
    saveScheduleAndSync(id, draft.getRequest().get("schedule"));
    return id;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public Long saveGuide(OfflineJobDefinitionDTO dto) {
    Long id = ensureDefinitionId(dto);
    OfflineJobDefinition existing = definitionRepository.findById(id).orElse(null);
    ensureEditable(existing);

    PreparedDefinition prepared = support.prepare(dto);
    String notificationConfigJson = resolveNotificationConfig(dto, existing);
    String editorMetaJson = resolveEditorMeta(dto, existing);
    ensureUniqueName(prepared.getJobName(), id);

    OfflineJobDefinition definition = existing == null ? new OfflineJobDefinition() : existing;
    applyPrepared(definition, existing, id, prepared);
    definition.setNotificationConfigJson(notificationConfigJson);
    definition.setEditorMetaJson(editorMetaJson);
    persist(existing, definition);
    saveScheduleAndSync(id, prepared.getRequest().get("schedule"));
    return id;
  }

  public String buildGuideConfig(OfflineJobDefinitionDTO dto) {
    return support.buildJobSpec(dto);
  }

  public String resolveLogicalJobSpec(OfflineJobDefinition definition) {
    if (definition == null || !StringUtils.hasText(definition.getJobSpecJson())) {
      throw new IllegalStateException("任务仍是草稿，请完成配置并保存");
    }
    return definition.getJobSpecJson();
  }

  public String resolveExecutionJobSpec(OfflineJobDefinition definition) {
    return resolveExecutionJobSpec(resolveLogicalJobSpec(definition));
  }

  public String resolveExecutionJobSpec(String logicalJobSpecJson) {
    if (!StringUtils.hasText(logicalJobSpecJson)) {
      throw new IllegalStateException("任务版本快照缺少 JobSpec");
    }
    return support.resolveExecutionJobSpec(logicalJobSpecJson);
  }

  /** 执行/调度/工作流读路径：当前发布快照；未发布返回 null——消费方只读已发布内容。 */
  public OfflineJobRevisionPO publishedRevision(OfflineJobDefinition definition) {
    return revisionService.publishedRevision(definition);
  }

  /** 发布：把当前草稿固化为新版本快照并移动发布指针；内容未变时幂等复用不追加。 */
  public OfflineJobRevisionService.PublishResult publish(Long id) {
    return revisionService.publish(id);
  }

  public List<OfflineJobRevisionService.VersionSummary> versions(Long id) {
    return revisionService.versions(id);
  }

  public OfflineJobRevisionService.RevisionDetailView versionDetail(Long id, int versionNo) {
    return revisionService.versionDetail(id, versionNo);
  }

  /** 回滚：以目标版内容覆盖草稿并立即发布（追加式，不抹历史）。 */
  public OfflineJobRevisionService.PublishResult rollback(Long id, int versionNo) {
    return revisionService.rollback(id, versionNo);
  }

  public OfflineJobDefinitionVO get(Long id) {
    validateId(id);
    OfflineJobDefinition definition = definitionRepository
        .findForViewById(id)
        .orElseThrow(() -> new IllegalArgumentException("离线同步任务不存在：" + id));
    OfflineJobDefinitionVO view = viewMapper.definition(definition);
    view.setHasPendingDraft(
        revisionService.pendingDraftFlags(java.util.List.of(definition)).get(definition.getId()));
    return view;
  }

  public JsonNode getEditDetail(Long id) {
    OfflineJobDefinition definition = require(id);
    JsonNode detail = notificationPolicyCodec.applyToEditDetail(
        support.editDetail(definition), definition.getNotificationConfigJson());
    return editorMetaCodec.applyToEditDetail(detail, definition.getEditorMetaJson());
  }

  public PageData<OfflineJobDefinition> pageDomain(OfflineDefinitionQuery query) {
    return definitionRepository.page(query);
  }

  public PagingData<OfflineJobDefinitionVO> page(OfflineJobDefinitionQueryDTO queryDTO) {
    OfflineJobDefinitionQueryDTO query =
        queryDTO == null ? new OfflineJobDefinitionQueryDTO() : queryDTO;
    OfflineDefinitionQuery domainQuery = new OfflineDefinitionQuery(
        query.getCurrent(),
        query.getPageSize(),
        query.getId(),
        query.getJobName(),
        query.getStatus(),
        query.getSourceType(),
        query.getSinkType(),
        query.getSourceTable(),
        query.getSinkTable(),
        query.getCreateTimeStart(),
        query.getCreateTimeEnd());
    PageData<OfflineJobDefinition> domainPage = definitionRepository.pageForView(domainQuery);
    Map<Long, Boolean> pendingFlags = revisionService.pendingDraftFlags(domainPage.records());
    return PagingData.from(domainPage.map(definition -> {
      OfflineJobDefinitionVO view = viewMapper.definition(definition);
      view.setHasPendingDraft(pendingFlags.get(definition.getId()));
      return view;
    }));
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public boolean online(Long id) {
    OfflineJobDefinition definition = require(id);
    resolveLogicalJobSpec(definition);
    // 存量兼容：从未显式发布但已有可执行配置的任务，上线时自动补发 v1。
    if (definition.getPublishedRevisionId() == null) {
      revisionService.publishIfNeededOnOnline(definition, "online-auto");
    }

    definition.setReleaseState(PublishState.PUBLISHED.name());
    definition.setUpdateTime(LocalDateTime.now());
    boolean updated = definitionRepository.update(definition);
    if (updated) {
      scheduleLifecycle.sync(id);
    }
    return updated;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public boolean offline(Long id) {
    OfflineJobDefinition definition = require(id);
    ensureNoOccupyingBatch(id, "运行中的 BatchExecution 不能下线，请先停止任务");

    definition.setReleaseState(PublishState.OFFLINE.name());
    definition.setUpdateTime(LocalDateTime.now());
    boolean updated = definitionRepository.update(definition);
    if (updated) {
      scheduleLifecycle.sync(id);
    }
    return updated;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public boolean delete(Long id) {
    OfflineJobDefinition definition = require(id);
    if (PublishState.PUBLISHED.matches(definition.getReleaseState())) {
      throw new IllegalStateException("已上线任务不能删除，请先下线");
    }
    ensureNoOccupyingBatch(id, "运行中的 BatchExecution 不能删除");

    scheduleLifecycle.remove(id);
    return definitionRepository.delete(id);
  }

  public OfflineJobDefinition require(Long id) {
    validateId(id);
    return definitionRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("离线同步任务不存在：" + id));
  }

  private Long ensureDefinitionId(OfflineJobDefinitionDTO dto) {
    if (dto == null) {
      throw new IllegalArgumentException("任务定义不能为空");
    }
    Long id = dto.getId();
    if (id == null || id <= 0L) {
      id = nextId();
      dto.setId(id);
    }
    return id;
  }

  private String resolveNotificationConfig(
      OfflineJobDefinitionDTO dto,
      OfflineJobDefinition existing) {
    if (dto.getNotification() == null && existing != null) {
      // Backward compatibility: an older client does not know the notification field and must not
      // erase an already-configured policy while editing another part of the task.
      return existing.getNotificationConfigJson();
    }
    return notificationPolicyCodec.encode(dto.getNotification());
  }

  private String resolveEditorMeta(
      OfflineJobDefinitionDTO dto,
      OfflineJobDefinition existing) {
    if (dto.getEditorMeta() == null && existing != null) {
      // Editor metadata is optional for older clients. Keep the selected icon when they save other
      // task settings so UI-only preferences never get erased accidentally.
      return existing.getEditorMetaJson();
    }
    return editorMetaCodec.encode(dto.getEditorMeta());
  }

  private void ensureCanSaveDraft(OfflineJobDefinition existing) {
    if (existing != null && StringUtils.hasText(existing.getJobSpecJson())) {
      throw new IllegalStateException("已生成可执行配置的任务不能退回草稿");
    }
  }

  private void ensureUniqueName(String jobName, Long id) {
    if (definitionRepository.existsByName(jobName, id)) {
      throw new IllegalArgumentException("离线同步任务名称已存在：" + jobName);
    }
  }

  private void applyDraft(
      OfflineJobDefinition definition,
      OfflineJobDefinition existing,
      Long id,
      DraftDefinition draft) {
    LocalDateTime now = LocalDateTime.now();
    definition.setId(id);
    definition.setJobName(draft.getJobName());
    definition.setJobDesc(draft.getJobDesc());
    definition.setMode(draft.getMode());
    definition.setDefinitionJson(draft.getDefinitionJson());
    definition.setJobSpecJson(null);
    definition.setConfigDigest(null);
    definition.setReleaseState(PublishState.OFFLINE.name());
    definition.setSourceType(draft.getSourceType());
    definition.setSinkType(draft.getSinkType());
    definition.setSourceDatasourceId(null);
    definition.setSinkDatasourceId(null);
    definition.setSourceTable(null);
    definition.setSinkTable(null);
    definition.setVersion(0);
    definition.setCreateTime(existing == null ? now : existing.getCreateTime());
    definition.setUpdateTime(now);
  }

  private void applyPrepared(
      OfflineJobDefinition definition,
      OfflineJobDefinition existing,
      Long id,
      PreparedDefinition prepared) {
    LocalDateTime now = LocalDateTime.now();
    definition.setId(id);
    definition.setJobName(prepared.getJobName());
    definition.setJobDesc(prepared.getJobDesc());
    definition.setMode(prepared.getMode());
    definition.setDefinitionJson(prepared.getDefinitionJson());
    definition.setJobSpecJson(prepared.getJobSpecJson());
    definition.setConfigDigest(prepared.getDigest());
    definition.setReleaseState(existing == null ? PublishState.OFFLINE.name() : existing.getReleaseState());
    definition.setSourceType(prepared.getSourceType());
    definition.setSinkType(prepared.getSinkType());
    definition.setSourceDatasourceId(prepared.getSourceDatasourceId());
    definition.setSinkDatasourceId(prepared.getSinkDatasourceId());
    definition.setSourceTable(prepared.getSourceTable());
    definition.setSinkTable(prepared.getSinkTable());
    definition.setVersion(nextVersion(existing));
    definition.setCreateTime(existing == null ? now : existing.getCreateTime());
    definition.setUpdateTime(now);
  }

  private int nextVersion(OfflineJobDefinition existing) {
    if (existing == null || existing.getVersion() == null) {
      return 1;
    }
    return Math.max(0, existing.getVersion()) + 1;
  }

  private void persist(OfflineJobDefinition existing, OfflineJobDefinition definition) {
    if (existing == null) {
      definitionRepository.insert(definition);
    } else {
      definitionRepository.update(definition);
    }
  }

  private void saveScheduleAndSync(Long id, JsonNode schedule) {
    scheduleRepository.saveSchedule(scheduleSupport.prepare(id, schedule));
    scheduleLifecycle.sync(id);
  }

  private void ensureEditable(OfflineJobDefinition definition) {
    if (definition == null) {
      return;
    }
    if (PublishState.PUBLISHED.matches(definition.getReleaseState())) {
      throw new IllegalStateException("已上线任务不能修改，请先下线");
    }
    ensureNoOccupyingBatch(definition.getId(), "运行中的 BatchExecution 不能修改");
  }

  private void ensureNoOccupyingBatch(Long id, String message) {
    if (batchRepository.hasOccupyingBatch(id)) {
      throw new IllegalStateException(message);
    }
  }

  private void validateId(Long id) {
    if (id == null || id <= 0L) {
      throw new IllegalArgumentException("任务定义 ID 不合法");
    }
  }
}
