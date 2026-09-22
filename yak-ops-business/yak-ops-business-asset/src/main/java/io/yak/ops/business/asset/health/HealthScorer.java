package io.yak.ops.business.asset.health;

import java.util.ArrayList;
import java.util.List;

/**
 * 健康度纯函数评分(requirement §3.8):完整性 40 / 可信度 40 / 活跃度 20。
 * N/A 项剔出分母(Σ实得/Σ适用满分×100),依赖数据缺失按 0 分并标"数据不可用",不伪造。
 */
public final class HealthScorer {

  /** 单项输入的适用性。 */
  public enum Availability {
    /** 适用且有数据。 */
    OK,
    /** 结构性不适用(如指标类无质量监控项),剔出分母。 */
    NOT_APPLICABLE,
    /** 适用但上游数据不可用,计 0 分并进分母。 */
    UNAVAILABLE
  }

  /**
   * @param fieldCommentRatio 0..1 字段注释覆盖率(AVAIL OK 时生效)
   * @param qualityRatio      0..1 质量监控覆盖×通过率
   * @param lineageRegistered 血缘域是否已登记该资产
   * @param securityClassified 是否已有安全定级
   * @param openChangesPending 是否有 OPEN 盘点变更待确认
   * @param downstreamCount   1 跳下游引用数
   * @param daysSinceUpdate   距源最近变更天数;null=不可得
   * @param viewCount30d      近 30 天浏览
   */
  public record HealthInputs(
      boolean hasDescription,
      boolean hasOwner,
      boolean inDirectory,
      int tagCount,
      Availability fieldCommentAvail,
      double fieldCommentRatio,
      Availability qualityAvail,
      double qualityRatio,
      Availability lineageAvail,
      boolean lineageRegistered,
      Availability securityAvail,
      boolean securityClassified,
      boolean openChangesPending,
      Availability downstreamAvail,
      int downstreamCount,
      Availability freshnessAvail,
      Long daysSinceUpdate,
      int viewCount30d) {

    public static HealthInputs unavailableData() {
      return new HealthInputs(false, false, false, 0,
          Availability.UNAVAILABLE, 0, Availability.UNAVAILABLE, 0,
          Availability.UNAVAILABLE, false, Availability.UNAVAILABLE, false,
          false, Availability.UNAVAILABLE, 0, Availability.UNAVAILABLE, null, 0);
    }
  }

  /** 单项得分明细(health_detail JSON 元素)。 */
  public record ItemScore(
      String key, String dimension, int max, int points, String state, String gap) {}

  public record HealthResult(int score, String grade, List<ItemScore> items) {}

  private HealthScorer() {}

  public static HealthResult score(HealthInputs in) {
    List<ItemScore> items = new ArrayList<>();
    items.add(binary("description", "COMPLETENESS", 10, in.hasDescription(), "缺描述"));
    items.add(binary("owner", "COMPLETENESS", 10, in.hasOwner(), "缺负责人"));
    items.add(binary("directory", "COMPLETENESS", 5, in.inDirectory(), "未入目录"));
    items.add(scored("tags", "COMPLETENESS", 5,
        in.tagCount() >= 3 ? 5 : in.tagCount() >= 1 ? 3 : 0,
        in.tagCount() > 0 ? null : "无业务标签"));
    items.add(ratioItem("field_comments", "COMPLETENESS", 10,
        in.fieldCommentAvail(), in.fieldCommentRatio(), "字段注释覆盖率不足"));
    items.add(ratioItem("quality", "TRUST", 15,
        in.qualityAvail(), in.qualityRatio(), "质量监控缺失或通过率低"));
    items.add(availabilityBinary("lineage", "TRUST", 10, in.lineageAvail(),
        in.lineageRegistered(), "血缘未登记"));
    items.add(availabilityBinary("security", "TRUST", 10, in.securityAvail(),
        in.securityClassified(), "安全未定级"));
    items.add(binary("changes_confirmed", "TRUST", 5, !in.openChangesPending(),
        "有源变更待确认"));
    items.add(scored("views", "ACTIVITY", 10,
        (int) Math.round(10 * Math.min(1.0,
            Math.log(1 + Math.max(0, in.viewCount30d())) / Math.log(31))),
        in.viewCount30d() > 0 ? null : "近 30 天无浏览"));
    items.add(freshness(in));
    items.add(downstream(in));

    int denominator = 0;
    int earned = 0;
    for (ItemScore item : items) {
      if ("NOT_APPLICABLE".equals(item.state())) {
        continue;
      }
      denominator += item.max();
      earned += item.points();
    }
    int score = denominator == 0 ? 100 : (int) Math.round(earned * 100.0 / denominator);
    return new HealthResult(score, grade(score), items);
  }

