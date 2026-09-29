package io.yak.ops.common.enums.metadata;

/**
 * 实体治理状态：借 OM 的 EntityStatus 7 值与"默认 Unprocessed"语义（蒸馏 §1.1）。
 *
 * <p>值面保留 OM 的字面量（含空格的两个值也照原样当契约用），因为
 * <b>Unprocessed 与 Rejected 的区分正是"没人看过"和"看过但不合格"</b>——治理度量必须能分这两个。
 */
public enum MetadataEntityStatus {

  DRAFT("Draft"),
  IN_REVIEW("In Review"),
  APPROVED("Approved"),
  ARCHIVED("Archived"),
  DEPRECATED("Deprecated"),
  REJECTED("Rejected"),
  /** 默认值：新登记、无人处置过。 */
  UNPROCESSED("Unprocessed");

  private final String value;

  MetadataEntityStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static MetadataEntityStatus fromValue(String value) {
    for (MetadataEntityStatus status : values()) {
      if (status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
        return status;
      }
    }
    return null;
  }
}
