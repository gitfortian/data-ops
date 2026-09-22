package io.yak.ops.business.sync.realtime.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic identity used to recover a Flink job after an uncertain CLI submission. */
public final class RealtimeRuntimeIdentity {

  private static final Pattern PIPELINE_NAME =
      Pattern.compile("(?m)(^pipeline:\\s*\\R[ \\t]+name:\\s*)[^\\r\\n]+");

  private RealtimeRuntimeIdentity() {}

  public static String jobName(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key 不能为空");
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
      return "yak-rt-" + HexFormat.of().formatHex(digest, 0, 16);
    } catch (Exception exception) {
      throw new IllegalStateException("无法生成 Flink runtime job identity", exception);
    }
  }

  public static String decoratePipeline(String pipelineYaml, String idempotencyKey) {
    if (pipelineYaml == null || pipelineYaml.isBlank()) {
      throw new IllegalArgumentException("Pipeline YAML 不能为空");
    }
    Matcher matcher = PIPELINE_NAME.matcher(pipelineYaml);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Pipeline YAML 缺少 pipeline.name，无法生成可恢复任务标识");
    }
    String runtimeName = jobName(idempotencyKey);
    return pipelineYaml.substring(0, matcher.start())
        + matcher.group(1)
        + runtimeName
        + pipelineYaml.substring(matcher.end());
  }
}
