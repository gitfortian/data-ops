package io.yak.ops.business.sync.realtime.domain;

import java.time.LocalDateTime;
import java.util.List;

/** User-facing diagnosis result for one Flink CDC runtime environment. */
public record ComputeEnvironmentDiagnosis(
    Long environmentId,
    String environmentName,
    String status,
    boolean ready,
    String summary,
    String detectedFlinkVersion,
    String detectedFlinkCdcVersion,
    String detectedJavaVersion,
    LocalDateTime checkedAt,
    List<Check> checks) {

  public static final String STATUS_HEALTHY = "HEALTHY";
  public static final String STATUS_WARNING = "WARNING";
  public static final String STATUS_FAILED = "FAILED";

  public record Check(String key, String label, String status, String message) {
    public static final String PASS = "PASS";
    public static final String WARN = "WARN";
    public static final String FAIL = "FAIL";
  }
}
