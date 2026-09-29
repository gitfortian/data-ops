package io.yak.ops.business.metadata.metamodel;

import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.common.bean.po.metadata.MdFieldDefPO;
import io.yak.ops.common.bean.po.metadata.MdTypeDefPO;
import io.yak.ops.common.constant.metadata.MetadataLineageAssetTypes;
import io.yak.ops.common.enums.metadata.MetadataEnums.BaseType;
import io.yak.ops.common.enums.metadata.MetadataEnums.MatchType;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeCategory;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeStatus;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 元模型的写前校验（ticket 129）：**错配置进不了库**。
 *
 * <p>为什么校验必须在这里、而不是等到采集或渲染时才发现：元模型是"类型即数据"，
 * 一行写歪的 {@code type_def} 会让后面所有实体都带着坏配置进目录——
 * 例如 {@code lineage_asset_type} 拼错，写入时一切正常，直到别人跑血缘查询时
 * {@code LineageAssetType.valueOf(该行)} 抛异常（plan §2.3 后果 1）。
 * 这类错误的排查成本随存量线性增长，所以只有"保存即拒"这一条防线划算。
 */
@Service
public class MetamodelValidationService {

  private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][A-Za-z0-9]{1,63}$");
  /** asset_key 前缀：小写段以 ":" 分隔且以 ":" 收尾（现网 model: / modeling:model: / semantic:field: 都是这形状）。 */
  private static final Pattern KEY_PREFIX_PATTERN =
      Pattern.compile("^[a-z][a-z0-9]*(:[a-z][a-z0-9]*)*:$");
  private static final Pattern FQN_PLACEHOLDER_PATTERN = Pattern.compile("\\{[A-Za-z][A-Za-z0-9]*}");

  private final MetadataTypeRegistry typeRegistry;
  private final MetadataSlotRegistry slotRegistry;

  public MetamodelValidationService(
      MetadataTypeRegistry typeRegistry, MetadataSlotRegistry slotRegistry) {
    this.typeRegistry = typeRegistry;
    this.slotRegistry = slotRegistry;
  }

  /**
   * @param isNew true=新建。{@code lineage_asset_type} 只在<b>新建</b>时硬必填：
   *     {@code databaseService}/{@code database}/{@code domain} 三行由迁移灌成 NULL
   *     （等 ticket 134 给枚举加值），若更新也拒 NULL，就没人能通过保存把它们补齐了。
   *     运行时的登记路径另有 49005，NULL 行进不了目录。
   */
  public void validateType(MdTypeDefPO candidate, boolean isNew) {
    requirePattern(candidate.getTypeName(), NAME_PATTERN, "type_name");
    requireIn(candidate.getCategory(), TypeCategory.values(), "category");
    requireIn(candidate.getStatus(), TypeStatus.values(), "status");
    requireText(candidate.getNameSpace(), "name_space");
    if (candidate.getSearchDefaultWeight() == null || candidate.getSearchDefaultWeight() <= 0f) {
      throw invalid("search_default_weight 必须为正");
    }
    if (TypeCategory.FIELD.name().equals(candidate.getCategory())) {
      if (Boolean.TRUE.equals(candidate.getCollectible())) {
        throw invalid("category=FIELD 是字段的类型，不是可采集实体");
      }
      return;
    }
    validateEntityType(candidate, isNew);
  }

  private void validateEntityType(MdTypeDefPO candidate, boolean isNew) {
    requirePattern(candidate.getKeyPrefix(), KEY_PREFIX_PATTERN, "key_prefix");
    requireFqnPattern(candidate.getFqnPattern());
    String lineageAssetType = candidate.getLineageAssetType();
    if (isBlank(lineageAssetType)) {
      if (isNew) {
        throw new MetadataException(
            MetadataErrorCode.LINEAGE_ASSET_TYPE_INVALID,
            "ENTITY 必须声明 lineage_asset_type；ticket 134 尚未给该枚举加值的类型请先不建");
      }
    } else if (!MetadataLineageAssetTypes.isKnown(lineageAssetType)) {
      // 放过去 = 别人读这行时 valueOf 抛异常，炸的是他们的血缘查询（§2.3 后果 1）。
      throw new MetadataException(
          MetadataErrorCode.LINEAGE_ASSET_TYPE_INVALID, "lineage_asset_type=" + lineageAssetType);
    }
    // 两条入口互斥：采集型由元数据主动拉，投影型由源域写时登记 + 定时对账（plan §1.3）。
    if (Boolean.TRUE.equals(candidate.getCollectible())) {
      if (!isBlank(candidate.getProviderBean())) {
        throw invalid("collectible=1 的实体不该有 provider_bean（对账副通道只服务投影类型）");
      }
    } else {
      requireText(candidate.getProviderBean(), "provider_bean");
    }
    boolean prefixTaken =
        typeRegistry.allTypeDefinitions().stream()
            .filter(definition -> !definition.typeName().equals(candidate.getTypeName()))
            .map(definition -> definition.type().getKeyPrefix())
            .anyMatch(candidate.getKeyPrefix()::equals);
    if (prefixTaken) {
      // 前缀是"从 asset_key 认类型"的唯一依据，两个类型共用一个就没法反解了。
      throw invalid("key_prefix 已被其它实体类型占用: " + candidate.getKeyPrefix());
    }
  }

  public void validateField(String typeName, MdFieldDefPO candidate, boolean isNew) {
    requirePattern(candidate.getFieldName(), NAME_PATTERN, "field_name");
    MetadataTypeRegistry.TypeDefinition owner = typeRegistry.find(typeName)
        .orElseThrow(() -> new MetadataException(MetadataErrorCode.TYPE_NOT_FOUND, "type=" + typeName));
    if (!owner.isEntity()) {
      throw invalid("字段只能挂在 category=ENTITY 的类型上");
    }
    owner.field(candidate.getFieldName())
        .filter(existing -> isNew || !existing.getId().equals(candidate.getId()))
        .ifPresent(existing -> {
          throw new MetadataException(
              MetadataErrorCode.FIELD_NAME_DUPLICATE, "type=" + typeName + " field=" + existing.getFieldName());
        });

    MetadataTypeRegistry.TypeDefinition fieldType = typeRegistry.find(candidate.getFieldType())
        .orElseThrow(
            () ->
                new MetadataException(
                    MetadataErrorCode.FIELD_TYPE_REF_INVALID, "field_type=" + candidate.getFieldType()));
    if (!TypeCategory.FIELD.name().equals(fieldType.type().getCategory())) {
      throw new MetadataException(
          MetadataErrorCode.FIELD_TYPE_REF_INVALID, "field_type 必须解析到 category=FIELD: " + candidate.getFieldType());
    }
    BaseType baseType = parseBaseType(candidate.getBaseType());
    requireIn(candidate.getMatchType(), MatchType.values(), MatchType::value, "match_type");

    // 防线一（plan §4.5）：要进检索面/筛选面板就得有落点，否则查询层只能对 JSON 袋做 LIKE。
    MetadataFieldLocations.FieldLocation location = MetadataFieldLocations.of(candidate);
    if ((isTrue(candidate.getSearchable()) || isTrue(candidate.getFacetable()))
        && !location.filterable()) {
      throw new MetadataException(
          MetadataErrorCode.FIELD_SLOT_REQUIRED,
          "field=" + candidate.getFieldName() + " 需要 storage_slot 或固有列落点");
    }
    slotRegistry.validateAssignment(typeName, candidate);

    if (baseType == BaseType.ENTITY_REFERENCE) {
      requireText(candidate.getEntityTypeRef(), "entity_type_ref");
      for (String reference : candidate.getEntityTypeRef().split(",")) {
        String target = reference.trim();
        if (target.isEmpty()) {
          continue;
        }
        MetadataTypeRegistry.TypeDefinition referenced = typeRegistry.find(target)
            .orElseThrow(
                () ->
                    new MetadataException(
                        MetadataErrorCode.ENTITY_REFERENCE_TYPE_INVALID, "entity_type_ref=" + target));
        if (!referenced.isEntity()) {
          throw new MetadataException(
              MetadataErrorCode.ENTITY_REFERENCE_TYPE_INVALID, "只能引用实体类型: " + target);
        }
      }
    }
  }

  private static void requireFqnPattern(String pattern) {
    requireText(pattern, "fqn_pattern");
    Matcher matcher = FQN_PLACEHOLDER_PATTERN.matcher(pattern);
    String stripped = matcher.replaceAll("");
    if (!matcher.find(0) || stripped.contains("{") || stripped.contains("}")) {
      throw invalid("fqn_pattern 需由 {占位符} 与分隔符组成，且不得有裸括号: " + pattern);
    }
  }

  private static boolean isTrue(Boolean value) {
    return Boolean.TRUE.equals(value);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static void requireText(String value, String field) {
    if (isBlank(value)) {
      throw invalid(field + " 必填");
    }
  }

  private static void requirePattern(String value, Pattern pattern, String field) {
    if (isBlank(value) || !pattern.matcher(value).matches()) {
      throw invalid(field + " 不合法(须小写开头的 camelCase，≤64): " + value);
    }
  }

  private static <E extends Enum<E>> void requireIn(String value, E[] values, String field) {
    requireIn(value, values, Enum::name, field);
  }

  private static <E extends Enum<E>> void requireIn(
      String value, E[] values, Function<E, String> key, String field) {
    List<E> known = Arrays.asList(values);
    boolean matched = value != null && known.stream().anyMatch(item -> key.apply(item).equals(value));
    if (!matched) {
      throw invalid(field + " 取值不合法: " + value);
    }
  }

  private static BaseType parseBaseType(String value) {
    try {
      return BaseType.valueOf(value);
    } catch (IllegalArgumentException | NullPointerException notAValueType) {
      // 不能留给 Enum.valueOf：抛 IllegalArgumentException 会被兜成 999，排查方向全错（plan §9 T3）。
      throw invalid("base_type 取值不合法: " + value);
    }
  }

  private static MetadataException invalid(String message) {
    return new MetadataException(MetadataErrorCode.INVALID_ARGUMENT, message);
  }
}
