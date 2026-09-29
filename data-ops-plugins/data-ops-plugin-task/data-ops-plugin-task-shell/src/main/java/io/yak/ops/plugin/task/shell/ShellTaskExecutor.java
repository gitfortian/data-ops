package io.yak.ops.plugin.task.shell;

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
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One physical Shell task execution attempt. */
final class ShellTaskExecutor implements TaskExecutor {

  private static final Logger log = LoggerFactory.getLogger(ShellTaskExecutor.class);
  private static final boolean IS_WINDOWS =
      System.getProperty("os.name", "").toLowerCase().contains("win");

  private final TaskDefinition definition;
  private final ShellTaskConfig config;
  private final Map<String, String> globalEnvVars;
  private final ResourceResolver resourceResolver;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicReference<Process> activeProcess = new AtomicReference<>();

  ShellTaskExecutor(
      TaskDefinition definition,
      ShellTaskConfig config,
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
      return cancelledResult("Shell execution was cancelled before start");
    }

    Path scriptFile = null;
    try {
      String scriptContent = resolveScriptContent(
          definition, resourceResolver, config.resourceId(), config.resourceVersion());
      scriptFile = writeScriptToTempFile(scriptContent);
      makeExecutable(scriptFile);
      Process process = startProcess(scriptFile);
      activeProcess.set(process);

      if (cancelled.get()) {
        process.destroyForcibly();
        return cancelledResult("Shell execution was cancelled after process start");
      }

      boolean finished = process.waitFor(config.timeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        activeProcess.set(null);
        return new TaskExecutionResult(
            TaskExecutionStatus.TIMEOUT,
            "Shell script timed out after " + config.timeoutSeconds() + " seconds",
            Map.of("shellExecutable", config.shellExecutable()));
      }

      String stdout = truncate(readStream(process.inputReader(StandardCharsets.UTF_8)));
      String stderr = truncate(readStream(process.errorReader(StandardCharsets.UTF_8)));
      int exitCode = process.exitValue();
      activeProcess.set(null);

      if (cancelled.get()) {
        return cancelledResult("Shell execution was cancelled");
      }

      Map<String, Object> output = new LinkedHashMap<>();
      output.put("exitCode", exitCode);
      output.put("stdout", stdout);
      output.put("stderr", stderr);
      output.put("shellExecutable", config.shellExecutable());

      if (exitCode == 0) {
        return TaskExecutionResult.success(output);
      }
      String detailMessage = buildFailureMessage(exitCode, stderr, config.shellExecutable());
      return new TaskExecutionResult(
          TaskExecutionStatus.FAILED,
          detailMessage,
          output);
    } catch (IOException exception) {
      if (cancelled.get()) {
        return cancelledResult(safeMessage(exception, "Shell execution failed"));
      }
      String diagnostic = buildProcessStartDiagnostic(exception, config.shellExecutable());
      Map<String, Object> output = new LinkedHashMap<>();
      output.put("shellExecutable", config.shellExecutable());
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
        : Files.createTempDirectory("yak-shell-");
    String suffix = ShellTaskConfig.isPowerShell(config.shellExecutable()) ? ".ps1" : ".sh";
    Path scriptFile = Files.createTempFile(dir, "task-", suffix);
    Files.writeString(scriptFile, content, StandardCharsets.UTF_8);
    return scriptFile;
  }

  /** On Linux/macOS, make the temp script executable so bash can run it. */
  private void makeExecutable(Path scriptFile) {
    if (IS_WINDOWS) return;
    try {
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(scriptFile);
      perms.add(PosixFilePermission.OWNER_EXECUTE);
      perms.add(PosixFilePermission.GROUP_EXECUTE);
      Files.setPosixFilePermissions(scriptFile, perms);
    } catch (Exception ignored) {
      // Non-POSIX filesystem; ignore
    }
  }

  private Process startProcess(Path scriptFile) throws IOException {
    List<String> command = new ArrayList<>();
    command.add(config.shellExecutable());

    if (ShellTaskConfig.isPowerShell(config.shellExecutable())) {
      // PowerShell requires -File flag and benefits from -NoProfile -NonInteractive
      command.add("-NoProfile");
      command.add("-NonInteractive");
      command.add("-File");
      command.add(scriptFile.toAbsolutePath().toString());
    } else {
      command.add(scriptFile.toAbsolutePath().toString());
    }
    command.addAll(config.scriptArgs());

    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(false);

    if (config.workingDirectory() != null) {
      builder.directory(Path.of(config.workingDirectory()).toFile());
    }

    Map<String, String> env = builder.environment();
    env.putAll(globalEnvVars);
    env.putAll(config.envVars());

    log.info("Starting Shell process: executable={}, command={}",
        config.shellExecutable(), command);

    return builder.start();
  }

  private TaskExecutionResult cancelledResult(String message) {
    return new TaskExecutionResult(
        TaskExecutionStatus.CANCELLED,
        message == null ? "Shell execution cancelled" : message,
        Map.of("shellExecutable", config.shellExecutable()));
  }

  private static String buildProcessStartDiagnostic(IOException exception, String shellExecutable) {
    String exceptionMessage = safeMessage(exception, "Shell execution failed");
    if (IS_WINDOWS) {
      if (ShellTaskConfig.isPowerShell(shellExecutable)) {
        return "Cannot start Shell process: " + exceptionMessage
            + ". PowerShell Core (pwsh) is not installed or not on PATH. "
            + "Install PowerShell Core: https://aka.ms/powershell, "
            + "or set SHELL_HOME to your pwsh installation directory.";
      }
      return "Cannot start Shell process: " + exceptionMessage
          + ". On Windows this usually means '" + shellExecutable + "' is not available. "
          + "Install pwsh (PowerShell Core), Git for Windows (includes bash), "
          + "or set SHELL_HOME environment variable.";
    }
    return "Cannot start Shell process: " + exceptionMessage
        + ". Ensure '" + shellExecutable + "' is installed and on the system PATH.";
  }

  private static String buildFailureMessage(int exitCode, String stderr, String shellExecutable) {
    String firstLine = stderr.isBlank() ? "" : ": " + stderr.lines().findFirst().orElse("");
    return "Shell script exited with code " + exitCode + firstLine;
  }
}
