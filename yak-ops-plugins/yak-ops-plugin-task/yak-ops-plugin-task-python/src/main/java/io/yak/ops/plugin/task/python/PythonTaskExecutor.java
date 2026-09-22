package io.yak.ops.plugin.task.python;

import static io.yak.ops.plugin.task.api.ScriptTaskSupport.readStream;
import static io.yak.ops.plugin.task.api.ScriptTaskSupport.resolveScriptContent;
import static io.yak.ops.plugin.task.api.ScriptTaskSupport.safeMessage;
import static io.yak.ops.plugin.task.api.ScriptTaskSupport.truncate;

import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.spi.resource.ResourceResolver;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One physical Python task execution attempt. */
final class PythonTaskExecutor implements TaskExecutor {

  private static final Logger log = LoggerFactory.getLogger(PythonTaskExecutor.class);
  private static final boolean IS_WINDOWS =
      System.getProperty("os.name", "").toLowerCase().contains("win");

  private final TaskDefinition definition;
  private final PythonTaskConfig config;
  private final Map<String, String> globalEnvVars;
  private final ResourceResolver resourceResolver;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicReference<Process> activeProcess = new AtomicReference<>();

  PythonTaskExecutor(
      TaskDefinition definition,
      PythonTaskConfig config,
      Map<String, String> globalEnvVars,
      ResourceResolver resourceResolver) {
    this.definition = definition;
    this.config = config;
    this.globalEnvVars = globalEnvVars != null ? globalEnvVars : Map.of();
    this.resourceResolver = resourceResolver;
  }

  @Override
  public TaskExecutionResult execute() throws Exception {
    if (cancelled.get()) {
      return cancelledResult("Python execution was cancelled before start");
    }

    Path scriptFile = null;
    try {
      String scriptContent = resolveScriptContent(
          definition, resourceResolver, config.resourceId(), config.resourceVersion());
      scriptFile = writeScriptToTempFile(scriptContent);
      Process process = startProcess(scriptFile);
      activeProcess.set(process);

      if (cancelled.get()) {
        process.destroyForcibly();
        return cancelledResult("Python execution was cancelled after process start");
      }

      boolean finished = process.waitFor(config.timeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        activeProcess.set(null);
        return new TaskExecutionResult(
            TaskExecutionStatus.TIMEOUT,
            "Python script timed out after " + config.timeoutSeconds() + " seconds",
            Map.of("pythonExecutable", config.pythonExecutable()));
      }

      String stdout = truncate(readStream(process.inputReader(StandardCharsets.UTF_8)));
      String stderr = truncate(readStream(process.errorReader(StandardCharsets.UTF_8)));
      int exitCode = process.exitValue();
      activeProcess.set(null);

      if (cancelled.get()) {
        return cancelledResult("Python execution was cancelled");
      }

      Map<String, Object> output = new LinkedHashMap<>();
      output.put("exitCode", exitCode);
      output.put("stdout", stdout);
      output.put("stderr", stderr);
      output.put("pythonExecutable", config.pythonExecutable());

      if (exitCode == 0) {
        return TaskExecutionResult.success(output);
      }
      String detailMessage = buildFailureMessage(exitCode, stderr, config.pythonExecutable());
      return new TaskExecutionResult(
          TaskExecutionStatus.FAILED,
          detailMessage,
          output);
    } catch (IOException exception) {
      if (cancelled.get()) {
        return cancelledResult(safeMessage(exception, "Python execution failed"));
      }
      String diagnostic = buildProcessStartDiagnostic(exception, config.pythonExecutable());
      Map<String, Object> output = new LinkedHashMap<>();
      output.put("pythonExecutable", config.pythonExecutable());
      return new TaskExecutionResult(
          TaskExecutionStatus.FAILED,
          diagnostic,
          output);
    } finally {
      if (scriptFile != null) {
        try { Files.deleteIfExists(scriptFile); } catch (IOException ignored) {}
      }
    }
  }

  @Override
  public void cancel() {
    cancelled.set(true);
    Process process = activeProcess.get();
    if (process != null) process.destroyForcibly();
  }

  private Path writeScriptToTempFile(String content) throws IOException {
    Path dir = config.workingDirectory() != null
        ? Path.of(config.workingDirectory())
        : Files.createTempDirectory("yak-python-");
    Path scriptFile = Files.createTempFile(dir, "task-", ".py");
    Files.writeString(scriptFile, content, StandardCharsets.UTF_8);
    return scriptFile;
  }

  private Process startProcess(Path scriptFile) throws IOException {
    List<String> command = new ArrayList<>();
    command.add(config.pythonExecutable());
    command.add(scriptFile.toAbsolutePath().toString());
    command.addAll(config.scriptArgs());

    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(false);

    if (config.workingDirectory() != null) {
      builder.directory(Path.of(config.workingDirectory()).toFile());
    }

    Map<String, String> env = builder.environment();
    // On Windows, Python defaults to the system locale encoding (e.g. GBK/CP1252)
    // which causes UnicodeEncodeError for non-ASCII output.  Enable UTF-8 mode
    // only on Windows; Linux/macOS already default to UTF-8.
    // Can be overridden via globalEnvVars or task-level envVars (PYTHONUTF8=0).
    if (IS_WINDOWS) {
      env.putIfAbsent("PYTHONUTF8", "1");
    }
    env.putAll(globalEnvVars);
    env.putAll(config.envVars());

    log.info("Starting Python process: executable={}, PYTHON_HOME={}, command={}",
        config.pythonExecutable(),
        globalEnvVars.getOrDefault("PYTHON_HOME", "(not set)"),
        command);

    return builder.start();
  }

  private TaskExecutionResult cancelledResult(String message) {
    return new TaskExecutionResult(
        TaskExecutionStatus.CANCELLED,
        message == null ? "Python execution cancelled" : message,
        Map.of("pythonExecutable", config.pythonExecutable()));
  }

  private static String buildProcessStartDiagnostic(IOException exception, String pythonExecutable) {
    String exceptionMessage = safeMessage(exception, "Python execution failed");
    if (IS_WINDOWS) {
      return "Cannot start Python process: " + exceptionMessage
          + ". On Windows this usually means the Java process cannot find '"
          + pythonExecutable + "' in its PATH. "
          + "Solutions: (1) set PYTHON_HOME environment variable to your Python installation directory "
          + "(e.g. C:\\Python312) and restart the application; "
          + "(2) or disable the Windows App Execution Alias for Python in "
          + "Settings > Apps > Advanced app settings > App execution aliases.";
    }
    return "Cannot start Python process: " + exceptionMessage
        + ". Ensure '" + pythonExecutable + "' is installed and on the system PATH.";
  }

  private static String buildFailureMessage(int exitCode, String stderr, String pythonExecutable) {
    if (exitCode == 9009) {
      return "Python executable not found (exit code 9009). "
          + "Set PYTHON_HOME environment variable or install Python and ensure '"
          + pythonExecutable + "' is on the system PATH.";
    }
    String firstLine = stderr.isBlank() ? "" : ": " + stderr.lines().findFirst().orElse("");
    return "Python script exited with code " + exitCode + firstLine;
  }
}
