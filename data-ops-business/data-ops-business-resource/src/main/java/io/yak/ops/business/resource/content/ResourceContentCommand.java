package io.yak.ops.business.resource.content;

/** Immutable command inputs for text resource mutations. */
public final class ResourceContentCommand {

  private ResourceContentCommand() {
  }

  public record Create(
      Long parentId,
      String name,
      String description,
      String contentType,
      String content) {
  }

  public record Update(String content) {
  }
}
