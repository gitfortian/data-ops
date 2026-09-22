package io.yak.ops.business.development.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** HTTP request contracts for data-development task authoring and manual execution. */
public final class DevelopmentTaskApi {

  private DevelopmentTaskApi() {
  }

  public record SaveDraftRequest(
      @NotBlank String taskType,
      @Min(1) int schemaVersion,
      String content,
      String configJson,
      @PositiveOrZero Long baseRevision) {
  }

  public record PublishRequest(
      @NotNull @Positive Long draftRevision) {
  }

  /** Runs the current editor definition without implicitly saving or publishing it. */
  public record RunRequest(
      @NotBlank String taskType,
      @Min(1) int schemaVersion,
      String content,
      String configJson) {
  }

  /** Parses the current SQL editor definition without saving, publishing or persisting lineage. */
  public record LineagePreviewRequest(
      @NotBlank String taskType,
      @Min(1) int schemaVersion,
      String content,
      String configJson,
      @Size(max = 256) String databaseName,
      @Size(max = 256) String schemaName) {
  }

  /** Checks the current SQL editor output fields against naming standards without saving. */
  public record StandardCheckRequest(
      @NotBlank String taskType,
      @Min(1) int schemaVersion,
      String content) {
  }
}
