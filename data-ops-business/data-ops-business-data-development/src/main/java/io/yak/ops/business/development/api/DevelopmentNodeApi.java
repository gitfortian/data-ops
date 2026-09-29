package io.yak.ops.business.development.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HTTP request contracts for data-development tree nodes. */
public final class DevelopmentNodeApi {

  private DevelopmentNodeApi() {}

  /** Project ownership is resolved exclusively from the trusted CurrentProject request context. */
  public record CreateRequest(
      @NotBlank String name,
      @NotBlank String type,
      @Min(0) Long directoryId) {
  }

  public record RenameRequest(@NotBlank @Size(max = 200) String name) {
  }

  /** directoryId is null or 0 to move to the root level. */
  public record MoveRequest(Long directoryId) {
  }
}
