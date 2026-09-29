package io.yak.ops.boot.project;

import io.yak.framework.security.common.vo.project.ProjectVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.service.ProjectService;
import io.yak.framework.security.service.UserService;
import io.yak.ops.core.project.ProjectAccessGuard;
import io.yak.ops.core.project.ProjectAuthorizationReason;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Resolves Project Space access from Yak Security project membership. */
@Component
public class YakSecurityProjectAccessGuard implements ProjectAccessGuard {

  private final ProjectService projectService;
  private final UserService userService;
  private final ProjectAuthorizationAuditBridge authorizationAudit;

  public YakSecurityProjectAccessGuard(
      ProjectService projectService,
      UserService userService,
      ProjectAuthorizationAuditBridge authorizationAudit) {
    this.projectService = projectService;
    this.userService = userService;
    this.authorizationAudit = authorizationAudit;
  }

  @Override
  public ProjectContext requireAccessible(Long projectId, String username) {
    if (projectId == null || projectId <= 0 || !StringUtils.hasText(username)) {
      authorizationAudit.denied(
          projectId, ProjectAuthorizationReason.PROJECT_ACCESS_INPUT_INVALID.name());
      throw hiddenNotFound();
    }

    if (!projectService.checkProjectExist(projectId)) {
      authorizationAudit.denied(projectId, ProjectAuthorizationReason.PROJECT_NOT_FOUND.name());
      throw hiddenNotFound();
    }

    UserBriefVO user = userService.getUserBriefByUsername(username);
    if (user == null || user.getId() == null) {
      authorizationAudit.denied(projectId, ProjectAuthorizationReason.PROJECT_ACTOR_NOT_FOUND.name());
      throw hiddenNotFound();
    }

    ProjectVO project = projectService.getProjectDetailByProjectId(projectId);
    if (project == null || project.getId() == null) {
      authorizationAudit.denied(projectId, ProjectAuthorizationReason.PROJECT_NOT_FOUND.name());
      throw hiddenNotFound();
    }

    AccessPath accessPath = accessPath(project, user.getId());
    if (accessPath == null) {
      authorizationAudit.denied(
          projectId, ProjectAuthorizationReason.PROJECT_MEMBERSHIP_REQUIRED.name());
      // Keep the outward error identical to a missing project to prevent Project ID enumeration.
      throw hiddenNotFound();
    }

    if (!Boolean.TRUE.equals(project.getRunning())) {
      authorizationAudit.denied(projectId, ProjectAuthorizationReason.PROJECT_UNAVAILABLE.name());
      throw new ProjectContextException(ProjectContextError.PROJECT_UNAVAILABLE);
    }

    authorizationAudit.allowed(
        projectId,
        project.getProjectName(),
        (accessPath == AccessPath.OWNER
                ? ProjectAuthorizationReason.PROJECT_OWNER_ACCESS_ALLOWED
                : ProjectAuthorizationReason.PROJECT_MEMBER_ACCESS_ALLOWED)
            .name());
    return new ProjectContext(project.getId(), project.getProjectName());
  }

  private ProjectContextException hiddenNotFound() {
    return new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
  }

  private AccessPath accessPath(ProjectVO project, Long userId) {
    if (containsUser(project.getOwnerList(), userId)) {
      return AccessPath.OWNER;
    }
    return containsUser(project.getUserList(), userId) ? AccessPath.MEMBER : null;
  }

  private boolean containsUser(List<UserBriefVO> users, Long userId) {
    List<UserBriefVO> safeUsers = users == null ? Collections.emptyList() : users;
    return safeUsers.stream().anyMatch(user -> Objects.equals(userId, user.getId()));
  }

  private enum AccessPath {
    OWNER,
    MEMBER
  }
}
