package io.yak.ops.business.resource.namespace;

/** Immutable command inputs for resource namespace mutations. */
public final class ResourceNamespaceCommand {

  private ResourceNamespaceCommand() {
  }

  public record CreateDirectory(Long parentId, String name, String description) {
  }

  public record Update(String name, String description) {
  }

  public record Move(Long targetParentId) {
  }
}
