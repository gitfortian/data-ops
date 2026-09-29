package io.yak.ops.business.dataservice.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DataServiceAuditSanitizerTest {

  private final DataServiceAuditSanitizer sanitizer = new DataServiceAuditSanitizer();

  @Test
  void masksSecretsAndCommonPersonalIdentifiersBeforePersistence() {
    Map<String, String> result = sanitizer.sanitize(Map.of(
        "password", "s3cr3t",
        "access_token", "token-value",
        "mobile", "13812345678",
        "id_card", "510123199001011234",
        "email", "someone@example.com",
        "status", "ACTIVE"));

    assertThat(result.get("password")).isEqualTo(DataServiceAuditSanitizer.REDACTED);
    assertThat(result.get("access_token")).isEqualTo(DataServiceAuditSanitizer.REDACTED);
    assertThat(result.get("mobile")).isEqualTo("138****5678");
    assertThat(result.get("id_card")).isEqualTo("510123********1234");
    assertThat(result.get("email")).isEqualTo("s***@example.com");
    assertThat(result.get("status")).isEqualTo("ACTIVE");
  }

  @Test
  void shortSensitiveValuesAreNeverPartiallyLeaked() {
    assertThat(sanitizer.sanitizeValue("phone", "12345"))
        .isEqualTo(DataServiceAuditSanitizer.REDACTED);
    assertThat(sanitizer.sanitizeValue("identity", "123456"))
        .isEqualTo(DataServiceAuditSanitizer.REDACTED);
    assertThat(sanitizer.sanitizeValue("authorization", "Bearer abc"))
        .isEqualTo(DataServiceAuditSanitizer.REDACTED);
  }
}
