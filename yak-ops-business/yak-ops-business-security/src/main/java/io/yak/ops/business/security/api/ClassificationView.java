package io.yak.ops.business.security.api;

/** 某数据对象的安全分级视图(供下游读等级/分类)。 */
public record ClassificationView(
    String objectKey,
    Long levelId,
    String levelCode,
    String levelName,
    Integer levelRank,
    Long categoryId,
    String categoryCode,
    String categoryName) {

  /** 敏感判定:等级序位达到或超过门限。 */
  public boolean sensitive(int thresholdRank) {
    return levelRank != null && levelRank >= thresholdRank;
  }
}
