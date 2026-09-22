package io.yak.ops.business.development.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class DevelopmentLineageWriteTransactionTest {
  @Test
  void staleRevisionCannotReplaceNewerLineage() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    DevelopmentSqlLineageService lineage = mock(DevelopmentSqlLineageService.class);
    when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(1L))).thenReturn(12L);
    DevelopmentTaskRevision old = revision(11, 1);

    new DevelopmentLineageWriteTransaction(jdbc, lineage)
        .writeIfLatest(node(), old, mock(DevelopmentSqlLineageService.PreparedLineage.class));

    verify(lineage, never()).applyPrepared(any(), any(), any());
  }

  private static DevelopmentNode node() {
    return new DevelopmentNode(1L, "sql", "SQL", null, null, true, Instant.now(), Instant.now());
  }

  private static DevelopmentTaskRevision revision(long id, int number) {
    return new DevelopmentTaskRevision(id, 1L, number, number,
        new TaskDefinition("SQL", 1, "select 1", "{\"dataSourceId\":\"1\"}"), "c", Instant.now());
  }
}
