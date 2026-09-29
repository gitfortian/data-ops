package io.yak.ops.boot.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditAuthorizationDecision;
import io.yak.ops.business.audit.BusinessAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ProjectAuthorizationAuditBridgeTest {

  @Test
  void translatesProjectAllowAndDenyWithoutPuttingUsernameIntoPayload() {
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<BusinessAuditService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(auditService);
    ProjectAuthorizationAuditBridge bridge = new ProjectAuthorizationAuditBridge(provider);

    bridge.allowed(7L, "Project A", "PROJECT_OWNER_ACCESS_ALLOWED");
    bridge.denied(8L, "PROJECT_MEMBERSHIP_REQUIRED");

    ArgumentCaptor<AuditAuthorizationDecision> captor =
        ArgumentCaptor.forClass(AuditAuthorizationDecision.class);
    verify(auditService, times(2)).authorizationDecision(captor.capture());

    AuditAuthorizationDecision allow = captor.getAllValues().get(0);
    assertThat(allow.permission()).isEqualTo(ProjectAuthorizationAuditBridge.PERMISSION);
    assertThat(allow.allowed()).isTrue();
    assertThat(allow.resourceType()).isEqualTo(ProjectAuthorizationAuditBridge.RESOURCE_TYPE);
    assertThat(allow.resourceId()).isEqualTo("7");
    assertThat(allow.resourceName()).isEqualTo("Project A");
    assertThat(allow.attributes().get("scope")).isEqualTo("PROJECT_SPACE");

    AuditAuthorizationDecision deny = captor.getAllValues().get(1);
    assertThat(deny.allowed()).isFalse();
    assertThat(deny.resourceId()).isEqualTo("8");
    assertThat(deny.reasonCode()).isEqualTo("PROJECT_MEMBERSHIP_REQUIRED");
  }

  @Test
  void requestLifecycleClearsDeferredAuthorizationStateAtBothBoundaries() {
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<BusinessAuditService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(auditService);
    ProjectAuthorizationAuditBridge bridge = new ProjectAuthorizationAuditBridge(provider);

    bridge.beginRequest();
    bridge.endRequest();

    verify(auditService, times(2)).clearAuthorizationDecisions();
  }

  @Test
  void missingAuditRuntimeNeverBreaksProjectAccess() {
    @SuppressWarnings("unchecked")
    ObjectProvider<BusinessAuditService> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);
    ProjectAuthorizationAuditBridge bridge = new ProjectAuthorizationAuditBridge(provider);

    assertThatCode(
            () -> {
              bridge.beginRequest();
              bridge.allowed(7L, "Project A", "PROJECT_MEMBER_ACCESS_ALLOWED");
              bridge.denied(7L, "PROJECT_MEMBERSHIP_REQUIRED");
              bridge.endRequest();
            })
        .doesNotThrowAnyException();
  }
}
