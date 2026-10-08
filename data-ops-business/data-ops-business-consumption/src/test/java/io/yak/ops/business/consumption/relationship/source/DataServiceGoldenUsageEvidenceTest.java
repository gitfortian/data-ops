package io.yak.ops.business.consumption.relationship.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.relationship.ConsumerRef;
import io.yak.ops.business.consumption.relationship.ConsumptionMode;
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import io.yak.ops.business.consumption.relationship.UsageEvidenceRepository;
import io.yak.ops.business.consumption.relationship.UsageEvidenceService;
import io.yak.ops.business.consumption.relationship.UsageNormalizationState;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AccessContext;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.execution.DataServiceAuditSanitizer;
import io.yak.ops.business.dataservice.execution.DataServiceInvocationRecorder;
import io.yak.ops.business.dataservice.repository.DataServiceCallLogRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Golden Path B contract from source-owned invocation evidence to normalized Usage Evidence. */
class DataServiceGoldenUsageEvidenceTest {

  @Test
  void successfulManagedInvocationKeepsConsumerAndPinnedRevisionThroughNormalization() {
    InMemoryCallLogRepository callLogs = new InMemoryCallLogRepository();
    DataServiceInvocationRecorder recorder = new DataServiceInvocationRecorder(
        callLogs, new ObjectMapper(), new DataServiceAuditSanitizer(), noEvents());

    recorder.record(
        definition(),
        Map.of("id", "1"),
        true,
        8L,
        1,
        null,
        new AccessContext("API_KEY", 5L, 21L, "Golden BI Consumer", "yak_gold"));

    InvocationRecord source = callLogs.last();
    assertNotNull(source);
    assertEquals(21L, source.consumerId());
    assertEquals(101L, source.sourceRevisionId());
    assertEquals(4, source.sourceRevisionNo());

    InMemoryUsageRepository usages = new InMemoryUsageRepository();
    DataServiceUsageEvidenceNormalizer normalizer =
        new DataServiceUsageEvidenceNormalizer(new UsageEvidenceService(usages));

    var result = normalizer.normalize(source);

    assertEquals(UsageNormalizationState.NORMALIZED, result.state());
    UsageEvidence evidence = result.evidence();
    assertNotNull(evidence);
    assertEquals("DATA_SERVICE:7", evidence.productKey().value());
    assertEquals("101", evidence.sourceVersion().identity());
    assertEquals("r4", evidence.sourceVersion().displayVersion());
    assertEquals("DATA_SERVICE:DATA_SERVICE_CONSUMER:21", evidence.consumerRef().identityKey());
    assertEquals(ConsumptionMode.API_INVOKE, evidence.consumptionMode());
    assertEquals("invocation:501", evidence.providerEvidenceRef());
    assertEquals("DATA_SERVICE_INVOCATION:501", evidence.deduplicationId());
  }

  @Test
  void failedInvocationRemainsAuditEvidenceAndDoesNotCreateUsage() {
    InMemoryCallLogRepository callLogs = new InMemoryCallLogRepository();
    DataServiceInvocationRecorder recorder = new DataServiceInvocationRecorder(
        callLogs, new ObjectMapper(), new DataServiceAuditSanitizer(), noEvents());

    recorder.record(
        definition(),
        Map.of("id", "1"),
        false,
        4L,
        0,
        "source execution failed",
        new AccessContext("API_KEY", 5L, 21L, "Golden BI Consumer", "yak_gold"));

    InvocationRecord source = callLogs.last();
    assertNotNull(source);
    assertEquals(false, source.success());
    assertEquals(21L, source.consumerId());
    assertEquals(101L, source.sourceRevisionId());

    InMemoryUsageRepository usages = new InMemoryUsageRepository();
    DataServiceUsageEvidenceNormalizer normalizer =
        new DataServiceUsageEvidenceNormalizer(new UsageEvidenceService(usages));

    var result = normalizer.normalize(source);

    assertEquals(UsageNormalizationState.IGNORED, result.state());
    assertNull(result.evidence());
    assertEquals(
        List.of(),
        usages.list(3L, ProductKey.parse("DATA_SERVICE:7"), null, 200));
  }

  @Test
  void successfulInvocationWithoutManagedConsumerIsExplicitGapAndCreatesNoUsage() {
    InMemoryCallLogRepository callLogs = new InMemoryCallLogRepository();
    DataServiceInvocationRecorder recorder = new DataServiceInvocationRecorder(
        callLogs, new ObjectMapper(), new DataServiceAuditSanitizer(), noEvents());

    recorder.record(
        definition(),
        Map.of("id", "1"),
        true,
        6L,
        1,
        null,
        AccessContext.publicAccess());

    InvocationRecord source = callLogs.last();
    assertNotNull(source);
    assertNull(source.consumerId());
    assertEquals(101L, source.sourceRevisionId());

    InMemoryUsageRepository usages = new InMemoryUsageRepository();
    DataServiceUsageEvidenceNormalizer normalizer =
        new DataServiceUsageEvidenceNormalizer(new UsageEvidenceService(usages));

    var result = normalizer.normalize(source);

    assertEquals(UsageNormalizationState.GAP, result.state());
    assertNull(result.evidence());
    assertEquals(
        List.of(),
        usages.list(3L, ProductKey.parse("DATA_SERVICE:7"), null, 200));
  }

