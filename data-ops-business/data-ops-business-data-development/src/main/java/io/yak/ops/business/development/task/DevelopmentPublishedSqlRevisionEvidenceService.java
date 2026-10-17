package io.yak.ops.business.development.task;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DataDev-owned, read-only evidence for a precisely published SQL TaskRevision.
 *
 * <p>Published revision integrity is independent of the mutable SQL editor.
 * No ModelVersionRef or executable DWD task binding is implied; DR-07 must
 * authorize that separate cross-domain transition.
 */
@Component
public class DevelopmentPublishedSqlRevisionEvidenceService {
  private final DevelopmentTaskNodeResolver nodes;
  private final DevelopmentTaskRevisionReader revisions;
  private final DevelopmentTaskDraftManager drafts;
  private final DevelopmentTaskDefinitionNormalizer normalizer;
  private final TaskDefinitionDigestCalculator digests;

  public DevelopmentPublishedSqlRevisionEvidenceService(
      DevelopmentTaskNodeResolver nodes,
      DevelopmentTaskRevisionReader revisions,
      DevelopmentTaskDraftManager drafts,
      DevelopmentTaskDefinitionNormalizer normalizer,
      TaskDefinitionDigestCalculator digests) {
    this.nodes = nodes;
    this.revisions = revisions;
    this.drafts = drafts;
    this.normalizer = normalizer;
    this.digests = digests;
  }

  public record Report(
      String nodeId, String taskRevisionId, int taskRevisionNo,
      long publishedFromDraftRevision, String immutableDefinitionSha256,
      long currentDraftRevision, boolean currentDraftMatchesPublishedDefinition,
      String status, boolean authorizedForFrozenExecution) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Report inspect(Long nodeId, int revisionNo) {
    DevelopmentNode node = nodes.requireTaskNode(nodeId);
    node.requireProjectId();
    if (!"SQL".equalsIgnoreCase(node.type())) {
      throw new IllegalArgumentException("正式 SQL 修订核验仅适用于 SQL 节点");
    }
    DevelopmentTaskRevision published = revisions.get(node, revisionNo);
    if (published == null || published.id() == null || published.id() <= 0
        || !Objects.equals(published.nodeId(), node.id())
        || published.revisionNo() != revisionNo
        || published.sourceDraftRevision() < 1) {
      throw new IllegalStateException("已发布 TaskRevision 身份与节点或草稿来源不一致");
    }
    TaskDefinition frozen = published.definition();
    if (frozen == null || !"SQL".equals(frozen.taskType())
        || frozen.content() == null || frozen.content().isBlank()
        || frozen.configJson() == null || frozen.schemaVersion() <= 0) {
      throw new IllegalStateException("已发布 SQL TaskRevision 快照不完整");
    }
    // Validate the exact stored definition. Never silently normalize/rewrite a
    // corrupt historical revision into another executable SQL payload.
    TaskDefinition normalized = normalizer.normalize(
        node, frozen.taskType(), frozen.schemaVersion(),
        frozen.content(), frozen.configJson());
    String computed = digests.calculate(frozen);
    if (!frozen.equals(normalized)
        || published.checksum() == null
        || !published.checksum().matches("[a-f0-9]{64}")
        || !Objects.equals(computed, published.checksum())) {
      throw new IllegalStateException("已发布 TaskRevision 定义校验和错误，禁止视为冻结可执行版本");
    }
    DevelopmentTaskDraft draft = drafts.get(node);
    boolean same = draft != null && draft.definition() != null
        && Objects.equals(computed, digests.calculate(normalizer.normalize(
            node, draft.definition().taskType(), draft.definition().schemaVersion(),
            draft.definition().content(), draft.definition().configJson())));
    return new Report(String.valueOf(node.id()), String.valueOf(published.id()),
        revisionNo, published.sourceDraftRevision(), computed,
        draft == null ? 0 : draft.draftRevision(), same,
        "PUBLISHED_SQL_REVISION_VERIFIED_NOT_EXECUTED", false);
  }
}
