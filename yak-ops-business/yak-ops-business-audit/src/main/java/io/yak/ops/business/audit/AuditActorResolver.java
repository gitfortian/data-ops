package io.yak.ops.business.audit;

/** Resolves the authenticated actor without coupling Audit to one authentication implementation. */
@FunctionalInterface
public interface AuditActorResolver {
  AuditActor currentActor();
}
