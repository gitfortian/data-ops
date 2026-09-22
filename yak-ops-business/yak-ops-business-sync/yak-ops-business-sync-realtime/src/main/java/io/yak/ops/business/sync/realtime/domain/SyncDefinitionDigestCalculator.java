package io.yak.ops.business.sync.realtime.domain;

import io.yak.ops.business.sync.realtime.domain.SyncDefinition.ExactTableSelector;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.FixedDelayRestart;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.NoRestart;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SyncRoute;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.TablePatternSelector;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.TableTarget;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Collectors;

/** Computes the canonical semantic digest used by immutable DefinitionVersion records. */
public final class SyncDefinitionDigestCalculator {

  private SyncDefinitionDigestCalculator() {}

  public static DefinitionDigest calculate(
      SyncDefinition definition, RuntimeEnvironmentRef runtimeEnvironment) {
    if (definition == null || runtimeEnvironment == null) {
      throw new IllegalArgumentException("Definition 与 RuntimeEnvironmentRef 不能为空");
    }

    String canonical = canonical(definition, runtimeEnvironment);
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(canonical.getBytes(StandardCharsets.UTF_8));
      return new DefinitionDigest(HexFormat.of().formatHex(digest));
    } catch (Exception exception) {
      throw new IllegalStateException("无法计算 DefinitionDigest", exception);
    }
  }

  static String canonical(
      SyncDefinition definition, RuntimeEnvironmentRef runtimeEnvironment) {
    String routes =
        definition.routes().stream()
            .map(SyncDefinitionDigestCalculator::routeKey)
            .sorted()
            .collect(Collectors.joining("\n"));

    String restart =
        switch (definition.executionPolicy().restartPolicy()) {
          case NoRestart ignored -> "none";
          case FixedDelayRestart fixed ->
              "fixed-delay:"
                  + fixed.maxAttempts()
                  + ":"
                  + fixed.delayMs();
        };

    return String.join(
        "\n",
        "source=" + definition.source().dataSourceRef(),
        "sink=" + definition.sink().dataSourceRef(),
        "startup=" + definition.syncPolicy().startupPolicy(),
        "schema=" + definition.syncPolicy().schemaEvolutionPolicy(),
        "parallelism=" + definition.executionPolicy().parallelism(),
        "checkpoint=" + definition.executionPolicy().checkpointPolicy().intervalMs(),
        "restart=" + restart,
        "sink.maxRetries=" + definition.executionPolicy().sinkWritePolicy().maxRetries(),
        "sink.batchSize=" + definition.executionPolicy().sinkWritePolicy().batchSize(),
        "sink.flushIntervalMs=" + definition.executionPolicy().sinkWritePolicy().flushIntervalMs(),
        "sink.maxBatchBytes=" + definition.executionPolicy().sinkWritePolicy().maxBatchBytes(),
        "runtimeEnvironment=" + runtimeEnvironment.id(),
        "routes:",
        routes);
  }

  private static String routeKey(SyncRoute route) {
    String selector =
        switch (route.source()) {
          case ExactTableSelector exact -> "exact:" + encoded(exact.table());
          case TablePatternSelector pattern -> "pattern:" + encoded(pattern.pattern());
        };
    String target =
        switch (route.target()) {
          case TableTarget table -> "table:" + encoded(table.table());
        };
    String replayKey =
        route.replayKey().fields().stream()
            .sorted()
            .map(SyncDefinitionDigestCalculator::encoded)
            .collect(Collectors.joining("|"));
    return selector + "->" + target + "#" + replayKey;
  }

  private static String encoded(String value) {
    return value.length() + ":" + value;
  }
}
