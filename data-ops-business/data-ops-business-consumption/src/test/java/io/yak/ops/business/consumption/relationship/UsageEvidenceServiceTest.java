package io.yak.ops.business.consumption.relationship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.product.model.ProductType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class UsageEvidenceServiceTest {

  @Test
  void normalizeIsIdempotentByProjectAndSourceDeduplicationId() {
    InMemoryRepository repository = new InMemoryRepository();
    UsageEvidenceService service = new UsageEvidenceService(repository);
    UsageEvidenceService.UsageEvidenceCommand command = new UsageEvidenceService.UsageEvidenceCommand(
        7L,
        new ProductKey(ProductType.DATASET, "42"),
        new SourceVersionRef("1001", "v3"),
        new ConsumerRef(ConsumerType.USER, "SECURITY", "user-9", "Alice"),
        LocalDateTime.of(2026, 9, 25, 8, 0),
        ConsumptionMode.QUERY,
        "DATASET_QUERY",
        "query:q-1",
        "DATASET_QUERY:q-1");

    UsageEvidence first = service.normalize(command);
    UsageEvidence second = service.normalize(command);

    assertEquals(1, repository.values.size());
    assertEquals(first.id(), second.id());
    assertSame(repository.values.get(0), first);
    assertEquals(UsageOutcome.SUCCESS, first.outcome());
    assertEquals("DATASET:42", first.productKey().value());
    assertEquals("USER:SECURITY:user-9", first.consumerRef().identityKey());
  }

  @Test
  void listKeepsSubscriptionAndUsageIndependent() {
    InMemoryRepository repository = new InMemoryRepository();
    UsageEvidenceService service = new UsageEvidenceService(repository);
    ProductKey key = new ProductKey(ProductType.DATA_SERVICE, "88");
    ConsumerRef consumer = new ConsumerRef(
        ConsumerType.DATA_SERVICE, "DATA_SERVICE", "consumer-4", "Partner API");
    service.normalize(new UsageEvidenceService.UsageEvidenceCommand(
        11L,
        key,
        new SourceVersionRef("revision-12", "r12"),
        consumer,
        LocalDateTime.of(2026, 9, 25, 8, 10),
        ConsumptionMode.API_INVOKE,
        "DATA_SERVICE_INVOCATION",
        "invocation:55",
        "DATA_SERVICE_INVOCATION:55"));

    List<UsageEvidence> result = service.list(11L, key, consumer, 20);

    assertEquals(1, result.size());
    assertEquals(ConsumptionMode.API_INVOKE, result.get(0).consumptionMode());
  }

  private static final class InMemoryRepository implements UsageEvidenceRepository {
    private final AtomicLong sequence = new AtomicLong(1);
    private final List<UsageEvidence> values = new ArrayList<>();

    @Override
    public Optional<UsageEvidence> findByDeduplicationId(Long projectId, String deduplicationId) {
      return values.stream()
          .filter(value -> value.projectId().equals(projectId)
              && value.deduplicationId().equals(deduplicationId))
          .findFirst();
    }

    @Override
    public UsageEvidence save(UsageEvidence evidence) {
      UsageEvidence saved = new UsageEvidence(
          sequence.getAndIncrement(),
          evidence.projectId(),
          evidence.productKey(),
          evidence.sourceVersion(),
          evidence.consumerRef(),
          evidence.observedAt(),
          evidence.consumptionMode(),
          evidence.outcome(),
          evidence.provider(),
          evidence.providerEvidenceRef(),
          evidence.deduplicationId(),
          evidence.normalizedAt());
      values.add(saved);
      return saved;
    }

    @Override
    public List<UsageEvidence> listByVersion(
        Long projectId, ProductKey productKey, String versionIdentity, int limit) {
      return values.stream()
          .filter(row -> row.projectId().equals(projectId))
          .filter(row -> row.productKey().equals(productKey))
          .filter(row -> row.sourceVersion().identity().equals(versionIdentity))
          .limit(Math.max(1, Math.min(200, limit)))
          .toList();
    }

    @Override
    public List<UsageEvidence> list(
        Long projectId, ProductKey productKey, ConsumerRef consumerRef, int limit) {
      return values.stream()
          .filter(value -> value.projectId().equals(projectId))
          .filter(value -> productKey == null || value.productKey().equals(productKey))
          .filter(value -> consumerRef == null || value.consumerRef().equals(consumerRef))
          .limit(limit)
          .toList();
    }
  }
}
