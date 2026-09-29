package io.yak.ops.business.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.core.project.CurrentProject;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class JdbcBusinessAuditServiceTest {

  @AfterEach
  void clearAuthorizationContext() {
    AuditAuthorizationDecisionContext.clear();
  }

  @Test
  void unavailableAuditStoreNeverBreaksBusinessCallerAndOperationIdIsNotTraceId() throws Exception {
    JdbcBusinessAuditService service = unavailableService();

    MDC.put("traceId", "trace-123");
    try {
      AuditOperationHandle handle =
          service.start(
              new AuditOperationRequest(
                  "TEST_OPERATION",
                  "Test operation",
                  "TEST_RESOURCE",
                  "42",
                  "resource",
                  "TEST",
                  Map.of()));

      assertThat(handle.operationId()).startsWith("AUD-").isNotEqualTo("trace-123");
      assertThatCode(
              () -> {
                handle.event(AuditEventType.RESOURCE_UPDATED, "updated", Map.of());
                handle.success("done");
                handle.failure("SHOULD_BE_NOOP", null);
              })
          .doesNotThrowAnyException();
    } finally {
      MDC.remove("traceId");
    }
  }

  @Test
  void authorizationDecisionsRemainFailOpenAndDeferredAllowDoesNotLeak() throws Exception {
    JdbcBusinessAuditService service = unavailableService();
    AuditAuthorizationDecision allow =
        AuditAuthorizationDecision.allow(
            "PROJECT_ACCESS",
            "PROJECT_MEMBER_ACCESS_ALLOWED",
            "PROJECT",
            "7",
            "Project A",
            Map.of());
    AuditAuthorizationDecision deny =
        AuditAuthorizationDecision.deny(
            "PROJECT_ACCESS",
            "PROJECT_MEMBERSHIP_REQUIRED",
            "PROJECT",
            "7",
            null,
            Map.of());

    assertThatCode(() -> service.authorizationDecision(allow)).doesNotThrowAnyException();
    assertThatCode(
            () ->
                service.start(
                    new AuditOperationRequest(
                        "TEST_OPERATION",
                        "Test operation",
                        "TEST_RESOURCE",
                        "42",
                        "resource",
                        "TEST",
                        Map.of())))
        .doesNotThrowAnyException();
    assertThat(AuditAuthorizationDecisionContext.drain()).isEmpty();

    assertThatCode(() -> service.authorizationDecision(deny)).doesNotThrowAnyException();
    assertThatCode(service::clearAuthorizationDecisions).doesNotThrowAnyException();
  }

  private JdbcBusinessAuditService unavailableService() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("audit database unavailable"));
    CurrentProject currentProject = Optional::empty;
    return new JdbcBusinessAuditService(
        dataSource,
        new DataSourceTransactionManager(dataSource),
        currentProject,
        new ObjectMapper());
  }
}
