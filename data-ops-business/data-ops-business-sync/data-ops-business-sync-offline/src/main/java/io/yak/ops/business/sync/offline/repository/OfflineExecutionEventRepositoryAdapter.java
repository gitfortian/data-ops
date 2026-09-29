package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineExecutionEventDao;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionFinalFailureEvent;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.common.bean.po.sync.offline.OfflineExecutionEventPO;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

/** 执行事件 PO 与领域模型之间的持久化适配器；Project 从所属 Execution 继承。 */
@ConditionalOnOfflineSyncEnabled
@Repository
public class OfflineExecutionEventRepositoryAdapter implements OfflineExecutionEventRepository {
  private final OfflineExecutionEventDao dao;
  private final OfflineJobExecutionRepository executionRepository;
  private final ApplicationEventPublisher eventPublisher;

  @org.springframework.beans.factory.annotation.Autowired
  public OfflineExecutionEventRepositoryAdapter(
      OfflineExecutionEventDao dao,
      OfflineJobExecutionRepository executionRepository,
      ApplicationEventPublisher eventPublisher) {
    this.dao = dao;
    this.executionRepository = executionRepository;
    this.eventPublisher = eventPublisher;
  }

  /** Focused repository tests can retain the pre-notification constructor. */
  public OfflineExecutionEventRepositoryAdapter(
      OfflineExecutionEventDao dao,
      OfflineJobExecutionRepository executionRepository) {
    this(dao, executionRepository, event -> {});
  }

  @Override
  public void append(OfflineExecutionEvent event) {
    if (event == null || event.getExecutionId() == null) {
      throw new IllegalArgumentException("执行事件缺少 executionId");
    }
    OfflineJobExecution execution = requireExecution(event.getExecutionId());
    OfflineExecutionEventPO po = new OfflineExecutionEventPO();
    BeanUtils.copyProperties(event, po);
    if (!dao.insert(po)) throw new IllegalStateException("记录离线同步执行事件失败");
    event.setId(po.getId());
    publishFinalFailure(event, execution);
  }

  @Override
  public List<OfflineExecutionEvent> list(Long executionId) {
    requireExecution(executionId);
    return dao.selectByExecutionId(executionId).stream().map(this::toDomain).toList();
  }

  @Override
  public List<OfflineExecutionEvent> listAfter(Long executionId, long afterId, int limit) {
    requireExecution(executionId);
    if (afterId < 0L) throw new IllegalArgumentException("事件游标不能为负数");
    if (limit < 1 || limit > 1000) throw new IllegalArgumentException("日志 limit 必须在 1 到 1000 之间");
    return dao.selectAfter(executionId, afterId, limit).stream().map(this::toDomain).toList();
  }

  private OfflineJobExecution requireExecution(Long executionId) {
    if (executionId == null || executionId <= 0L) {
      throw new IllegalArgumentException("executionId 必须大于 0");
    }
    return executionRepository.findById(executionId)
        .orElseThrow(() -> new IllegalArgumentException("离线同步执行实例不存在：" + executionId));
  }

  private void publishFinalFailure(
      OfflineExecutionEvent event,
      OfflineJobExecution execution) {
    if (!"FAILED".equalsIgnoreCase(event.getToStatus())
        || "FAILED".equalsIgnoreCase(event.getFromStatus())
        || execution.getNextRetryTime() != null) {
      return;
    }
    eventPublisher.publishEvent(
        new OfflineExecutionFinalFailureEvent(
            execution.getId(),
            execution.getJobDefinitionId(),
            execution.getErrorMessage()));
  }

  private OfflineExecutionEvent toDomain(OfflineExecutionEventPO po) {
    OfflineExecutionEvent value = new OfflineExecutionEvent();
    BeanUtils.copyProperties(po, value);
    return value;
  }
}
