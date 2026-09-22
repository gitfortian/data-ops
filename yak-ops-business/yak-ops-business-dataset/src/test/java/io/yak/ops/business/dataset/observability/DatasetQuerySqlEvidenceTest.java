package io.yak.ops.business.dataset.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DatasetQuerySqlEvidenceTest {

  private final DatasetQuerySqlEvidence evidence = new DatasetQuerySqlEvidence();

  @Test
  void redactsLiteralValuesAndBuildsStableShapeFingerprint() {
    DatasetQueryPerformance first = evidence.sanitize(trace(
        "select * from patient where name = 'Alice' and age = 42 and id in (1001, 1002) -- PHI"));
    DatasetQueryPerformance second = evidence.sanitize(trace(
        "select * from patient where name = 'Bob' and age = 99 and id in (3001, 3002)"));

    assertNotNull(first.sqlHash());
    assertEquals(64, first.sqlHash().length());
    assertEquals(first.sqlHash(), second.sqlHash());
    assertFalse(first.sql().contains("Alice"));
    assertFalse(first.sql().contains("42"));
    assertFalse(first.sql().contains("1001"));
    assertFalse(first.sql().contains("PHI"));
  }

  private DatasetQueryPerformance trace(String sql) {
    return new DatasetQueryPerformance(
        "q1", 1L, "patients", 2L, 3, "SQL_QUERY", "ds-1", sql,
        1L, 2L, 3L, 4L, 10L, 1, false, Instant.EPOCH);
  }
}
