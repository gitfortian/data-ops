package io.yak.ops.business.dataservice.observability;

import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.repository.DataServiceOverviewRepository;
import io.yak.ops.business.dataservice.repository.DataServiceOverviewRepository.ApiStatistics;
import io.yak.ops.business.dataservice.repository.DataServiceOverviewRepository.Snapshot;
import io.yak.ops.business.dataservice.repository.DataServiceOverviewRepository.TrendBucket;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceOverviewReader {

  private static final int HOT_API_LIMIT = 8;
  private static final int FAILURE_LIMIT = 8;

  private final DataServiceOverviewRepository repository;

  public Overview overview(String range) {
    return overviewAt(range, LocalDateTime.now());
  }

  Overview overviewAt(String range, LocalDateTime now) {
    RangeWindow window = RangeWindow.of(range, now);
    Snapshot snapshot =
        repository.load(
            window.startTime(),
            now,
            window.bucketMinutes(),
            window.bucketCount(),
            HOT_API_LIMIT,
            FAILURE_LIMIT);

    List<MutableTrendPoint> trend = createTrend(window);
    for (TrendBucket bucket : snapshot.trend()) {
      if (bucket.bucketIndex() < 0 || bucket.bucketIndex() >= trend.size()) continue;
      trend
          .get(bucket.bucketIndex())
          .accept(
              bucket.calls(),
              bucket.successCalls(),
              bucket.failureCalls(),
              bucket.totalDurationMs());
    }

    List<HotApi> hotApis = snapshot.hotApis().stream().map(this::hotApi).toList();
    List<FailureItem> failures = snapshot.recentFailures().stream().map(FailureItem::from).toList();
    long failureCalls = Math.max(0L, snapshot.totalCalls() - snapshot.successCalls());

    return new Overview(
        window.range(),
        window.startTime(),
        now,
        snapshot.apiTotal(),
        snapshot.runningApis(),
        Math.max(0L, snapshot.apiTotal() - snapshot.runningApis()),
        snapshot.totalCalls(),
        snapshot.successCalls(),
        failureCalls,
        percent(snapshot.successCalls(), snapshot.totalCalls()),
        average(snapshot.totalDurationMs(), snapshot.totalCalls()),
        snapshot.totalRows(),
        trend.stream().map(MutableTrendPoint::toView).toList(),
        hotApis,
        failures);
  }

  private List<MutableTrendPoint> createTrend(RangeWindow window) {
    List<MutableTrendPoint> points = new ArrayList<>(window.bucketCount());
    for (int index = 0; index < window.bucketCount(); index++) {
      points.add(
          new MutableTrendPoint(
              window.label(
                  window.startTime().plusMinutes((long) index * window.bucketMinutes()))));
    }
    return points;
  }

  private HotApi hotApi(ApiStatistics value) {
    String fallback = value.apiId() == null ? "未知 API" : "API #" + value.apiId();
    String name = firstText(value.name(), value.path(), fallback);
    String path = firstText(value.path(), "");
    return new HotApi(
        value.apiId(),
        name,
        path,
        value.calls(),
        percent(value.successCalls(), value.calls()),
        average(value.totalDurationMs(), value.calls()));
  }

  private static long average(long total, long count) {
    return count <= 0 ? 0L : Math.round((double) Math.max(0L, total) / count);
  }

  private static double percent(long numerator, long denominator) {
    return denominator <= 0 ? 0D : Math.round((numerator * 1000D) / denominator) / 10D;
  }

  private static String firstText(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }

  public record Overview(
      String range,
      LocalDateTime startTime,
      LocalDateTime endTime,
      long apiTotal,
      long runningApis,
      long stoppedApis,
      long totalCalls,
      long successCalls,
      long failureCalls,
      double successRate,
      long averageDurationMs,
      long totalRows,
      List<TrendPoint> trend,
      List<HotApi> hotApis,
      List<FailureItem> recentFailures) {}

  public record TrendPoint(
      String time,
      long calls,
      long successCalls,
      long failureCalls,
      long averageDurationMs) {}

  public record HotApi(
      Long apiId,
      String name,
      String path,
      long calls,
      double successRate,
      long averageDurationMs) {}

  public record FailureItem(
      Long id,
      Long apiId,
      String serviceName,
      String servicePath,
      long durationMs,
      String errorMessage,
      LocalDateTime createTime) {

    static FailureItem from(InvocationRecord log) {
      return new FailureItem(
          log.id(),
          log.apiId(),
          log.serviceName(),
          log.servicePath(),
          Math.max(0L, log.durationMs()),
          log.errorMessage(),
          log.createTime());
    }
  }

  private static final class MutableTrendPoint {
    private final String time;
    private long calls;
    private long successes;
    private long failures;
    private long duration;

    MutableTrendPoint(String time) {
      this.time = time;
    }

    void accept(long calls, long successes, long failures, long duration) {
      this.calls = Math.max(0L, calls);
      this.successes = Math.max(0L, successes);
      this.failures = Math.max(0L, failures);
      this.duration = Math.max(0L, duration);
    }

    TrendPoint toView() {
      return new TrendPoint(time, calls, successes, failures, average(duration, calls));
    }
  }

  private record RangeWindow(
      String range,
      LocalDateTime startTime,
      int bucketMinutes,
      int bucketCount,
      DateTimeFormatter labelFormatter) {

    static RangeWindow of(String raw, LocalDateTime now) {
      String range = raw == null || raw.isBlank() ? "24h" : raw.trim().toLowerCase();
      return switch (range) {
        case "24h" -> create("24h", now, 60, 24, DateTimeFormatter.ofPattern("HH:mm"));
        case "7d" -> create("7d", now, 360, 28, DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        case "30d" -> create("30d", now, 1_440, 30, DateTimeFormatter.ofPattern("MM-dd"));
        default -> throw new IllegalArgumentException("运行概览时间范围仅支持 24h、7d、30d");
      };
    }

    static RangeWindow create(
        String range,
        LocalDateTime now,
        int bucketMinutes,
        int count,
        DateTimeFormatter formatter) {
      int bucketHours = Math.max(1, bucketMinutes / 60);
      LocalDateTime aligned =
          bucketMinutes >= 1_440
              ? now.toLocalDate().atStartOfDay()
              : now
                  .withHour((now.getHour() / bucketHours) * bucketHours)
                  .withMinute(0)
                  .withSecond(0)
                  .withNano(0);
      LocalDateTime endBoundary = aligned.plusMinutes(bucketMinutes);
      return new RangeWindow(
          range,
          endBoundary.minusMinutes((long) bucketMinutes * count),
          bucketMinutes,
          count,
          formatter);
    }

    String label(LocalDateTime time) {
      return labelFormatter.format(time);
    }
  }
}
