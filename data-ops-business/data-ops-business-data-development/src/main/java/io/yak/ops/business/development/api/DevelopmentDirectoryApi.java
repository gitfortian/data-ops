package io.yak.ops.business.development.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HTTP request contracts for data-development directories. */
public final class DevelopmentDirectoryApi {

  private DevelopmentDirectoryApi() {}

  public record CreateRequest(
      @Min(1) Long parentId,
      @NotBlank @Size(max = 128) String name) {
  }

  public record RenameRequest(@NotBlank @Size(max = 128) String name) {
  }

  /** parentId is null or 0 to move to the root level. */
  public record MoveRequest(Long parentId) {
  }
}
