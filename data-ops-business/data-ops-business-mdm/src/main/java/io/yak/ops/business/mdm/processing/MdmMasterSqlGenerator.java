package io.yak.ops.business.mdm.processing;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * 主数据加工 SQL 生成器(55a 方案 A;R2 口径:读平台库落地表,不再直连业务源表).
 * 生成的 SQL 在平台业务库内同库执行:N 张落地表(mdm_landing_*)→ 统一主数据表
 * {@code yak_mdm_record},由数据开发任务承载执行。核心口径(dev-plan D-M7):
 *
 * <ul>
 *   <li><b>master_id</b> = MD5(CONCAT(entity_code, ':', PK 列值)) —— 确定性:同一 PK 值跨来源
 *       一致,多源 PK 值相同时自动统一为同一 master_id(D3);
 *   <li><b>source_ids</b> = JSON_OBJECT(datasource_id, PK 值) —— 记录各系统原始 ID(D4);
 *   <li><b>attributes</b> = JSON_OBJECT(属性编码, 落地列) —— 落地列取来源 field_mapping
 *       (属性编码→源列名,落地表按源列名建列),未配置的属性编码回退同名;
 *   <li>来源表与目标表均按平台库名限定(执行数据源的默认库可能不同库);
 *   <li>UPSERT 到 yak_mdm_record(唯一键 project_id+entity_id+master_id);来源 ID 按数据源键覆盖写,
 *       不重复累积;有效属性保留已审批/清洗的属性覆盖值,无实际变化时不递增 version。
 * </ul>
 *
 * <p>仅生成 SQL 文本,不执行;执行引擎归数据开发(22/44 模式)。
 */
public final class MdmMasterSqlGenerator {

