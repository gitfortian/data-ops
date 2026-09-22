package io.yak.ops.business.mdm.api;

import jakarta.validation.constraints.NotNull;

/** Request contracts of the collection landing link (R1). */
public final class MdmCollectApi {

  private MdmCollectApi() {}

  /** 生成落地任务:已确认来源 + 平台库侧的落地目标数据源(登记的数据源 ID)。 */
  public record LinkRequest(
      @NotNull(message = "来源不能为空") Long sourceId,
      @NotNull(message = "落地目标数据源不能为空") Long sinkDatasourceId) {}
}
