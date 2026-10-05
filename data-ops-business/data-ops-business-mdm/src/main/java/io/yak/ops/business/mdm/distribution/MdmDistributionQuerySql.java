package io.yak.ops.business.mdm.distribution;

import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 分发 API 的查询 SQL(R5,review P0-1.8):发布到 data-service 的语句模板。
 * 口径「发布粒度=实体查询定义」:SQL 固定读统一主数据表 ACTIVE 行、
 * 数据实时查表不锁快照;project_id/entity_id 内联字面量(runtime 调用无项目头,
 * 与 API Key 共同构成租户边界)。属性列经 JSON_EXTRACT 展平,编码白名单校验防注入。
 */
public final class MdmDistributionQuerySql {

  private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,64}");
  private static final String RECORD_TABLE = "yak_mdm_record";
  private static final String ENTITY_TABLE = "yak_mdm_entity";

  private MdmDistributionQuerySql() {}

  public static String build(Long projectId, Long entityId, String database, List<String> codes) {
    return build(projectId, entityId, database, codes, false);
  }

  public static String build(Long projectId, Long entityId, String database, List<String> codes,
      boolean postgresql) {
    if (projectId == null || projectId <= 0 || entityId == null || entityId <= 0) {
      throw new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, "分发 SQL 缺少 project_id/entity_id");
    }
    requireSafe(database, "平台库名");
    StringBuilder sql = new StringBuilder("SELECT\n")
        .append("  r.master_id AS master_id,\n")
        .append("  r.version AS version,\n");
    for (String code : codes) {
      requireSafe(code, "属性编码");
      sql.append("  JSON_UNQUOTE(JSON_EXTRACT(r.attributes, '$.\"")
          .append(code)
          .append("\"')) AS `")
          .append(code)
          .append("`,\n");
    }
    sql.append("  r.update_time AS update_time\n")
        .append("FROM `")
        .append(database)
        .append("`.`")
        .append(RECORD_TABLE)
        .append("` r\n")
        .append("WHERE r.project_id = ")
        .append(projectId)
        .append(" AND r.entity_id = ")
        .append(entityId)
        .append(" AND r.status = 'ACTIVE'\n")
        .append("  AND EXISTS (SELECT 1 FROM `")
        .append(database)
        .append("`.`")
        .append(ENTITY_TABLE)
        .append("` e WHERE e.project_id = r.project_id")
        .append(" AND e.id = r.entity_id AND e.status = 'ACTIVE')\n")
        .append("ORDER BY r.id");
    if (postgresql) {
      String pg = sql.toString().replace('`', '"');
      for (String code : codes) {
        pg = pg.replace("JSON_UNQUOTE(JSON_EXTRACT(r.attributes, '$.\"" + code + "\"'))",
            "(r.attributes ->> '" + code + "')");
      }
      return pg;
    }
    return sql.toString();
  }

  private static void requireSafe(String value, String label) {
    if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
      throw new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, label + "不合法: " + value);
    }
  }
}
