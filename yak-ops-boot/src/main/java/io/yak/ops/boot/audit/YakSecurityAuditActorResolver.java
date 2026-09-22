package io.yak.ops.boot.audit;

import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.ops.business.audit.AuditActor;
import io.yak.ops.business.audit.AuditActorResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Adapts Yak Security's stable login identity into the shared Audit actor contract. */
@Component
@Primary
@RequiredArgsConstructor
public class YakSecurityAuditActorResolver implements AuditActorResolver {

  private static final Logger log = LoggerFactory.getLogger(YakSecurityAuditActorResolver.class);

  private final ObjectProvider<AuthenticationManager> authenticationManagerProvider;

  @Override
  public AuditActor currentActor() {
    AuthenticationManager authenticationManager = authenticationManagerProvider.getIfAvailable();
    if (authenticationManager == null) {
      return AuditActor.system();
    }
    try {
      if (!authenticationManager.isLogin()) {
        return AuditActor.system();
      }
      Long userId = authenticationManager.getLoginUserId();
      String userName = authenticationManager.getLoginUsername();
      return new AuditActor(userId == null ? null : String.valueOf(userId), userName, "USER");
    } catch (RuntimeException exception) {
      log.warn("Unable to resolve Yak Security audit actor; using system actor", exception);
      return AuditActor.system();
    }
  }
}
