package io.yak.ops.boot.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.ops.core.project.ProjectAccessGuard;
import io.yak.ops.core.project.ProjectAuthorizationReason;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextException;
import io.yak.ops.core.project.ProjectHeaders;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class ProjectScopeInterceptorTest {

  private ProjectContextRuntime currentProject;
  private ProjectAccessGuard accessGuard;
  private CurrentUserProvider currentUserProvider;
  private ProjectAuthorizationAuditBridge authorizationAudit;
  private ProjectScopeInterceptor interceptor;

  @BeforeEach
  void setUp() {
    currentProject = new ProjectContextRuntime();
    accessGuard = mock(ProjectAccessGuard.class);
    currentUserProvider = mock(CurrentUserProvider.class);
    authorizationAudit = mock(ProjectAuthorizationAuditBridge.class);
    interceptor =
        new ProjectScopeInterceptor(
            currentProject, accessGuard, currentUserProvider, authorizationAudit);
  }

  @Test
  void keepsLegacyEndpointsGlobalEvenWhenHeaderExists() throws Exception {
    MockHttpServletRequest request = requestWithProject("7");

    interceptor.preHandle(request, new MockHttpServletResponse(), handler("legacy"));

    assertFalse(currentProject.isPresent());
    verifyNoInteractions(accessGuard, currentUserProvider);
    verify(authorizationAudit).beginRequest();
  }

  @Test
  void optionalEndpointAcceptsMissingProjectDuringBackfill() throws Exception {
    interceptor.preHandle(
        new MockHttpServletRequest(), new MockHttpServletResponse(), handler("optional"));

    assertFalse(currentProject.isPresent());
    verifyNoInteractions(accessGuard, currentUserProvider);
  }

  @Test
  void requiredEndpointRejectsMissingProjectForAuthenticatedUser() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    when(currentUserProvider.getCurrentUser(request)).thenReturn("alice");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean proceeded = interceptor.preHandle(request, response, handler("required"));

    assertFalse(proceeded);
    assertEquals(400, response.getStatus());
    assertTrue(response.getContentAsString().contains("40010"));
    verify(authorizationAudit)
        .denied(null, ProjectAuthorizationReason.PROJECT_REQUIRED.name());
    verifyNoInteractions(accessGuard);
  }

  @Test
  void invalidProjectHeaderIsAuditedWithoutPersistingRawInput() throws Exception {
    MockHttpServletRequest request = requestWithProject("not-a-project-id");
    when(currentUserProvider.getCurrentUser(request)).thenReturn("alice");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean proceeded = interceptor.preHandle(request, response, handler("required"));

    assertFalse(proceeded);
    assertEquals(404, response.getStatus());
    assertTrue(response.getContentAsString().contains("40410"));
    verify(authorizationAudit)
        .denied(null, ProjectAuthorizationReason.PROJECT_ID_INVALID.name());
    verifyNoInteractions(accessGuard);
  }

  @Test
  void anonymousRequiredRequestDefersToAuthenticationBoundary() throws Exception {
    MockHttpServletRequest request = requestWithProject("7");

    interceptor.preHandle(request, new MockHttpServletResponse(), handler("required"));

    assertFalse(currentProject.isPresent());
    verifyNoInteractions(accessGuard);
  }

  @Test
  void bindsAuthorizedProjectAndClearsRequestContextsAfterCompletion() throws Exception {
    MockHttpServletRequest request = requestWithProject("7");
    when(currentUserProvider.getCurrentUser(request)).thenReturn("alice");
    when(accessGuard.requireAccessible(7L, "alice"))
        .thenReturn(new ProjectContext(7L, "Project A"));

    interceptor.preHandle(request, new MockHttpServletResponse(), handler("required"));
    assertEquals(7L, currentProject.requireProjectId());

    interceptor.afterCompletion(
        request, new MockHttpServletResponse(), handler("required"), null);

    verify(authorizationAudit).endRequest();
    assertFalse(currentProject.isPresent());
  }

  @Test
  void rejectedMissingProjectReleasesAuditWithoutSpringAfterCompletion() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    when(currentUserProvider.getCurrentUser(request)).thenReturn("alice");

    assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), handler("required")));

    // Spring MVC only invokes afterCompletion for successfully preHandled interceptors.
    // This test intentionally NEVER calls afterCompletion.
    verify(authorizationAudit).denied(null, ProjectAuthorizationReason.PROJECT_REQUIRED.name());
    verify(authorizationAudit).endRequest();
    assertFalse(currentProject.isPresent());
  }

  @Test
  void rejectedInvalidProjectHeaderReleasesAuditWithoutSpringAfterCompletion() throws Exception {
    MockHttpServletRequest request = requestWithProject("bad-id");
    when(currentUserProvider.getCurrentUser(request)).thenReturn("alice");

    assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), handler("required")));

    verify(authorizationAudit).denied(null, ProjectAuthorizationReason.PROJECT_ID_INVALID.name());
    verify(authorizationAudit).endRequest();
    assertFalse(currentProject.isPresent());
  }

  @Test
  void failingAuthenticationProviderDoesNotLeakPreviousRequestProject() throws Exception {
    MockHttpServletRequest request = requestWithProject("99");
    currentProject.bind(new ProjectContext(71L, "previous"));
    when(currentUserProvider.getCurrentUser(request))
        .thenThrow(new IllegalStateException("authorization backend failed"));

    assertThrows(IllegalStateException.class,
        () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("required")));

    verify(authorizationAudit).beginRequest();
    verify(authorizationAudit).endRequest();
    verifyNoInteractions(accessGuard);
    assertFalse(currentProject.isPresent());
  }

  @Test
  void failingProjectAccessGuardDoesNotLeakPreviousRequestOrGrantAccess() throws Exception {
    MockHttpServletRequest request = requestWithProject("99");
    when(currentUserProvider.getCurrentUser(request)).thenReturn("alice");
    when(accessGuard.requireAccessible(99L, "alice"))
        .thenThrow(new IllegalStateException("project store unavailable"));

    assertThrows(IllegalStateException.class,
        () -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("required")));

    verify(authorizationAudit).endRequest();
    assertFalse(currentProject.isPresent());
  }

  private MockHttpServletRequest requestWithProject(String projectId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ProjectHeaders.PROJECT_ID, projectId);
    return request;
  }

  private HandlerMethod handler(String methodName) throws Exception {
    Method method = TestController.class.getDeclaredMethod(methodName);
    return new HandlerMethod(new TestController(), method);
  }

  private static class TestController {

    void legacy() {}

    @ProjectScope(ProjectMigrationMode.PROJECT_OPTIONAL)
    void optional() {}

    @ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
    void required() {}
  }
}
