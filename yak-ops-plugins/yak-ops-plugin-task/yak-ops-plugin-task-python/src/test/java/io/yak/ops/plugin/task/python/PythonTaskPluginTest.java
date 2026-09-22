package io.yak.ops.plugin.task.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PythonTaskPluginTest {

  @Test
  void resolvesDefaultExecutableFromPythonHome() {
    String executable = PythonTaskConfig.defaultPythonExecutable();
    if (System.getenv(PythonTaskConfig.PYTHON_HOME_ENV) != null) {
      String pythonHome = System.getenv(PythonTaskConfig.PYTHON_HOME_ENV);
      if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
        assertEquals(
            java.nio.file.Path.of(pythonHome, "python.exe").toString(),
            executable);
      } else {
        assertEquals(
            java.nio.file.Path.of(pythonHome, "bin", "python").toString(),
            executable);
      }
    } else {
      assertEquals("python", executable);
    }
  }

  @Test
  void parsesConfigWithExplicitExecutable() {
    PythonTaskConfig config =
        PythonTaskConfig.parse(
            "{\"pythonExecutable\":\"/opt/python3.11/bin/python\",\"timeoutSeconds\":120}");
    assertEquals("/opt/python3.11/bin/python", config.pythonExecutable());
    assertEquals(120, config.timeoutSeconds());
  }

  @Test
  void validatesTypeAndContent() {
    PythonTaskPlugin plugin = new PythonTaskPlugin();

    assertTrue(
        plugin
            .validate(
                new TaskDefinition(
                    "PYTHON", 1, "print('hello')", "{}"))
            .valid());

    assertFalse(
        plugin.validate(new TaskDefinition("SQL", 1, "select 1", "{}")).valid());
    assertFalse(
        plugin.validate(new TaskDefinition("PYTHON", 1, "", "{}")).valid());
    assertFalse(
        plugin.validate(new TaskDefinition("PYTHON", 2, "print('hello')", "{}")).valid());
  }

  @Test
  void rejectsInvalidConfigJson() {
    PythonTaskPlugin plugin = new PythonTaskPlugin();
    TaskDefinition definition = new TaskDefinition("PYTHON", 1, "print('hello')", "not-json");

    assertFalse(plugin.validate(definition).valid());
    assertTrue(
        plugin.validate(definition).issues().stream()
            .anyMatch(issue -> "PYTHON_CONFIG_INVALID".equals(issue.code())));
  }

  @Test
  void executesPythonScriptAndReturnsSuccess() throws Exception {
    PythonTaskPlugin plugin = new PythonTaskPlugin();
    TaskDefinition definition =
        new TaskDefinition(
            "PYTHON",
            1,
            "import sys; print('out'); print('err', file=sys.stderr)",
            "{\"timeoutSeconds\":10}");

    TaskExecutionContext context =
        new TaskExecutionContext() {
          @Override
          public TaskExecutionTrigger trigger() {
            return TaskExecutionTrigger.MANUAL;
          }

          @Override
          public Map<String, Object> parameters() {
            return Map.of();
          }
        };

    assertTrue(plugin.descriptor().executable());
    assertTrue(plugin.descriptor().cancellable());

    TaskExecutor executor = plugin.createExecutor(definition, context);
    TaskExecutionResult result = executor.execute();

    assertEquals(TaskExecutionStatus.SUCCESS, result.status());
    assertEquals(0, result.output().get("exitCode"));
    assertEquals("out", result.output().get("stdout"));
  }

  @Test
  void capturesNonZeroExitCodeAsFailed() throws Exception {
    PythonTaskPlugin plugin = new PythonTaskPlugin();
    TaskDefinition definition =
        new TaskDefinition(
            "PYTHON",
            1,
            "import sys; sys.exit(1)",
            "{}");

    TaskExecutionContext context =
        new TaskExecutionContext() {
          @Override
          public TaskExecutionTrigger trigger() {
            return TaskExecutionTrigger.MANUAL;
          }

          @Override
          public Map<String, Object> parameters() {
            return Map.of();
          }
        };

    TaskExecutor executor = plugin.createExecutor(definition, context);
    TaskExecutionResult result = executor.execute();

    assertEquals(TaskExecutionStatus.FAILED, result.status());
    assertEquals(1, result.output().get("exitCode"));
  }
}