  @Test
  void normalizationProviderFailureIsUnavailableAndSameSourceCanBeRetried() {
    InMemoryCallLogRepository callLogs = new InMemoryCallLogRepository();
    DataServiceInvocationRecorder recorder = new DataServiceInvocationRecorder(
        callLogs, new ObjectMapper(), new DataServiceAuditSanitizer(), noEvents());

    recorder.record(
        definition(),
        Map.of("id", "1"),
        true,
        8L,
        1,
        null,
        new AccessContext("API_KEY", 5L, 21L, "Golden BI Consumer", "yak_gold"));

    InvocationRecord source = callLogs.last();
    InMemoryUsageRepository usages = new InMemoryUsageRepository();
    usages.failNextSave();
    DataServiceUsageEvidenceNormalizer normalizer =
        new DataServiceUsageEvidenceNormalizer(new UsageEvidenceService(usages));

    var failed = normalizer.normalize(source);

    assertEquals(UsageNormalizationState.UNAVAILABLE, failed.state());
    assertNull(failed.evidence());
    assertEquals("invocation:501", failed.providerEvidenceRef());
    assertEquals(
        List.of(),
        usages.list(3L, ProductKey.parse("DATA_SERVICE:7"), null, 200));

    var retried = normalizer.normalize(source);

    assertEquals(UsageNormalizationState.NORMALIZED, retried.state());
    assertNotNull(retried.evidence());
    assertEquals("DATA_SERVICE_INVOCATION:501", retried.evidence().deduplicationId());
    assertEquals(
        1,
        usages.list(3L, ProductKey.parse("DATA_SERVICE:7"), null, 200).size());
  }

  private DataServiceDefinition definition() {
    LocalDateTime publishedAt = LocalDateTime.of(2026, 9, 26, 9, 0);
    return DataServiceDefinition.restore(
        7L,
        3L,
        11L,
        new DataServiceSettings("Orders", "/orders", 100, 30, true, null, false),
        new PublishedRuntimeSnapshot(9L, "select id from orders where id = :id"),
        new SourceReference("DATASET", "42", 101L, 4),
        new RuntimePolicy(false, 60, 100, false, 5, 30),
        AuthMode.NONE,
        publishedAt,
        publishedAt);
  }

  private ApplicationEventPublisher noEvents() {
    return event -> {};
  }

  private static final class InMemoryCallLogRepository implements DataServiceCallLogRepository {
    private InvocationRecord last;

    @Override
    public InvocationRecord save(InvocationRecord record) {
      last = new InvocationRecord(
          501L,
          record.projectId(),
          record.apiId(),
          record.serviceName(),
          record.servicePath(),
          record.callerType(),
          record.apiKeyId(),
          record.consumerId(),
          record.apiKeyName(),
          record.apiKeyPrefix(),
          record.sourceRevisionId(),
          record.sourceRevisionNo(),
          record.paramsJson(),
          record.success(),
          record.durationMs(),
          record.rowCount(),
          record.errorMessage(),
          record.createTime());
      return last;
    }

    InvocationRecord last() {
      return last;
    }

    @Override
    public List<InvocationRecord> recent(int limit) {
      return last == null ? List.of() : List.of(last);
    }

    @Override
    public List<InvocationRecord> recentByApi(Long apiId, int limit) {
      return last != null && last.apiId().equals(apiId) ? List.of(last) : List.of();
    }

    @Override
    public Optional<InvocationRecord> findByApiAndId(Long apiId, Long invocationId) {
      return last != null && last.apiId().equals(apiId) && last.id().equals(invocationId)
          ? Optional.of(last) : Optional.empty();
    }

    @Override
    public List<InvocationRecord> between(LocalDateTime from, LocalDateTime to) {
      return last == null ? List.of() : List.of(last);
    }
  }

  private static final class InMemoryUsageRepository implements UsageEvidenceRepository {
    private final AtomicLong sequence = new AtomicLong(601L);
    private final List<UsageEvidence> values = new ArrayList<>();
    private boolean failNextSave;

    void failNextSave() {
      failNextSave = true;
    }

    @Override
    public Optional<UsageEvidence> findByDeduplicationId(Long projectId, String deduplicationId) {
      return values.stream()
          .filter(value -> value.projectId().equals(projectId))
          .filter(value -> value.deduplicationId().equals(deduplicationId))
          .findFirst();
    }

    @Override
    public UsageEvidence save(UsageEvidence evidence) {
      if (failNextSave) {
        failNextSave = false;
        throw new IllegalStateException("usage store unavailable");
      }
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
