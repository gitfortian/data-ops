package io.yak.ops.business.metadata.harvest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metadata.dao.model.CatalogAssetRow;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.metamodel.MetadataFieldLocations;
import io.yak.ops.business.metadata.metamodel.MetadataFieldLocations.FieldLocation;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry.TypeDefinition;
import io.yak.ops.common.bean.po.metadata.MdFieldDefPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.BaseType;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code md_attributes} 的唯一写入处（架构守卫按文件名放行本类）。
 *
 * <p>三件事，都不是"顺手"能省掉的：
 * <ol>
 *   <li><b>未登记的属性直接拒</b>（49012）。半年后"这个字段是谁写的"必须有答案，
 *       否则目录就成了一袋说不清来源的值（plan §9 T18）。</li>
 *   <li><b>提槽字段用槽名当键</b>：生成列取的是 {@code $."<槽名>"}，写字段名的话
 *       槽永远是 NULL、筛选永远命中零行，而且不报错（{@link MetadataFieldLocations}）。</li>
 *   <li><b>值必须按 {@code base_type} 归一</b>，因为生成列的 CAST 是 MySQL 在做：
 *       实测 {@code s_date_1} 收到 JSON 数字直接 {@code 3156 Invalid JSON value for CAST to DATETIME}
 *       整条写入失败，日期一律得是 {@code yyyy-MM-dd HH:mm:ss} 文本（本机 8.0.46 预演）。</li>
 * </ol>
 *
 * <p>键序按 {@code field_def.ordinal} 固定，与调用方传 Map 的顺序无关：属性袋逐字节稳定，
 * upsert 里那条 {@code md_attributes <=> VALUES(md_attributes)} 才能真正挡住无意义重写。
 */
@Component
public class MetadataAttributeCodec {

  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter DATE_TIME_MICROS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  /** 生成列 s_str_* 是 VARCHAR(256)；超了不是截断问题，是这条 INSERT 直接 1406。 */
  private static final int SLOT_STRING_MAX = 256;

  private final ObjectMapper objectMapper;

  public MetadataAttributeCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * 校验 + 序列化 + 落到行上。{@code values} 的键是<b>字段名</b>（不是槽名），
   * 键的换算是本类的活——调用方不该知道哪些字段被提了槽。
   */
  public void applyTo(
      CatalogAssetRow row, TypeDefinition definition, Map<String, ?> values) {
    row.setMdAttributes(encode(definition, values));
  }

  /** 只产出 JSON 文本，给不写共表的调用方（单测与登记侧复用）。 */
  public String encode(TypeDefinition definition, Map<String, ?> values) {
    Map<String, Object> bag = new LinkedHashMap<>();
    for (MdFieldDefPO field : definition.fields()) {
      FieldLocation location = MetadataFieldLocations.of(field);
      if (location.kind() == MetadataFieldLocations.Kind.NATIVE) {
        // 固有列的值走列本身（entity_status/layer_code/…），进袋就是第二份没人读的死数据。
        continue;
      }
      Object raw = values == null ? null : values.get(field.getFieldName());
      Object normalized = normalize(definition, field, raw);
      if (normalized == null) {
        continue;
      }
      bag.put(location.jsonKey(), normalized);
    }
    if (values != null) {
      for (String key : values.keySet()) {
        if (definition.field(key).isEmpty()) {
          throw new MetadataException(
              MetadataErrorCode.ATTRIBUTE_NOT_DEFINED,
              "type=" + definition.typeName() + " field=" + key);
        }
      }
    }
    try {
      return objectMapper.writeValueAsString(bag);
    } catch (JsonProcessingException exception) {
      throw new MetadataException(
          MetadataErrorCode.INVALID_ARGUMENT, "属性袋序列化失败", exception);
    }
  }

  /** 某字段在本行里的实际键（查询侧/校验侧共用，避免两边各拼一次）。 */
  public String jsonKey(MdFieldDefPO field) {
    return MetadataFieldLocations.of(field).jsonKey();
  }

  private Object normalize(TypeDefinition definition, MdFieldDefPO field, Object raw) {
    if (raw == null) {
      return null;
    }
    FieldLocation location = MetadataFieldLocations.of(field);
    BaseType baseType = baseTypeOf(field);
    Object value = switch (baseType) {
      case STRING, ENTITY_REFERENCE -> text(raw);
      case INTEGER -> integer(raw);
      case NUMBER -> decimal(raw);
      case BOOLEAN -> bool(raw);
      case DATE -> date(raw);
      case DATETIME -> dateTime(raw);
      case JSON, ARRAY -> throw new MetadataException(
          MetadataErrorCode.INVALID_ARGUMENT,
          "结构化属性应落 yak_md_asset_extension，不进属性袋：type="
              + definition.typeName()
              + " field="
              + field.getFieldName());
    };
    if (value != null
        && location.kind() == MetadataFieldLocations.Kind.SLOT
        && value instanceof String text
        && text.length() > SLOT_STRING_MAX) {
      throw new MetadataException(
          MetadataErrorCode.INVALID_ARGUMENT,
          "提槽字段 " + field.getFieldName() + " 的值超过槽位宽度 " + SLOT_STRING_MAX);
    }
    return value;
  }

  private BaseType baseTypeOf(MdFieldDefPO field) {
    try {
      return BaseType.valueOf(field.getBaseType());
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new MetadataException(
          MetadataErrorCode.FIELD_TYPE_REF_INVALID, "base_type=" + field.getBaseType());
    }
  }

  private String text(Object raw) {
    String value = String.valueOf(raw).trim();
    return value.isEmpty() ? null : value;
  }

  private Long integer(Object raw) {
    if (raw instanceof Number number) {
      return number.longValue();
    }
    String value = text(raw);
    return value == null ? null : Long.parseLong(value);
  }

  private BigDecimal decimal(Object raw) {
    if (raw instanceof Number number) {
      return new BigDecimal(number.toString());
    }
    String value = text(raw);
    return value == null ? null : new BigDecimal(value);
  }

  private Boolean bool(Object raw) {
    if (raw instanceof Boolean flag) {
      return flag;
    }
    if (raw instanceof Number number) {
      return number.intValue() != 0;
    }
    String value = text(raw);
    return value == null ? null : Boolean.parseBoolean(value);
  }

  private String date(Object raw) {
    if (raw instanceof LocalDate localDate) {
      return DATE.format(localDate);
    }
    if (raw instanceof LocalDateTime dateTime) {
      return DATE.format(dateTime.toLocalDate());
    }
    return text(raw);
  }

  /** 一律出文本：JSON 数字进 {@code s_date_1} 会让整条写入报 3156（见类注释）。 */
  private String dateTime(Object raw) {
    if (raw instanceof LocalDateTime dateTime) {
      return dateTime.getNano() == 0 ? DATE_TIME.format(dateTime) : DATE_TIME_MICROS.format(dateTime);
    }
    if (raw instanceof LocalDate localDate) {
      return DATE.format(localDate);
    }
    return text(raw);
  }
}
