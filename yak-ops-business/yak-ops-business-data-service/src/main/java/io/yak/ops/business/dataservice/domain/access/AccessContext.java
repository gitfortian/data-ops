package io.yak.ops.business.dataservice.domain.access;

/** Caller identity recorded for one Data Service invocation. */
public record AccessContext(
    String callerType,
    Long apiKeyId,
    String apiKeyName,
    String apiKeyPrefix) {

  public static AccessContext publicAccess() {
    return new AccessContext("PUBLIC", null, null, null);
  }

  public static AccessContext console() {
    return new AccessContext("CONSOLE", null, null, null);
  }

  public static AccessContext rejectedApiKey() {
    return new AccessContext("API_KEY", null, null, null);
  }
}
