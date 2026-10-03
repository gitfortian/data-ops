package io.yak.ops.business.metadata.metamodel;

import io.yak.ops.business.metadata.controller.v1.dto.MetamodelRequests;
import io.yak.ops.business.metadata.dao.mapper.MdFieldDefMapper;
import io.yak.ops.business.metadata.dao.mapper.MdTypeDefMapper;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.dao.model.MdFieldDefPO;
import io.yak.ops.business.metadata.dao.model.MdTypeDefPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.SlotName;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeStatus;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 元模型的读写入口（ticket 129）。
 *
 * <p><b>为什么 {@code GET /types} 是"唯一来源"</b>：前端目录页的列清单、详情页的面板、搜索的
 * 类型 facet 全部由这里的行驱动。写死一份类型常量就等于把"加一类元数据不改代码"这条卖点作废
 * （ticket 132 的 grep 守护守的就是它）。
 *
 * <p><b>为什么这里不加 {@code @Transactional}</b>：每个方法都是单条 insert/update，
 * 自身即原子；把 {@link MetadataTypeRegistry#invalidate()} 放在事务里反而会制造
 * "提交前被并发读方回填旧快照"的窗口。元模型是低频配置写，不是一致性单元。
 */
@Service
public class MetamodelAdminService {

  private static final String NAMESPACE_PLATFORM = "platform";

  private final MdTypeDefMapper typeMapper;
  private final MdFieldDefMapper fieldMapper;
  private final MetadataTypeRegistry typeRegistry;
  private final MetadataSlotRegistry slotRegistry;
  private final MetamodelValidationService validation;

  public MetamodelAdminService(
      MdTypeDefMapper typeMapper,
      MdFieldDefMapper fieldMapper,
      MetadataTypeRegistry typeRegistry,
      MetadataSlotRegistry slotRegistry,
      MetamodelValidationService validation) {
    this.typeMapper = typeMapper;
    this.fieldMapper = fieldMapper;
    this.typeRegistry = typeRegistry;
    this.slotRegistry = slotRegistry;
    this.validation = validation;
  }

  /** 类型清单（前端渲染与检索配置的唯一来源）。 */
  public List<TypeView> listTypes(String category, boolean includeFields) {
    return typeRegistry.allTypeDefinitions().stream()
        .filter(definition -> category == null || category.isBlank()
            || category.equals(definition.type().getCategory()))
        .map(definition -> TypeView.of(definition, includeFields))
        .toList();
  }

  public TypeView getType(String typeName, boolean includeFields) {
    return TypeView.of(typeRegistry.require(typeName), includeFields);
  }

  /** 槽位占用与余量：plan §2.4.1 要"槽够不够"在第一天就被评审，而不是等到第 20 个扩展字段。 */
  public SlotBoard slotBoard() {
    List<SlotView> used = slotRegistry.occupancy().stream()
        .map(usage -> new SlotView(usage.slot(), usage.typeName(), usage.fieldName()))
        .toList();
    List<String> capacity =
        Arrays.stream(SlotName.values()).map(SlotName::column).toList();
    return new SlotBoard(capacity, used, slotRegistry.freeSlots());
  }

  public TypeView createType(MetamodelRequests.TypeDefDTO dto) {
    MdTypeDefPO candidate = new MdTypeDefPO();
    apply(candidate, dto);
    if (NAMESPACE_PLATFORM.equals(candidate.getNameSpace())) {
      // platform 命名空间由迁移脚本拥有：接口造出来的"平台类型"会让两侧的定义对不上。
      throw new MetadataException(
          MetadataErrorCode.INVALID_ARGUMENT, "name_space=platform 只允许由迁移脚本写入");
    }
    if (typeRegistry.find(candidate.getTypeName()).isPresent()) {
      throw new MetadataException(MetadataErrorCode.TYPE_NAME_DUPLICATE, "type=" + candidate.getTypeName());
    }
    validation.validateType(candidate, true);
    typeMapper.insert(candidate);
    typeRegistry.invalidate();
    return TypeView.of(new MetadataTypeRegistry.TypeDefinition(candidate, List.of()), true);
  }

  public TypeView updateType(String typeName, MetamodelRequests.TypeDefDTO dto) {
    MetadataTypeRegistry.TypeDefinition existing = typeRegistry.require(typeName);
    MdTypeDefPO merged = existing.type();
    if (!Objects.equals(merged.getTypeName(), typeName)) {
      throw new MetadataException(MetadataErrorCode.INVALID_ARGUMENT, "路径与 body 的 typeName 不一致");
    }
    apply(merged, dto);
    validation.validateType(merged, false);
    typeMapper.updateById(merged);
    typeRegistry.invalidate();
    return TypeView.of(new MetadataTypeRegistry.TypeDefinition(merged, existing.fields()), true);
  }

  public TypeView archiveType(String typeName) {
    MetadataTypeRegistry.TypeDefinition existing = typeRegistry.require(typeName);
    MdTypeDefPO type = existing.type();
    // 永不物理删：历史目录行的 type_id 还要能解析（plan §2.2 status 列注释）。
    type.setStatus(TypeStatus.DEPRECATED.name());
    typeMapper.updateById(type);
    typeRegistry.invalidate();
    return TypeView.of(existing, false);
  }

  public FieldView createField(String typeName, MetamodelRequests.FieldDefDTO dto) {
    MetadataTypeRegistry.TypeDefinition owner = typeRegistry.require(typeName);
    MdFieldDefPO candidate = new MdFieldDefPO();
    apply(candidate, dto);
    candidate.setTypeId(owner.type().getId());
    validation.validateField(typeName, candidate, true);
    fieldMapper.insert(candidate);
    typeRegistry.invalidate();
    return FieldView.of(candidate);
  }

  public FieldView updateField(String typeName, String fieldName, MetamodelRequests.FieldDefDTO dto) {
    MetadataTypeRegistry.TypeDefinition owner = typeRegistry.require(typeName);
    MdFieldDefPO merged = owner.field(fieldName)
        .orElseThrow(
            () ->
                new MetadataException(
                    MetadataErrorCode.ATTRIBUTE_NOT_DEFINED, "type=" + typeName + " field=" + fieldName));
    apply(merged, dto);
    if (!Objects.equals(merged.getFieldName(), fieldName)) {
      throw new MetadataException(MetadataErrorCode.INVALID_ARGUMENT, "路径与 body 的 fieldName 不一致");
    }
    validation.validateField(typeName, merged, false);
    fieldMapper.updateById(merged);
    typeRegistry.invalidate();
    return FieldView.of(merged);
  }

  private static void apply(MdTypeDefPO target, MetamodelRequests.TypeDefDTO dto) {
    target.setTypeName(dto.getTypeName().trim());
    target.setCategory(dto.getCategory().trim().toUpperCase());
    target.setDisplayName(dto.getDisplayName());
    target.setParentTypes(emptyToNull(dto.getParentTypes()));
    target.setFqnPattern(emptyToNull(dto.getFqnPattern()));
    target.setProviderBean(emptyToNull(dto.getProviderBean()));
    target.setLineageAssetType(emptyToNull(dto.getLineageAssetType()));
    target.setKeyPrefix(emptyToNull(dto.getKeyPrefix()));
    target.setIconUrl(emptyToNull(dto.getIconUrl()));
    target.setColor(dto.getColor());
    target.setDescription(dto.getDescription());
    target.setNameSpace(firstNonBlank(dto.getNameSpace(), "custom"));
    target.setKeySeparator(firstNonBlank(dto.getKeySeparator(), "."));
    target.setStatus(firstNonBlank(dto.getStatus(), TypeStatus.ACTIVE.name()));
    target.setCollectible(Boolean.TRUE.equals(dto.getCollectible()));
    target.setSearchIncludeByDefault(dto.getSearchIncludeByDefault() == null
        || Boolean.TRUE.equals(dto.getSearchIncludeByDefault()));
    target.setSearchDefaultWeight(dto.getSearchDefaultWeight() == null ? 1.0f : dto.getSearchDefaultWeight());
    if (target.getId() == null) {
      target.setVersion(1);
    }
  }

  private static void apply(MdFieldDefPO target, MetamodelRequests.FieldDefDTO dto) {
    target.setFieldName(dto.getFieldName().trim());
    target.setFieldType(dto.getFieldType().trim());
    target.setDisplayName(dto.getDisplayName());
    target.setDescription(dto.getDescription());
    target.setEntityTypeRef(emptyToNull(dto.getEntityTypeRef()));
    target.setConstraintDef(emptyToNull(dto.getConstraintDef()));
    target.setDefaultValue(emptyToNull(dto.getDefaultValue()));
    target.setStorageSlot(emptyToNull(dto.getStorageSlot()));
    target.setRequired(Boolean.TRUE.equals(dto.getRequired()));
    target.setIsNull(dto.getIsNull() == null || Boolean.TRUE.equals(dto.getIsNull()));
    target.setSearchable(Boolean.TRUE.equals(dto.getSearchable()));
    target.setFacetable(Boolean.TRUE.equals(dto.getFacetable()));
    target.setShowInList(Boolean.TRUE.equals(dto.getShowInList()));
    target.setDeprecated(Boolean.TRUE.equals(dto.getDeprecated()));
    target.setBaseType(dto.getBaseType().trim().toUpperCase());
    target.setMatchType(firstNonBlank(dto.getMatchType(), "text"));
    target.setBoost(dto.getBoost() == null ? 1.0f : dto.getBoost());
    target.setOrdinal(dto.getOrdinal() == null ? 0 : dto.getOrdinal());
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String firstNonBlank(String value, String fallback) {
    String trimmed = emptyToNull(value);
    return trimmed == null ? fallback : trimmed;
  }

  /** 类型视图：实体类型带字段清单，字段类型只带自身。 */
  public record TypeView(
      Long id,
      String typeName,
      String category,
      String nameSpace,
      String displayName,
      String parentTypes,
      String fqnPattern,
      String keySeparator,
      String providerBean,
      String lineageAssetType,
      String keyPrefix,
      boolean collectible,
      float searchDefaultWeight,
      boolean searchIncludeByDefault,
      String iconUrl,
      String color,
      String status,
      Integer version,
      String description,
      List<FieldView> fields) {

    static TypeView of(MetadataTypeRegistry.TypeDefinition definition, boolean includeFields) {
      MdTypeDefPO type = definition.type();
      return new TypeView(
          type.getId(),
          type.getTypeName(),
          type.getCategory(),
          type.getNameSpace(),
          type.getDisplayName(),
          type.getParentTypes(),
          type.getFqnPattern(),
          type.getKeySeparator(),
          type.getProviderBean(),
          type.getLineageAssetType(),
          type.getKeyPrefix(),
          Boolean.TRUE.equals(type.getCollectible()),
          type.getSearchDefaultWeight() == null ? 1.0f : type.getSearchDefaultWeight(),
          !Boolean.FALSE.equals(type.getSearchIncludeByDefault()),
          type.getIconUrl(),
          type.getColor(),
          type.getStatus(),
          type.getVersion(),
          type.getDescription(),
          includeFields ? definition.fields().stream().map(FieldView::of).toList() : List.of());
    }
  }

  /**
   * 字段视图。多带一个 {@code filterable}：UI 据此决定要不要给这个字段渲染筛选控件
   * ——能筛的才给控件，而不是让人点了才发现没反应（"能选择就不填"在元模型这一侧的落法）。
   */
  public record FieldView(
      Long id,
      String fieldName,
      String fieldType,
      String displayName,
      String description,
      String baseType,
      String entityTypeRef,
      String constraintDef,
      String defaultValue,
      boolean required,
      boolean isNull,
      boolean searchable,
      String matchType,
      float boost,
      boolean facetable,
      boolean filterable,
      String storageSlot,
      String jsonKey,
      Integer ordinal,
      boolean showInList,
      boolean deprecated) {

    static FieldView of(MdFieldDefPO field) {
      MetadataFieldLocations.FieldLocation location = MetadataFieldLocations.of(field);
      return new FieldView(
          field.getId(),
          field.getFieldName(),
          field.getFieldType(),
          field.getDisplayName(),
          field.getDescription(),
          field.getBaseType(),
          field.getEntityTypeRef(),
          field.getConstraintDef(),
          field.getDefaultValue(),
          Boolean.TRUE.equals(field.getRequired()),
          !Boolean.FALSE.equals(field.getIsNull()),
          Boolean.TRUE.equals(field.getSearchable()),
          field.getMatchType(),
          field.getBoost() == null ? 1.0f : field.getBoost(),
          Boolean.TRUE.equals(field.getFacetable()),
          location.filterable(),
          field.getStorageSlot(),
          location.jsonKey(),
          field.getOrdinal(),
          Boolean.TRUE.equals(field.getShowInList()),
          Boolean.TRUE.equals(field.getDeprecated()));
    }
  }

  public record SlotView(String slot, String typeName, String fieldName) {}

  /** 已用/空闲两张表就是"7 个槽够不够"的全部答案，随概览页露出（ticket 126 的指标位）。 */
  public record SlotBoard(List<String> capacity, List<SlotView> used, List<String> free) {}
}