  /** 与 DedupSql 同口径:属性编码/列名仅允许安全标识符,防 SQL 注入。 */
  private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,64}");

  /** 统一主数据表真实表名(review P0-1.2:此前误写为 mdm_record)。 */
  private static final String RECORD_TABLE = "yak_mdm_record";

  private MdmMasterSqlGenerator() {}

  /** 实体属性规格:编码 + 是否 PK。 */
  public record AttributeSpec(String code, boolean pk) {}

  /** 落地表规格(R2):平台库内的采集落地表 + 其来源数据源 ID(source_ids 键)。 */
  public record LandingSpec(String database, String table, Long datasourceId) {}

  /** 生成一个落地表的加工 SQL(MySQL 方言,09 先 MySQL)。 */
  public static String generate(
      Long projectId, Long entityId, String entityCode, List<AttributeSpec> attributes,
      LandingSpec landing, Map<String, String> fieldMapping) {
    return generate(projectId, entityId, entityCode, attributes, landing, fieldMapping, false);
  }

  public static String generate(
      Long projectId, Long entityId, String entityCode, List<AttributeSpec> attributes,
      LandingSpec landing, Map<String, String> fieldMapping, boolean postgresql) {
    if (projectId == null || entityId == null || !hasText(entityCode)) {
      throw new IllegalArgumentException("projectId/entityId/entityCode 不能为空");
    }
    if (attributes == null || attributes.isEmpty()) {
      throw new IllegalArgumentException("实体属性不能为空");
    }
    for (AttributeSpec attribute : attributes) {
      requireSafe(attribute.code(), "属性编码");
    }
    if (attributes.stream().map(AttributeSpec::code).distinct().count() != attributes.size()) {
      throw new IllegalArgumentException("实体属性编码不能重复");
    }
    AttributeSpec pk =
        attributes.stream().filter(AttributeSpec::pk).findFirst().orElseThrow(
            () -> new IllegalArgumentException("实体必须包含 PK 属性"));
    if (landing == null || landing.datasourceId() == null
        || !hasText(landing.table()) || !hasText(landing.database())) {
      throw new IllegalArgumentException("落地表绑定不完整");
    }
    requireSafe(landing.database(), "平台库名");
    requireSafe(landing.table(), "落地表名");
    if (fieldMapping != null) {
      fieldMapping.values().forEach(column -> requireSafe(column, "映射源列名"));
    }
    if (postgresql) {
      return postgresSql(projectId, entityId, entityCode, attributes, landing, fieldMapping, pk);
    }
    String pkColumn = quote(resolveColumn(fieldMapping, pk.code()));
    StringJoiner attrJson = new StringJoiner(", ", "JSON_OBJECT(", ")");
    for (AttributeSpec attribute : attributes) {
      attrJson.add(
          literal(attribute.code()) + ", " + quote(resolveColumn(fieldMapping, attribute.code())));
    }
    return buildSql(
        projectId, entityId, entityCode,
        quote(landing.database()) + "." + quote(RECORD_TABLE),
        quote(landing.database()) + "." + quote(landing.table()),
        pkColumn, attrJson.toString(), landing.datasourceId(), attributes);
  }

  /** Atomic source refresh, retaining approved overrides and incrementing version only on changes. */
  private static String postgresSql(Long projectId, Long entityId, String entityCode,
      List<AttributeSpec> attributes, LandingSpec landing, Map<String, String> mapping, AttributeSpec pk) {
    String target = pgQuote(landing.database()) + "." + pgQuote(RECORD_TABLE);
    String source = pgQuote(landing.database()) + "." + pgQuote(landing.table());
    String pkColumn = pgQuote(resolveColumn(mapping, pk.code()));
    StringJoiner incoming = new StringJoiner(", ", "jsonb_build_object(", ")");
    StringJoiner effective = new StringJoiner(", ", "jsonb_build_object(", ")");
    for (AttributeSpec attribute : attributes) {
      String key = literal(attribute.code());
      incoming.add(key + ", " + pgQuote(resolveColumn(mapping, attribute.code())));
      effective.add(key + ", CASE WHEN jsonb_exists(COALESCE(current.attribute_overrides, '{}'::jsonb), "
          + key + ") THEN current.attribute_overrides -> " + key
          + " ELSE excluded.attributes -> " + key + " END");
    }
    String sourceKey = literal(landing.datasourceId().toString());
    String changed = "current.attributes IS DISTINCT FROM " + effective
        + " OR (current.source_ids -> " + sourceKey + ") IS DISTINCT FROM (excluded.source_ids -> " + sourceKey + ")";
    return """
        INSERT INTO %s AS current (project_id, entity_id, master_id, attributes, source_ids, status, version)
        SELECT %d, %d, MD5(CONCAT(%s, ':', %s::text)), %s, jsonb_build_object(%s, %s), 'ACTIVE', 1
        FROM %s
        ON CONFLICT (project_id, entity_id, master_id) DO UPDATE SET
          version = current.version + CASE WHEN %s THEN 1 ELSE 0 END,
          attributes = %s,
          source_ids = COALESCE(current.source_ids, '{}'::jsonb) || excluded.source_ids;
        """.formatted(target, projectId, entityId, literal(entityCode), pkColumn, incoming,
            sourceKey, pkColumn, source, changed, effective);
  }

  private static String pgQuote(String identifier) {
    return "\"" + identifier + "\"";
  }

  /** 属性编码对应源列:field_mapping 优先,未配置/空值回退同名(约定口径;落地表按源列名建列)。 */
  private static String resolveColumn(Map<String, String> fieldMapping, String attributeCode) {
    String column = fieldMapping == null ? null : fieldMapping.get(attributeCode);
    return hasText(column) ? column : attributeCode;
  }

  private static String buildSql(
      Long projectId, Long entityId, String entityCode, String targetTable, String sourceTable,
      String pkColumn, String attributesJson, Long datasourceId, List<AttributeSpec> attributes) {
    String sourcePath = literal("$.\"" + datasourceId + "\"");
    String incomingSourceId = "JSON_EXTRACT(VALUES(source_ids), " + sourcePath + ")";
    String existingSourceIds = "COALESCE(source_ids, JSON_OBJECT())";
    String effectiveAttributes = effectiveAttributes(attributes);
    String sourceChanged = "NOT (JSON_EXTRACT(" + existingSourceIds + ", " + sourcePath
        + ") <=> " + incomingSourceId + ")";
    String changed = "NOT (attributes <=> " + effectiveAttributes + ") OR " + sourceChanged;
    String sourceIdsUpdate = "JSON_SET(" + existingSourceIds + ", " + sourcePath + ", "
        + incomingSourceId + ")";
    return """
        -- 主数据加工任务: 实体 %2$s ← 落地表 %3$s
        -- 同库写入统一主数据表 %1$s;在数据开发对平台业务库连接执行(MDM 零执行引擎,D-M11)。
        INSERT INTO %1$s
          (project_id, entity_id, master_id, attributes, source_ids, status, version)
        SELECT
          %4$d,
          %5$d,
          MD5(CONCAT('%2$s', ':', %6$s)),
          %7$s,
          JSON_OBJECT('%8$d', %6$s),
          'ACTIVE',
          1
        FROM %3$s
        ON DUPLICATE KEY UPDATE
          version = version + IF(%9$s, 1, 0),
          attributes = %10$s,
          source_ids = %11$s;
        """
        .formatted(
            targetTable,
            escapeLiteral(entityCode),
            sourceTable,
            projectId,
            entityId,
            pkColumn,
            attributesJson,
            datasourceId,
            changed,
            effectiveAttributes,
            sourceIdsUpdate);
  }

  /** Source values refresh normally; explicit MDM corrections override only their own fields. */
  private static String effectiveAttributes(List<AttributeSpec> attributes) {
    StringJoiner set = new StringJoiner(", ", "JSON_SET(VALUES(attributes), ", ")");
    for (AttributeSpec attribute : attributes) {
      String path = literal("$.\"" + attribute.code() + "\"");
      String overrides = "COALESCE(attribute_overrides, JSON_OBJECT())";
      set.add(path);
      set.add("CASE WHEN JSON_CONTAINS_PATH(" + overrides + ", 'one', " + path + ")"
          + " THEN JSON_EXTRACT(" + overrides + ", " + path + ")"
          + " ELSE JSON_EXTRACT(VALUES(attributes), " + path + ") END");
    }
    return set.toString();
  }

  private static void requireSafe(String value, String label) {
    if (!hasText(value) || !SAFE_IDENTIFIER.matcher(value).matches()) {
      throw new IllegalArgumentException(label + "不合法: " + value);
    }
  }

  private static String quote(String identifier) {
    return "`" + identifier + "`";
  }

  /** SQL 字符串字面量(单引号包裹,' 转义双写)。 */
  private static String literal(String value) {
    return "'" + escapeLiteral(value) + "'";
  }

  private static String escapeLiteral(String value) {
    return value.replace("'", "''");
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
