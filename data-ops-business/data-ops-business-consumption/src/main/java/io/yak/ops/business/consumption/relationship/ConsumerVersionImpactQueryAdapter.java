package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.api.ConsumerVersionImpactQueryApi;
import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.core.security.ActionAuthorization;
import io.yak.ops.spi.section.SectionStatus;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class ConsumerVersionImpactQueryAdapter implements ConsumerVersionImpactQueryApi {
  private final ActionAuthorization authorization;
  private final CurrentProject currentProject;
  private final ProductDiscoveryService discovery;
  private final SubscriptionRepository subscriptions;
  private final UsageEvidenceRepository usage;

  @Override
  public Result read(String type, String identity, String version) {
    if (!("DATASET".equals(type) || "DATA_SERVICE".equals(type)) || identity == null
        || !identity.matches("[1-9][0-9]{0,18}") || Long.parseLong(identity) <= 0
        || version == null || !version.matches("[1-9][0-9]{0,29}")) throw new IllegalArgumentException("来源版本无效");
    authorization.requirePermission(AssetPermissionCode.READ);
    Long project = currentProject.requireProjectId();
    if (project == null || project <= 0) throw new IllegalArgumentException("缺少当前项目");
    ProductKey key = ProductKey.parse(type + ":" + identity);
    try {
      var lookup = discovery.get(key);
      if (lookup == null) return failed(type, identity, version, SectionStatus.UNAVAILABLE);
      if (lookup.state() != ProductLookupState.FOUND) return failed(type, identity, version,
          lookup.state() == ProductLookupState.FORBIDDEN ? SectionStatus.PERMISSION_DENIED : SectionStatus.UNAVAILABLE);
      var product = lookup.product();
      if (product == null || !project.equals(product.projectId()) || !key.equals(product.productKey())) {
        return failed(type, identity, version, SectionStatus.PERMISSION_DENIED);
      }
      Window observed = window(() -> {
        var rows = usage.listByVersion(project, key, version, WINDOW_LIMIT);
        if (rows == null || rows.size() > WINDOW_LIMIT || rows.stream().anyMatch(row -> row == null
            || !project.equals(row.projectId()) || !key.equals(row.productKey())
            || !version.equals(row.sourceVersion().identity()) || row.outcome() != UsageOutcome.SUCCESS)) throw new IllegalStateException();
        var merged = new LinkedHashMap<ConsumerIdentity, MutableConsumer>();
        rows.forEach(row -> merged.computeIfAbsent(ConsumerIdentity.of(row.consumerRef()), ignored -> new MutableConsumer(row.consumerRef()))
            .record(row.observedAt()));
        return grouped(rows.size(), merged);
      });
      boolean active = product.activeVersion() != null && version.equals(product.activeVersion().identity());
      if (!active && observed.recordCount() == 0) return failed(type, identity, version,
          observed.status() == SectionStatus.EMPTY ? SectionStatus.NOT_APPLICABLE : observed.status());
      Window declared = window(() -> {
        var rows = subscriptions.listRecentActive(project, key, WINDOW_LIMIT);
        if (rows == null || rows.size() > WINDOW_LIMIT || rows.stream().anyMatch(row -> row == null
            || !project.equals(row.projectId()) || !key.equals(row.productKey())
            || row.status() != SubscriptionStatus.ACTIVE)) throw new IllegalStateException();
        var merged = new LinkedHashMap<ConsumerIdentity, MutableConsumer>();
        rows.forEach(row -> merged.computeIfAbsent(ConsumerIdentity.of(row.consumerRef()), ignored -> new MutableConsumer(row.consumerRef())).record(null));
        return grouped(rows.size(), merged);
      });
      return new Result(type, identity, version, SectionStatus.OK,
          active ? "ACTIVE_SOURCE_REFERENCE" : "NORMALIZED_SUCCESS_REFERENCE", declared, observed);
    } catch (ActionAccessDeniedException | SecurityException denied) {
      return failed(type, identity, version, SectionStatus.PERMISSION_DENIED);
    } catch (RuntimeException unavailable) {
      return failed(type, identity, version, SectionStatus.UNAVAILABLE);
    }
  }

  private static Result failed(String type, String identity, String version, SectionStatus status) {
    return new Result(type, identity, version, status, null, null, null);
  }
  private static Window window(Supplier<Window> read) {
    try { return read.get(); }
    catch (ActionAccessDeniedException | SecurityException denied) {
      return new Window(SectionStatus.PERMISSION_DENIED, 0, "UNKNOWN", List.of());
    } catch (RuntimeException unavailable) {
      return new Window(SectionStatus.UNAVAILABLE, 0, "UNKNOWN", List.of());
    }
  }
  private static Window grouped(int count, Map<ConsumerIdentity, MutableConsumer> consumers) {
    return new Window(count == 0 ? SectionStatus.EMPTY : SectionStatus.OK, count,
        count == WINDOW_LIMIT ? "LIMIT_REACHED" : "WITHIN_LIMIT",
        consumers.values().stream().map(MutableConsumer::freeze).toList());
  }
  private record ConsumerIdentity(ConsumerType type, String domain, String identity) {
    private static ConsumerIdentity of(ConsumerRef ref) {
      return new ConsumerIdentity(ref.consumerType(), ref.sourceDomain(), ref.sourceIdentity());
    }
  }
  private static final class MutableConsumer {
    private final ConsumerRef ref;
    private int count;
    private LocalDateTime lastObservedAt;
    private MutableConsumer(ConsumerRef ref) { this.ref = ref; }
    private void record(LocalDateTime at) {
      count++;
      if (at != null && (lastObservedAt == null || at.isAfter(lastObservedAt))) lastObservedAt = at;
    }
    private Consumer freeze() { return new Consumer(ref.consumerType().name(), ref.sourceDomain(), ref.sourceIdentity(), count, lastObservedAt); }
  }
}
