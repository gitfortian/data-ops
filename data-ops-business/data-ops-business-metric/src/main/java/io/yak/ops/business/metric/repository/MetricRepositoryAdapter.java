package io.yak.ops.business.metric.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.metric.dao.mapper.MetricMapper;
import io.yak.ops.business.metric.dao.mapper.MetricTagRelMapper;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricStatus;
import io.yak.ops.business.metric.domain.MetricType;
import io.yak.ops.business.metric.domain.StatPeriod;
import io.yak.ops.business.metric.dao.model.MetricPO;
import io.yak.ops.business.metric.dao.model.MetricTagRelPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 指标主表仓储适配。 */
@Repository
public class MetricRepositoryAdapter implements MetricRepository {

  private final MetricMapper mapper;
  private final MetricTagRelMapper tagRelMapper;
  private final CurrentProject currentProject;

  public MetricRepositoryAdapter(
      MetricMapper mapper, MetricTagRelMapper tagRelMapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.tagRelMapper = tagRelMapper;
    this.currentProject = currentProject;
  }

  @Override
  public Metric insert(Metric metric, String operator) {
    Long projectId = requiredProjectId();
    LocalDateTime now = LocalDateTime.now();
    MetricPO po = toPo(metric);
    po.setProjectId(projectId);
    po.setCreatedBy(operator);
    po.setUpdatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return metric.withPersisted(po.getId(), operator, now);
  }

