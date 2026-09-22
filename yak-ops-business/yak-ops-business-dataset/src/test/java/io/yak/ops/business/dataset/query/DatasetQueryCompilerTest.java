package io.yak.ops.business.dataset.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.dataset.DatasetAggregation;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetFilter;
import io.yak.ops.business.dataset.DatasetFilterOperator;
import io.yak.ops.business.dataset.DatasetMetricBinding;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetSort;
import io.yak.ops.business.dataset.DatasetSortDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetQueryCompilerTest {

  private final DatasetQueryCompiler compiler = new DatasetQueryCompiler();

  @Test
  void compilesDimensionMetricFilterAndSort() {
    List<DatasetField> fields =
        List.of(
            field("region", "region", DatasetFieldDataType.STRING, DatasetFieldRole.DIMENSION),
            field("amount", "amount", DatasetFieldDataType.NUMBER, DatasetFieldRole.MEASURE));
    DatasetQueryRequest request =
        new DatasetQueryRequest(
            null,
            List.of("region"),
            List.of(new DatasetMetricBinding("amount", DatasetAggregation.SUM)),
            List.of(new DatasetFilter("region", DatasetFilterOperator.EQ, "East'Asia", null)),
            List.of(new DatasetSort("amount", DatasetAggregation.SUM, DatasetSortDirection.DESC)),
            100,
            null);

    DatasetQueryCompiler.CompiledQuery compiled =
        compiler.compile("SELECT region, amount FROM sales", fields, request);

    assertTrue(compiled.sql().contains("yak_dataset_source.region AS d0"));
    assertTrue(compiled.sql().contains("SUM(yak_dataset_source.amount) AS m1"));
    assertTrue(compiled.sql().contains("yak_dataset_source.region = 'East''Asia'"));
    assertTrue(compiled.sql().contains("GROUP BY yak_dataset_source.region"));
    assertTrue(compiled.sql().contains("ORDER BY m1 DESC"));
    assertTrue(compiled.sql().endsWith("LIMIT 101"));
    assertEquals(2, compiled.bindings().size());
  }

  @Test
  void rawPreviewWorksWithoutSchema() {
    DatasetQueryCompiler.CompiledQuery compiled =
        compiler.compile("SELECT * FROM sales", List.of(), null);
    assertTrue(
        compiled.sql().startsWith("SELECT yak_dataset_source.* FROM (SELECT * FROM sales)"));
    assertTrue(compiled.bindings().isEmpty());
  }

  @Test
  void rejectsMutationAndMultiStatementSources() {
    assertThrows(
        IllegalArgumentException.class,
        () -> compiler.compile("DELETE FROM sales", List.of(), null));
    assertThrows(
        IllegalArgumentException.class,
        () -> compiler.compile("SELECT * FROM sales; DROP TABLE sales", List.of(), null));
  }

  @Test
  void mutationWordsInsideStringAndCommentDoNotCauseFalsePositive() {
    DatasetQueryCompiler.CompiledQuery compiled =
        compiler.compile(
            "SELECT 'delete from x' AS note /* drop table y */", List.of(), null);
    assertTrue(compiled.sql().contains("'delete from x'"));
  }

  private DatasetField field(
      String id,
      String physicalName,
      DatasetFieldDataType dataType,
      DatasetFieldRole role) {
    return new DatasetField(
        id, 1L, physicalName, physicalName, dataType, true, null, role, 1);
  }
}
