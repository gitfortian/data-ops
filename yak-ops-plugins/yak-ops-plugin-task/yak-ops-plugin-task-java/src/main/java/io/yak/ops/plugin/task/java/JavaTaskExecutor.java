package io.yak.ops.plugin.task.java;

import static io.yak.ops.plugin.task.api.ScriptTaskSupport.readStream;
import static io.yak.ops.plugin.task.api.ScriptTaskSupport.safeMessage;
import static io.yak.ops.plugin.task.api.ScriptTaskSupport.truncate;

import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.spi.resource.ResolvedResource;
import io.yak.ops.spi.resource.ResourceResolver;
import io.yak.ops.spi.resource.TempDirectoryUtils;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One physical Java JAR task execution attempt. */
final class JavaTaskExecutor implements TaskExecutor {

  private static final Logger log = LoggerFactory.getLogger(JavaTaskExecutor.class);
  /** Dedicated thread pool for stream reading to avoid ForkJoinPool.commonPool() contention. */
  private static final ExecutorService STREAM_READER =
      Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "yak-java-stream-reader");
        t.setDaemon(true);
        return t;
      });

  private final TaskDefinition definition;
  private final JavaTaskConfig config;
  private final Map<String, String> globalEnvVars;
  private final ResourceResolver resourceResolver;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicReference<Process> activeProcess = new AtomicReference<>();

  JavaTaskExecutor(
      TaskDefinition definition,
      JavaTaskConfig config,
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
      return cancelledResult("Java execution was cancelled before start");
    }

    // 1. Resolve all JAR resources into a shared temp directory
    Path sharedDir = Files.createTempDirectory("yak-java-jars-");
    List<Path> jarPaths;
    try {
      jarPaths = resolveAllResources(sharedDir);
    } catch (IOException exception) {
      TempDirectoryUtils.deleteRecursively(sharedDir);
      if (cancelled.get()) {
        return cancelledResult(safeMessage(exception, "Java execution failed"));
      }
      return new TaskExecutionResult(
          TaskExecutionStatus.FAILED,
          "Failed to resolve JAR resources: " + safeMessage(exception, "unknown error"),
          Map.of());
    }

    try {
      if (cancelled.get()) {
        return cancelledResult("Java execution was cancelled after resource download");
      }

      // 2. Build java command
      List<String> command = buildCommand(jarPaths);
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectErrorStream(false);

      // 3. Set environment variables
      Map<String, String> env = builder.environment();
      env.putAll(globalEnvVars);
      env.putAll(config.envVars());

      // 4. Start process
      Process process;
      try {
        process = builder.start();
      } catch (IOException exception) {
        if (cancelled.get()) {
          return cancelledResult(safeMessage(exception, "Java execution failed"));
        }
        String javaExecutable = command.get(0);
        String diagnostic = buildProcessStartDiagnostic(exception, javaExecutable);
        return new TaskExecutionResult(TaskExecutionStatus.FAILED, diagnostic, Map.of());
      }
      activeProcess.set(process);

      try {
        return runProcess(process, command);
      } finally {
        destroyAndWait(process);
        activeProcess.set(null);
      }
    } finally {
      try {
        TempDirectoryUtils.deleteRecursively(sharedDir);
      } catch (IOException ignored) {}
    }
  }

  /**
   * Resolve all resource JARs into the shared temp directory.
   * Each resource is resolved, its JAR is copied to {@code sharedDir}, then
   * the original resolved resource is closed (cleaning up its own temp dir).
   */
  private List<Path> resolveAllResources(Path sharedDir) throws IOException {
    List<Path> jarPaths = new ArrayList<>(config.resources().size());
    for (JavaTaskConfig.ResourceRef ref : config.resources()) {
      try (ResolvedResource resource = ref.resourceVersion() > 0
          ? resourceResolver.resolve(ref.resourceId(), ref.resourceVersion())
          : resourceResolver.resolve(ref.resourceId())) {
        Path target = sharedDir.resolve(resource.fileName());
        Files.copy(resource.localPath(), target, StandardCopyOption.REPLACE_EXISTING);
        jarPaths.add(target);
      }
    }
    return jarPaths;
  }

  /** Runs the process to completion, handling timeout and cancellation. */
  private TaskExecutionResult runProcess(Process process, List<String> command) {
    CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> {
      try {
        return truncate(readStream(process.inputReader(java.nio.charset.StandardCharsets.UTF_8)));
      } catch (IOException e) {
        return "[failed to read stdout: " + e.getMessage() + "]";
      }
    }, STREAM_READER);
    CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
      try {
        return truncate(readStream(process.errorReader(java.nio.charset.StandardCharsets.UTF_8)));
      } catch (IOException e) {
        return "[failed to read stderr: " + e.getMessage() + "]";
      }
    }, STREAM_READER);

    try {
      boolean finished = process.waitFor(config.timeoutSeconds(), TimeUnit.SECONDS);
      if (!finished) {
        return buildTimeoutResult(process, stdoutFuture, stderrFuture, command);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return cancelledResult("Java execution interrupted");
    }

    String stdout = getStreamOutput(stdoutFuture, "stdout");
    String stderr = getStreamOutput(stderrFuture, "stderr");
    int exitCode = process.exitValue();

    if (cancelled.get()) {
      return cancelledResult("Java execution was cancelled");
    }

    Map<String, Object> output = new LinkedHashMap<>();
    output.put("exitCode", exitCode);
    output.put("stdout", stdout);
    output.put("stderr", stderr);
    output.put("javaExecutable", command.get(0));
    output.put("jarCount", config.resources().size());

    if (exitCode == 0) {
      return TaskExecutionResult.success(output);
    }
    String detailMessage = buildFailureMessage(exitCode, stderr);
    return new TaskExecutionResult(TaskExecutionStatus.FAILED, detailMessage, output);
  }

  private TaskExecutionResult buildTimeoutResult(
      Process process,
      CompletableFuture<String> stdoutFuture,
      CompletableFuture<String> stderrFuture,
      List<String> command) {
    process.destroyForcibly();
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("javaExecutable", command.get(0));
    output.put("timeoutSeconds", config.timeoutSeconds());

    String partialStdout = getStreamOutput(stdoutFuture, "stdout");
    String partialStderr = getStreamOutput(stderrFuture, "stderr");
    if (partialStdout != null && !partialStdout.isEmpty()) output.put("stdout", partialStdout);
    if (partialStderr != null && !partialStderr.isEmpty()) output.put("stderr", partialStderr);

    return new TaskExecutionResult(
        TaskExecutionStatus.TIMEOUT,
        "Java process timed out after " + config.timeoutSeconds() + " seconds",
        output);
  }

  private static String getStreamOutput(CompletableFuture<String> future, String name) {
    try {
      return future.get(10, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      return "[timed out reading " + name + "]";
    } catch (Exception e) {
      return "[failed to read " + name + ": " + e.getMessage() + "]";
    }
  }

  @Override
  public void cancel() {
    cancelled.set(true);
    Process process = activeProcess.get();
    if (process != null) destroyAndWait(process);
  }

  private static void destroyAndWait(Process process) {
    process.destroyForcibly();
    try {
      process.waitFor(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Build the java command line.
   *
   * <p>Single JAR without mainClass → {@code java [jvmArgs] -jar file.jar [programArgs]}
   * <p>Multiple JARs or explicit mainClass → {@code java [jvmArgs] -cp cp mainClass [programArgs]}
   */
  private List<String> buildCommand(List<Path> jarPaths) {
    List<String> command = new ArrayList<>();
    String javaExecutable = JavaTaskConfig.defaultJavaExecutable(globalEnvVars, config.envVars());
    command.add(javaExecutable);
    command.addAll(config.jvmArgs());

    boolean multiJar = jarPaths.size() > 1;
    boolean hasMainClass = config.mainClass() != null;

    if (multiJar || hasMainClass) {
      // -cp mode: join all JAR paths with platform path separator
      String classpath = jarPaths.stream()
          .map(p -> p.toAbsolutePath().toString())
          .reduce((a, b) -> a + java.io.File.pathSeparator + b)
          .orElse("");
      command.add("-cp");
      command.add(classpath);
      command.add(config.mainClass());
    } else {
      // -jar mode: single JAR, use manifest Main-Class
      command.add("-jar");
      command.add(jarPaths.get(0).toAbsolutePath().toString());
    }
    command.addAll(config.programArgs());

    log.debug("Starting Java process: executable={}, jars={}, args={}",
        javaExecutable, jarPaths.size(), command.subList(1, command.size()));
    return command;
  }

  private TaskExecutionResult cancelledResult(String message) {
    return new TaskExecutionResult(
        TaskExecutionStatus.CANCELLED,
        message == null ? "Java execution cancelled" : message,
        Map.of());
  }

  private static String buildProcessStartDiagnostic(IOException exception, String javaExecutable) {
    String exceptionMessage = safeMessage(exception, "Java execution failed");
    return "Cannot start Java process: " + exceptionMessage
        + ". Ensure '" + javaExecutable + "' is installed and on the system PATH, "
        + "or set JAVA_HOME environment variable.";
  }

  private static String buildFailureMessage(int exitCode, String stderr) {
    String firstLine = stderr.isBlank() ? "" : ": " + stderr.lines().findFirst().orElse("");
    return "Java process exited with code " + exitCode + firstLine;
  }
}
