package io.yak.ops.plugin.task.all;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.spi.task.model.TaskDefinition;
import org.junit.jupiter.api.Test;

class TaskPluginAssemblyTest {

  @Test
  void discoversSqlPluginThroughServiceLoader() {
    TaskPluginRegistry registry =
        TaskPluginRegistry.load(Thread.currentThread().getContextClassLoader());

    TaskPlugin sql = registry.require("SQL");

    assertEquals("SQL", sql.descriptor().type());
    assertTrue(sql.descriptor().executable());
    assertTrue(sql.descriptor().cancellable());
    assertTrue(
        sql.validate(
                new TaskDefinition(
                    "SQL",
                    1,
                    "select 1",
                    "{\"dataSourceId\":\"1\"}"))
            .valid());
  }

  @Test
  void discoversPythonPluginThroughServiceLoader() {
    TaskPluginRegistry registry =
        TaskPluginRegistry.load(Thread.currentThread().getContextClassLoader());

    TaskPlugin python = registry.require("PYTHON");

    assertEquals("PYTHON", python.descriptor().type());
    assertTrue(python.descriptor().executable());
    assertTrue(python.descriptor().cancellable());
    assertTrue(
        python.validate(
                new TaskDefinition(
                    "PYTHON",
                    1,
                    "print('hello')",
                    "{}"))
            .valid());
  }
}
