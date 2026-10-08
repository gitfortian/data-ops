package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.relationship.source.DataServiceUsageEvidenceSynchronizer;
import io.yak.ops.business.consumption.relationship.source.DatasetUsageEvidenceSynchronizer;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Aggregates declared Subscription and observed Usage without conflating either with lineage. */
@Service
public class ConsumerImpactService {

  private final SubscriptionRepository subscriptions;
  private final UsageEvidenceRepository usage;
  private final CurrentProject currentProject;
  private final DatasetUsageEvidenceSynchronizer datasetSynchronizer;
  private final DataServiceUsageEvidenceSynchronizer dataServiceSynchronizer;

  @Autowired
  public ConsumerImpactService(
      SubscriptionRepository subscriptions,
      UsageEvidenceRepository usage,
      CurrentProject currentProject,
      DatasetUsageEvidenceSynchronizer datasetSynchronizer,
      DataServiceUsageEvidenceSynchronizer dataServiceSynchronizer) {
    this.subscriptions = subscriptions;
    this.usage = usage;
    this.currentProject = currentProject;
    this.datasetSynchronizer = datasetSynchronizer;
    this.dataServiceSynchronizer = dataServiceSynchronizer;
  }

  /** Compatibility constructor for focused tests without source adapters. */
  public ConsumerImpactService(
      SubscriptionRepository subscriptions,
      UsageEvidenceRepository usage,
      CurrentProject currentProject) {
    this(subscriptions, usage, currentProject, null, null);
  }

  public ConsumerImpactView view(ProductKey productKey, int usageLimit) {
    Long projectId = currentProject.requireProjectId();
    List<Subscription> declared;
    List<UsageEvidence> observed;
    ConsumerImpactView.EvidenceState subscriptionState;
    ConsumerImpactView.EvidenceState usageState;
    int limit = Math.max(1, Math.min(200, usageLimit));
    SourceSyncCoverage sourceCoverage = synchronizeSource(productKey, limit);

    try {
      declared = subscriptions.list(projectId, productKey, null);
      subscriptionState = declared.isEmpty()
          ? ConsumerImpactView.EvidenceState.EMPTY
          : ConsumerImpactView.EvidenceState.READY;
    } catch (RuntimeException failure) {
      declared = List.of();
      subscriptionState = ConsumerImpactView.EvidenceState.UNAVAILABLE;
    }

    try {
      observed = usage.list(projectId, productKey, null, limit);
      // A full *window* is still readable evidence. It is not a provider outage.
      usageState = sourceCoverage.readUnavailable() || sourceCoverage.gaps() > 0
          ? ConsumerImpactView.EvidenceState.UNAVAILABLE
          : observed.isEmpty()
          ? ConsumerImpactView.EvidenceState.EMPTY
          : ConsumerImpactView.EvidenceState.READY;
    } catch (RuntimeException failure) {
      observed = List.of();
      usageState = ConsumerImpactView.EvidenceState.UNAVAILABLE;
    }

    Map<String, MutableKnownConsumer> merged = new LinkedHashMap<>();
    for (Subscription row : declared) {
      if (row.status() != SubscriptionStatus.ACTIVE) continue;
      MutableKnownConsumer consumer = merged.computeIfAbsent(
          row.consumerRef().identityKey(), ignored -> new MutableKnownConsumer(row.consumerRef()));
      consumer.declaredModes.add(row.consumptionMode());
      consumer.activeSubscriptions++;
      consumer.lastDeclaredAt = later(consumer.lastDeclaredAt, row.updatedAt());
    }
    for (UsageEvidence row : observed) {
      MutableKnownConsumer consumer = merged.computeIfAbsent(
          row.consumerRef().identityKey(), ignored -> new MutableKnownConsumer(row.consumerRef()));
      consumer.observedModes.add(row.consumptionMode());
      consumer.successfulUsage++;
      consumer.lastObservedAt = later(consumer.lastObservedAt, row.observedAt());
      String evidenceRef = row.provider() + ":" + row.providerEvidenceRef();
      consumer.providerEvidenceRefs.add(evidenceRef);
      consumer.recordVersion(row.sourceVersion(), row.observedAt(), evidenceRef);
    }

    List<ConsumerImpactView.KnownConsumer> consumers = merged.values().stream()
        .map(MutableKnownConsumer::freeze)
        .sorted(Comparator.comparing(c -> c.consumerRef().identityKey()))
        .toList();

    boolean windowLimited = sourceCoverage.limitReached() || observed.size() == limit;
    boolean providerIncomplete = subscriptionState == ConsumerImpactView.EvidenceState.UNAVAILABLE
        || usageState == ConsumerImpactView.EvidenceState.UNAVAILABLE;
    String coverage = providerIncomplete
        ? "Known consumers are partial because source reconciliation is incomplete or one or more evidence providers are unavailable."
        : windowLimited
        ? "Known consumers are limited to the requested source or normalized usage window; the row limit was reached and older or external consumers may not be included."
        : "Known consumers include declared subscriptions and normalized successful usage in the reconciled source window only; external consumers outside available evidence are not claimed complete.";
    ConsumerImpactView.EvidenceCoverage detail = new ConsumerImpactView.EvidenceCoverage(
        limit, sourceCoverage.recordCount(), observed.size(), sourceCoverage.limitReached(),
        observed.size() == limit, sourceCoverage.gaps(), sourceCoverage.readUnavailable());
    return new ConsumerImpactView(productKey, subscriptionState, usageState, consumers, coverage, detail);
  }

