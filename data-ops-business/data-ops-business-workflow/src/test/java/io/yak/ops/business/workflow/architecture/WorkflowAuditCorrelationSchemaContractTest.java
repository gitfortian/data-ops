package io.yak.ops.business.workflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.workflow.dao.mapper.WorkflowExecutionMapper;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class WorkflowAuditCorrelationSchemaContractTest {

  @Test
  void v2AddsOnlyNullableExecutionAuditCarrierWithoutBackfill() throws IOException {
    String baseline =
        section(resource("db/migration/yak-workflow/V1__workflow_baseline.sql"),
            "V1__baseline_workflow.sql");
    String migration =
        section(resource("db/migration/yak-workflow/V1__workflow_baseline.sql"),
            "V2__add_execution_audit_carrier.sql");
    String upper = migration.toUpperCase(Locale.ROOT);

    assertThat(baseline).doesNotContain("audit_carrier_json");
    assertThat(migration)
        .contains("ALTER TABLE yak_workflow_execution")
        .contains("ADD COLUMN audit_carrier_json LONGTEXT NULL");
    assertThat(upper)
        .doesNotContain("UPDATE YAK_WORKFLOW_EXECUTION")
        .doesNotContain("INSERT INTO YAK_WORKFLOW_EXECUTION")
        .doesNotContain("DELETE FROM YAK_WORKFLOW_EXECUTION");
  }

  /**
   * 审计关联读写在 Mapper 注解中（原 XML 已移除），契约不变：只能按 id + project 定位，
   * 且不得借道改写运行态真值列。
   */
  @Test
  void correlationMapperStaysProjectScopedAndDoesNotRewriteRuntimeTruth() throws Exception {
    String update =
        annotationSql(
            WorkflowExecutionMapper.class,
            "updateAuditCarrier",
            Update.class,
            String.class,
            long.class,
            String.class);
    String select =
        annotationSql(
            WorkflowExecutionMapper.class,
            "selectAuditCarrierJson",
            Select.class,
            String.class,
            long.class);

    assertThat(update)
        .contains("audit_carrier_json = #{carrierJson}")
        .contains("id = #{executionId}")
        .contains("project_id = #{projectId}")
        .doesNotContain("status =")
        .doesNotContain("updated_at =")
        .doesNotContain("runtime_metadata_json =");
    assertThat(select).contains("audit_carrier_json").contains("project_id = #{projectId}");
  }

  private static String annotationSql(
      Class<?> mapper,
      String methodName,
      Class<? extends Annotation> annotationType,
      Class<?>... parameterTypes)
      throws NoSuchMethodException {
    Annotation annotation = mapper.getMethod(methodName, parameterTypes).getAnnotation(annotationType);
    assertThat(annotation).as("%s.%s 必须带 @%s", mapper.getSimpleName(), methodName,
        annotationType.getSimpleName()).isNotNull();
    return annotationValue(annotation);
  }

  private static String annotationValue(Annotation annotation) {
    try {
      String[] value = (String[]) annotation.annotationType().getMethod("value").invoke(annotation);
      return String.join("\n", value);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("无法读取注解 SQL: " + annotation, exception);
    }
  }

  /**
   * 合并后的单文件按 {@code -- Source: <原路径>} 分段;按文件名取回原迁移的正文,
   * 使原先针对单个文件的断言继续有效。
   */
  private static String section(String sql, String sourceFileName) {
    // 合并后的单文件可能是 CRLF：先归一化换行，否则行尾 \r 会让 endsWith 失配。
    String[] lines = sql.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    int start = -1;
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].startsWith("-- Source:") && lines[i].trim().endsWith(sourceFileName)) {
        start = i + 1;
        break;
      }
    }
    if (start < 0) throw new IllegalStateException("missing source section: " + sourceFileName);
    StringBuilder body = new StringBuilder();
    for (int i = start; i < lines.length; i++) {
      if (lines[i].startsWith("-- Source:")) break;
      body.append(lines[i]).append('\n');
    }
    return body.toString();
  }
  private String resource(String path) throws IOException {
    try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
      if (input == null) throw new IllegalStateException("Missing test resource: " + path);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
