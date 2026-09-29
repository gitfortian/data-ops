package io.yak.ops.business.agent.domain;

/**
 * 步骤载荷读模型（trace v2）：来自 yak_agent_step.request_json/response_json 的
 * 信封解码结果（编码侧为 telemetry.PayloadEnvelope，读模型在此保持 neutral 化，
 * domain 不依赖 telemetry）。mode 语义与编码侧四档一致。
 */
public record StepPayload(
    String mode,
    String content,
    Boolean truncated,
    Long size,
    String sha256,
    Integer messageCount,
    String preview) {

  public static final String MODE_INLINE = "INLINE";
  public static final String MODE_SUMMARY_HASH = "SUMMARY_HASH";
  public static final String MODE_HASH_ONLY = "HASH_ONLY";
  public static final String MODE_OMITTED = "OMITTED";
}
