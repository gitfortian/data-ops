package io.yak.ops.business.metric.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 编码统一生成策略单测(M-1/M-7):中文不丢信息、同名确定、异名不撞。 */
class CodeGeneratorTest {

  @Test
  void pureChineseNameFallsBackToPrefixedHash() {
    String code = CodeGenerator.fromName("订单量", "metric");
    assertThat(code).matches("metric_[0-9a-f]{6}");
    assertThat(code).isEqualTo(CodeGenerator.fromName("订单量", "metric"));
  }

  @Test
  void mixedNameKeepsLatinPrefixAndHashSuffixDistinguishesNames() {
    String atomic = CodeGenerator.fromName("QA测试原子指标", "metric");
    String composite = CodeGenerator.fromName("QA测试复合指标", "metric");
    assertThat(atomic).matches("qa_[0-9a-f]{6}");
    assertThat(atomic).isNotEqualTo(composite);
  }

  @Test
  void pureAsciiNameIsSanitizedWithoutHash() {
    assertThat(CodeGenerator.fromName("GMV Total", "metric")).isEqualTo("gmv_total");
    assertThat(CodeGenerator.fromName("ord_cnt_1d", "metric")).isEqualTo("ord_cnt_1d");
  }

  @Test
  void tagUsesSameStrategyAsMetric() {
    assertThat(CodeGenerator.fromName("QA测试标签", "tag")).matches("qa_[0-9a-f]{6}");
  }

  @Test
  void blankNameYieldsEmpty() {
    assertThat(CodeGenerator.fromName("  ", "metric")).isEmpty();
    assertThat(CodeGenerator.fromName(null, "metric")).isEmpty();
  }
}
