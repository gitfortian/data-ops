package io.yak.ops.common.enums.lifecycle;

/** 数据生命周期(TTL)枚举集合。 */
public final class LifecycleEnums {

  /** 策略适用范围。 */
  public enum PolicyScope {
    /** 分层默认策略(每层至多一条,内置不可删)。 */
    LAYER_DEFAULT,
    /** 自定义策略(模型绑定引用)。 */
    CUSTOM
  }

  /** 分区粒度。 */
  public enum Granularity {
    DAY,
    MONTH,
    YEAR
  }

  /** 语句方言族(D2:按目标真实类型解析,策略不手选)。 */
  public enum StorageType {
    DORIS,
    PAIMON,
    UNSUPPORTED
  }

  /** 下发流水状态。attempts 达上限后置 EXHAUSTED。 */
  public enum DispatchStatus {
    SUCCESS,
    FAILED,
    RETRYING,
    EXHAUSTED
  }

  /** 下发触发方式。 */
  public enum TriggerType {
    MANUAL,
    BATCH,
    RETRY
  }

  /** 模型 TTL 状态机(D5)。 */
  public enum ModelState {
    UNSET,
    APPLIED,
    DRIFT,
    FAILED
  }

  /** 生效策略来源。 */
  public enum BindingSource {
    OVERRIDE,
    LAYER_DEFAULT,
    LEGACY_LAYER,
    NONE
  }

  /** 策略/模型启停。 */
  public enum Status {
    ENABLED,
    DISABLED
  }

  private LifecycleEnums() {}
}
