package io.yak.ops.common.enums.lifecycle;

import io.yak.framework.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 数据生命周期模块错误码(47001+ 段)。 */
@Getter
@RequiredArgsConstructor
public enum LifecycleErrorCode implements ErrorCode {

  POLICY_NOT_FOUND(47001, "TTL 策略不存在"),
  DUPLICATE_POLICY_CODE(47002, "策略编码已存在"),
  LAYER_DEFAULT_EXISTS(47003, "该分层已有默认策略,请直接编辑"),
  POLICY_REFERENCED(47004, "策略已被模型绑定引用,无法删除"),
  INVALID_RETENTION(47005, "保留期不合法,需满足 热≤冷≤销毁"),
  NO_TIME_PARTITION(47006, "该表无时间分区,不适用 TTL"),
  STORAGE_NOT_DISPATCHABLE(47007, "目标存储不支持平台下发,请复制语句手工执行"),
  DISPATCH_FAILED(47008, "TTL 语句下发失败"),
  CONFIRM_EXPIRED(47009, "预览确认已过期,请重新预览后再下发"),
  BINDING_NOT_FOUND(47010, "模型生命周期绑定不存在"),
  LAYER_CONFIG_MISSING(47011, "分层未配置库名或数据源,无法定位目标表"),
  MODEL_NOT_FOUND(47012, "模型不存在"),
  POLICY_VERSION_NOT_FOUND(47013, "策略版本不存在");

  private final Integer code;
  private final String message;
}
