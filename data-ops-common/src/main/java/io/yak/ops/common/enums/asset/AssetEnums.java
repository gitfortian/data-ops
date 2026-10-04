package io.yak.ops.common.enums.asset;

/** 数据资产(Asset Center)枚举集合(状态机/来源域见同级独立文件)。 */
public final class AssetEnums {

  /** 展示类型,与 LineageAssetType 命名对齐;MANUAL 资产用 DOC。 */
  public enum AssetType {
    TABLE,
    METRIC,
    DATASET,
    DATA_SERVICE,
    DASHBOARD,
    CHART,
    TASK,
    DOC
  }

  /** 对账产生的变更类型。 */
  public enum ChangeType {
    NEW,
    META_CHANGED,
    SOURCE_GONE,
    REAPPEARED
  }

  /** 变更记录处理态。 */
  public enum HandleStatus {
    OPEN,
    CONFIRMED,
    IGNORED
  }

  /** 编目规则类型(二选一目标)。 */
  public enum RuleType {
    DIRECTORY,
    TAG
  }

  /** 健康度等级(纯函数派生,D7)。 */
  public enum HealthGrade {
    A,
    B,
    C,
    D
  }

  /** 360° 详情分区可用性(失败不伪造空)。 */
  public enum SectionStatus {
    OK,
    UNAVAILABLE
  }

  /** 通用启停。 */
  public enum Status {
    ENABLED,
    DISABLED
  }

  private AssetEnums() {}
}
