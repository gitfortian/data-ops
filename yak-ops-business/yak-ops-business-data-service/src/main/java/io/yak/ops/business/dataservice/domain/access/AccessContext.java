package io.yak.ops.business.dataservice.domain.access;

/** Caller identity recorded for one Data Service invocation. */
public record AccessContext(
    String callerType,
    Long apiKeyId,
    Long consumerId,
    String apiKeyName,
    String apiKeyPrefix) {

  /** Compatibility constructor for callers that do not own a managed consumer identity. */
  public AccessContext(String callerType, Long apiKeyId, String apiKeyName, String apiKeyPrefix) {
    this(callerType, apiKeyId, null, apiKeyName, apiKeyPrefix);
  }

  public static AccessContext publicAccess() {
    return new AccessContext("PUBLIC", null, null, null, null);
  }

  public static AccessContext console() {
    return new AccessContext("CONSOLE", null, null, null, null);
  }

  public static AccessContext rejectedApiKey() {
    return new AccessContext("API_KEY", null, null, null, null);
  }
}
