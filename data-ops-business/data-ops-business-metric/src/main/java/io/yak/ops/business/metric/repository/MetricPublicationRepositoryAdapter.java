package io.yak.ops.business.metric.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.metric.dao.mapper.MetricActivePublicationMapper;
import io.yak.ops.business.metric.dao.mapper.MetricMapper;
import io.yak.ops.business.metric.dao.mapper.MetricPublicationEventMapper;
import io.yak.ops.business.metric.dao.model.MetricActivePublicationPO;
import io.yak.ops.business.metric.dao.model.MetricPO;
import io.yak.ops.business.metric.dao.model.MetricPublicationEventPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Project-scoped publication persistence. Publication events are append-only. */
@Repository
public class MetricPublicationRepositoryAdapter implements MetricPublicationRepository {

  private final MetricPublicationEventMapper eventMapper;
  private final MetricActivePublicationMapper activeMapper;
  private final MetricMapper metricMapper;
  private final CurrentProject currentProject;

  public MetricPublicationRepositoryAdapter(
      MetricPublicationEventMapper eventMapper,
      MetricActivePublicationMapper activeMapper,
      MetricMapper metricMapper,
      CurrentProject currentProject) {
    this.eventMapper = eventMapper;
    this.activeMapper = activeMapper;
    this.metricMapper = metricMapper;
    this.currentProject = currentProject;
  }

  @Override
  public Integer lockCurrentMetricVersion(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    MetricPO metric = metricMapper.selectOne(new LambdaQueryWrapper<MetricPO>()
        .eq(MetricPO::getProjectId, projectId)
        .eq(MetricPO::getId, metricId)
        .last("FOR UPDATE"));
    return metric == null ? null : metric.getVersion();
  }

  @Override
  public MetricPublicationEventPO appendEvent(MetricPublicationEventPO event) {
    event.setProjectId(currentProject.requireProjectId());
    if (event.getActedAt() == null) {
      event.setActedAt(LocalDateTime.now());
    }
    eventMapper.insert(event);
    return event;
  }

  @Override
  public MetricPublicationEventPO findEvent(Long eventId) {
    if (eventId == null) return null;
    Long projectId = currentProject.requireProjectId();
    return eventMapper.selectOne(new LambdaQueryWrapper<MetricPublicationEventPO>()
        .eq(MetricPublicationEventPO::getProjectId, projectId)
        .eq(MetricPublicationEventPO::getId, eventId));
  }

  @Override
  public List<MetricPublicationEventPO> listEvents(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    return eventMapper.selectList(new LambdaQueryWrapper<MetricPublicationEventPO>()
        .eq(MetricPublicationEventPO::getProjectId, projectId)
        .eq(MetricPublicationEventPO::getMetricId, metricId)
        .orderByDesc(MetricPublicationEventPO::getActedAt)
        .orderByDesc(MetricPublicationEventPO::getId));
  }

  @Override
  public boolean hasEvents(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    return eventMapper.selectCount(new LambdaQueryWrapper<MetricPublicationEventPO>()
        .eq(MetricPublicationEventPO::getProjectId, projectId)
        .eq(MetricPublicationEventPO::getMetricId, metricId)) > 0;
  }

  @Override
  public MetricActivePublicationPO findActive(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    return activeMapper.selectOne(new LambdaQueryWrapper<MetricActivePublicationPO>()
        .eq(MetricActivePublicationPO::getProjectId, projectId)
        .eq(MetricActivePublicationPO::getMetricId, metricId));
  }

  @Override
  public List<MetricActivePublicationPO> listActive() {
    Long projectId = currentProject.requireProjectId();
    return activeMapper.selectList(new LambdaQueryWrapper<MetricActivePublicationPO>()
        .eq(MetricActivePublicationPO::getProjectId, projectId)
        .orderByAsc(MetricActivePublicationPO::getMetricId));
  }

  @Override
  public List<MetricActivePublicationPO> listActiveByMetricIds(List<Long> metricIds) {
    if (metricIds == null || metricIds.isEmpty()) return List.of();
    Long projectId = currentProject.requireProjectId();
    return activeMapper.selectList(new LambdaQueryWrapper<MetricActivePublicationPO>()
        .eq(MetricActivePublicationPO::getProjectId, projectId)
        .in(MetricActivePublicationPO::getMetricId, metricIds)
        .orderByAsc(MetricActivePublicationPO::getMetricId));
  }

  @Override
  public MetricActivePublicationPO findActiveForUpdate(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    return activeMapper.selectOne(new LambdaQueryWrapper<MetricActivePublicationPO>()
        .eq(MetricActivePublicationPO::getProjectId, projectId)
        .eq(MetricActivePublicationPO::getMetricId, metricId)
        .last("FOR UPDATE"));
  }

  @Override
  public void replaceActive(MetricActivePublicationPO pointer) {
    pointer.setProjectId(currentProject.requireProjectId());
    activeMapper.upsert(pointer);
  }

  @Override
  public boolean clearActive(Long metricId, Long expectedPublicationEventId) {
    Long projectId = currentProject.requireProjectId();
    return activeMapper.deleteExpected(projectId, metricId, expectedPublicationEventId) > 0;
  }
}
