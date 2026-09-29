package io.yak.ops.boot.project;

import io.yak.ops.business.audit.AuditAuthorizationDecision;
import io.yak.ops.business.audit.BusinessAuditService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Adapts Project Space access facts to the shared, fail-open authorization audit contract. */
@Component
public class ProjectAuthorizationAuditBridge {

  static final String PERMISSION = "PROJECT_ACCESS";
  static final String RESOURCE_TYPE = "PROJECT";

  private static final Logger log = LoggerFactory.getLogger(ProjectAuthorizationAuditBridge.class);

  private final ObjectProvider<BusinessAuditService> auditServiceProvider;

  public ProjectAuthorizationAuditBridge(ObjectProvider<BusinessAuditService> auditServiceProvider) {
    this.auditServiceProvider = auditServiceProvider;
  }

  public void beginRequest() {
    BusinessAuditService auditService = auditService();
    if (auditService == null) return;
    safe(auditService::clearAuthorizationDecisions);
  }

  public void allowed(Long projectId, String projectName, String reasonCode) {
    BusinessAuditService auditService = auditService();
    if (auditService == null) return;
    safe(
        () ->
            auditService.authorizationDecision(
                AuditAuthorizationDecision.allow(
                    PERMISSION,
                    reasonCode,
                    RESOURCE_TYPE,
                    resourceId(projectId),
                    projectName,
                    Map.of("scope", "PROJECT_SPACE"))));
  }

  public void denied(Long projectId, String reasonCode) {
    BusinessAuditService auditService = auditService();
    if (auditService == null) return;
    safe(
        () ->
            auditService.authorizationDecision(
                AuditAuthorizationDecision.deny(
                    PERMISSION,
                    reasonCode,
                    RESOURCE_TYPE,
                    resourceId(projectId),
                    null,
                    Map.of("scope", "PROJECT_SPACE"))));
  }

  /** Drops an ALLOW decision if no business AuditOperation claimed it during this request. */
  public void endRequest() {
    BusinessAuditService auditService = auditService();
    if (auditService == null) return;
    safe(auditService::clearAuthorizationDecisions);
  }

  private BusinessAuditService auditService() {
    try {
      return auditServiceProvider.getIfAvailable();
    } catch (RuntimeException exception) {
      log.warn("Unable to resolve audit runtime; Project access semantics are unchanged", exception);
      return null;
    }
  }

  private String resourceId(Long projectId) {
    return projectId == null || projectId <= 0L ? null : String.valueOf(projectId);
  }

  private void safe(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException exception) {
      log.warn("Project authorization audit failed; Project access semantics are unchanged", exception);
    }
  }
}
