package io.yak.ops.business.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** 脱敏引擎纯函数单测:各算法行为 + 参数解析。 */
class MaskingEngineTest {

  @Test
  void supportedCoversAllAlgorithms() {
    assertEquals(
        Set.of("MASK_PARTIAL", "HASH", "FULL_MASK", "NULLIFY", "REPLACE", "KEEP_FORMAT"),
        MaskingEngine.supported());
  }

  @Test
  void nullValueStaysNull() {
    assertNull(MaskingEngine.mask(null, "FULL_MASK", null));
  }

  @Test
  void blankAlgoKeepsOriginal() {
    assertEquals("13800000000", MaskingEngine.mask("13800000000", "  ", null));
    assertEquals("abc", MaskingEngine.mask("abc", "UNKNOWN_ALGO", null));
  }

  @Test
  void fullMaskCapsAtSixAndHonoursMaskChar() {
    assertEquals("******", MaskingEngine.mask("abcdefghijkl", "FULL_MASK", null));
    assertEquals("###", MaskingEngine.mask("xyz", "FULL_MASK", "{\"maskChar\":\"#\"}"));
  }

  @Test
  void nullifyReturnsEmpty() {
    assertEquals("", MaskingEngine.mask("secret", "NULLIFY", null));
  }

  @Test
  void keepFormatPreservesNonAlnum() {
    assertEquals("****-****-****", MaskingEngine.mask("1234-5678-9012", "KEEP_FORMAT", null));
  }

  @Test
  void replaceFillsFullLength() {
    assertEquals("xxxxx", MaskingEngine.mask("hello", "REPLACE", "{\"replacement\":\"x\"}"));
  }

  @Test
  void maskPartialKeepsEdges() {
    assertEquals("1*********0", MaskingEngine.mask("13800000000", "MASK_PARTIAL",
        "{\"keepLeft\":1,\"keepRight\":1}"));
    // keepLeft + keepRight >= length -> fully masked
    assertEquals("****", MaskingEngine.mask("abcd", "MASK_PARTIAL", "{\"keepLeft\":2,\"keepRight\":2}"));
  }

  @Test
  void hashIsDeterministicAndTruncated() {
    String once = MaskingEngine.mask("customer-1", "HASH", "{\"length\":8}");
    assertEquals(8, once.length());
    assertEquals(once, MaskingEngine.mask("customer-1", "HASH", "{\"length\":8}"));
    assertFalse(once.equals(MaskingEngine.mask("customer-2", "HASH", "{\"length\":8}")));
  }

  @Test
  void malformedParamsFallBackToDefaults() {
    assertEquals("******", MaskingEngine.mask("abcdefghijkl", "FULL_MASK", "not-json"));
    assertTrue(MaskingEngine.supported().contains("HASH"));
  }
}
