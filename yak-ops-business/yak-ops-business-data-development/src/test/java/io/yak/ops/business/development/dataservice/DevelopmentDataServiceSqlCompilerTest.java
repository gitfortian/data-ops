package io.yak.ops.business.development.dataservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DevelopmentDataServiceSqlCompilerTest {

  private final DevelopmentDataServiceSqlCompiler compiler = new DevelopmentDataServiceSqlCompiler();

  @Test
  void discoversNamedParametersWithoutTreatingCastsCommentsOrStringsAsParameters() {
    String sql = """
        select id, ':ignored' as literal, created_at::date
        from orders
        where status = :status
          and owner_id = :ownerId
        -- :commented
        """;

    assertEquals(List.of("status", "ownerId"), compiler.parameterNames(sql));
  }

  @Test
  void compilesNamedParametersToJdbcPlaceholdersInStableOrder() {
    DevelopmentDataServiceSqlCompiler.CompiledSql compiled = compiler.compile(
        "select * from orders where status = :status and owner_id = :ownerId",
        Map.of("status", "PAID", "ownerId", 7));

    assertEquals("select * from orders where status = ? and owner_id = ?", compiled.sql());
    assertEquals(List.of("PAID", 7), compiled.parameters());
  }

  @Test
  void rejectsNonSelectStatements() {
    assertThrows(
        IllegalArgumentException.class,
        () -> compiler.validateSelectOnly("delete from orders"));
  }
}
