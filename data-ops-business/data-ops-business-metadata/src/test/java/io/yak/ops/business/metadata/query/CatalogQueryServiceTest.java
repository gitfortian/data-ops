package io.yak.ops.business.metadata.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metadata.api.EntityDTO;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry.TypeDefinition;
import io.yak.ops.business.metadata.dao.model.MdFieldDefPO;
import io.yak.ops.business.metadata.dao.model.MdTypeDefPO;
import io.yak.ops.core.project.CurrentProject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * 目录行的按键直读与 DTO 换算（ticket 118）。
 *
 * <p>{@code MetadataQueryApi} 的"禁止第二套行映射"能不能立住，看的就是这里：行 Map 换形状时
 * 类型名、属性袋、槽值三样各归一位，且搜索侧已经翻译过 typeName 的行也走得通同一扇门。
 */
class CatalogQueryServiceTest {

  private final MetadataTypeRegistry typeRegistry = mock(MetadataTypeRegistry.class);
  private final CurrentProject currentProject = mock(CurrentProject.class);
  private final CatalogQueryService service = new CatalogQueryService(
      mock(DataSource.class), new ObjectMapper(), typeRegistry, currentProject);

  @Test
  void slotValuesAreReportedByFieldNameNotSlotColumn() {
    when(typeRegistry.find("table")).thenReturn(Optional.of(type("table", null)));
    when(typeRegistry.allTypeDefinitions()).thenReturn(List.of(type("table", null)));
    when(typeRegistry.fields("table"))
        .thenReturn(List.of(field("tableName", "STRING", "s_str_1"), field("comment", "STRING", null)));

    EntityDTO dto = service.toEntityDto(tableRow(3L, "s_str_1"));

    assertThat(dto.typeName()).isEqualTo("table");
    assertThat(dto.slotValues())
        .as("槽键翻回字段名：消费方不需要知道哪个字段占了哪个槽")
        .containsEntry("tableName", "ods_order");
    assertThat(dto.attributes())
        .as("属性袋保持原样，槽只是它的物化视图")
        .containsEntry("comment", "订单表");
    assertThat(dto.facts())
        .as("type_id 不出 API 层（plan §4.2）")
        .doesNotContainKey("typeId");
    assertThat(dto.tableName()).isEqualTo("ods_order");
  }

  @Test
  void aRowAlreadyTranslatedByTheSearchPathNeedsNoSecondRead() {
    Map<String, Object> row = tableRow(3L, "s_str_1");
    row.remove("typeId");
    row.put("typeName", "table");

    assertThat(service.toEntityDto(row).typeName())
        .as("搜索侧的行 typeId 已换成 typeName（它还要出 typeDisplayName），不能再反查一次")
        .isEqualTo("table");
  }

  @Test
  void unparsableAttributeBagStaysVisibleInsteadOfBecomingEmpty() {
    when(typeRegistry.find("table")).thenReturn(Optional.of(type("table", null)));
    when(typeRegistry.fields("table")).thenReturn(List.of());
    Map<String, Object> row = tableRow(3L, "s_str_1");
    row.put("attributes", "{not json");

    EntityDTO dto = service.toEntityDto(row);

    assertThat(dto.attributes()).isEmpty();
    assertThat(dto.facts()).containsEntry("attributesUnparsed", "{not json");
  }

  @Test
  void nullableDirectoryFactsAndSlotValuesRemainReadable() {
    when(typeRegistry.allTypeDefinitions()).thenReturn(List.of(type("table", null)));
    when(typeRegistry.fields("table"))
        .thenReturn(List.of(field("tableName", "STRING", "s_str_1")));
    Map<String, Object> row = tableRow(3L, "s_str_1");
    row.put("ownerUser", null);
    row.put("schemaName", null);
    Map<String, Object> bag = new LinkedHashMap<>();
    bag.put("s_str_1", null);
    row.put("attributes", bag);

    EntityDTO dto = service.toEntityDto(row);

    assertThat(dto.facts())
        .containsEntry("ownerUser", null)
        .containsEntry("schemaName", null);
    assertThat(dto.slotValues()).containsEntry("tableName", null);
  }

  @Test
  void childTypeComesFromTheParentPairDeclaredInTypeDef() {
    when(typeRegistry.allTypeDefinitions())
        .thenReturn(List.of(type("table", null), type("tableColumn", "table")));

    assertThat(service.childTypeOf("table")).contains("tableColumn");
    assertThat(service.childTypeOf("tableColumn"))
        .as("没有子级类型 = 空，不是异常")
        .isEmpty();
  }

  private static Map<String, Object> tableRow(long typeId, String slotKey) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", 21L);
    row.put("typeId", typeId);
    row.put("assetKey", "table:1:ods:t1");
    row.put("providerType", "HARVESTED");
    row.put("tableName", "ods_order");
    Map<String, Object> bag = new LinkedHashMap<>();
    bag.put(slotKey, "ods_order");
    bag.put("comment", "订单表");
    row.put("attributes", bag);
    return row;
  }

  private static TypeDefinition type(String typeName, String parentTypes) {
    MdTypeDefPO po = new MdTypeDefPO();
    po.setId(typeName.equals("table") ? 3L : 4L);
    po.setTypeName(typeName);
    po.setCategory("ENTITY");
    po.setStatus("ACTIVE");
    po.setParentTypes(parentTypes);
    return new TypeDefinition(po, List.of());
  }

  private static MdFieldDefPO field(String fieldName, String baseType, String slot) {
    MdFieldDefPO field = new MdFieldDefPO();
    field.setFieldName(fieldName);
    field.setBaseType(baseType);
    field.setStorageSlot(slot);
    field.setDeprecated(false);
    return field;
  }
}
