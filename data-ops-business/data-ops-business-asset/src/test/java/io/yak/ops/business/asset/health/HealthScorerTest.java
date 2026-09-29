package io.yak.ops.business.asset.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.asset.health.HealthScorer.Availability;
import io.yak.ops.business.asset.health.HealthScorer.HealthInputs;
import io.yak.ops.business.asset.health.HealthScorer.HealthResult;
import io.yak.ops.business.asset.health.HealthScorer.ItemScore;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 健康度纯函数单测(ticket 98):满分路径、N/A 归一、UNAVAILABLE 进分母、档位边界。 */
class HealthScorerTest {

  private static HealthInputs perfect() {
    return new HealthInputs(true, true, true, 3,
        Availability.OK, 1.0, Availability.OK, 1.0,
        Availability.OK, true, Availability.OK, true,
        false, Availability.OK, 5,
        Availability.OK, 3L, 30);
  }

  private static Map<String, ItemScore> byKey(HealthResult result) {
    return result.items().stream()
        .collect(Collectors.toMap(ItemScore::key, Function.identity()));
  }

  @Test
  void allApplicableMetScoresA100() {
    HealthResult result = HealthScorer.score(perfect());
    assertEquals(100, result.score());
    assertEquals("A", result.grade());
    assertTrue(result.items().stream().allMatch(i -> "OK".equals(i.state())));
    assertTrue(result.items().stream().allMatch(i -> i.points() == i.max()));
  }

  @Test
  void notApplicableItemsAreExcludedFromDenominator() {
    // 指标类:字段注释/质量/定级/下游均 N/A,其余全满足 → 100 分,不被结构性拖垮
    HealthResult result = HealthScorer.score(new HealthInputs(true, true, true, 3,
        Availability.NOT_APPLICABLE, 0, Availability.NOT_APPLICABLE, 0,
        Availability.OK, true, Availability.NOT_APPLICABLE, false,
        false, Availability.NOT_APPLICABLE, 0,
        Availability.OK, 3L, 30));
    assertEquals(100, result.score());
    Map<String, ItemScore> items = byKey(result);
    assertEquals("NOT_APPLICABLE", items.get("quality").state());
    assertEquals("NOT_APPLICABLE", items.get("security").state());
    // N/A 项仍在明细中展示(供前端"不适用"渲染)
    assertEquals(12, items.size());
  }

  @Test
  void unavailableScoresZeroButStaysInDenominator() {
    // 全 UNAVAILABLE + 布尔项未满足:仅剩"变更已确认"布尔项得分 → 低分 D(缺口可见,不伪造)
    HealthResult result = HealthScorer.score(HealthInputs.unavailableData());
    assertEquals("D", result.grade());
    Map<String, ItemScore> items = byKey(result);
    assertEquals("数据不可用", items.get("field_comments").gap());
    assertEquals("数据不可用", items.get("quality").gap());
    assertEquals(0, items.get("field_comments").points());
    // UNAVAILABLE 进分母:12 项全部计分母,布尔项仅 changes_confirmed 得分
    int denominator = result.items().stream().mapToInt(ItemScore::max).sum();
    assertEquals(100, denominator);
    assertEquals(100, result.items().stream()
        .filter(i -> !"NOT_APPLICABLE".equals(i.state()))
        .mapToInt(ItemScore::max).sum());
    assertEquals(5, result.score());
  }

  @Test
  void gradeBoundaries() {
    assertEquals("A", HealthScorer.grade(85));
    assertEquals("B", HealthScorer.grade(84));
    assertEquals("B", HealthScorer.grade(70));
    assertEquals("C", HealthScorer.grade(69));
    assertEquals("C", HealthScorer.grade(55));
    assertEquals("D", HealthScorer.grade(54));
  }

  @Test
  void tagAndFreshnessAndDownstreamTiers() {
    Map<String, ItemScore> one = byKey(HealthScorer.score(tierInput(1, 60, 0)));
    assertEquals(3, one.get("tags").points());
    assertEquals(3, one.get("freshness").points());
    assertEquals(0, one.get("downstream").points());
    assertTrue(one.get("downstream").gap() != null);

    Map<String, ItemScore> full = byKey(HealthScorer.score(tierInput(3, 150, 9)));
    assertEquals(5, full.get("tags").points());
    assertEquals(1, full.get("freshness").points());
    assertEquals(5, full.get("downstream").points());
    assertNull(full.get("downstream").gap());
  }

  @Test
  void viewsAreLogarithmicAndOpenChangesCostTrust() {
    Map<String, ItemScore> views = byKey(HealthScorer.score(new HealthInputs(true, true, true, 3,
        Availability.OK, 1.0, Availability.OK, 1.0, Availability.OK, true,
        Availability.OK, true, true, Availability.OK, 5, Availability.OK, 3L, 0)));
    assertEquals(0, views.get("views").points());
    assertEquals(0, views.get("changes_confirmed").points());
    assertEquals("有源变更待确认", views.get("changes_confirmed").gap());
  }

  private static HealthInputs tierInput(int tags, int days, int downstream) {
    return new HealthInputs(true, true, true, tags,
        Availability.OK, 1.0, Availability.OK, 1.0,
        Availability.OK, true, Availability.OK, true,
        false, Availability.OK, downstream,
        Availability.OK, (long) days, 30);
  }
}
