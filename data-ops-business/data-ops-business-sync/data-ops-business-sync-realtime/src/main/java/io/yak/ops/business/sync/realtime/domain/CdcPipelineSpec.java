package io.yak.ops.business.sync.realtime.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * Stable, password-free control-plane model.
 *
 * <p>Connection coordinates and credentials belong to datasource definitions and are deliberately
 * resolved only for validation/deployment. They must never be copied into this model.
 */
public record CdcPipelineSpec(
    @NotNull Long sourceDataSourceRef,
    @NotNull Long sinkDataSourceRef,
    @NotEmpty List<@Valid TableRoute> tables,
    @Pattern(regexp = "initial|latest-offset") String startupMode,
    @NotNull SchemaEvolution schemaEvolution,
    @Min(1) @Max(256) int parallelism,
    @Min(10_000) long checkpointIntervalMs,
    @Valid @NotNull RestartPolicy restart,
    @Valid @NotNull SinkTuning sink) {

  public CdcPipelineSpec {
    tables = tables == null ? List.of() : List.copyOf(tables);
  }

  public enum SchemaEvolution {
    EVOLVE,
    IGNORE,
    FAIL
  }

  public enum MatchMode {
    EXACT,
    REGEX
  }

  public record TableRoute(
      @NotBlank String sourceTable,
      @NotBlank String sinkTable,
      @NotNull MatchMode matchMode,
      @NotEmpty List<@NotBlank String> keyColumns) {

    public TableRoute {
      keyColumns = keyColumns == null ? List.of() : List.copyOf(keyColumns);
    }
  }

  public record RestartPolicy(
      @Pattern(regexp = "fixed-delay|failure-rate|none") String strategy,
      @Min(0) int attempts,
      @Min(0) long delayMs) {}

  public record SinkTuning(
      @Min(0) int maxRetries,
      @Min(1) int batchSize,
      @Min(1) long flushIntervalMs,
      @Min(1) long maxBatchBytes,
      @Min(1) int statementCacheSize,
      boolean strictReplaySafety) {}
}
