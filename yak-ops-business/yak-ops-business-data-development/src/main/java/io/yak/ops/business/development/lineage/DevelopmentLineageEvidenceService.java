package io.yak.ops.business.development.lineage;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentLineageOutboxRepository;
import io.yak.ops.business.development.repository.DevelopmentLineageOutboxRepository.DiagnosticRecord;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.development.service.DevelopmentSqlLineageService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Read-side projection for governance evidence produced from one immutable Development revision.
 *
 * <p>The durable outbox remains the delivery truth. This service never writes Lineage and never
 * invents EMPTY when the delivery record is missing or unreadable.
 */
@Service
public class DevelopmentLineageEvidenceService {

  private static final Set<String> DELIVERY_STATES =
      Set.of("PENDING", "RUNNING", "FAILED", "SUCCEEDED");

  private final DevelopmentNodeRepository nodes;
  private final DevelopmentTaskRevisionRepository revisions;
  private final DevelopmentLineageOutboxRepository outbox;

  public DevelopmentLineageEvidenceService(
      DevelopmentNodeRepository nodes,
      DevelopmentTaskRevisionRepository revisions,
      DevelopmentLineageOutboxRepository outbox) {
    this.nodes = nodes;
    this.revisions = revisions;
    this.outbox = outbox;
  }

  public Evidence get(long nodeId, int revisionNo) {
    if (nodeId <= 0L) throw new IllegalArgumentException("nodeId 必须大于 0");
    if (revisionNo <= 0) throw new IllegalArgumentException("revisionNo 必须大于 0");

    DevelopmentNode node = nodes.findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("数据开发节点不存在：" + nodeId));
    node.requireProjectId();
    DevelopmentTaskRevision revision = revisions.findByRevisionNo(nodeId, revisionNo)
        .orElseThrow(() -> new IllegalArgumentException(
            "数据开发发布版本不存在：nodeId=" + nodeId + ", revisionNo=" + revisionNo));

    if (!node.id().equals(revision.nodeId())) {
      throw new IllegalStateException("发布版本不属于当前数据开发节点");
    }

    if (revision.definition() == null
        || !"SQL".equalsIgnoreCase(revision.definition().taskType())) {
      return new Evidence(
          node.id(),
          revision.id(),
          revision.revisionNo(),
          revision.createTime(),
          "NOT_APPLICABLE",
          "当前发布版本不是 SQL，不生成 SQL Lineage evidence",
          null,
          0,
          null,
          null,
          null,
          null,
          null);
    }

    String assetKey = DevelopmentSqlLineageService.sqlTaskAssetKey(node.id());
    Optional<DiagnosticRecord> diagnostic = outbox.findDiagnostic(node.id(), revision.id());
    if (diagnostic.isEmpty()) {
      return new Evidence(
          node.id(),
          revision.id(),
          revision.revisionNo(),
          revision.createTime(),
          "UNAVAILABLE",
          "未找到该 Published SQL Revision 的 durable lineage delivery evidence；不能解释为没有血缘",
          assetKey,
          0,
          null,
          null,
          null,
          null,
          null);
    }

    DiagnosticRecord record = diagnostic.get();
    String rawStatus = record.status() == null ? "" : record.status().trim().toUpperCase();
    String status = DELIVERY_STATES.contains(rawStatus) ? rawStatus : "UNAVAILABLE";
    String reason = switch (status) {
      case "PENDING" -> "Lineage evidence 已进入 durable outbox，等待处理";
      case "RUNNING" -> "Lineage evidence 正在写入 Lineage Truth Owner";
      case "FAILED" -> "Lineage evidence 写入失败，durable outbox 将按退避策略重试";
      case "SUCCEEDED" -> "Lineage evidence 已提交到 Lineage Truth Owner";
      default -> "Lineage delivery 状态不可识别；不能解释为 EMPTY";
    };

    return new Evidence(
        node.id(),
        revision.id(),
        revision.revisionNo(),
        revision.createTime(),
        status,
        reason,
        assetKey,
        record.attempts(),
        record.lastError(),
        record.nextAttemptTime(),
        record.createTime(),
        record.updateTime(),
        record.taskId());
  }

  public record Evidence(
      long nodeId,
      long revisionId,
      int revisionNo,
      Instant publishTime,
      String status,
      String reason,
      String lineageAssetKey,
      int attempts,
      String lastError,
      LocalDateTime nextAttemptTime,
      LocalDateTime deliveryCreateTime,
      LocalDateTime deliveryUpdateTime,
      String deliveryTaskId) {}
}
