package io.yak.ops.business.dataservice.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DataServiceSqlCompilerTest {
  private final DataServiceSqlCompiler compiler = new DataServiceSqlCompiler();

  @Test
  void compilesNamedParametersToJdbcPlaceholders() {
    DataServiceSqlCompiler.CompiledSql compiled = compiler.compile(
        "select id, name from sys_user where department = :department and status = :status",
        Map.of("department", "研发部", "status", "ACTIVE"));
    assertThat(compiled.sql()).contains("department = ?").contains("status = ?");
    assertThat(compiled.parameters()).containsExactly("研发部", "ACTIVE");
  }

  @Test
  void rejectsMissingNamedParameter() {
    assertThatThrownBy(() -> compiler.compile("select * from sys_user where id = :id", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonSelectAndMultipleStatements() {
    assertThatThrownBy(() -> compiler.compile("delete from sys_user", Map.of()))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SELECT");
    assertThatThrownBy(() -> compiler.compile("select 1; select 2", Map.of()))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SELECT");
  }

  @Test
  void discoversDistinctParameterNames() {
    assertThat(compiler.parameterNames("select * from t where a = :id or b = :id and c = :name"))
        .containsExactly("id", "name");
  }
}
