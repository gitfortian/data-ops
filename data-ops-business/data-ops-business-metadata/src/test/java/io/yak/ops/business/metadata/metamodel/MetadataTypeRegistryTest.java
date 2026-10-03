package io.yak.ops.business.metadata.metamodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.dao.model.MdFieldDefPO;
import io.yak.ops.business.metadata.dao.model.MdTypeDefPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeStatus;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 类型注册表的读侧语义（plan §10 测试 7 的"不重启即生效"部分）。
 *
 * <p>它守的是整条可扩展性的命门：<b>元模型改了要不要重启</b>。答案必须是" TTL 到点或写完即生效"，
 * 否则"加一类元数据不改代码"只剩一半（数据库能改、进程不认）。
 * 真正的端到端（往库里插一行 {@code field_def} 后立刻能 {@code queryFilter}）要连库跑，见 ticket 117 的集成用例。
 */
class MetadataTypeRegistryTest {

  @Test
  void newlyInsertedFieldDefBecomesVisibleWithoutRestart() {
    List<MdTypeDefPO> types = List.of(MetamodelFixtures.entityType(1L, "table"));
    List<MdFieldDefPO> fields = new java.util.ArrayList<>();
    fields.add(MetamodelFixtures.field(1L, "tableName", "STRING", null));
    // ttl=0：每次读都回源。生产默认 30s，写接口另外主动 invalidate()。
    MetadataTypeRegistry registry = MetamodelFixtures.registry(types, fields, 0L);
    assertThat(registry.require("table").field("dataType")).isEmpty();

    MdFieldDefPO dataType = MetamodelFixtures.field(1L, "dataType", "STRING", "s_str_1");
    dataType.setSearchable(true);
    fields.add(dataType);

    assertThat(registry.require("table").field("dataType"))
        .as("插一行 field_def 即生效，不重启不改代码")
        .isPresent();
    assertThat(registry.require("table").searchableFields()).hasSize(1);
  }

  @Test
  void cacheIsActuallyUsedAndCanBeInvalidatedExplicitly() {
    List<MdTypeDefPO> types = List.of(MetamodelFixtures.entityType(1L, "table"));
    List<MdFieldDefPO> fields = new java.util.ArrayList<>();
    MetadataTypeRegistry registry = MetamodelFixtures.registry(types, fields, 60_000L);
    assertThat(registry.require("table").fields()).isEmpty();

    fields.add(MetamodelFixtures.field(1L, "tableName", "STRING", null));
    assertThat(registry.require("table").fields())
        .as("长 TTL 下不主动失效就看不到，这是缓存的本分")
        .isEmpty();

    registry.invalidate();
    assertThat(registry.require("table").fields()).hasSize(1);
  }

  @Test
  void unknownTypeNameFailsWith49002Not999() {
    MetadataTypeRegistry registry = MetamodelFixtures.registry(List.of(), List.of(), 0L);
    assertThatThrownBy(() -> registry.require("dataset"))
        .isInstanceOfSatisfying(
            MetadataException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(MetadataErrorCode.TYPE_NOT_FOUND));
  }

  @Test
  void paddedAndCasedTypeNamesStillResolve() {
    MetadataTypeRegistry registry =
        MetamodelFixtures.registry(List.of(MetamodelFixtures.entityType(1L, "table")), List.of(), 0L);
    assertThat(registry.find("  table ")).as("类型名两端空白不作数").isPresent();
    assertThat(registry.find("TABLE")).as("类型名大小写敏感：TABLE 不是 table").isEmpty();
  }

  @Test
  void fieldsComeBackInDeclaredOrder() {
    MdFieldDefPO second = MetamodelFixtures.field(1L, "bField", "STRING", null);
    second.setOrdinal(20);
    MdFieldDefPO first = MetamodelFixtures.field(1L, "aField", "STRING", null);
    first.setOrdinal(10);
    MetadataTypeRegistry registry =
        MetamodelFixtures.registry(
            List.of(MetamodelFixtures.entityType(1L, "table")), List.of(second, first), 0L);

    assertThat(registry.fields("table")).extracting(MdFieldDefPO::getFieldName)
        .containsExactly("aField", "bField");
  }

  @Test
  void onlyActiveEntityTypesDriveTheCatalog() {
    MdTypeDefPO table = MetamodelFixtures.entityType(1L, "table");
    MdTypeDefPO deprecated = MetamodelFixtures.entityType(2L, "legacyThing");
    deprecated.setStatus(TypeStatus.DEPRECATED.name());
    MdTypeDefPO fieldType = MetamodelFixtures.fieldType(3L, "STRING");
    MetadataTypeRegistry registry =
        MetamodelFixtures.registry(List.of(table, deprecated, fieldType), List.of(), 0L);

    assertThat(registry.activeEntityTypes()).extracting(MetadataTypeRegistry.TypeDefinition::typeName)
        .as("已废弃类型不再出现在 facet，但历史行仍能按 id 解析")
        .containsExactly("table");
    assertThat(registry.find("legacyThing")).isPresent();
  }
}