  private SourceSyncCoverage synchronizeSource(ProductKey productKey, int limit) {
    try {
      List<UsageNormalizationResult> results;
      if (productKey.productType() == ProductType.DATASET && datasetSynchronizer != null) {
        Long datasetId = parseProductId(productKey);
        if (datasetId == null) return SourceSyncCoverage.failed();
        results = datasetSynchronizer.synchronizeRecentByProduct(datasetId, limit);
      } else if (productKey.productType() == ProductType.DATA_SERVICE && dataServiceSynchronizer != null) {
        Long apiId = parseProductId(productKey);
        if (apiId == null) return SourceSyncCoverage.failed();
        results = dataServiceSynchronizer.synchronizeRecentByProduct(apiId, limit);
      } else {
        return SourceSyncCoverage.failed();
      }
      int gaps = (int) results.stream().filter(result ->
          result.state() == UsageNormalizationState.GAP
              || result.state() == UsageNormalizationState.UNAVAILABLE).count();
      // Equality means we reached the query budget, not that the source provider is broken.
      return new SourceSyncCoverage(results.size(), results.size() >= limit, gaps, false);
    } catch (RuntimeException unavailable) {
      return SourceSyncCoverage.failed();
    }
  }

  private record SourceSyncCoverage(int recordCount, boolean limitReached, int gaps, boolean readUnavailable) {
    static SourceSyncCoverage failed() {
      return new SourceSyncCoverage(0, false, 0, true);
    }
  }

  private Long parseProductId(ProductKey productKey) {
    try {
      long id = Long.parseLong(productKey.sourceIdentity());
      return id > 0 ? id : null;
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  private static LocalDateTime later(LocalDateTime current, LocalDateTime candidate) {
    if (candidate == null) return current;
    return current == null || candidate.isAfter(current) ? candidate : current;
  }

  private static final class MutableKnownConsumer {
    private final ConsumerRef consumerRef;
    private final Set<ConsumptionMode> declaredModes = new LinkedHashSet<>();
    private final Set<ConsumptionMode> observedModes = new LinkedHashSet<>();
    private final Set<String> providerEvidenceRefs = new LinkedHashSet<>();
    private final Map<String, MutableObservedVersion> observedVersions = new LinkedHashMap<>();
    private int activeSubscriptions;
    private int successfulUsage;
    private LocalDateTime lastDeclaredAt;
    private LocalDateTime lastObservedAt;

    private MutableKnownConsumer(ConsumerRef consumerRef) {
      this.consumerRef = consumerRef;
    }

    private void recordVersion(SourceVersionRef version, LocalDateTime observedAt, String evidenceRef) {
      observedVersions.computeIfAbsent(version.identity(), ignored -> new MutableObservedVersion(version))
          .record(version, observedAt, evidenceRef);
    }

    private ConsumerImpactView.KnownConsumer freeze() {
      List<ConsumerImpactView.ObservedVersion> versions = observedVersions.values().stream()
          .map(MutableObservedVersion::freeze)
          .sorted(Comparator.comparing(ConsumerImpactView.ObservedVersion::lastObservedAt).reversed()
              .thenComparing(version -> version.sourceVersion().identity()))
          .toList();
      return new ConsumerImpactView.KnownConsumer(
          consumerRef,
          new ArrayList<>(declaredModes),
          new ArrayList<>(observedModes),
          activeSubscriptions,
          successfulUsage,
          lastDeclaredAt,
          lastObservedAt,
          new ArrayList<>(providerEvidenceRefs),
          versions);
    }
  }

  private static final class MutableObservedVersion {
    private SourceVersionRef sourceVersion;
    private int successfulUsage;
    private LocalDateTime lastObservedAt;
    private final Set<String> providerEvidenceRefs = new LinkedHashSet<>();

    private MutableObservedVersion(SourceVersionRef sourceVersion) {
      this.sourceVersion = sourceVersion;
    }

    private void record(SourceVersionRef candidate, LocalDateTime time, String evidenceRef) {
      successfulUsage++;
      providerEvidenceRefs.add(evidenceRef);
      // Version ID owns identity; display version is descriptive and can be missing in historic audit.
      if (candidate.displayVersion() != null
          && (sourceVersion.displayVersion() == null || lastObservedAt == null || !time.isBefore(lastObservedAt))) {
        sourceVersion = candidate;
      }
      lastObservedAt = later(lastObservedAt, time);
    }

    private ConsumerImpactView.ObservedVersion freeze() {
      return new ConsumerImpactView.ObservedVersion(
          sourceVersion, successfulUsage, lastObservedAt, new ArrayList<>(providerEvidenceRefs));
    }
  }
}
