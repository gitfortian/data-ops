package io.yak.ops.business.metric.publication;

import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import org.springframework.stereotype.Component;

/**
 * Applies the Metric READ check for internal evidence reads.
 * Project membership is already enforced before CurrentProject is bound by ProjectScopeInterceptor;
 * this guard must not substitute a second, potentially different project-membership interpretation.
 */
@Component
public class CurrentUserMetricPublicationReadAccess implements MetricPublicationReadAccess {

  @Override
  public boolean canRead(Long projectId) {
    return projectId != null
        && YakSecurityContext.isAuthenticated()
        && YakSecurityContext.hasPermission(MetricPermissionCode.READ);
  }
}
