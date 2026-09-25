package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Aggregates declared Subscription and observed Usage without conflating either with lineage. */
@Service
@RequiredArgsConstructor
public class ConsumerImpactService {

  private final SubscriptionRepository subscriptions;
  private final UsageEvidenceRepository usage;
  private final CurrentProject currentProject;

  public ConsumerImpactView view(ProductKey productKey, int usageLimit) {
    Long projectId = currentProject.requireProjectId();
    List<Subscription> declared;
    List<UsageEvidence> observed;
    ConsumerImpactView.EvidenceState subscriptionState;
    ConsumerImpactView.EvidenceState usageState;

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
      observed = usage.list(projectId, productKey, null, Math.max(1, Math.min(1000, usageLimit)));
      usageState = observed.isEmpty()
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
      consumer.providerEvidenceRefs.add(row.provider() + ":" + row.providerEvidenceRef());
    }

    List<ConsumerImpactView.KnownConsumer> consumers = merged.values().stream()
        .map(MutableKnownConsumer::freeze)
        .sorted(Comparator.comparing(c -> c.consumerRef().identityKey()))
        .toList();

    String coverage = subscriptionState == ConsumerImpactView.EvidenceState.UNAVAILABLE
        || usageState == ConsumerImpactView.EvidenceState.UNAVAILABLE
        ? "Known consumers are partial because one or more evidence providers are unavailable."
        : "Known consumers include declared subscriptions and normalized successful usage only; external consumers outside available evidence are not claimed complete.";
    return new ConsumerImpactView(productKey, subscriptionState, usageState, consumers, coverage);
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
    private int activeSubscriptions;
    private int successfulUsage;
    private LocalDateTime lastDeclaredAt;
    private LocalDateTime lastObservedAt;

    private MutableKnownConsumer(ConsumerRef consumerRef) {
      this.consumerRef = consumerRef;
    }

    private ConsumerImpactView.KnownConsumer freeze() {
      return new ConsumerImpactView.KnownConsumer(
          consumerRef,
          new ArrayList<>(declaredModes),
          new ArrayList<>(observedModes),
          activeSubscriptions,
          successfulUsage,
          lastDeclaredAt,
          lastObservedAt,
          new ArrayList<>(providerEvidenceRefs));
    }
  }
}
