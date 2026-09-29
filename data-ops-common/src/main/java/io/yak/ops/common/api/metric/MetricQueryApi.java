package io.yak.ops.common.api.metric;

import java.util.Collection;
import java.util.List;

/**
 * 指标只读 SPI:建模(DWS/ADS 反推)消费(ticket 60)。
 *
 * <p>SPI 放 common 是因为 metric 模块依赖 modeling,若放 modeling 会形成
 * modeling→metric→modeling 循环依赖。实现由 data-ops-business-metric 提供;
 * 未部署 metric 时建模侧按「无指标」降级,不阻断派生。
 */
public interface MetricQueryApi {

  /** 按 id 批量取启用指标(只读视图,供 DWS/ADS 反推);不存在/停用不返回。 */
  List<MetricQueryView> listEnabledByIds(Collection<Long> ids);
}
