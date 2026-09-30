package io.yak.ops.business.metric.repository;

import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.common.bean.po.metric.MetricDependencyPO;
import java.util.List;

/** 指标血缘登记仓储接口。 */
public interface MetricDependencyRepository {

  /**
   * 全量替换该指标的血缘登记行。
   * 依赖清单由服务层解析(含引用时刻的 code/version 快照,供影响分析比对),仓储只负责落库。
   */
  void syncDependencies(Metric metric, List<DependencySpec> dependencies);

  void deleteByMetric(Long metricId);

  List<MetricDependencyPO> listByMetric(Long metricId);

  /** 反查登记了该上游(MODEL/CALIBER/UNIT/REF_METRIC/COMPOSITION)的依赖行,供影响分析反向视图。 */
  List<MetricDependencyPO> listByDependency(String dependencyType, Long dependencyId);

  long countByDependency(String dependencyType, Long dependencyId);

  /**
   * 一条血缘登记。
   *
   * @param dependencyType MODEL/CALIBER/UNIT/REF_METRIC/COMPOSITION
   * @param dependencyVersion 引用时刻的上游版本号快照,解析不到时为 null
   */
  record DependencySpec(
      String dependencyType, Long dependencyId,
      String dependencyCode, Integer dependencyVersion) {}
}
