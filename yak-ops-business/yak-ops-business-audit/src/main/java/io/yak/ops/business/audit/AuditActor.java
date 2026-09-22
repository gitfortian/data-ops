package io.yak.ops.business.audit;

/** Historical identity snapshot attached to a business audit operation. */
public record AuditActor(String id, String name, String type) {

  public AuditActor {
    id = normalize(id);
    name = normalize(name);
    type = normalize(type);
    if (type == null) {
      type = id == null && name == null ? "SYSTEM" : "USER";
    }
  }

  public static AuditActor system() {
    return new AuditActor(null, null, "SYSTEM");
  }

  private static String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
