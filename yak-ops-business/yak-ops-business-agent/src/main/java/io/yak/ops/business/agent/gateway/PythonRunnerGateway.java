package io.yak.ops.business.agent.gateway;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.yak.ops.business.agent.config.AgentProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Python 执行出站网关：临时目录 + 独立进程 + 信号量限流 + 超时强杀 + 清理后释放。
 * 进程细节停在本边界；默认关闭（yak.agent.python.enabled=false），显式开启才装配。
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
public class PythonRunnerGateway {

  private final AgentProperties properties;
  private final Semaphore permits;

  public PythonRunnerGateway(AgentProperties properties) {
    this.properties = properties;
    this.permits = new Semaphore(Math.max(1, properties.getPython().getMaxConcurrent()));
  }

  /** 阻塞式执行，调用方负责放到独立的调度线程上（工具层以 Mono 包裹）。 */
  public String execute(String code) {
    if (!permits.tryAcquire()) {
      return "Error: 并发分析任务已达上限（"
          + properties.getPython().getMaxConcurrent()
          + "），请稍后重试。";
    }
    Path workDir = null;
    try {
      workDir = Files.createTempDirectory("yak-agent-python-");
      return doExecute(code, workDir);
    } catch (IOException e) {
      log.error("python execution io error", e);
      return "Error: 执行环境异常 " + e.getMessage();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "Error: 执行被中断";
    } finally {
      cleanup(workDir);
      permits.release();
    }
  }

  private String doExecute(String code, Path workDir) throws IOException, InterruptedException {
    int timeoutSeconds = Math.max(1, properties.getPython().getTimeoutSeconds());
    Path script = workDir.resolve("analysis.py");
    Files.writeString(script, code, StandardCharsets.UTF_8);

    Process process =
        new ProcessBuilder(
                properties.getPython().getCommand(), "-I", script.toAbsolutePath().toString())
            .directory(workDir.toFile())
            .redirectErrorStream(true)
            .start();

    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
      return "Error: 执行超时（超过 " + timeoutSeconds + " 秒已被强制终止）";
    }

    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exit = process.exitValue();
    if (exit != 0) {
      return "Error(exit " + exit + "):\n" + output;
    }
    return output == null || output.isBlank() ? "(无输出)" : output;
  }

  private void cleanup(Path workDir) {
    if (workDir == null) {
      return;
    }
    try (var paths = Files.walk(workDir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException ignored) {
          // 尽力清理，失败不阻断主流程
        }
      });
    } catch (IOException e) {
      log.warn("failed to cleanup python work dir {}: {}", workDir, e.getMessage());
    }
  }
}
