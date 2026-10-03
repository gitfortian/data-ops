package io.yak.ops.business.metadata.metamodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.dao.model.MdFieldDefPO;
import io.yak.ops.business.metadata.dao.model.MdTypeDefPO;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 元模型写前校验（ticket 129，plan §10 测试 8 的正反用例）。
 *
 * <p>这一层是"错配置进不了库"的唯一时机：一行写歪的元模型不会立刻报错，
 * 它会让后面所有实体都带着坏配置进目录，排查成本随存量线性增长。
 */
class MetamodelValidationServiceTest {

  private final MetadataTypeRegistry registry =
      MetamodelFixtures.registry(
          List.of(
              MetamodelFixtures.entityType(1L, "table"),
              MetamodelFixtures.entityType(2L, "database"),
              MetamodelFixtures.fieldType(3L, "STRING"),
              MetamodelFixtures.fieldType(4L, "ENTITY_REFERENCE")),
          List.of(),
          0L);
  private final MetamodelValidationService validation = MetamodelFixtures.validation(registry);

  @Test
  void aWellFormedEntityTypePasses() {
    assertThatCode(() -> validation.validateType(MetamodelFixtures.entityType(9L, "dataset"), true))
        .doesNotThrowAnyException();
  }

  @Test
  void entityTypeWithoutIdentityFieldsCannotBeSaved() {
    MdTypeDefPO noPrefix = MetamodelFixtures.entityType(9L, "dataset");
    noPrefix.setKeyPrefix(null);
    expectError(() -> validation.validateType(noPrefix, true), MetadataErrorCode.INVALID_ARGUMENT);

    MdTypeDefPO noFqn = MetamodelFixtures.entityType(9L, "dataset");
    noFqn.setFqnPattern("   ");
    expectError(() -> validation.validateType(noFqn, true), MetadataErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void fqnPatternMustBePlaceholdersAndSeparatorsOnly() {
    MdTypeDefPO constant = MetamodelFixtures.entityType(9L, "dataset");
    constant.setFqnPattern("dataset");
    expectError(() -> validation.validateType(constant, true), MetadataErrorCode.INVALID_ARGUMENT);

    MdTypeDefPO strayBrace = MetamodelFixtures.entityType(9L, "dataset");
    strayBrace.setFqnPattern("{name");
    expectError(() -> validation.validateType(strayBrace, true), MetadataErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void lineageAssetTypeMustBeALineageConstant() {
    // 写进去能存，但 lineage 读每行都 valueOf —— 错值炸的是别人的血缘查询（plan §2.3 后果 1）。
    MdTypeDefPO typo = MetamodelFixtures.entityType(9L, "dataset");
    typo.setLineageAssetType("TABLES");
    expectError(
        () -> validation.validateType(typo, true), MetadataErrorCode.LINEAGE_ASSET_TYPE_INVALID);
    expectError(
        () -> validation.validateType(typo, false), MetadataErrorCode.LINEAGE_ASSET_TYPE_INVALID);
  }

  @Test
  void pendingNullLineageMappingStaysEditableSoTicket134CanFillIt() {
    // databaseService/database/domain 三行由迁移灌成 NULL；若更新也拒 NULL，就没人能把它们补上。
    MdTypeDefPO pending = MetamodelFixtures.entityType(2L, "database");
    pending.setLineageAssetType(null);
    assertThatCode(() -> validation.validateType(pending, false)).doesNotThrowAnyException();

    // 新建则必须声明：NULL 不是"待补"，是"还不知道怎么进血缘图"。
    expectError(
        () -> validation.validateType(pending, true), MetadataErrorCode.LINEAGE_ASSET_TYPE_INVALID);
  }

  @Test
  void keyPrefixMustBeGloballyUnique() {
    // 前缀是"从 asset_key 反推类型"的唯一依据，两个类型共用一个就没法反解。
    MdTypeDefPO clash = MetamodelFixtures.entityType(9L, "dataset");
    clash.setKeyPrefix("table:");
    expectError(() -> validation.validateType(clash, true), MetadataErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void theTwoEntryChannelsCannotBothBeOn() {
    MdTypeDefPO harvestedWithProvider = MetamodelFixtures.entityType(9L, "dataset");
    harvestedWithProvider.setProviderBean("datasetEntityProvider");
    expectError(
        () -> validation.validateType(harvestedWithProvider, true), MetadataErrorCode.INVALID_ARGUMENT);

    MdTypeDefPO projectionWithoutProvider = MetamodelFixtures.entityType(9L, "dataset");
    projectionWithoutProvider.setCollectible(false);
    expectError(
        () -> validation.validateType(projectionWithoutProvider, true),
        MetadataErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void fieldTypesCannotBeCollectibleEntities() {
    MdTypeDefPO fieldType = MetamodelFixtures.fieldType(9L, "MONEY");
    fieldType.setCollectible(true);
    expectError(() -> validation.validateType(fieldType, true), MetadataErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void searchableFieldWithoutAnyLandingSpotIsRejected() {
    MdFieldDefPO field = MetamodelFixtures.field(1L, "engine", "STRING", null);
    field.setSearchable(true);
    expectError(
        () -> validation.validateField("table", field, true), MetadataErrorCode.FIELD_SLOT_REQUIRED);

    // 有槽即放行；固有列（layer_code）也是落点，不占 7 个槽里的名额。
    MdFieldDefPO slotted = MetamodelFixtures.field(1L, "engine", "STRING", "s_str_3");
    slotted.setSearchable(true);
    assertThatCode(() -> validation.validateField("table", slotted, true))
        .doesNotThrowAnyException();
    MdFieldDefPO viaNativeColumn = MetamodelFixtures.field(1L, "layerCode", "STRING", null);
    viaNativeColumn.setFacetable(true);
    assertThatCode(() -> validation.validateField("table", viaNativeColumn, true))
        .doesNotThrowAnyException();
  }

  @Test
  void facetableFieldMustAlsoHaveALandingSpot() {
    MdFieldDefPO field = MetamodelFixtures.field(1L, "engine", "STRING", null);
    field.setFacetable(true);
    expectError(
        () -> validation.validateField("table", field, true), MetadataErrorCode.FIELD_SLOT_REQUIRED);
  }

  @Test
  void fieldTypeMustResolveToACategoryFieldRow() {
    MdFieldDefPO unknown = MetamodelFixtures.field(1L, "tableName", "MONEY", null);
    expectError(
        () -> validation.validateField("table", unknown, true), MetadataErrorCode.FIELD_TYPE_REF_INVALID);

    MdFieldDefPO pointsAtEntity = MetamodelFixtures.field(1L, "tableName", "table", null);
    expectError(
        () -> validation.validateField("table", pointsAtEntity, true),
        MetadataErrorCode.FIELD_TYPE_REF_INVALID);
  }

  @Test
  void duplicateFieldNameInTheSameTypeIsRejected() {
    MetadataTypeRegistry withFields =
        MetamodelFixtures.registry(
            List.of(
                MetamodelFixtures.entityType(1L, "table"),
                MetamodelFixtures.fieldType(3L, "STRING")),
            List.of(MetamodelFixtures.field(1L, "tableName", "STRING", null)),
            0L);
    MetamodelValidationService underTest = MetamodelFixtures.validation(withFields);

    expectError(
        () -> underTest.validateField("table", MetamodelFixtures.field(1L, "tableName", "STRING", null), true),
        MetadataErrorCode.FIELD_NAME_DUPLICATE);

    // 更新自己不算重复。
    MdFieldDefPO same = MetamodelFixtures.field(1L, "tableName", "STRING", null);
    same.setId(withFields.require("table").field("tableName").orElseThrow().getId());
    assertThatCode(() -> underTest.validateField("table", same, false)).doesNotThrowAnyException();
  }

  @Test
  void entityReferenceFieldsMustPointAtRealEntityTypes() {
    MdFieldDefPO missingRef = MetamodelFixtures.field(1L, "relatedTable", "ENTITY_REFERENCE", null);
    missingRef.setFieldType("ENTITY_REFERENCE");
    expectError(
        () -> validation.validateField("table", missingRef, true), MetadataErrorCode.INVALID_ARGUMENT);

    missingRef.setEntityTypeRef("dataset");
    expectError(
        () -> validation.validateField("table", missingRef, true),
        MetadataErrorCode.ENTITY_REFERENCE_TYPE_INVALID);

    // 引用字段类型（STRING）不是实体 → 也要拒。
    MdFieldDefPO pointsAtFieldType = MetamodelFixtures.field(1L, "relatedTable", "ENTITY_REFERENCE", null);
    pointsAtFieldType.setFieldType("ENTITY_REFERENCE");
    pointsAtFieldType.setEntityTypeRef("STRING");
    expectError(
        () -> validation.validateField("table", pointsAtFieldType, true),
        MetadataErrorCode.ENTITY_REFERENCE_TYPE_INVALID);

    MdFieldDefPO ok = MetamodelFixtures.field(1L, "relatedTable", "ENTITY_REFERENCE", null);
    ok.setFieldType("ENTITY_REFERENCE");
    ok.setEntityTypeRef("table,database");
    assertThatCode(() -> validation.validateField("table", ok, true)).doesNotThrowAnyException();
  }

  @Test
  void badEnumValuesFailAs49037RatherThanLeakingIllegalArgumentException() {
    // Enum.valueOf 抛出去会被兜成 999，排查方向全错（plan §9 T3）。
    MdFieldDefPO field = MetamodelFixtures.field(1L, "tableName", "STRING", null);
    field.setBaseType("VARCHAR");
    expectError(
        () -> validation.validateField("table", field, true), MetadataErrorCode.INVALID_ARGUMENT);

    MdFieldDefPO badMatchType = MetamodelFixtures.field(1L, "tableName", "STRING", null);
    badMatchType.setMatchType("fuzzy");
    expectError(
        () -> validation.validateField("table", badMatchType, true),
        MetadataErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void fieldsCannotHangOffAFieldType() {
    expectError(
        () -> validation.validateField("STRING", MetamodelFixtures.field(3L, "x", "STRING", null), true),
        MetadataErrorCode.INVALID_ARGUMENT);
  }

  private static void expectError(Runnable call, MetadataErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            MetadataException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
  }
}
