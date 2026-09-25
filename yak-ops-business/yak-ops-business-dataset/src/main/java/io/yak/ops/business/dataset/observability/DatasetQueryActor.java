package io.yak.ops.business.dataset.observability;

/** Stable actor identity attached to Dataset query owning evidence. */
public record DatasetQueryActor(String actorType, String actorId) {

  public DatasetQueryActor {
    actorType = requireText(actorType, "actorType");
    actorId = requireText(actorId, "actorId");
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
