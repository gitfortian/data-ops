package io.yak.ops.business.metric.publication;

import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import org.springframework.stereotype.Component;

/** Applies the same Metric READ and project-access requirements used by the public controller. */
@Component
public class CurrentUserMetricPublicationReadAccess implements MetricPublicationReadAccess {

  @Override
  public boolean canRead(Long projectId) {
    return projectId != null
        && YakSecurityContext.isAuthenticated()
        && YakSecurityContext.hasPermission(MetricPermissionCode.READ)
        && YakSecurityContext.canAccessProject(projectId);
  }
}
