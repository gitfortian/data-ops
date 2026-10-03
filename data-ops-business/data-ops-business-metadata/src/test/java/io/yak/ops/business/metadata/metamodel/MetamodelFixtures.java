package io.yak.ops.business.metadata.metamodel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metadata.dao.mapper.MdFieldDefMapper;
import io.yak.ops.business.metadata.dao.mapper.MdTypeDefMapper;
import io.yak.ops.business.metadata.dao.model.MdFieldDefPO;
import io.yak.ops.business.metadata.dao.model.MdTypeDefPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeCategory;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeStatus;
import java.util.List;

/** 元模型单测的公共夹具：不起 Spring、不连库，mapper 用 stub。 */
final class MetamodelFixtures {

  private MetamodelFixtures() {}

  static MetadataTypeRegistry registry(
      List<MdTypeDefPO> types, List<MdFieldDefPO> fields, long ttlMillis) {
    MdTypeDefMapper typeMapper = mock(MdTypeDefMapper.class);
    MdFieldDefMapper fieldMapper = mock(MdFieldDefMapper.class);
    when(typeMapper.selectList(any())).thenReturn(types);
    when(fieldMapper.selectList(any())).thenReturn(fields);
    return new MetadataTypeRegistry(typeMapper, fieldMapper, ttlMillis);
  }

  static MetadataSlotRegistry slots(MetadataTypeRegistry registry) {
    return new MetadataSlotRegistry(registry);
  }

  static MetamodelValidationService validation(MetadataTypeRegistry registry) {
    return new MetamodelValidationService(registry, slots(registry));
  }

  static MdTypeDefPO entityType(long id, String typeName) {
    MdTypeDefPO type = type(id, typeName, TypeCategory.ENTITY);
    type.setKeyPrefix(typeName + ":");
    type.setFqnPattern("{name}");
    type.setLineageAssetType("TABLE");
    type.setCollectible(true);
    return type;
  }

  static MdTypeDefPO fieldType(long id, String typeName) {
    return type(id, typeName, TypeCategory.FIELD);
  }

  static MdTypeDefPO type(long id, String typeName, TypeCategory category) {
    MdTypeDefPO type = new MdTypeDefPO();
    type.setId(id);
    type.setTypeName(typeName);
    type.setCategory(category.name());
    type.setNameSpace("platform");
    type.setDisplayName(typeName);
    type.setKeySeparator(".");
    type.setCollectible(false);
    type.setSearchDefaultWeight(1.0f);
    type.setSearchIncludeByDefault(true);
    type.setStatus(TypeStatus.ACTIVE.name());
    type.setVersion(1);
    return type;
  }

  static MdFieldDefPO field(long typeId, String fieldName, String baseType, String slot) {
    MdFieldDefPO field = new MdFieldDefPO();
    field.setId(typeId * 1000 + Math.abs(fieldName.hashCode() % 1000));
    field.setTypeId(typeId);
    field.setFieldName(fieldName);
    field.setFieldType(baseType);
    field.setDisplayName(fieldName);
    field.setBaseType(baseType);
    field.setMatchType("exact");
    field.setBoost(1.0f);
    field.setRequired(false);
    field.setIsNull(true);
    field.setSearchable(false);
    field.setFacetable(false);
    field.setShowInList(false);
    field.setDeprecated(false);
    field.setStorageSlot(slot);
    field.setOrdinal(0);
    return field;
  }
}
