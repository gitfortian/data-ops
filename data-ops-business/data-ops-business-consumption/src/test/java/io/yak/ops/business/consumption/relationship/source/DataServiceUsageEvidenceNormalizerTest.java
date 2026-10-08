package io.yak.ops.business.consumption.relationship.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.relationship.ConsumerRef;
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import io.yak.ops.business.consumption.relationship.UsageEvidenceRepository;
import io.yak.ops.business.consumption.relationship.UsageEvidenceService;
import io.yak.ops.business.consumption.relationship.UsageNormalizationState;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DataServiceUsageEvidenceNormalizerTest {

  @Test
  void successfulManagedInvocationNormalizesStableConsumerAndRevision() {
    InMemoryRepository repository = new InMemoryRepository();
    DataServiceUsageEvidenceNormalizer normalizer = normalizer(repository);

    var result = normalizer.normalize(invocation(55L, true, 4L, 9001L, 12, "Partner A"));

    assertEquals(UsageNormalizationState.NORMALIZED, result.state());
    assertNotNull(result.evidence());
    assertEquals("DATA_SERVICE:88", result.evidence().productKey().value());
    assertEquals("9001", result.evidence().sourceVersion().identity());
    assertEquals("r12", result.evidence().sourceVersion().displayVersion());
    assertEquals("DATA_SERVICE:DATA_SERVICE_CONSUMER:4", result.evidence().consumerRef().identityKey());
    assertEquals("DATA_SERVICE_INVOCATION:55", result.evidence().deduplicationId());
  }

  @Test
  void failedInvocationIsAuditButNotUsage() {
    InMemoryRepository repository = new InMemoryRepository();

    var result = normalizer(repository).normalize(invocation(56L, false, 4L, 9001L, 12, "Partner A"));

    assertEquals(UsageNormalizationState.IGNORED, result.state());
    assertEquals(0, repository.values.size());
  }

  @Test
  void historicalInvocationWithoutConsumerOrPinnedRevisionIsGapNotEmptyUsage() {
    InMemoryRepository repository = new InMemoryRepository();

    var result = normalizer(repository).normalize(invocation(57L, true, null, null, null, "legacy-key"));

    assertEquals(UsageNormalizationState.GAP, result.state());
    assertEquals(0, repository.values.size());
  }

  @Test
  void retryingSameInvocationRemainsIdempotent() {
    InMemoryRepository repository = new InMemoryRepository();
    DataServiceUsageEvidenceNormalizer normalizer = normalizer(repository);
    InvocationRecord source = invocation(58L, true, 4L, 9002L, 13, "Partner A");

    var first = normalizer.normalize(source);
    var second = normalizer.normalize(source);

    assertEquals(UsageNormalizationState.NORMALIZED, first.state());
    assertEquals(UsageNormalizationState.NORMALIZED, second.state());
    assertEquals(1, repository.values.size());
    assertEquals(first.evidence().id(), second.evidence().id());
  }

  private DataServiceUsageEvidenceNormalizer normalizer(InMemoryRepository repository) {
    return new DataServiceUsageEvidenceNormalizer(new UsageEvidenceService(repository));
  }

  private InvocationRecord invocation(
      Long id,
      boolean success,
      Long consumerId,
      Long sourceRevisionId,
      Integer sourceRevisionNo,
      String displayName) {
    return new InvocationRecord(
        id,
        7L,
        88L,
        "customer-service",
        "/customer",
        "API_KEY",
        33L,
        consumerId,
        displayName,
        "yak_1234",
        sourceRevisionId,
        sourceRevisionNo,
        "{}",
        success,
        12L,
        success ? 3 : 0,
        success ? null : "boom",
        LocalDateTime.of(2026, 9, 25, 8, 30));
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
