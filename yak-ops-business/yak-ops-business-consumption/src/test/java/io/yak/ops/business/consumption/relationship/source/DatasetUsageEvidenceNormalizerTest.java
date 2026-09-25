package io.yak.ops.business.consumption.relationship.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.relationship.ConsumerRef;
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import io.yak.ops.business.consumption.relationship.UsageEvidenceRepository;
import io.yak.ops.business.consumption.relationship.UsageEvidenceService;
import io.yak.ops.business.consumption.relationship.UsageNormalizationState;
import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DatasetUsageEvidenceNormalizerTest {

  @Test
  void successfulAttributedQueryNormalizesExactDatasetVersion() {
    InMemoryRepository repository = new InMemoryRepository();

    var result = normalizer(repository).normalize(7L, query("q-1", DatasetQueryStatus.SUCCESS,
        "alice", 9001L, 12));

    assertEquals(UsageNormalizationState.NORMALIZED, result.state());
    assertNotNull(result.evidence());
    assertEquals("DATASET:88", result.evidence().productKey().value());
    assertEquals("9001", result.evidence().sourceVersion().identity());
    assertEquals("v12", result.evidence().sourceVersion().displayVersion());
    assertEquals("USER:SECURITY_PRINCIPAL:alice", result.evidence().consumerRef().identityKey());
    assertEquals("DATASET_QUERY_PERFORMANCE:q-1", result.evidence().deduplicationId());
  }

  @Test
  void failedQueryIsDiagnosticButNotUsage() {
    InMemoryRepository repository = new InMemoryRepository();

    var result = normalizer(repository).normalize(7L, query("q-2", DatasetQueryStatus.FAILED,
        "alice", 9001L, 12));

    assertEquals(UsageNormalizationState.IGNORED, result.state());
    assertEquals(0, repository.values.size());
  }

  @Test
  void historicalQueryWithoutSubjectIsGapNotEmptyUsage() {
    InMemoryRepository repository = new InMemoryRepository();

    DatasetQueryPerformance legacy = new DatasetQueryPerformance(
        "q-3", 88L, "customer", 9001L, 12, "SQL_QUERY", "ds", null, null,
        DatasetQueryStatus.SUCCESS, null, null, null,
        0L, 1L, 2L, 1L, 4L, 10, false, Instant.parse("2026-09-25T01:00:00Z"),
        Instant.parse("2026-09-25T01:00:00.004Z"));

    var result = normalizer(repository).normalize(7L, legacy);

    assertEquals(UsageNormalizationState.GAP, result.state());
    assertEquals(0, repository.values.size());
  }

  @Test
  void retryingSameQueryRemainsIdempotent() {
    InMemoryRepository repository = new InMemoryRepository();
    DatasetUsageEvidenceNormalizer normalizer = normalizer(repository);
    DatasetQueryPerformance source = query("q-4", DatasetQueryStatus.SUCCESS, "alice", 9002L, 13);

    var first = normalizer.normalize(7L, source);
    var second = normalizer.normalize(7L, source);

    assertEquals(UsageNormalizationState.NORMALIZED, first.state());
    assertEquals(UsageNormalizationState.NORMALIZED, second.state());
    assertEquals(1, repository.values.size());
    assertEquals(first.evidence().id(), second.evidence().id());
  }

  private DatasetUsageEvidenceNormalizer normalizer(InMemoryRepository repository) {
    return new DatasetUsageEvidenceNormalizer(new UsageEvidenceService(repository));
  }

  private DatasetQueryPerformance query(
      String queryId,
      DatasetQueryStatus status,
      String subjectIdentity,
      Long versionId,
      Integer versionNo) {
    return new DatasetQueryPerformance(
        queryId,
        88L,
        "customer",
        versionId,
        versionNo,
        "SQL_QUERY",
        "ds",
        "select * from customer",
        null,
        status,
        status == DatasetQueryStatus.SUCCESS ? null : "EXECUTE_SOURCE",
        status == DatasetQueryStatus.SUCCESS ? null : "RuntimeException",
        status == DatasetQueryStatus.SUCCESS ? null : "boom",
        "USER",
        "SECURITY_PRINCIPAL",
        subjectIdentity,
        subjectIdentity,
        0L,
        1L,
        2L,
        1L,
        4L,
        status == DatasetQueryStatus.SUCCESS ? 10 : 0,
        false,
        Instant.parse("2026-09-25T01:00:00Z"),
        Instant.parse("2026-09-25T01:00:00.004Z"));
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
