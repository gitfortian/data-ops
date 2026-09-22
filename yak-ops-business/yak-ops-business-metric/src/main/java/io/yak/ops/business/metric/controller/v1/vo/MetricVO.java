package io.yak.ops.business.metric.controller.v1.vo;

import io.yak.ops.business.metric.domain.Metric;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 指标视图对象。 */
@Data
public class MetricVO {

  private Long id;
  private String metricCode;
  private String metricName;
  private Long domainId;
  private String domainName;
  private Long processId;
  private String processName;
  private String metricType;
  private Long caliberId;
  private String caliberName;
  private String calRule;
  private String measureExpr;
  private String filterExpr;
  private String dimModelIds;
  private Long refMetricId;
  private String refMetricName;
  private String dimConstraint;
  private String qualifiersJson;
  private Long modelId;
  private String modelName;
  private String statDimensions;
  private String statPeriod;
  private Long unitId;
  private String unitName;
  private String businessDesc;
  private String owner;
  private String status;
  private int version;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private List<CompositionVO> compositions;

  public static MetricVO from(Metric m) {
    MetricVO vo = new MetricVO();
    vo.setId(m.id());
    vo.setMetricCode(m.metricCode());
    vo.setMetricName(m.metricName());
    vo.setDomainId(m.domainId());
    vo.setProcessId(m.processId());
    vo.setMetricType(m.metricType() != null ? m.metricType().name() : null);
    vo.setCaliberId(m.caliberId());
    vo.setCalRule(m.calRule());
    vo.setMeasureExpr(m.measureExpr());
    vo.setFilterExpr(m.filterExpr());
    vo.setDimModelIds(m.dimModelIds());
    vo.setRefMetricId(m.refMetricId());
    vo.setDimConstraint(m.dimConstraint());
    vo.setQualifiersJson(m.qualifiersJson());
    vo.setModelId(m.modelId());
    vo.setStatDimensions(m.statDimensions());
    vo.setStatPeriod(m.statPeriod() != null ? m.statPeriod().name() : null);
    vo.setUnitId(m.unitId());
    vo.setBusinessDesc(m.businessDesc());
    vo.setOwner(m.owner());
    vo.setStatus(m.status() != null ? m.status().name() : null);
    vo.setVersion(m.version());
    vo.setCreatedBy(m.createdBy());
    vo.setUpdatedBy(m.updatedBy());
    vo.setCreateTime(m.createTime());
    vo.setUpdateTime(m.updateTime());
    return vo;
  }

  @Data
  public static class CompositionVO {
    private Long id;
    private Long subMetricId;
    private String subMetricCode;
    private String subMetricName;
    private String operator;
    private String expression;
    private int sortOrder;
  }
}
