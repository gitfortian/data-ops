package io.yak.ops.business.dataservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataServiceClusterRuntimeContractTest {

  @Test
  void rateLimitIsSharedPersistenceInsteadOfPerJvmMap() throws Exception {
    String limiter = read("access/DataServiceRateLimiter.java");
    String adapter = read("repository/DataServiceRateLimitRepositoryAdapter.java");

    assertThat(limiter)
        .contains("DataServiceRateLimitRepository")
        .doesNotContain("ConcurrentHashMap");
    assertThat(adapter)
        .contains("request_count = request_count + 1")
        .contains("AND request_count = ?")
        .contains("DuplicateKeyException");
  }

  @Test
  void cacheStaysNodeLocalButNamespaceUsesDurableGenerationAndRuntimeShape() throws Exception {
    String runtime = read("runtime/LocalDataServiceRuntime.java");
    String invoker = read("execution/DataServiceInvoker.java");

    assertThat(runtime)
        .contains("Caffeine")
        .contains("\"LOCAL\"");
    assertThat(invoker)
        .contains("definition.runtimeGeneration()")
        .contains("definition.settings().maxRows()")
        .contains("policy.cacheTtlSeconds()")
        .doesNotContain(".append(definition.updateTime())");
  }

  @Test
  void auditIsSanitizedBeforePersistence() throws Exception {
    String recorder = read("execution/DataServiceInvocationRecorder.java");
    String sanitizer = read("execution/DataServiceAuditSanitizer.java");

    assertThat(recorder).contains("sanitizer.sanitize(parameters)");
    assertThat(sanitizer)
        .contains("[REDACTED]")
        .contains("password")
        .contains("authorization")
        .contains("maskPhone")
        .contains("maskIdentity")
        .contains("maskEmail");
  }

  @Test
  void rawAuditRollupAndDeletionShareOneTransactionBoundary() throws Exception {
    String adapter = read("repository/DataServiceObservabilityMaintenanceRepositoryAdapter.java");

    assertThat(adapter)
        .contains("TransactionTemplate")
        .contains("yak_ops_data_service_call_log_hourly")
        .contains("INSERT INTO")
        .contains("DELETE FROM yak_ops_data_service_call_log")
        .contains("transactionTemplate.execute");
  }

  @Test
  void runtimeStatusSeparatesClusterInvocationFromLocalResilience() throws Exception {
    String manager = read("runtime/DataServiceRuntimePolicyManager.java");
    assertThat(manager)
        .contains("DataServiceRuntimeMetricsRepository")
        .contains("CLUSTER_INVOCATION_LOCAL_RESILIENCE")
        .contains("local.cacheEntries()")
        .contains("metrics.totalCalls()");
  }

  private String read(String relative) throws Exception {
    Path local = Path.of("src/main/java/io/yak/ops/business/dataservice").resolve(relative);
    if (Files.isRegularFile(local)) return Files.readString(local);
    return Files.readString(Path.of(
        "yak-ops-business/yak-ops-business-data-service/src/main/java/io/yak/ops/business/dataservice")
        .resolve(relative));
  }
}