  @Override
  public Optional<Metric> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<MetricPO>()
                    .eq(MetricPO::getId, id)
                    .eq(MetricPO::getProjectId, projectId)))
        .map(MetricRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<Metric> findByCode(String metricCode) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<MetricPO>()
                    .eq(MetricPO::getMetricCode, metricCode)
                    .eq(MetricPO::getProjectId, projectId)))
        .map(MetricRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByCode(String metricCode) {
    Long projectId = requiredProjectId();
    return mapper.exists(
        new LambdaQueryWrapper<MetricPO>()
            .eq(MetricPO::getMetricCode, metricCode)
            .eq(MetricPO::getProjectId, projectId));
  }

  @Override
  public boolean update(Metric metric) {
    Long projectId = requiredProjectId();
    MetricPO po = toPo(metric);
    // This is a full editable-definition replacement, not a sparse patch.
    // MyBatis entity update skips null values: DERIVED → ATOMIC, for example,
    // must actually clear the old ref_metric_id and qualifier/model references.
    return mapper.update(null,
        new LambdaUpdateWrapper<MetricPO>()
            .set(MetricPO::getMetricName, po.getMetricName())
            .set(MetricPO::getDomainId, po.getDomainId())
            .set(MetricPO::getProcessId, po.getProcessId())
            .set(MetricPO::getMetricType, po.getMetricType())
            .set(MetricPO::getCaliberId, po.getCaliberId())
            .set(MetricPO::getCalRule, po.getCalRule())
            .set(MetricPO::getMeasureExpr, po.getMeasureExpr())
            .set(MetricPO::getFilterExpr, po.getFilterExpr())
            .set(MetricPO::getDimModelIds, po.getDimModelIds())
            .set(MetricPO::getRefMetricId, po.getRefMetricId())
            .set(MetricPO::getDimConstraint, po.getDimConstraint())
            .set(MetricPO::getQualifiersJson, po.getQualifiersJson())
            .set(MetricPO::getModelId, po.getModelId())
            .set(MetricPO::getStatDimensions, po.getStatDimensions())
            .set(MetricPO::getStatPeriod, po.getStatPeriod())
            .set(MetricPO::getUnitId, po.getUnitId())
            .set(MetricPO::getBusinessDesc, po.getBusinessDesc())
            .set(MetricPO::getOwner, po.getOwner())
            .set(MetricPO::getStatus, po.getStatus())
            .set(MetricPO::getVersion, po.getVersion())
            .set(MetricPO::getUpdatedBy, po.getUpdatedBy())
            .set(MetricPO::getUpdateTime, po.getUpdateTime())
            .eq(MetricPO::getId, metric.id())
            .eq(MetricPO::getProjectId, projectId)
            .eq(MetricPO::getVersion, metric.version() - 1)) == 1;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
        new LambdaQueryWrapper<MetricPO>()
            .eq(MetricPO::getId, id)
            .eq(MetricPO::getProjectId, projectId)) > 0;
  }

  @Override
  public PageData<Metric> page(int pageNo, int pageSize, Long domainId, Long processId,
      String metricType, String status, String keyword, String owner, List<Long> tagIds) {
    Long projectId = requiredProjectId();
    List<Long> tagMetricIds = null;
    if (tagIds != null && !tagIds.isEmpty()) {
      tagMetricIds = tagRelMapper.selectList(new LambdaQueryWrapper<MetricTagRelPO>()
              .eq(MetricTagRelPO::getProjectId, projectId)
              .in(MetricTagRelPO::getTagId, tagIds))
          .stream().map(MetricTagRelPO::getMetricId).distinct().toList();
      if (tagMetricIds.isEmpty()) {
        return new PageData<>(List.of(), 0, 0, pageNo, pageSize);
      }
    }
    LambdaQueryWrapper<MetricPO> wrapper = new LambdaQueryWrapper<MetricPO>()
        .eq(MetricPO::getProjectId, projectId)
        .eq(domainId != null, MetricPO::getDomainId, domainId)
        .eq(processId != null, MetricPO::getProcessId, processId)
        .eq(StringUtils.hasText(metricType), MetricPO::getMetricType, metricType)
        .eq(StringUtils.hasText(status), MetricPO::getStatus, status)
        .eq(StringUtils.hasText(owner), MetricPO::getOwner, owner)
        .in(tagMetricIds != null, MetricPO::getId, tagMetricIds)
        .and(StringUtils.hasText(keyword), w -> w
            .like(MetricPO::getMetricCode, keyword)
            .or()
            .like(MetricPO::getMetricName, keyword))
        .orderByDesc(MetricPO::getUpdateTime);
    Page<MetricPO> page = mapper.selectPage(
        new Page<>(pageNo, pageSize), wrapper);
    return new PageData<>(
        page.getRecords().stream().map(MetricRepositoryAdapter::toDomain).toList(),
        page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
  }

  @Override
  public long count() {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
        new LambdaQueryWrapper<MetricPO>().eq(MetricPO::getProjectId, projectId));
  }

  @Override
  public long countByDomain(Long domainId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
        new LambdaQueryWrapper<MetricPO>()
            .eq(MetricPO::getProjectId, projectId)
            .eq(MetricPO::getDomainId, domainId));
  }

  @Override
  public long countByType(String metricType) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
        new LambdaQueryWrapper<MetricPO>()
            .eq(MetricPO::getProjectId, projectId)
            .eq(MetricPO::getMetricType, metricType));
  }

  @Override
  public List<Metric> listByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    Long projectId = requiredProjectId();
    return mapper.selectList(
        new LambdaQueryWrapper<MetricPO>()
            .in(MetricPO::getId, ids)
            .eq(MetricPO::getProjectId, projectId))
        .stream().map(MetricRepositoryAdapter::toDomain).toList();
  }

  @Override
  public List<Metric> listReferring(Long refMetricId) {
    if (refMetricId == null) return List.of();
    Long projectId = requiredProjectId();
    return mapper.selectList(
        new LambdaQueryWrapper<MetricPO>()
            .eq(MetricPO::getProjectId, projectId)
            .eq(MetricPO::getRefMetricId, refMetricId))
        .stream().map(MetricRepositoryAdapter::toDomain).toList();
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private static MetricPO toPo(Metric m) {
    MetricPO po = new MetricPO();
    po.setId(m.id());
    po.setMetricCode(m.metricCode());
    po.setMetricName(m.metricName());
    po.setDomainId(m.domainId());
    po.setProcessId(m.processId());
    po.setMetricType(m.metricType() != null ? m.metricType().name() : null);
    po.setCaliberId(m.caliberId());
    po.setCalRule(m.calRule());
    po.setMeasureExpr(m.measureExpr());
    po.setFilterExpr(m.filterExpr());
    po.setDimModelIds(m.dimModelIds());
    po.setRefMetricId(m.refMetricId());
    po.setDimConstraint(m.dimConstraint());
    po.setQualifiersJson(m.qualifiersJson());
    po.setModelId(m.modelId());
    po.setStatDimensions(m.statDimensions());
    po.setStatPeriod(m.statPeriod() != null ? m.statPeriod().name() : null);
    po.setUnitId(m.unitId());
    po.setBusinessDesc(m.businessDesc());
    po.setOwner(m.owner());
    po.setStatus(m.status() != null ? m.status().name() : null);
    po.setVersion(m.version());
    po.setCreatedBy(m.createdBy());
    po.setUpdatedBy(m.updatedBy());
    po.setCreateTime(m.createTime());
    po.setUpdateTime(m.updateTime());
    return po;
  }

  static Metric toDomain(MetricPO po) {
    return new Metric(
        po.getId(),
        po.getMetricCode(),
        po.getMetricName(),
        po.getDomainId(),
        po.getProcessId(),
        po.getMetricType() != null ? MetricType.valueOf(po.getMetricType()) : null,
        po.getCaliberId(),
        po.getCalRule(),
        po.getMeasureExpr(),
        po.getFilterExpr(),
        po.getDimModelIds(),
        po.getRefMetricId(),
        po.getDimConstraint(),
        po.getQualifiersJson(),
        po.getModelId(),
        po.getStatDimensions(),
        po.getStatPeriod() != null ? StatPeriod.valueOf(po.getStatPeriod()) : null,
        po.getUnitId(),
        po.getBusinessDesc(),
        po.getOwner(),
        po.getStatus() != null ? MetricStatus.valueOf(po.getStatus()) : null,
        po.getVersion() != null ? po.getVersion() : 1,
        po.getCreatedBy(),
        po.getUpdatedBy(),
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
