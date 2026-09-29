package io.yak.ops.business.metric.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricQualifier;
import io.yak.ops.business.metric.domain.MetricStatus;
import io.yak.ops.business.metric.domain.MetricType;
import io.yak.ops.business.metric.domain.StatPeriod;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 派生指标自动组装单测(02):组装正确性 + 存量自由文本 dimConstraint 兼容两分支。
 */
class DerivedMetricAssemblerTest {

  private final DerivedMetricAssembler assembler = new DerivedMetricAssembler();

  private static Metric metric(Long id, MetricType type, String measureExpr, String filterExpr,
      Long modelId, String dimConstraint, String qualifiersJson) {
    return new Metric(id, "m_" + id, "指标" + id, 1L, 2L, type, 60L, "口径规则", measureExpr,
        filterExpr, null, null, dimConstraint, qualifiersJson, modelId, null, StatPeriod.DAY,
        70L, null, "tester", MetricStatus.ENABLED, 1, null, null,
        LocalDateTime.now(), LocalDateTime.now());
  }

  private static Metric derivedReferring(Metric atomic, String qualifiersJson) {
    return new Metric(null, "d_1", "派生", null, null, MetricType.DERIVED, null, null, null,
        null, null, atomic.id(), null, qualifiersJson, null, null, StatPeriod.DAY, null,
        null, "tester", MetricStatus.ENABLED, 1, null, null, null, null);
  }

  // ── 组装正确性 ──

  @Test
  void assemblesMeasureFilterModelAndInheritsCaliberUnit() {
    Metric atomic = metric(20L, MetricType.ATOMIC, "SUM(amount)", "pay_status != '已取消'", 9L, null, null);
    List<MetricQualifier> quals = assembler.parse(
        "[{\"field\":\"order_type\",\"op\":\"=\",\"value\":\"线上\"},{\"field\":\"amount\",\"op\":\"BETWEEN\",\"value\":\"100,200\"}]");

    Metric assembled = assembler.assemble(derivedReferring(atomic, "[...]"), atomic, quals);

    assertThat(assembled.measureExpr()).isEqualTo("SUM(amount)");
    assertThat(assembled.modelId()).isEqualTo(9L);
    assertThat(assembled.caliberId()).isEqualTo(60L);
    assertThat(assembled.unitId()).isEqualTo(70L);
    assertThat(assembled.domainId()).isEqualTo(1L);
    assertThat(assembled.processId()).isEqualTo(2L);
    assertThat(assembled.filterExpr())
        .isEqualTo("pay_status != '已取消' AND order_type = '线上' AND amount BETWEEN 100 AND 200");
    assertThat(assembled.dimConstraint()).contains("AND");
    assertThat(assembled.qualifiersJson()).isEqualTo("[...]");
  }

  @Test
  void assemblesFilterFromQualifiersOnlyWhenAtomicHasNoFilter() {
    Metric atomic = metric(20L, MetricType.ATOMIC, "COUNT(1)", null, 9L, null, null);
    List<MetricQualifier> quals = List.of(new MetricQualifier("city", "IN", "北京,上海"));

    Metric assembled = assembler.assemble(derivedReferring(atomic, null), atomic, quals);

    assertThat(assembled.filterExpr()).isEqualTo("city IN ('北京', '上海')");
  }

  @Test
  void userProvidedCaliberAndPeriodWinOverInheritance() {
    Metric atomic = metric(20L, MetricType.ATOMIC, "SUM(amount)", null, 9L, null, null);
    Metric draft = derivedReferring(atomic, null)
        .withEditable("派生", null, null, MetricType.DERIVED, 61L, null, null, null, null, null,
            null, null, null, null, StatPeriod.MONTH, 71L, null, "tester", "tester", LocalDateTime.now());

    Metric assembled = assembler.assemble(draft, atomic, List.of(new MetricQualifier("a", "=", "1")));

    assertThat(assembled.caliberId()).isEqualTo(61L);
    assertThat(assembled.unitId()).isEqualTo(71L);
    assertThat(assembled.statPeriod()).isEqualTo(StatPeriod.MONTH);
    assertThat(assembled.measureExpr()).isEqualTo("SUM(amount)");
  }

  @Test
  void rejectsAssemblyWhenAtomicMissesMeasureExpr() {
    Metric atomic = metric(20L, MetricType.ATOMIC, null, null, 9L, null, null);

    assertThatThrownBy(() -> assembler.assemble(derivedReferring(atomic, null), atomic,
        List.of(new MetricQualifier("a", "=", "1"))))
        .isInstanceOf(MetricException.class)
        .hasMessageContaining("缺少度量表达式");
  }

  // ── 限定条件编译契约(03 试算消费) ──

  @Test
  void compilesOperatorSetToStandardPredicates() {
    assertThat(assembler.compilePredicates(List.of(
        new MetricQualifier("a", "=", "x"),
        new MetricQualifier("b", ">", "10"),
        new MetricQualifier("c", "LIKE", "%南%"),
        new MetricQualifier("d", "BETWEEN", "2024-01-01 AND 2024-12-31"))))
        .isEqualTo("a = 'x' AND b > 10 AND c LIKE '%南%' AND d BETWEEN '2024-01-01' AND '2024-12-31'");
  }

  @Test
  void escapesQuotesAndRejectsBadInput() {
    assertThat(MetricQualifier.compile(new MetricQualifier("name", "=", "O'Brien")))
        .isEqualTo("name = 'O''Brien'");
    assertThatThrownBy(() -> MetricQualifier.compile(new MetricQualifier("bad field", "=", "1")))
        .isInstanceOf(MetricException.class);
    assertThatThrownBy(() -> MetricQualifier.compile(new MetricQualifier("a", "REGEXP", "1")))
        .isInstanceOf(MetricException.class)
        .satisfies(e -> assertThat(((MetricException) e).getErrorCode())
            .isEqualTo(MetricErrorCode.INVALID_COMPOSITION));
    assertThatThrownBy(() -> MetricQualifier.compile(new MetricQualifier("a", "=", "  ")))
        .isInstanceOf(MetricException.class);
  }

  @Test
  void parsesLenientlyAndRejectsBrokenJson() {
    assertThat(assembler.parse(null)).isEmpty();
    assertThat(assembler.parse("")).isEmpty();
    assertThatThrownBy(() -> assembler.parse("{not-json"))
        .isInstanceOf(MetricException.class);
  }

  // ── 存量兼容:自由文本 dimConstraint(qualifiersJson 空)不组装 ──

  @Test
  void legacyFreeTextDerivedIsNotAssembled() {
    Metric legacy = derivedReferring(metric(20L, MetricType.ATOMIC, "SUM(x)", null, 9L, null, null), null);
    List<MetricQualifier> quals = assembler.parse(legacy.qualifiersJson());

    // 服务层触发条件:无有效限定 → 原样落库,measure/filter/modelId 保持登记式空值
    assertThat(quals.stream().allMatch(DerivedMetricAssembler::isBlank)).isTrue();
    assertThat(legacy.measureExpr()).isNull();
    assertThat(legacy.modelId()).isNull();
  }

  @Test
  void blankPlaceholderRowsAreNotQualifiers() {
    assertThat(assembler.parse("[{},{\"field\":\"\",\"op\":\"\",\"value\":\"\"}]"))
        .allMatch(DerivedMetricAssembler::isBlank);
  }
}
