package io.yak.ops.business.agent.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 载荷策略引擎可证伪验收：JSON 感知截断保可解析、指纹档不落原文、敏感键整段省略。 */
class PayloadPolicyTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void inlineShortTextIsVerbatimAndMarkedUntruncated() {
    PayloadEnvelope envelope = PayloadPolicy.inline("{\"a\":1}");
    assertEquals(PayloadEnvelope.MODE_INLINE, envelope.mode());
    assertFalse(envelope.truncated());
    assertEquals("{\"a\":1}", envelope.content());
    assertNotNull(envelope.sha256());
  }

  @Test
  void oversizeJsonObjectStaysParseableAfterTruncation() throws Exception {
    // 真实工具输出的典型形态：少数超长字符串值（行数据/序列化大对象）
    StringBuilder big = new StringBuilder("{\"rows\":[");
    for (int i = 0; i < 4; i++) {
      if (i > 0) {
        big.append(',');
      }
      big.append("\"记录-").append(i).append('-').append("x".repeat(9000)).append("\"");
    }
    big.append("]}");
    String text = big.toString();

    PayloadEnvelope envelope = PayloadPolicy.inline(text, 2000);
    assertEquals(PayloadEnvelope.MODE_INLINE, envelope.mode());
    assertTrue(envelope.truncated(), "超限必须标记截断");
    assertEquals(text.length(), envelope.size().longValue(), "size 为原始长度");
    // JSON 感知截断的核心承诺：落库内容仍可被 JSON 解析
    JsonNode parsed = JSON.readTree(envelope.content());
    assertTrue(parsed.has("rows"), "结构键保留");
    assertEquals(4, parsed.get("rows").size(), "数组元素数保留");
    assertTrue(envelope.content().length() <= 2000 + 64, "收缩后接近预算上限");
  }

  @Test
  void oversizeNonJsonFallsBackToHeadTailTruncation() {
    String text = "y".repeat(20000);
    PayloadEnvelope envelope = PayloadPolicy.inline(text, 1000);
    assertTrue(envelope.truncated());
    assertTrue(envelope.content().contains("...[truncated]..."));
  }

  @Test
  void summaryHashNeverCarriesRawContent() {
    PayloadEnvelope envelope = PayloadPolicy.summaryHash("机密原文".repeat(100), 7, "末条预览", 20);
    assertEquals(PayloadEnvelope.MODE_SUMMARY_HASH, envelope.mode());
    assertNull(envelope.content(), "SUMMARY_HASH 档不得携带原文");
    assertEquals(7, envelope.messageCount());
    assertEquals("末条预览", envelope.preview());
    assertNotNull(envelope.sha256());
  }

  @Test
  void forbiddenRawKeyOmitsWholePayload() {
    PayloadEnvelope envelope = PayloadPolicy.inline("{\"raw_prompt\":\"不应落库的原文\"}");
    assertEquals(PayloadEnvelope.MODE_OMITTED, envelope.mode());
    assertNull(envelope.content());
  }

  @Test
  void nullPayloadStaysNull() {
    assertNull(PayloadPolicy.inline(null));
    assertNull(PayloadPolicy.summaryHash(null, 0, null, 0));
    assertNull(PayloadPolicy.hashOnly(null));
  }

  @Test
  void encodeIsStableJson() throws Exception {
    String encoded = PayloadPolicy.inline("ok").encode();
    JsonNode parsed = JSON.readTree(encoded);
    assertEquals(PayloadEnvelope.MODE_INLINE, parsed.get("mode").asText());
  }
}
