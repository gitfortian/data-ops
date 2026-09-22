package io.yak.ops.business.development.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.domain.DevelopmentTaskRevisionSummary;
import io.yak.ops.business.development.lineage.DevelopmentLineageOutbox;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskDraftRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.development.service.DevelopmentDraftConflictException;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable task-authoring facade; specialized roles own Draft, validation, publication and reads. */
@Service
public class DevelopmentTaskService {

  private final DevelopmentTaskNodeResolver nodes;
  private final DevelopmentTaskDefinitionNormalizer definitions;
  private final DevelopmentTaskDraftManager drafts;
  private final DevelopmentTaskValidator validator;
  private final TaskDefinitionDigestCalculator digests;
  private final DevelopmentTaskPublisher publisher;
  private final DevelopmentTaskRevisionReader revisions;
  private final DevelopmentLineageOutbox outbox;

  /** Keeps focused unit tests and non-Spring callers source compatible. */
  public DevelopmentTaskService(
      DevelopmentNodeRepository nodeRepository,
      DevelopmentTaskDraftRepository draftRepository,
      DevelopmentTaskRevisionRepository revisionRepository,
      TaskCatalogService taskCatalogService,
      TaskPluginRegistry pluginRegistry,
      ObjectMapper objectMapper) {
    this(
        nodeRepository,
        draftRepository,
        revisionRepository,
        taskCatalogService,
        pluginRegistry,
        objectMapper,
        null);
  }

  public DevelopmentTaskService(
      DevelopmentNodeRepository nodeRepository,
      DevelopmentTaskDraftRepository draftRepository,
      DevelopmentTaskRevisionRepository revisionRepository,
      TaskCatalogService taskCatalogService,
      TaskPluginRegistry pluginRegistry,
      ObjectMapper objectMapper,
      DevelopmentLineageOutbox outbox) {
    this(
        new DevelopmentTaskNodeResolver(nodeRepository),
        new DevelopmentTaskDefinitionNormalizer(objectMapper),
        new DevelopmentTaskDraftManager(draftRepository, nodeRepository, pluginRegistry),
        new DevelopmentTaskValidator(pluginRegistry),
        new TaskDefinitionDigestCalculator(),
        new DevelopmentTaskPublisher(revisionRepository, nodeRepository, taskCatalogService),
        new DevelopmentTaskRevisionReader(revisionRepository),
        outbox);
  }

  @Autowired
  public DevelopmentTaskService(
      DevelopmentTaskNodeResolver nodes,
      DevelopmentTaskDefinitionNormalizer definitions,
      DevelopmentTaskDraftManager drafts,
      DevelopmentTaskValidator validator,
      TaskDefinitionDigestCalculator digests,
      DevelopmentTaskPublisher publisher,
      DevelopmentTaskRevisionReader revisions,
      DevelopmentLineageOutbox outbox) {
    this.nodes = nodes;
    this.definitions = definitions;
    this.drafts = drafts;
    this.validator = validator;
    this.digests = digests;
    this.publisher = publisher;
    this.revisions = revisions;
    this.outbox = outbox;
  }

  public DevelopmentTaskDraft getDraft(Long nodeId) {
    DevelopmentNode node = nodes.requireTaskNode(nodeId);
    DevelopmentTaskDraft draft = drafts.get(node);
    if (!"SQL".equalsIgnoreCase(node.type())) return draft;

    TaskDefinition normalized = definitions.normalize(
        node,
        draft.definition().taskType(),
        draft.definition().schemaVersion(),
        draft.definition().content(),
        draft.definition().configJson());
    if (normalized.equals(draft.definition())) return draft;
    return new DevelopmentTaskDraft(
        draft.nodeId(),
        normalized,
        draft.draftRevision(),
        draft.createTime(),
        draft.updateTime());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentTaskDraft saveDraft(
      Long nodeId,
      String taskType,
      int schemaVersion,
      String content,
      String configJson,
      Long baseRevision) {
    DevelopmentNode node = nodes.requireTaskNode(nodeId);
    TaskDefinition definition =
        definitions.normalize(node, taskType, schemaVersion, content, configJson);
    long expectedRevision = baseRevision == null ? 0L : baseRevision;
    return drafts.save(node, definition, expectedRevision)
        .orElseThrow(() -> new DevelopmentDraftConflictException(
            "草稿已被其他会话更新，请刷新后重新保存（当前基线：" + expectedRevision + "）"));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentTaskRevision publish(Long nodeId, long expectedDraftRevision) {
    DevelopmentNode node = nodes.requireTaskNode(nodeId);
    DevelopmentTaskDraft draft = drafts.lockForPublish(node);
    if (!draft.matchesRevision(expectedDraftRevision)) {
      throw new DevelopmentDraftConflictException(
          "发布失败：草稿版本已变化，期望 "
              + expectedDraftRevision
              + "，当前 "
              + draft.draftRevision());
    }

    TaskDefinition definition = definitions.normalize(
        node,
        draft.definition().taskType(),
        draft.definition().schemaVersion(),
        draft.definition().content(),
        draft.definition().configJson());
    DevelopmentTaskValidation validation = validator.validateForPublish(definition);
    if (!validation.valid()) {
      throw new DevelopmentTaskValidationException(validation.message(), validation.issues());
    }

    DevelopmentTaskRevision published =
        publisher.publish(node, draft, definition, digests.calculate(definition));
    if (outbox != null && "SQL".equalsIgnoreCase(definition.taskType())) {
      outbox.enqueue(node.id(), published.id());
    }
    return published;
  }

  public List<DevelopmentTaskRevisionSummary> listRevisions(Long nodeId) {
    return revisions.list(nodes.requireTaskNode(nodeId));
  }

  public DevelopmentTaskRevision getRevision(Long nodeId, int revisionNo) {
    return revisions.get(nodes.requireTaskNode(nodeId), revisionNo);
  }
}
