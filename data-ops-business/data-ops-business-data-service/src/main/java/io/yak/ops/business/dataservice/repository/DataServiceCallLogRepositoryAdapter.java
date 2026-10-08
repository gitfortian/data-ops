package io.yak.ops.business.dataservice.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceCallLogMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceCallLogPO;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceCallLogRepositoryAdapter implements DataServiceCallLogRepository {

  private final DataServiceCallLogMapper mapper;
  private final CurrentProject currentProject;

  @Override
  public InvocationRecord save(InvocationRecord record) {
    if (record == null || record.projectId() == null || record.projectId() <= 0L) {
      throw new IllegalArgumentException("调用日志缺少 Project Space");
    }
    DataServiceCallLogPO po = toPo(record);
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public List<InvocationRecord> recent(int limit) {
    Long projectId = currentProject.requireProjectId();
    int size = normalizeLimit(limit);
    return mapper.selectList(
            Wrappers.<DataServiceCallLogPO>lambdaQuery()
                .eq(DataServiceCallLogPO::getProjectId, projectId)
                .orderByDesc(DataServiceCallLogPO::getCreateTime)
                .orderByDesc(DataServiceCallLogPO::getId)
                .last("LIMIT " + size))
        .stream().map(this::toDomain).toList();
  }

  @Override
  public List<InvocationRecord> recentByApi(Long apiId, int limit) {
    if (apiId == null || apiId <= 0L) {
      throw new IllegalArgumentException("数据服务 ID 必须大于 0");
    }
    Long projectId = currentProject.requireProjectId();
    int size = normalizeLimit(limit);
    return mapper.selectList(
            Wrappers.<DataServiceCallLogPO>lambdaQuery()
                .eq(DataServiceCallLogPO::getProjectId, projectId)
                .eq(DataServiceCallLogPO::getApiId, apiId)
                .orderByDesc(DataServiceCallLogPO::getCreateTime)
                .orderByDesc(DataServiceCallLogPO::getId)
                .last("LIMIT " + size))
        .stream().map(this::toDomain).toList();
  }

  /**
   * Consumption Usage is based only on successful invocation evidence. Apply
   * success filtering inside the owning Project-scoped SQL query BEFORE the
   * bounded window, so a burst of failed calls cannot evict real consumers.
   * The ordinary recentByApi view remains unchanged for failure diagnostics.
   */
  @Override
  public List<InvocationRecord> recentSuccessfulByApi(Long apiId, int limit) {
    if (apiId == null || apiId <= 0L) {
      throw new IllegalArgumentException("数据服务 ID 必须大于 0");
    }
    Long projectId = currentProject.requireProjectId();
    int size = normalizeLimit(limit);
    return mapper.selectList(
            Wrappers.<DataServiceCallLogPO>lambdaQuery()
                .eq(DataServiceCallLogPO::getProjectId, projectId)
                .eq(DataServiceCallLogPO::getApiId, apiId)
                .eq(DataServiceCallLogPO::getSuccess, true)
                .orderByDesc(DataServiceCallLogPO::getCreateTime)
                .orderByDesc(DataServiceCallLogPO::getId)
                .last("LIMIT " + size))
        .stream().map(this::toDomain).toList();
  }

  /**
   * Query source-owned successful invocation audit for one immutable revision.
   * Filter Project, API, revision and success inside the same DB query before
   * ordering and LIMIT; never read a global audit and filter it in Java.
   */
  @Override
  public List<InvocationRecord> recentSuccessfulByApiAndRevision(
      Long apiId, Long sourceRevisionId, int limit) {
    if (apiId == null || apiId <= 0L || sourceRevisionId == null || sourceRevisionId <= 0L) {
      throw new IllegalArgumentException("数据服务 ID 与来源修订 ID 必须大于 0");
    }
    Long projectId = currentProject.requireProjectId();
    int size = normalizeLimit(limit);
    return mapper.selectList(
            Wrappers.<DataServiceCallLogPO>lambdaQuery()
                .eq(DataServiceCallLogPO::getProjectId, projectId)
                .eq(DataServiceCallLogPO::getApiId, apiId)
                .eq(DataServiceCallLogPO::getSourceRevisionId, sourceRevisionId)
                .eq(DataServiceCallLogPO::getSuccess, true)
                .orderByDesc(DataServiceCallLogPO::getCreateTime)
                .orderByDesc(DataServiceCallLogPO::getId)
                .last("LIMIT " + size))
        .stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<InvocationRecord> findByApiAndId(Long apiId, Long invocationId) {
    if (apiId == null || apiId <= 0L || invocationId == null || invocationId <= 0L) {
      throw new IllegalArgumentException("数据服务 ID 与调用记录 ID 必须大于 0");
    }
    Long projectId = currentProject.requireProjectId();
    // All three predicates MUST remain on the one database query. Never select
    // globally by audit ID then check ownership after deserializing another Project's data.
    DataServiceCallLogPO row = mapper.selectOne(
        Wrappers.<DataServiceCallLogPO>lambdaQuery()
            .eq(DataServiceCallLogPO::getProjectId, projectId)
            .eq(DataServiceCallLogPO::getApiId, apiId)
            .eq(DataServiceCallLogPO::getId, invocationId));
    return Optional.ofNullable(row).map(this::toDomain);
  }

  @Override
  public List<InvocationRecord> between(LocalDateTime from, LocalDateTime to) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(
            Wrappers.<DataServiceCallLogPO>lambdaQuery()
                .eq(DataServiceCallLogPO::getProjectId, projectId)
                .ge(from != null, DataServiceCallLogPO::getCreateTime, from)
                .le(to != null, DataServiceCallLogPO::getCreateTime, to)
                .orderByAsc(DataServiceCallLogPO::getCreateTime)
                .orderByAsc(DataServiceCallLogPO::getId))
        .stream().map(this::toDomain).toList();
  }

  private int normalizeLimit(int limit) {
    return Math.max(1, Math.min(1_000, limit));
  }

  private InvocationRecord toDomain(DataServiceCallLogPO po) {
    return new InvocationRecord(
        po.getId(), po.getProjectId(), po.getApiId(), po.getServiceName(), po.getServicePath(),
        po.getCallerType(), po.getApiKeyId(), po.getConsumerId(), po.getApiKeyName(), po.getApiKeyPrefix(),
        po.getSourceRevisionId(), po.getSourceRevisionNo(), po.getParamsJson(),
        Boolean.TRUE.equals(po.getSuccess()), value(po.getDurationMs()), value(po.getRowCount()),
        po.getErrorMessage(), po.getCreateTime());
  }

  private DataServiceCallLogPO toPo(InvocationRecord record) {
    DataServiceCallLogPO po = new DataServiceCallLogPO();
    po.setId(record.id());
    po.setProjectId(record.projectId());
    po.setApiId(record.apiId());
    po.setServiceName(record.serviceName());
    po.setServicePath(record.servicePath());
    po.setCallerType(record.callerType());
    po.setApiKeyId(record.apiKeyId());
    po.setConsumerId(record.consumerId());
    po.setApiKeyName(record.apiKeyName());
    po.setApiKeyPrefix(record.apiKeyPrefix());
    po.setSourceRevisionId(record.sourceRevisionId());
    po.setSourceRevisionNo(record.sourceRevisionNo());
    po.setParamsJson(record.paramsJson());
    po.setSuccess(record.success());
    po.setDurationMs(record.durationMs());
    po.setRowCount(record.rowCount());
    po.setErrorMessage(record.errorMessage());
    po.setCreateTime(record.createTime());
    return po;
  }

  private long value(Long value) { return value == null ? 0L : Math.max(0L, value); }
  private int value(Integer value) { return value == null ? 0 : Math.max(0, value); }
}
