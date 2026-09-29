package io.yak.ops.common.enums.metadata;

/** 元数据中心枚举集合（落库字面量与本文件 value 一致；改值是数据变更不是改名）。 */
public final class MetadataEnums {

  /** 两条入口：物理采集 / 内部投影登记。 */
  public enum ProviderType {
    HARVESTED,
    REGISTERED
  }

  /** SUSPECT = 触发坍塌熔断，整轮不落任何 GONE。 */
  public enum RunStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    SUSPECT
  }

  public enum TriggerType {
    SCHEDULE,
    MANUAL,
    DRY_RUN
  }

  /** 重试状态机。DEAD 是终态但有出口：对账每轮会把这些实体重新捞回来。 */
  public enum RetryStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    DEAD
  }

  public enum RetryOperation {
    REGISTER,
    UNREGISTER
  }

  /** 变更流水类型（append-only）。 */
  public enum ChangeType {
    NEW,
    CHANGED,
    GONE,
    REVIVED,
    ATTR_CHANGED,
    STATUS_CHANGED,
    LABEL_CHANGED
  }

  /** 任务类型：与差异矩阵/治理缺口一一对应。 */
  public enum TaskType {
    FILL_COMMENT,
    CONFIRM_LABEL,
    FIX_CONFORMANCE,
    REVIEW_GONE
  }

  /** 标签来源。机器与继承一律不可当人工判断用（配合 LabelState）。 */
  public enum LabelType {
    MANUAL,
    AUTOMATED,
    PROPAGATED,
    DERIVED
  }

  /** 机器/继承默认 SUGGESTED，人工点确认才 CONFIRMED。 */
  public enum LabelState {
    SUGGESTED,
    CONFIRMED
  }

  /** 元模型类别：实体类型 vs 字段类型（对齐 OM type.json category）。 */
  public enum TypeCategory {
    ENTITY,
    FIELD
  }

  /** 类型定义状态：永不物理删，历史实体还要能解析。 */
  public enum TypeStatus {
    ACTIVE,
    DEPRECATED
  }

  /** 决定"怎么存、怎么筛"的唯一判别。 */
  public enum BaseType {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    DATE,
    DATETIME,
    ENTITY_REFERENCE,
    JSON,
    ARRAY
  }

  /** 字段匹配方式；落库为小写字面量。 */
  public enum MatchType {
    TEXT("text"),
    EXACT("exact"),
    LIKE("like"),
    RANGE("range");

    private final String value;

    MatchType(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  /**
   * 提槽生成列：与 yak_metadata_asset 上一次性建齐的 7 个 STORED 生成列同名。
   * 加第 8 个槽位是一次 ALGORITHM=COPY 的运维窗口，不是免费迁移，故写死在此。
   *
   * <p>"哪种 {@link BaseType} 能进哪个槽"不在此声明——判据在
   * {@code MetadataSlotRegistry.accepts}，那里同时写着"为什么 BIGINT 槽不收 NUMBER"。
   */
  public enum SlotName {
    S_STR_1("s_str_1"),
    S_STR_2("s_str_2"),
    S_STR_3("s_str_3"),
    S_NUM_1("s_num_1"),
    S_NUM_2("s_num_2"),
    S_BOOL_1("s_bool_1"),
    S_DATE_1("s_date_1");

    private final String column;

    SlotName(String column) {
      this.column = column;
    }

    public String column() {
      return column;
    }

    public static SlotName fromColumn(String column) {
      for (SlotName slot : values()) {
        if (slot.column.equalsIgnoreCase(column)) {
          return slot;
        }
      }
      return null;
    }
  }

  private MetadataEnums() {}
}
