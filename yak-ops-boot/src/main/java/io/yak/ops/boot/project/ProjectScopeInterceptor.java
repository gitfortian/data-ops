package io.yak.ops.boot.project;

import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.ops.core.project.ProjectAccessGuard;
import io.yak.ops.core.project.ProjectAuthorizationReason;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import io.yak.ops.core.project.ProjectHeaders;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Establishes trusted CurrentProject only for endpoints that have entered Project Space rollout. */
@Component
public class ProjectScopeInterceptor implements HandlerInterceptor {

  private final ProjectContextRuntime currentProject;
  private final ProjectAccessGuard accessGuard;
  private final CurrentUserProvider currentUserProvider;
  private final ProjectAuthorizationAuditBridge authorizationAudit;

  public ProjectScopeInterceptor(
      ProjectContextRuntime currentProject,
      ProjectAccessGuard accessGuard,
      CurrentUserProvider currentUserProvider,
      ProjectAuthorizationAuditBridge authorizationAudit) {
    this.currentProject = currentProject;
    this.accessGuard = accessGuard;
    this.currentUserProvider = currentUserProvider;
    this.authorizationAudit = authorizationAudit;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    authorizationAudit.beginRequest();
    currentProject.clear();
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }

    ProjectMigrationMode mode = resolveMode(handlerMethod);
    if (mode == ProjectMigrationMode.LEGACY_GLOBAL) {
      return true;
    }

    String rawProjectId = request.getHeader(ProjectHeaders.PROJECT_ID);
    if (!StringUtils.hasText(rawProjectId) && mode == ProjectMigrationMode.PROJECT_OPTIONAL) {
      return true;
    }

    // Authentication remains the outer boundary. If this interceptor happens to run before the
    // Yak Security authentication interceptor, an anonymous request must still receive the normal
    // authentication response instead of leaking Project Space validation semantics first.
    String username = currentUserProvider.getCurrentUser(request);
    if (!StringUtils.hasText(username)) {
      return true;
    }

    try {
      Long projectId = parseProjectId(rawProjectId, mode);
      if (projectId == null) {
        return true;
      }
      ProjectContext context = accessGuard.requireAccessible(projectId, username);
      currentProject.bind(context);
      return true;
    } catch (ProjectContextException exception) {
      // 直接落约定响应(400/40010 等),避免被各业务模块 advice 的 catch-all 兜成 999。
      ProjectContextExceptionHandler.writeToResponse(exception, response);
      return false;
    }
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    try {
      authorizationAudit.endRequest();
    } finally {
      currentProject.clear();
    }
  }

  ProjectMigrationMode resolveMode(HandlerMethod handlerMethod) {
    ProjectScope methodScope =
        AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ProjectScope.class);
    if (methodScope != null) {
      return methodScope.value();
    }

    ProjectScope typeScope =
        AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ProjectScope.class);
    return typeScope == null ? ProjectMigrationMode.LEGACY_GLOBAL : typeScope.value();
  }

  private Long parseProjectId(String rawProjectId, ProjectMigrationMode mode) {
    if (!StringUtils.hasText(rawProjectId)) {
      if (mode == ProjectMigrationMode.PROJECT_REQUIRED) {
        authorizationAudit.denied(null, ProjectAuthorizationReason.PROJECT_REQUIRED.name());
        throw new ProjectContextException(ProjectContextError.PROJECT_REQUIRED);
      }
      return null;
    }

    try {
      long projectId = Long.parseLong(rawProjectId.trim());
      if (projectId <= 0) {
        throw new NumberFormatException("project ID must be positive");
      }
      return projectId;
    } catch (NumberFormatException exception) {
      authorizationAudit.denied(null, ProjectAuthorizationReason.PROJECT_ID_INVALID.name());
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND, exception);
    }
  }
}
