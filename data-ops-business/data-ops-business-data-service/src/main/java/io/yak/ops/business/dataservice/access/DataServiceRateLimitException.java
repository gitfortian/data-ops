package io.yak.ops.business.dataservice.access;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class DataServiceRateLimitException extends RuntimeException {
  private final Long apiKeyId;
  private final String apiKeyName;
  private final String apiKeyPrefix;

  public DataServiceRateLimitException(String message, Long apiKeyId, String apiKeyName, String apiKeyPrefix) {
    super(message); this.apiKeyId = apiKeyId; this.apiKeyName = apiKeyName; this.apiKeyPrefix = apiKeyPrefix;
  }
  public Long apiKeyId() { return apiKeyId; }
  public String apiKeyName() { return apiKeyName; }
  public String apiKeyPrefix() { return apiKeyPrefix; }
}
