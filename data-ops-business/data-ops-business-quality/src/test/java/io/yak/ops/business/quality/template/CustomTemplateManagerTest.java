package io.yak.ops.business.quality.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplate;
import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplateSpec;
import io.yak.ops.business.quality.repository.CustomTemplateRepository;
import io.yak.ops.common.enums.quality.QualityEnums.CheckMethod;
import io.yak.ops.common.enums.quality.QualityEnums.CheckType;
import io.yak.ops.common.enums.quality.QualityEnums.ComparisonOperator;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CustomTemplateManagerTest {
  @Mock private CustomTemplateRepository repository;
  private CustomTemplateManager manager;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    manager = new CustomTemplateManager(repository, new CustomTemplatePolicy());
  }

  @Test
  void shouldCreateExecutableCustomTemplateDefaults() {
    when(repository.insertTemplate(any())).thenReturn(7L);
    when(repository.find(7L)).thenReturn(Optional.of(template(7L)));

    CustomTemplate result = manager.create(
        new CustomTemplateCommand.Save(
            "订单数量校验", "统计订单数量", "完整性", null,
            "SELECT COUNT(*) FROM ${tableName} WHERE ${where};", "set demo=value",
            CheckType.NUMERIC, CheckMethod.FIXED_VALUE,
            ComparisonOperator.GTE, BigDecimal.ONE, null),
        "tester");

    ArgumentCaptor<CustomTemplateSpec> captor = ArgumentCaptor.forClass(CustomTemplateSpec.class);
    verify(repository).insertTemplate(captor.capture());
    CustomTemplateSpec write = captor.getValue();
    assertThat(write.templateSql()).isEqualTo("SELECT COUNT(*) FROM ${table} WHERE ${where}");
    assertThat(write.parameterSchema())
        .contains("\"defaultOperator\":\"GTE\"")
        .contains("\"defaultSql\"");
    assertThat(write.operator()).isEqualTo("tester");
    assertThat(result.id()).isEqualTo(7L);
  }

  @Test
  void shouldRejectMultipleStatements() {
    assertThatThrownBy(() -> manager.create(
        new CustomTemplateCommand.Save(
            "多语句模板", null, "自定义", null,
            "SELECT 1; SELECT 2", null,
            CheckType.NUMERIC, CheckMethod.FIXED_VALUE,
            ComparisonOperator.EQ, BigDecimal.ZERO, null),
        "tester"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("单条只读 SELECT");
  }

  private CustomTemplate template(long id) {
    LocalDateTime now = LocalDateTime.now();
    return new CustomTemplate(
        id, "CUSTOM_SQL_TEST", "订单数量校验", "统计订单数量",
        RuleType.CUSTOM_SQL, RuleScope.TABLE, "完整性",
        "{\"fields\":[\"customSql\",\"operator\",\"threshold\"]}",
        false, true, 0, 1000, null, null,
        "SELECT COUNT(*) FROM ${table}", null,
        CheckType.NUMERIC, CheckMethod.FIXED_VALUE, "tester", now, now);
  }
}
