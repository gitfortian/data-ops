package io.yak.ops.business.modeling.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.modeling.api.ModelingStructureApi.ColumnInput;
import io.yak.ops.business.modeling.api.ModelingStructureApi.IndexInput;
import io.yak.ops.business.modeling.api.ModelingStructureApi.PartitionInput;
import io.yak.ops.business.modeling.api.ModelingStructureApi.SaveStructureRequest;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/** Structure rules: identifier names, column uniqueness, pk/index/partition references. */
class ModelStructureServiceTest {

  private ModelRepository modelRepository;
  private ModelStructureRepository structureRepository;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private ModelStructureService service;

  @BeforeEach
  void setUp() {
    modelRepository = Mockito.mock(ModelRepository.class);
    structureRepository = Mockito.mock(ModelStructureRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    service = new ModelStructureService(modelRepository, structureRepository, auditService);
  }

  private Model model() {
    return Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
  }

  private void stubSave(boolean modelExists) {
    when(modelRepository.findById(42L)).thenReturn(modelExists ? Optional.of(model()) : Optional.empty());
    when(structureRepository.updateTableInfo(any(), any(), any())).thenReturn(modelExists);
    when(structureRepository.updateTableAttributes(any(), any(), any(), any(), any(), any()))
        .thenReturn(modelExists);
  }

  @Test
  void saveFallsBackToModelCodeAndReplacesColumns() {
    stubSave(true);

    StructureView view =
        service.save(
            42L,
            new SaveStructureRequest(
                null,
                " 用户表注释 ",
                List.of(
                    new ColumnInput("user_id", "BIGINT", null, null, false, null, "主键", null),
                    new ColumnInput("name", "VARCHAR", 128, null, true, null, null, "姓名")),
                null,
                null,
                null,
                null),
            "bob");

    assertThat(view.tableName()).isEqualTo("dim_user");
    assertThat(view.tableComment()).isEqualTo("用户表注释");
    assertThat(view.columns()).hasSize(2);
    assertThat(view.columns().get(0).sortOrder()).isEqualTo(0);
    verify(structureRepository).updateTableInfo(eq(42L), eq("dim_user"), eq("用户表注释"));
    verify(structureRepository).replaceColumns(eq(42L), anyList());
    verify(structureRepository).replaceIndexes(eq(42L), anyList());
    verify(audit).event(eq(AuditEventType.RESOURCE_UPDATED), anyString(), any());
    verify(audit).success(anyString());
  }

  @Test
  void savePersistsPrimaryKeyIndexesPartitionAndProperties() {
    stubSave(true);

    StructureView view =
        service.save(
            42L,
            new SaveStructureRequest(
                "dim_user",
                null,
                List.of(
                    new ColumnInput("user_id", "BIGINT", null, null, false, null, null, null),
                    new ColumnInput("dt", "DATE", null, null, false, null, null, null),
                    new ColumnInput("name", "VARCHAR", 128, null, true, null, null, null)),
                List.of("user_id"),
                List.of(
                    new IndexInput("idx_name", true, "BTREE", List.of("name", "user_id")),
                    new IndexInput("IDX_DT", false, null, List.of("dt"))),
                new PartitionInput("RANGE", List.of("dt"), "YEAR(dt)"),
                Map.of("ENGINE", "InnoDB")),
            "bob");

    assertThat(view.primaryKey()).containsExactly("user_id");
    assertThat(view.indexes()).hasSize(2);
    assertThat(view.indexes().get(0).columns()).containsExactly("name", "user_id");
    assertThat(view.partition().type()).isEqualTo("RANGE");
    assertThat(view.partition().columns()).containsExactly("dt");
    assertThat(view.tableProperties()).containsEntry("ENGINE", "InnoDB");

    verify(structureRepository)
        .updateTableAttributes(
            eq(42L), eq("[\"user_id\"]"), eq("RANGE"), eq("[\"dt\"]"), eq("YEAR(dt)"),
            eq("{\"ENGINE\":\"InnoDB\"}"));
    verify(structureRepository)
        .replaceIndexes(
            eq(42L),
            ArgumentMatchers.<List<IndexDefinition>>argThat(list -> {
              return list != null
                  && list.size() == 2
                  && "idx_name".equals(list.get(0).indexName())
                  && Boolean.TRUE.equals(list.get(0).uniqueIndex())
                  && "IDX_DT".equals(list.get(1).indexName());
            }));
  }

  @Test
  void saveRejectsDuplicateColumnNamesCaseInsensitively() {
    stubSave(true);

    assertThatThrownBy(
            () ->
                service.save(
                    42L,
                    new SaveStructureRequest(
                        null,
                        null,
                        List.of(
                            new ColumnInput("user_id", "BIGINT", null, null, null, null, null, null),
                            new ColumnInput("USER_ID", "BIGINT", null, null, null, null, null, null)),
                        null, null, null, null),
                    "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.INVALID_COLUMN));

    verify(structureRepository, never()).replaceColumns(any(), anyList());
    verify(audit).failure(ArgumentMatchers.eq("MODELING_STRUCTURE_SAVE_FAILED"), any());
  }

  @Test
  void saveRejectsInvalidIdentifierNames() {
    stubSave(true);

    assertThatThrownBy(
            () ->
                service.save(
                    42L,
                    new SaveStructureRequest(
                        "dim user!", null,
                        List.of(
                            new ColumnInput("user_id", "BIGINT", null, null, null, null, null, null)),
                        null, null, null, null),
                    "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ModelingErrorCode.INVALID_TABLE_NAME));

    assertThatThrownBy(
            () ->
                service.save(
                    42L,
                    new SaveStructureRequest(
                        null, null,
                        List.of(
                            new ColumnInput("user id", "BIGINT", null, null, null, null, null, null)),
                        null, null, null, null),
                    "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.INVALID_COLUMN));
  }

  @Test
  void saveRejectsPrimaryKeyReferencingMissingColumn() {
    stubSave(true);

    assertThatThrownBy(
            () ->
                service.save(
                    42L,
                    new SaveStructureRequest(
                        null, null,
                        List.of(
                            new ColumnInput("user_id", "BIGINT", null, null, null, null, null, null)),
                        List.of("user_id", "ghost"), null, null, null),
                    "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.INVALID_COLUMN));

    verify(structureRepository, never()).replaceIndexes(any(), anyList());
  }

  @Test
  void saveRejectsReservedAndDuplicateAndDanglingIndexNames() {
    stubSave(true);
    List<ColumnInput> columns =
        List.of(new ColumnInput("user_id", "BIGINT", null, null, null, null, null, null));

    assertThatThrownBy(
            () ->
                service.save(
                    42L,
                    new SaveStructureRequest(null, null, columns,
                        null, List.of(new IndexInput("primary", false, null, List.of("user_id"))),
                        null, null),
                    "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.INVALID_COLUMN));

    assertThatThrownBy(
            () ->
                service.save(
                    42L,
                    new SaveStructureRequest(null, null, columns,
                        null,
                        List.of(
                            new IndexInput("idx_a", false, null, List.of("user_id")),
                            new IndexInput("IDX_A", false, null, List.of("user_id"))),
                        null, null),
                    "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.INVALID_COLUMN));

    assertThatThrownBy(
            () ->
                service.save(
                    42L,
                    new SaveStructureRequest(null, null, columns,
                        null, List.of(new IndexInput("idx_x", false, null, List.of("ghost"))),
                        null, null),
                    "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.INVALID_COLUMN));

    assertThatThrownBy(
            () ->
                service.save(
                    42L,
                    new SaveStructureRequest(null, null, columns,
                        null, List.of(new IndexInput("idx_empty", false, null, List.of())),
                        null, null),
                    "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.INVALID_COLUMN));

    verify(structureRepository, never()).replaceIndexes(any(), anyList());
  }

  @Test
  void saveRejectsPartitionReferencingMissingColumn() {
    stubSave(true);

    assertThatThrownBy(
            () ->
                service.save(
                    42L,
                    new SaveStructureRequest(
                        null, null,
                        List.of(
                            new ColumnInput("user_id", "BIGINT", null, null, null, null, null, null)),
                        null, null,
                        new PartitionInput("RANGE", List.of("ghost"), null),
                        null),
                    "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.INVALID_COLUMN));
  }

  @Test
  void getReturnsStoredTableInfoColumnsAndAttributes() {
    when(modelRepository.findById(42L)).thenReturn(Optional.of(model()));
    when(structureRepository.findTableName(42L)).thenReturn(Optional.of("t_user"));
    when(structureRepository.findTableComment(42L)).thenReturn(Optional.of("用户"));
    when(structureRepository.findColumns(42L)).thenReturn(List.of());
    when(structureRepository.findPrimaryKeyJson(42L)).thenReturn(Optional.of("[\"user_id\"]"));
    when(structureRepository.findIndexes(42L))
        .thenReturn(
            List.of(new io.yak.ops.business.modeling.domain.IndexDefinition(9L, "idx_name", true, "BTREE", List.of("name"))));
    when(structureRepository.findPartitionType(42L)).thenReturn(Optional.of("HASH"));
    when(structureRepository.findPartitionColumnsJson(42L)).thenReturn(Optional.of("[\"user_id\"]"));
    when(structureRepository.findPartitionExpr(42L)).thenReturn(Optional.empty());
    when(structureRepository.findTablePropertiesJson(42L)).thenReturn(Optional.of("{\"ENGINE\":\"InnoDB\"}"));

    StructureView view = service.get(42L);

    assertThat(view.tableName()).isEqualTo("t_user");
    assertThat(view.primaryKey()).containsExactly("user_id");
    assertThat(view.indexes()).hasSize(1);
    assertThat(view.indexes().get(0).columns()).containsExactly("name");
    assertThat(view.partition().type()).isEqualTo("HASH");
    assertThat(view.partition().columns()).containsExactly("user_id");
    assertThat(view.tableProperties()).containsEntry("ENGINE", "InnoDB");
  }

  @Test
  void unknownModelFailsWithNotFound() {
    when(modelRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(99L))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.NOT_FOUND));
    assertThatThrownBy(
            () ->
                service.save(
                    99L,
                    new SaveStructureRequest(null, null, List.of(), null, null, null, null),
                    "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.NOT_FOUND));
  }

  @Test
  void validateReportsTypeCatalogAndLengthIssues() {
    when(modelRepository.findById(42L)).thenReturn(Optional.of(model()));

    List<ValidationIssue> issues =
        service.validate(
            42L,
            new SaveStructureRequest(
                null, null,
                List.of(
                    new ColumnInput("c1", "SOMETYPE", null, null, null, null, null, null),
                    new ColumnInput("c2", "VARCHAR", null, null, null, null, null, null),
                    new ColumnInput("c3", "INT", null, 7, null, null, null, null),
                    new ColumnInput("c4", "DECIMAL", 5, 7, null, null, null, null)),
                null, null, null, null));

    org.assertj.core.api.Assertions.assertThat(issues).extracting(ValidationIssue::severity, ValidationIssue::message)
        .contains(
            org.assertj.core.api.Assertions.tuple(
                ValidationIssue.Severity.ERROR, "类型 SOMETYPE 不在 MYSQL 类型目录中"),
            org.assertj.core.api.Assertions.tuple(
                ValidationIssue.Severity.ERROR, "类型 VARCHAR 需要指定长度"),
            org.assertj.core.api.Assertions.tuple(
                ValidationIssue.Severity.ERROR, "类型 INT 不支持小数位"),
            org.assertj.core.api.Assertions.tuple(
                ValidationIssue.Severity.ERROR, "小数位必须小于长度/精度"));
  }

  @Test
  void validateFlagsReservedWordsAsWarningsOnly() {
    when(modelRepository.findById(42L)).thenReturn(Optional.of(model()));

    List<ValidationIssue> issues =
        service.validate(
            42L,
            new SaveStructureRequest(
                "order", null,
                List.of(new ColumnInput("order", "BIGINT", null, null, null, null, null, null)),
                null, null, null, null));

    org.assertj.core.api.Assertions.assertThat(issues)
        .allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(ValidationIssue.Severity.WARNING))
        .hasSize(2);
  }

  /** 聚合列输入:18 参构造,仅填角色/聚合函数/口径。 */
  private static ColumnInput aggColumn(
      String name, String type, String role, String func, String expr) {
    return new ColumnInput(
        name, type, null, null, null, null, null, null,
        null, null, null, null, null, null, null, role, func, expr);
  }

  @Test
  void validateAggregateColumnsChecksRoleAndFunction() {
    when(modelRepository.findById(42L)).thenReturn(Optional.of(model()));

    List<ValidationIssue> issues =
        service.validate(
            42L,
            new SaveStructureRequest(
                null, null,
                List.of(
                    aggColumn("bad_role", "BIGINT", "GROUP", null, null),
                    aggColumn("no_func", "BIGINT", "MEASURE", null, null),
                    aggColumn("bad_func", "BIGINT", "MEASURE", "MEDIAN", null),
                    aggColumn("ok_measure", "BIGINT", "MEASURE", "SUM", "sum(x)"),
                    aggColumn("ok_dimension", "VARCHAR", "DIMENSION", null, null)),
                null, null, null, null));

    List<String> aggregateMessages = issues.stream()
        .filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR)
        .map(ValidationIssue::message)
        .filter(msg -> msg.contains("角色") || msg.contains("聚合函数"))
        .toList();

    assertThat(aggregateMessages)
        .anySatisfy(msg -> assertThat(msg).contains("GROUP"))
        .anySatisfy(msg -> assertThat(msg).contains("聚合函数"))
        .hasSize(3);
  }

  @Test
  void savePersistsAggregateColumns() {
    stubSave(true);

    service.save(
        42L,
        new SaveStructureRequest(
            "dws_amount", null,
            List.of(aggColumn("pay_amt", "BIGINT", "MEASURE", "SUM", "sum(pay_amount)")),
            null, null, null, null),
        "alice");

    @SuppressWarnings("unchecked")
    org.mockito.ArgumentCaptor<List<io.yak.ops.business.modeling.domain.ColumnDefinition>> captor =
        org.mockito.ArgumentCaptor.forClass(List.class);
    verify(structureRepository).replaceColumns(eq(42L), captor.capture());

    io.yak.ops.business.modeling.domain.ColumnDefinition saved = captor.getValue().get(0);
    assertThat(saved.fieldRole()).isEqualTo("MEASURE");
    assertThat(saved.aggregateFunc()).isEqualTo("SUM");
    assertThat(saved.transformExpr()).isEqualTo("sum(pay_amount)");
  }

  @Test
  void saveBlocksOnValidationError() {
    stubSave(true);

    assertThatThrownBy(
            () ->
                service.save(
                    42L,
                    new SaveStructureRequest(
                        null, null,
                        List.of(new ColumnInput("c1", "SOMETYPE", null, null, null, null, null, null)),
                        null, null, null, null),
                    "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.INVALID_COLUMN);
              assertThat(exception.getUserMessage()).contains("SOMETYPE");
            });

    verify(structureRepository, never()).replaceColumns(any(), anyList());
    verify(audit).failure(ArgumentMatchers.eq("MODELING_STRUCTURE_SAVE_FAILED"), any());
  }

  @Test
  void dialectCatalogCoversEveryDialect() {
    for (io.yak.ops.business.modeling.domain.ModelDialect dialect :
        io.yak.ops.business.modeling.domain.ModelDialect.values()) {
      assertThat(StructureDialectCatalog.types(dialect)).isNotEmpty();
    }
    assertThat(StructureDialectCatalog.lookupType(ModelDialect.MYSQL, "varchar"))
        .isNotNull()
        .satisfies(
            spec -> {
              assertThat(spec.lengthRequired()).isTrue();
              assertThat(spec.scaleAllowed()).isFalse();
            });
    assertThat(StructureDialectCatalog.isReservedWord(ModelDialect.MYSQL, "partition")).isTrue();
  }
}
