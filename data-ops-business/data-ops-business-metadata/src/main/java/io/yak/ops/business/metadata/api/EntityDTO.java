package io.yak.ops.business.metadata.api;

import java.util.Map;

/**
 * 一个目录实体（ticket 118，plan §5.2）。
 *
 * <p>形状固定为「类型 + 三袋」：{@code facts} 是目录固有列，{@code attributes} 是属性袋
 * （{@code md_attributes}），{@code slotValues} 是<b>已提槽</b>字段的实际值（键是字段名，不是槽列名）。
 * 消费方按 {@code typeName} 解释内容——字段清单的权威来源是 {@code GET /api/v1/metadata/types}，
 * 不是任何常量类；否则"加字段免改表"会在 API 边界上被重新写死。
 *
 * <p>本对象由 {@code CatalogRowRead} 的行映射直接装配，新增目录列不会让本 DTO 悄悄漏字段。
 */
public record EntityDTO(
    long id,
    String typeName,
    Map<String, Object> facts,
    Map<String, Object> attributes,
    Map<String, Object> slotValues) {

  // 符合性对账（工单 120）与物理表定位反复要的坐标，给类型化入口；其余事实留在 facts 里。
  public String assetKey() {
    return string("assetKey");
  }

  public String providerType() {
    return string("providerType");
  }

  public String sourceId() {
    return string("sourceId");
  }

  public String dataSourceId() {
    return string("dataSourceId");
  }

  public String databaseName() {
    return string("databaseName");
  }

  public String schemaName() {
    return string("schemaName");
  }

  public String tableName() {
    return string("tableName");
  }

  public String columnName() {
    return string("columnName");
  }

  public Long parentAssetId() {
    return facts.get("parentAssetId") instanceof Number number ? number.longValue() : null;
  }

  /** 登记投影实体（源域 own，目录只存投影）；物理采集为 false。 */
  public boolean projected() {
    return "REGISTERED".equals(providerType());
  }

  private String string(String key) {
    Object value = facts.get(key);
    return value == null ? null : String.valueOf(value);
  }
}
