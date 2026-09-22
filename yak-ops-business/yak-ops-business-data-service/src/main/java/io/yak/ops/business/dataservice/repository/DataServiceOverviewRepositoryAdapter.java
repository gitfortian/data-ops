package io.yak.ops.business.dataservice.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceCallLogMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceOverviewMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceCallLogPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceOverviewHotApiPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceOverviewSummaryPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceOverviewTrendPO;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for bounded Data Service overview projections. */
@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceOverviewRepositoryAdapter implements DataServiceOverviewRepository {

  private final DataServiceOverviewMapper overviewMapper;
  private final DataServiceCallLogMapper callLogMapper;
  private final CurrentProject currentProject;

  @Override
  public Snapshot load(
      LocalDateTime from,
      LocalDateTime to,
      int bucketMinutes,
      int bucketCount,
      int hotApiLimit,
      int failureLimit) {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("Data Service overview 时间范围无效");
    }

    Long projectId = currentProject.requireProjectId();
    int minutes = Math.max(1, bucketMinutes);
    int buckets = Math.max(1, bucketCount);
    int hotLimit = normalizeLimit(hotApiLimit);
    int failures = normalizeLimit(failureLimit);
    DataServiceOverviewSummaryPO summary = overviewMapper.selectSummary(projectId, from, to);

    List<TrendBucket> trend =
        overviewMapper.selectTrend(projectId, from, to, minutes).stream()
            .filter(value -> value.getBucketIndex() != null)
            .filter(value -> value.getBucketIndex() >= 0 && value.getBucketIndex() < buckets)
            .map(this::trend)
            .toList();
    List<ApiStatistics> hotApis =
        overviewMapper.selectHotApis(projectId, from, to, hotLimit).stream().map(this::hotApi).toList();
    List<InvocationRecord> recentFailures =
        callLogMapper
            .selectList(
                Wrappers.<DataServiceCallLogPO>lambdaQuery()
                    .eq(DataServiceCallLogPO::getProjectId, projectId)
                    .ge(DataServiceCallLogPO::getCreateTime, from)
                    .le(DataServiceCallLogPO::getCreateTime, to)
                    .eq(DataServiceCallLogPO::getSuccess, false)
                    .orderByDesc(DataServiceCallLogPO::getCreateTime)
                    .orderByDesc(DataServiceCallLogPO::getId)
                    .last("LIMIT " + failures))
            .stream()
            .map(this::invocation)
            .toList();

    return new Snapshot(
        number(summary == null ? null : summary.getApiTotal()),
        number(summary == null ? null : summary.getRunningApis()),
        number(summary == null ? null : summary.getTotalCalls()),
        number(summary == null ? null : summary.getSuccessCalls()),
        number(summary == null ? null : summary.getTotalDurationMs()),
        number(summary == null ? null : summary.getTotalRows()),
        trend,
        hotApis,
        recentFailures);
  }

  private TrendBucket trend(DataServiceOverviewTrendPO value) {
    return new TrendBucket(
        value.getBucketIndex(),
        number(value.getCalls()),
        number(value.getSuccessCalls()),
        number(value.getFailureCalls()),
        number(value.getTotalDurationMs()));
  }

  private ApiStatistics hotApi(DataServiceOverviewHotApiPO value) {
    return new ApiStatistics(
        value.getApiId(),
        value.getName(),
        value.getPath(),
        number(value.getCalls()),
        number(value.getSuccessCalls()),
        number(value.getTotalDurationMs()));
  }

  private InvocationRecord invocation(DataServiceCallLogPO value) {
    return new InvocationRecord(
        value.getId(),
        value.getProjectId(),
        value.getApiId(),
        value.getServiceName(),
        value.getServicePath(),
        value.getCallerType(),
        value.getApiKeyId(),
        value.getApiKeyName(),
        value.getApiKeyPrefix(),
        value.getParamsJson(),
        Boolean.TRUE.equals(value.getSuccess()),
        number(value.getDurationMs()),
        integer(value.getRowCount()),
        value.getErrorMessage(),
        value.getCreateTime());
  }

  private int normalizeLimit(int limit) {
    return Math.max(1, Math.min(100, limit));
  }

  private long number(Long value) {
    return value == null ? 0L : Math.max(0L, value);
  }

  private int integer(Integer value) {
    return value == null ? 0 : Math.max(0, value);
  }
}
