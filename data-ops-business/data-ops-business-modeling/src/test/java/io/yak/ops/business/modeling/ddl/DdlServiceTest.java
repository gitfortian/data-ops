package io.yak.ops.business.modeling.ddl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.modeling.version.ModelPublishedStructureReader;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Dialect template selection and published-snapshot assembly. */
class DdlServiceTest {

  private ModelRepository modelRepository;
  private ModelPublishedStructureReader structureReader;
  private DdlService service;

  @BeforeEach
  void setUp() {
    modelRepository = Mockito.mock(ModelRepository.class);
    structureReader = Mockito.mock(ModelPublishedStructureReader.class);
    service = new DdlService(
        modelRepository,
        structureReader,
        List.of(
            new MysqlDdlGenerator(),
            new PostgreSqlDdlGenerator(),
            new OracleDdlGenerator(),
            new DorisDdlGenerator(),
            new StarRocksDdlGenerator(),
            new ClickHouseDdlGenerator()));
  }

  private Model model(ModelDialect dialect) {
    return Model.create("dim_user", "用户维度表", dialect, null)
        .withPersisted(42L, "alice", null, null);
  }

  private StructureView view(String tableName, List<StructureView.ColumnView> columns,
      List<String> primaryKey) {
    return new StructureView(
        42L, "dim_user", "用户维度表", null, "PUBLISHED", null,
        tableName, null, columns, primaryKey, List.of(),
        new StructureView.PartitionView(null, List.of(), null), Map.of());
  }

  private void stubEmptyStructure() {
    when(structureReader.publishedStructure(42L)).thenReturn(view(null, List.of(), List.of()));
  }

  @Test
  void generateUsesStoredTableNameFallbackAndDialectTemplate() {
    when(modelRepository.findById(42L)).thenReturn(Optional.of(model(ModelDialect.MYSQL)));
    when(structureReader.publishedStructure(42L)).thenReturn(view(null,
        List.of(new StructureView.ColumnView(null, "user_id", "BIGINT", null, null, false,
            null, null, null, 0, null, null, null, null, null, null, null, null, null, null)),
        List.of("user_id")));

    DdlService.DdlView view = service.generate(42L);

    assertThat(view.dialect()).isEqualTo("MYSQL");
    assertThat(view.script()).contains("CREATE TABLE `dim_user` (");
    assertThat(view.script()).contains("PRIMARY KEY (`user_id`)");
  }

  @Test
  void generateResolvesATemplateForEverySupportedDialect() {
    stubEmptyStructure();

    for (ModelDialect dialect : ModelDialect.values()) {
      when(modelRepository.findById(42L)).thenReturn(Optional.of(model(dialect)));

      DdlService.DdlView view = service.generate(42L);

      assertThat(view.dialect()).isEqualTo(dialect.name());
      assertThat(view.script())
          .as("方言 %s 应能选到建库脚本模板", dialect)
          .contains("CREATE TABLE");
    }
  }

  @Test
  void generateFailsForDialectWithoutTemplate() {
    stubEmptyStructure();
    when(modelRepository.findById(42L)).thenReturn(Optional.of(model(ModelDialect.POSTGRESQL)));
    DdlService mysqlOnly = new DdlService(
        modelRepository, structureReader, List.of(new MysqlDdlGenerator()));

    assertThatThrownBy(() -> mysqlOnly.generate(42L))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.INVALID_DIALECT));
  }

  @Test
  void generateFailsForUnknownModel() {
    when(modelRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.generate(99L))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.NOT_FOUND));
  }
}
