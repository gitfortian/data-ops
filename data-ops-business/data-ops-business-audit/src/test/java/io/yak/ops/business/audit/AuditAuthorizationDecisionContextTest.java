package io.yak.ops.business.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuditAuthorizationDecisionContextTest {

  @AfterEach
  void clear() {
    AuditAuthorizationDecisionContext.clear();
  }

  @Test
  void drainReturnsDeferredDecisionsAndClearsThreadLocal() {
    AuditAuthorizationDecision decision =
        AuditAuthorizationDecision.allow(
            "PROJECT_ACCESS",
            "PROJECT_MEMBER_ACCESS_ALLOWED",
            "PROJECT",
            "7",
            "Project A",
            Map.of("scope", "PROJECT_SPACE"));

    AuditAuthorizationDecisionContext.defer(decision);

    assertThat(AuditAuthorizationDecisionContext.drain()).containsExactly(decision);
    assertThat(AuditAuthorizationDecisionContext.drain()).isEmpty();
  }

  @Test
  void authorizationFactoryKeepsCategoryStatusReasonAndProjectResource() {
    AuditAuthorizationDecision decision =
        AuditAuthorizationDecision.deny(
            "PROJECT_ACCESS",
            "PROJECT_MEMBERSHIP_REQUIRED",
            "PROJECT",
            "7",
            null,
            Map.of());

    AuditEventRequest event = AuditEventRequest.authorization(decision, "authorization:test");

    assertThat(event.type()).isEqualTo(AuditEventType.AUTHORIZATION_DECISION);
    assertThat(event.category()).isEqualTo(AuditEventCategory.AUTHORIZATION);
    assertThat(event.status()).isEqualTo(AuditEventStatus.FAILURE);
    assertThat(event.reasonCode()).isEqualTo("PROJECT_MEMBERSHIP_REQUIRED");
    assertThat(event.resourceType()).isEqualTo("PROJECT");
    assertThat(event.resourceId()).isEqualTo("7");
    assertThat(event.payload().get("permission")).isEqualTo("PROJECT_ACCESS");
    assertThat(event.payload().get("decision")).isEqualTo("DENY");
  }
}
