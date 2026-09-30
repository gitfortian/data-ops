package io.yak.ops.business.semantic.usage;

import io.yak.ops.business.semantic.api.StandardUsageApi;
import io.yak.ops.business.semantic.dao.mapper.SemanticStandardUsageMapper;
import io.yak.ops.common.bean.po.semantic.SemanticStandardUsagePO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default usage SPI implementation: plain project-scoped event inserts plus a
 * server-side count aggregation (no unbounded list-then-count).
 */
@Component
@Slf4j
public class StandardUsageService implements StandardUsageApi {

  private final SemanticStandardUsageMapper mapper;
  private final CurrentProject currentProject;

  public StandardUsageService(SemanticStandardUsageMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void record(UsageEvent event) {
    SemanticStandardUsagePO po = new SemanticStandardUsagePO();
    po.setProjectId(currentProject.requireProjectId());
    po.setStandardId(event.standardId());
    po.setUsageType(event.usageType());
    po.setScene(event.scene());
    po.setModelRef(event.modelRef());
    po.setOperatedBy(event.operatedBy());
    po.setCreateTime(LocalDateTime.now());
    mapper.insert(po);
  }

  @Override
  public UsageSummary summary(Long standardId) {
    Long projectId = currentProject.requireProjectId();
    Map<String, Object> counts = mapper.selectSummary(projectId, standardId);
    long apply = count(counts.get("applyCount"));
    long bypass = count(counts.get("bypassCount"));
    return new UsageSummary(standardId, apply, bypass);
  }

  private static long count(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  /** 供上报方的安全包装:吞异常并告警(推送式 fail-open 语义)。 */
  public void recordQuietly(UsageEvent event) {
    try {
      record(event);
    } catch (RuntimeException exception) {
      log.warn("usage report failed (ignored): std={}, type={}", event.standardId(),
          event.usageType(), exception);
    }
  }
}
