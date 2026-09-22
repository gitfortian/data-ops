package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Exact-name lookup used only for recovering deployments that have no persisted Flink JobId. */
@Component
public class FlinkJobDiscoveryClient {

  private static final Pattern JOB_ID = Pattern.compile("(?i)[0-9a-f]{32}");

  private final HttpClient client;
  private final ObjectMapper json;
  private final RealtimeSyncProperties properties;

  public FlinkJobDiscoveryClient(
      @Qualifier("realtimeHttpClient") HttpClient client,
      @Qualifier("realtimeObjectMapper") ObjectMapper json,
      RealtimeSyncProperties properties) {
    this.client = client;
    this.json = json;
    this.properties = properties;
  }

  public List<String> findJobIds(
      ComputeEnvironmentSnapshot environment, String exactRuntimeJobName) {
    if (exactRuntimeJobName == null || exactRuntimeJobName.isBlank()) {
      throw new IllegalArgumentException("runtime job name 不能为空");
    }
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(baseUrl(environment) + "/jobs/overview"))
              .timeout(properties.getRequestTimeout())
              .header("Accept", "application/json")
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw failure("Flink jobs overview HTTP " + response.statusCode(), null);
      }
      JsonNode root =
          response.body() == null || response.body().isBlank()
              ? json.createObjectNode()
              : json.readTree(response.body());
      JsonNode jobs = root.path("jobs");
      if (!jobs.isArray()) {
        throw failure("Flink jobs overview 缺少 jobs 数组", null);
      }
      List<String> result = new ArrayList<>();
      for (JsonNode job : jobs) {
        if (!exactRuntimeJobName.equals(job.path("name").asText())) {
          continue;
        }
        String id = job.path("jid").asText().toLowerCase(Locale.ROOT);
        if (!JOB_ID.matcher(id).matches()) {
          throw failure("Flink jobs overview 返回了无效 jobId", null);
        }
        result.add(id);
      }
      return List.copyOf(result);
    } catch (HttpTimeoutException exception) {
      throw failure("Flink jobs overview 请求超时", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw failure("Flink jobs overview 请求被中断", exception);
    } catch (IOException exception) {
      throw failure("Flink jobs overview 连接失败", exception);
    }
  }

  private String baseUrl(ComputeEnvironmentSnapshot environment) {
    if (environment == null || environment.config() == null) {
      throw new IllegalArgumentException("运行环境配置不能为空");
    }
    return environment.config().restUrl().replaceAll("/+$", "");
  }

  private RealtimeEngineException failure(String message, Throwable cause) {
    return new RealtimeEngineException(message, true, null, cause);
  }
}
