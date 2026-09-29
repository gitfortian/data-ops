package io.yak.ops.business.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 脱敏器契约（票 03）：黑名单替换、嵌套/数组、截断、非 JSON 摘要、参数名白名单输出。 */
class AuditPayloadRedactorTest {

  private final AuditPayloadRedactor redactor =
      new AuditPayloadRedactor(new ObjectMapper(), List.of("mfa_backup_code"));

  private String redact(String json) {
    return redactor.redactJsonBody(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void redactsTopLevelAndNestedSensitiveFields() {
    String out =
        redact(
            "{\"name\":\"orders\",\"password\":\"p1\",\"conf\":{\"jdbcPassword\":\"x\","
                + "\"url\":\"jdbc:mysql://h/db\"},\"tags\":[{\"token\":\"t1\"},\"plain\"]}");
    assertTrue(out.contains("\"password\":\"***\""));
    assertTrue(out.contains("\"jdbcPassword\":\"***\""));
    assertTrue(out.contains("\"token\":\"***\""));
    assertTrue(out.contains("\"url\":\"jdbc:mysql://h/db\""));
    assertTrue(out.contains("\"name\":\"orders\""));
    assertFalse(out.contains("p1"));
    assertFalse(out.contains("\"t1\""));
  }

  @Test
  void configuredExtraTokensAreHonoured() {
    assertTrue(redact("{\"mfa_backup_code\":\"12345\"}").contains("\"***\""));
  }

  @Test
  void longPayloadIsTruncated() {
    String big = "{\"filler\":\"" + "a".repeat(20_000) + "\"}";
    String out = redact(big);
    assertTrue(out.length() < 9_000);
    assertTrue(out.endsWith("...<truncated>"));
  }

  @Test
  void unparsableBodyLeavesOnlySizeMarker() {
    String out = redactor.redactJsonBody("not-json-at-all".getBytes(StandardCharsets.UTF_8));
    assertEquals("<non-json-body bytes=15>", out);
  }

  @Test
  void emptyBodyYieldsNothing() {
    assertNull(redactor.redactJsonBody(new byte[0]));
    assertNull(redactor.redactJsonBody(null));
    assertNull(redact("   "));
  }

  @Test
  void parameterNamesKeepKeysWithoutValuesAndRedactSensitiveKeys() {
    List<String> names =
        redactor.parameterNames(
            Map.of(
                "pageNum", new String[] {"1"},
                "accessToken", new String[] {"secret-value"}));
    assertTrue(names.contains("pageNum"));
    assertTrue(names.contains("***"));
    assertFalse(names.stream().anyMatch(v -> v.contains("secret-value")));
  }
}
