package io.yak.ops.business.quality.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.yak.ops.common.annotation.quality.QualityDateTimeFormat;
import io.yak.ops.common.bean.vo.quality.CustomQualityTemplateVO;
import io.yak.ops.common.bean.vo.quality.QualityExecutionVO;
import io.yak.ops.common.bean.vo.quality.QualityExecutionWorkspaceVO;
import io.yak.ops.common.bean.vo.quality.QualityMonitorVO;
import io.yak.ops.common.bean.vo.quality.QualityTableAssetVO;
import io.yak.ops.common.bean.vo.quality.QualityWorkspaceVO;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityDateTimeFormatTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serializesTimestampToSecondPrecisionWithSpaceSeparator() throws Exception {
    QualityWorkspaceVO.Stats stats = new QualityWorkspaceVO.Stats(
        3, 2, 8, 1, LocalDateTime.of(2026, 8, 6, 18, 20, 6, 653_000_000));

    String actual = objectMapper
        .readTree(objectMapper.writeValueAsString(stats))
        .get("latestExecutionTime")
        .asText();

    assertThat(actual).isEqualTo("2026-08-06 18:20:06");
  }

  @Test
  void everyQualityResponseTimestampUsesTheSharedFormat() {
    List<Class<?>> contracts = List.of(
        QualityMonitorVO.class,
        QualityTableAssetVO.class,
        QualityExecutionVO.class,
        QualityWorkspaceVO.class,
        QualityExecutionWorkspaceVO.class,
        CustomQualityTemplateVO.class);

    RecordComponent[] timestampComponents = contracts.stream()
        .flatMap(type -> Arrays.stream(type.getDeclaredClasses()))
        .filter(Class::isRecord)
        .flatMap(type -> Arrays.stream(type.getRecordComponents()))
        .filter(component -> component.getType() == LocalDateTime.class)
        .toArray(RecordComponent[]::new);

    assertThat(timestampComponents)
        .isNotEmpty()
        .allSatisfy(component -> assertThat(
            component.isAnnotationPresent(QualityDateTimeFormat.class))
            .as("%s.%s should declare @QualityDateTimeFormat",
                component.getDeclaringRecord().getSimpleName(),
                component.getName())
            .isTrue());
  }
}
