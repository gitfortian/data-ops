package io.yak.ops.business.development.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DevelopmentNodeModelTest {

  @Test
  void classifiesProcessingAndOutputResponsibilities() {
    assertEquals(DevelopmentNodeCategory.PROCESSING, DevelopmentNodeType.SQL.category());
    assertEquals(DevelopmentNodeCategory.PROCESSING, DevelopmentNodeType.SHELL.category());
    assertEquals(DevelopmentNodeCategory.PROCESSING, DevelopmentNodeType.HTTP.category());
    assertEquals(DevelopmentNodeCategory.PROCESSING, DevelopmentNodeType.PYTHON.category());
    assertEquals(DevelopmentNodeCategory.OUTPUT, DevelopmentNodeType.DATASET.category());
    assertEquals(DevelopmentNodeCategory.OUTPUT, DevelopmentNodeType.DATA_SERVICE.category());
  }

  @Test
  void taskLifecycleIsAnExplicitCapability() {
    assertTrue(DevelopmentNodeType.SQL.supportsTaskLifecycle());
    assertTrue(DevelopmentNodeType.SHELL.supportsTaskLifecycle());
    assertTrue(DevelopmentNodeType.HTTP.supportsTaskLifecycle());
    assertTrue(DevelopmentNodeType.PYTHON.supportsTaskLifecycle());
    assertFalse(DevelopmentNodeType.DATASET.supportsTaskLifecycle());
    assertFalse(DevelopmentNodeType.DATA_SERVICE.supportsTaskLifecycle());
  }

  @Test
  void keepsOutputNodesOutsideProcessingLifecycle() {
    assertTrue(DevelopmentNodeType.SQL.isProcessing());
    assertTrue(DevelopmentNodeType.SHELL.isProcessing());
    assertFalse(DevelopmentNodeType.DATASET.isProcessing());
    assertFalse(DevelopmentNodeType.DATA_SERVICE.isProcessing());
    assertTrue(DevelopmentNodeType.DATASET.isOutput());
    assertTrue(DevelopmentNodeType.DATA_SERVICE.isOutput());
  }

  @Test
  void nodeOwnsTaskLifecycleCapabilityGate() {
    Instant now = Instant.parse("2026-08-24T00:00:00Z");
    DevelopmentNode sql = new DevelopmentNode(1L, "SQL", "sql", null, null, true, now, now);
    DevelopmentNode dataset =
        new DevelopmentNode(2L, "Dataset", "DATASET", null, null, true, now, now);

    assertEquals(DevelopmentNodeType.SQL, sql.nodeType());
    assertTrue(sql.supportsTaskLifecycle());
    sql.requireTaskLifecycle();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, dataset::requireTaskLifecycle);
    assertTrue(exception.getMessage().contains("不是可执行开发任务"));
  }
}
