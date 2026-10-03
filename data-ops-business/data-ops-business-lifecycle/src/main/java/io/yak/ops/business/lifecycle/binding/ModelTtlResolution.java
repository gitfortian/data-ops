package io.yak.ops.business.lifecycle.binding;

import io.yak.ops.business.lifecycle.generate.TtlStatement;
import io.yak.ops.business.modeling.api.ModelTtlQueryApi.TtlModelSource;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.business.lifecycle.dao.model.LifecycleDispatchRecordPO;
import io.yak.ops.business.lifecycle.dao.model.LifecyclePolicyPO;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.BindingSource;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.ModelState;

/**
 * 模型 TTL 解析结果(所有读路径复用:Tab/预览/下发/监控)。
 *
 * @param virtualPolicy 兜底旧字段合成的只读策略(D1),true 时不可被引用
 * @param previewable 可预览/可下发:有时间分区且层配了数据源+库名,且语句 writable
 * @param notPreviewableReason previewable=false 时给用户的明示原因
 */
public record ModelTtlResolution(
    TtlModelSource model,
    WarehouseLayer layer,
    BindingSource bindingSource,
    LifecyclePolicyPO policy,
    boolean virtualPolicy,
    ModelState state,
    LifecycleDispatchRecordPO lastDispatch,
    TtlStatement statement,
    boolean hasTimePartition,
    boolean previewable,
    String notPreviewableReason) {

  /** 无任何生效策略(UNSET)的解析结果。 */
  public static ModelTtlResolution unset(TtlModelSource model, WarehouseLayer layer) {
    return new ModelTtlResolution(model, layer, BindingSource.NONE, null, false,
        ModelState.UNSET, null, null, false, false,
        "未配置 TTL 策略:可初始化分层默认策略,或在模型上绑定自定义策略");
  }
}
