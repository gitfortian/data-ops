package io.yak.ops.business.datasource.controller.v1.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.datasource.execution.audit.SqlExecutionAuditCriteria;
import io.yak.ops.business.datasource.execution.audit.SqlExecutionAuditSummary;
import io.yak.ops.common.bean.dto.observability.SqlExecutionAuditQueryDTO;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqlExecutionAuditConverterTest {
  private final SqlExecutionAuditConverter converter = new SqlExecutionAuditConverter();

  @Test
  void mapsTransportFiltersToTypedCriteria() {
    SqlExecutionAuditQueryDTO dto = new SqlExecutionAuditQueryDTO();
    dto.setPageNo(2);
    dto.setPageSize(50);
    dto.setDataSourceId("42");
    dto.setCaller("task-plugin");
    dto.setStatus("succeeded");
    SqlExecutionAuditCriteria result = converter.criteria(dto);
    assertThat(result.pageNo()).isEqualTo(2);
    assertThat(result.pageSize()).isEqualTo(50);
    assertThat(result.dataSourceId()).isEqualTo("42");
    assertThat(result.caller()).isEqualTo(SqlExecutionCaller.TASK_PLUGIN);
    assertThat(result.status()).isEqualTo(SqlExecutionStatus.SUCCEEDED);
  }

  @Test
  void rejectsInvertedTimeRangeAtTransportBoundary() {
    SqlExecutionAuditQueryDTO dto = new SqlExecutionAuditQueryDTO();
    dto.setStartedFrom(LocalDateTime.of(2026, 8, 24, 10, 0));
    dto.setStartedTo(LocalDateTime.of(2026, 8, 24, 9, 0));
    assertThatThrownBy(() -> converter.criteria(dto))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("startedFrom must not be after startedTo");
  }

  @Test
  void preservesLegacyOtherBucketForNullStatementType() {
    SqlExecutionAuditSummary summary = new SqlExecutionAuditSummary(
        1L, 1L, 0L, 0L, 0L, 1D, 12.5D, 20L, 18L, 2L, 0L,
        List.of(new SqlExecutionAuditSummary.StatementTypeCount(null, 1L)));
    assertThat(converter.summary(summary).statementTypes().getFirst().statementType()).isEqualTo("OTHER");
    assertThat(converter.summary(summary).avgDurationMs()).isEqualTo(12.5D);
  }
}
