package io.yak.ops.core.plugin.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginDescriptor;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskPluginRegistryTest {

  @Test
  void routesPluginsByNormalizedType() {
    TaskPluginRegistry registry = TaskPluginRegistry.from(List.of(new StubPlugin("sql")));

    assertTrue(registry.find(" SQL ").isPresent());
    assertEquals("SQL", registry.require("sql").type());
    assertEquals(List.of("SQL"), registry.descriptors().stream().map(TaskPluginDescriptor::type).toList());
  }

  @Test
  void rejectsDuplicateTypesIgnoringCase() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> TaskPluginRegistry.from(List.of(new StubPlugin("sql"), new StubPlugin("SQL"))));

    assertTrue(exception.getMessage().contains("Duplicate task plugin for type SQL"));
  }

  @Test
  void rejectsMissingPlugin() {
    TaskPluginRegistry registry = TaskPluginRegistry.from(List.of(new StubPlugin("SQL")));

    assertThrows(IllegalArgumentException.class, () -> registry.require("PYTHON"));
  }

  private static final class StubPlugin implements TaskPlugin {

    private final TaskPluginDescriptor descriptor;

    private StubPlugin(String type) {
      descriptor =
          new TaskPluginDescriptor(type, type, "test", "1.0.0", 1, false, false);
    }

    @Override
    public TaskPluginDescriptor descriptor() {
      return descriptor;
    }

    @Override
    public TaskValidationResult validate(TaskDefinition definition) {
      return TaskValidationResult.ok();
    }
  }
}
