package io.yak.ops.business.development.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DevelopmentResourceNameTest {

  @Test
  void nodeNameOwnsNormalizationAndValidation() {
    assertEquals("任务", DevelopmentNodeName.of("  任务  ").value());
    assertThrows(IllegalArgumentException.class, () -> DevelopmentNodeName.of("   "));
    assertThrows(IllegalArgumentException.class, () -> DevelopmentNodeName.of("目录/任务"));
    assertThrows(IllegalArgumentException.class, () -> DevelopmentNodeName.of("目录\\任务"));
    assertThrows(
        IllegalArgumentException.class,
        () -> DevelopmentNodeName.of("a".repeat(DevelopmentNodeName.MAX_LENGTH + 1)));
  }

  @Test
  void directoryNameOwnsPathSegmentRules() {
    assertEquals("ODS", DevelopmentDirectoryName.of(" ODS ").value());
    assertThrows(IllegalArgumentException.class, () -> DevelopmentDirectoryName.of("."));
    assertThrows(IllegalArgumentException.class, () -> DevelopmentDirectoryName.of(".."));
    assertThrows(IllegalArgumentException.class, () -> DevelopmentDirectoryName.of("ODS/DWD"));
    assertThrows(IllegalArgumentException.class, () -> DevelopmentDirectoryName.of("ODS\\DWD"));
    assertThrows(
        IllegalArgumentException.class,
        () -> DevelopmentDirectoryName.of("a".repeat(DevelopmentDirectoryName.MAX_LENGTH + 1)));
  }

  @Test
  void nodeTypeOwnsCommandSideParsing() {
    assertEquals(DevelopmentNodeType.SQL, DevelopmentNodeType.require(" sql "));
    assertThrows(IllegalArgumentException.class, () -> DevelopmentNodeType.require(" "));
    assertThrows(IllegalArgumentException.class, () -> DevelopmentNodeType.require("UNKNOWN"));
  }
}
