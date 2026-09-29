package io.yak.ops.business.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditContextTest {

  @Test
  void nestedScopesRestorePreviousCarrierAndNeverLeak() {
    AuditCarrier outer = carrier("AUD-outer");
    AuditCarrier inner = carrier("AUD-inner");

    assertThat(AuditContext.current()).isEmpty();
    try (AuditContext.Scope ignored = AuditContext.open(outer)) {
      assertThat(AuditContext.current()).contains(outer);
      try (AuditContext.Scope nested = AuditContext.open(inner)) {
        assertThat(AuditContext.current()).contains(inner);
      }
      assertThat(AuditContext.current()).contains(outer);
    }
    assertThat(AuditContext.current()).isEmpty();
  }

  private AuditCarrier carrier(String operationId) {
    return new AuditCarrier(
        operationId,
        "7",
        "tester",
        "USER",
        11L,
        "Project A",
        "OFFLINE_SYNC",
        "42",
        "orders",
        "TEST");
  }
}
