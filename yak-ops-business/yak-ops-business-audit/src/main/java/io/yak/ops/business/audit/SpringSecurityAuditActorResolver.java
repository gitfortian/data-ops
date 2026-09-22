package io.yak.ops.business.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Fallback actor resolver for deployments without a richer security identity adapter. */
final class SpringSecurityAuditActorResolver implements AuditActorResolver {

  @Override
  public AuditActor currentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return AuditActor.system();
    }
    String name = authentication.getName();
    if (name == null || name.isBlank() || "anonymousUser".equalsIgnoreCase(name)) {
      return AuditActor.system();
    }
    return new AuditActor(null, name, "USER");
  }
}
