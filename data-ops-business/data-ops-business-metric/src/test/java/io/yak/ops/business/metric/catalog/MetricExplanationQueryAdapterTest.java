package io.yak.ops.business.metric.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.yak.ops.business.metric.dao.model.MetricVersionPO;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.business.metric.support.MetricSnapshotDigest;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MetricExplanationQueryAdapterTest {
  private final MetricRepository metrics = mock(MetricRepository.class);
  private final MetricVersionRepository versions = mock(MetricVersionRepository.class);
  private final ActionAuthorization authorization = mock(ActionAuthorization.class);
  private final MetricExplanationQueryAdapter query = new MetricExplanationQueryAdapter(metrics, versions, authorization);
  private final String snapshot = "{\"metricName\":\"交易额\",\"metricType\":\"ATOMIC\",\"measureExpr\":\"SUM(amount)\",\"owner\":\"private-owner\",\"unexpected\":\"secret\"}";
  private void source(String json) {
    var metric = mock(Metric.class); when(metric.version()).thenReturn(3);
    when(metrics.findById(7L)).thenReturn(Optional.of(metric));
    var version = new MetricVersionPO(); version.setId(19L); version.setMetricId(7L); version.setVersion(3); version.setSnapshot(json);
    when(versions.findByMetricAndVersion(7L, 3)).thenReturn(version);
  }
  @Test void permissionFailurePrecedesAllSourceReads() {
    doThrow(new IllegalArgumentException("forbidden")).when(authorization).requirePermission(MetricPermissionCode.READ);
    assertThrows(IllegalArgumentException.class, () -> query.require(7, 3)); verifyNoInteractions(metrics, versions);
  }
  @Test void currentImmutableVersionProjectsOnlyWhitelistedFactsAndDigest() {
    source(snapshot); var value = query.require(7, 3);
    assertEquals(MetricSnapshotDigest.sha256(snapshot), value.definition()); assertEquals(19L, value.versionId());
    assertEquals(3, value.facts().size()); assertEquals("SUM(amount)", value.facts().getLast().value());
    assertFalse(value.facts().toString().contains("secret")); assertFalse(value.facts().toString().contains("private-owner"));
  }
  @Test void staleVersionCannotReadOrExplainAnotherSnapshot() {
    source(snapshot); assertThrows(IllegalArgumentException.class, () -> query.require(7, 2));
    verifyNoInteractions(versions);
  }
  @Test void compositeFactsKeepRecordedReferenceVersionAndExcludeUnknownNestedProperties() {
    source("{\"metricName\":\"复合\",\"metricType\":\"COMPOSITE\",\"compositions\":[{\"subMetricId\":8,\"subMetricVersion\":2,\"operator\":\"REF\",\"owner\":\"secret\"}]}");
    var facts = query.require(7, 3).facts();
    assertTrue(facts.getLast().value().contains("\"subMetricVersion\":2"));
    assertFalse(facts.toString().contains("secret"));
  }
  @Test void unavailableMalformedAndOversizeSnapshotsFailInsteadOfTruncatingExpressions() {
    source("{}"); assertThrows(IllegalStateException.class, () -> query.require(7, 3));
    source("bad-json"); assertThrows(IllegalStateException.class, () -> query.require(7, 3));
    source(snapshot.replace("SUM(amount)", "x".repeat(4097))); assertThrows(IllegalStateException.class, () -> query.require(7, 3));
    source("x".repeat(65537)); assertThrows(IllegalStateException.class, () -> query.require(7, 3));
    source(snapshot); when(versions.findByMetricAndVersion(7L, 3)).thenReturn(null);
    assertThrows(IllegalStateException.class, () -> query.require(7, 3));
  }
  @Test void explicitSnapshotReadsItsOwnImmutableVersionAfterCurrentAdvances() {
    source(snapshot);
    when(metrics.findById(7L).orElseThrow().version()).thenReturn(4);
    assertThrows(IllegalArgumentException.class, () -> query.require(7, 3));
    assertEquals(MetricSnapshotDigest.sha256(snapshot), query.requireSnapshot(7, 3).definition());
    verify(versions).findByMetricAndVersion(7L, 3);
    assertThrows(IllegalStateException.class, () -> query.requireSnapshot(7, 2));
    doThrow(new SecurityException("revoked")).when(authorization).requirePermission(MetricPermissionCode.READ);
    assertThrows(SecurityException.class, () -> query.requireSnapshot(7, 3));
  }

}
