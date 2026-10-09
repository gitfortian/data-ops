package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.relationship.ConsumerImpactView.EvidenceState;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionSummary;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Bounded persisted evidence only. Source reconciliation belongs to the Consumption journey. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class ConsumerUsageSummaryReader {
  private final SubscriptionRepository subscriptions;
  private final UsageEvidenceRepository usage;
  private final CurrentProject currentProject;

  public Summary read(ProductKey productKey, int requestedLimit) {
    Long projectId = currentProject.requireProjectId();
    if (projectId == null || projectId <= 0 || productKey == null) {
      throw new IllegalArgumentException("Consumer evidence requires a project and product");
    }
    int limit = Math.max(1, Math.min(200, requestedLimit));
    Window<Subscription> declared = readWindow(() -> {
      List<Subscription> rows = subscriptions.listRecentActive(projectId, productKey, limit);
      if (rows == null || rows.size() > limit || rows.stream().anyMatch(row -> row == null
          || !projectId.equals(row.projectId()) || !productKey.equals(row.productKey())
          || row.status() != SubscriptionStatus.ACTIVE)) throw new IllegalStateException();
      return rows;
    }, limit);
    Window<UsageEvidence> observed = readWindow(() -> {
      List<UsageEvidence> rows = usage.list(projectId, productKey, null, limit);
      if (rows == null || rows.size() > limit || rows.stream().anyMatch(row -> row == null
          || !projectId.equals(row.projectId()) || !productKey.equals(row.productKey())
          || row.outcome() != UsageOutcome.SUCCESS)) throw new IllegalStateException();
      return rows;
    }, limit);

    Map<String, ConsumerRef> consumers = new LinkedHashMap<>();
    declared.rows().forEach(row -> consumers.put(row.consumerRef().identityKey(), row.consumerRef()));
    observed.rows().forEach(row -> consumers.put(row.consumerRef().identityKey(), row.consumerRef()));
    LocalDateTime lastObservedAt = observed.rows().stream().map(UsageEvidence::observedAt)
        .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
    return new Summary(consumers.size(), count(consumers, ConsumerType.USER),
        count(consumers, ConsumerType.TEAM), count(consumers, ConsumerType.DASHBOARD),
        count(consumers, ConsumerType.DATA_SERVICE), count(consumers, ConsumerType.JOB),
        observed.readable() ? observed.rows().size() : null,
        declared.readable() ? declared.rows().size() : null, lastObservedAt,
        declared.state(), observed.state(), limit, limit, declared.extent(), observed.extent());
  }

  private static int count(Map<String, ConsumerRef> consumers, ConsumerType type) {
    return (int) consumers.values().stream().filter(ref -> ref.consumerType() == type).count();
  }

  private static <T> Window<T> readWindow(Supplier<List<T>> read, int limit) {
    try {
      List<T> rows = List.copyOf(read.get());
      return new Window<>(rows.isEmpty() ? EvidenceState.EMPTY : EvidenceState.READY, rows,
          rows.size() == limit ? "LIMIT_REACHED" : "WITHIN_LIMIT");
    } catch (ActionAccessDeniedException | SecurityException denied) {
      return new Window<>(EvidenceState.FORBIDDEN, List.of(), "UNKNOWN");
    } catch (RuntimeException unavailable) {
      return new Window<>(EvidenceState.UNAVAILABLE, List.of(), "UNKNOWN");
    }
  }

  private record Window<T>(EvidenceState state, List<T> rows, String extent) {
    boolean readable() { return state == EvidenceState.READY || state == EvidenceState.EMPTY; }
  }

  /** Counts describe only the readable windows; unreadable side-specific counts remain unknown. */
  public record Summary(int consumerCount, int userCount, int teamCount, int dashboardCount,
      int dataServiceCount, int jobCount, Integer successfulUsageCount, Integer activeSubscriptionCount,
      LocalDateTime lastObservedAt, EvidenceState subscriptionState, EvidenceState usageState,
      int subscriptionWindowLimit, int usageWindowLimit, String subscriptionWindowState,
      String usageWindowState) implements SectionSummary {
    public SectionStatus status() {
      if (consumerCount > 0) return SectionStatus.OK;
      if (subscriptionState == EvidenceState.FORBIDDEN || usageState == EvidenceState.FORBIDDEN) {
        return SectionStatus.PERMISSION_DENIED;
      }
      if (subscriptionState == EvidenceState.UNAVAILABLE || usageState == EvidenceState.UNAVAILABLE) {
        return SectionStatus.UNAVAILABLE;
      }
      return SectionStatus.EMPTY;
    }

    public String coverageNote() {
      return "Persisted evidence windows only; source reconciliation was NOT_PERFORMED. "
          + "Counts include only readable windows, not all consumers or complete history. "
          + "LIMIT_REACHED may omit older records; WITHIN_LIMIT or EMPTY does not prove source completeness. "
          + "Unknown freshness and unreadable providers require source-owner review. Independent reads are not an atomic snapshot.";
    }

    @Override
    public Map<String, Object> values() {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("scope", "当前项目已持久化有效订阅与归一化成功使用的有限窗口；未同步来源");
      values.put("consumerCount", consumerCount);
      values.put("userCount", userCount);
      values.put("teamCount", teamCount);
      values.put("dashboardCount", dashboardCount);
      values.put("dataServiceCount", dataServiceCount);
      values.put("jobCount", jobCount);
      values.put("successfulUsageCount", successfulUsageCount);
      values.put("activeSubscriptionCount", activeSubscriptionCount);
      values.put("lastObservedAt", lastObservedAt);
      values.put("subscriptionState", subscriptionState.name());
      values.put("usageState", usageState.name());
      values.put("subscriptionWindowLimit", subscriptionWindowLimit);
      values.put("usageWindowLimit", usageWindowLimit);
      values.put("subscriptionWindowState", subscriptionWindowState);
      values.put("usageWindowState", usageWindowState);
      values.put("sourceReconciliation", "NOT_PERFORMED");
      values.put("coverageNote", coverageNote());
      return values;
    }
  }
}