  /** A ≥85 / B ≥70 / C ≥55 / D <55。 */
  public static String grade(int score) {
    if (score >= 85) {
      return "A";
    }
    if (score >= 70) {
      return "B";
    }
    if (score >= 55) {
      return "C";
    }
    return "D";
  }

  private static ItemScore binary(String key, String dimension, int max, boolean met, String gap) {
    return new ItemScore(key, dimension, max, met ? max : 0, "OK", met ? null : gap);
  }

  private static ItemScore scored(String key, String dimension, int max, int points, String gap) {
    return new ItemScore(key, dimension, max, Math.min(max, points), "OK",
        points >= max ? null : gap);
  }

  private static ItemScore ratioItem(String key, String dimension, int max,
      Availability avail, double ratio, String gap) {
    if (avail == Availability.NOT_APPLICABLE) {
      return new ItemScore(key, dimension, max, 0, "NOT_APPLICABLE", null);
    }
    if (avail == Availability.UNAVAILABLE) {
      return new ItemScore(key, dimension, max, 0, "UNAVAILABLE", "数据不可用");
    }
    int points = (int) Math.round(max * Math.min(1.0, Math.max(0.0, ratio)));
    return new ItemScore(key, dimension, max, points, "OK", points >= max ? null : gap);
  }

  private static ItemScore availabilityBinary(String key, String dimension, int max,
      Availability avail, boolean met, String gap) {
    if (avail == Availability.NOT_APPLICABLE) {
      return new ItemScore(key, dimension, max, 0, "NOT_APPLICABLE", null);
    }
    if (avail == Availability.UNAVAILABLE) {
      return new ItemScore(key, dimension, max, 0, "UNAVAILABLE", "数据不可用");
    }
    return new ItemScore(key, dimension, max, met ? max : 0, "OK", met ? null : gap);
  }

  private static ItemScore freshness(HealthInputs in) {
    if (in.freshnessAvail() == Availability.UNAVAILABLE || in.daysSinceUpdate() == null) {
      return new ItemScore("freshness", "ACTIVITY", 5, 0, "UNAVAILABLE", "数据不可用");
    }
    long days = in.daysSinceUpdate();
    int points = days <= 30 ? 5 : days <= 90 ? 3 : days <= 180 ? 1 : 0;
    return new ItemScore("freshness", "ACTIVITY", 5, points, "OK",
        points == 5 ? null : "源超 " + days + " 天未更新");
  }

  private static ItemScore downstream(HealthInputs in) {
    if (in.downstreamAvail() == Availability.NOT_APPLICABLE) {
      return new ItemScore("downstream", "ACTIVITY", 5, 0, "NOT_APPLICABLE", null);
    }
    if (in.downstreamAvail() == Availability.UNAVAILABLE) {
      return new ItemScore("downstream", "ACTIVITY", 5, 0, "UNAVAILABLE", "数据不可用");
    }
    int points = Math.min(5, Math.max(0, in.downstreamCount()));
    return new ItemScore("downstream", "ACTIVITY", 5, points, "OK",
        points == 5 ? null : "下游引用不足");
  }
}
