package io.yak.ops.business.audit;

/** Business-facing presentation derived from a stable audit event. */
public record AuditEventPresentation(String title, String description) {}
