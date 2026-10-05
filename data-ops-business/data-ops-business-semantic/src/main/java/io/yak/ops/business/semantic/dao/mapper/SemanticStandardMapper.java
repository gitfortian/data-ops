package io.yak.ops.business.semantic.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.semantic.dao.StandardListRow;
import io.yak.ops.business.semantic.dao.model.SemanticStandardPO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper for the semantic standard table.
 * 统一分页(32.1):五类原始行 + CODE 按码集 SQL GROUP BY 组行,UNION 后统一分页。
 */
@Mapper
public interface SemanticStandardMapper extends BaseMapper<SemanticStandardPO> {

  /**
   * 统一分页查询。kind 为空 → 五类原始行 + 码集组行;kind=CODE → 仅码集组行;
   * 其余类别 → 仅该类原始行。码集分组键 COALESCE(NULLIF(code_set_code,''), std_code)。
   */
  @Select(
      "<script>"
          + "SELECT * FROM ("
          + " SELECT s.id AS id, s.kind AS kind, s.std_code AS code, s.std_name AS name,"
          + "   s.status AS status, s.version AS version, s.sort_order AS sortOrder,"
          + "   s.is_preset AS preset, s.description AS description, s.update_time AS updateTime,"
          + "   NULL AS valueCount, s.code_set_code AS codeSetCode,"
          + "   s.scope AS scope, s.layer AS layer, s.rule_expr AS ruleExpr, s.example AS example,"
          + "   s.type_code AS typeCode, s.std_type AS stdType, s.source_mapping AS sourceMapping,"
          + "   s.code_value AS codeValue, s.code_label AS codeLabel,"
          + "   s.unit_code AS unitCode, s.unit_type AS unitType,"
          + "   s.caliber_code AS caliberCode, s.cal_rule AS calRule, s.business_desc AS businessDesc,"
          + "   s.level_code AS levelCode, s.mask_rule AS maskRule"
          + " FROM yak_semantic_standard s"
          + " WHERE s.project_id = #{projectId}"
          + " <choose>"
          + "   <when test='kind != null and kind != \"CODE\"'> AND s.kind = #{kind} </when>"
          + "   <when test='kind == null'> AND s.kind != 'CODE' </when>"
          + "   <otherwise> AND 1 = 0 </otherwise>"
          + " </choose>"
          + " <if test='status != null and status != \"\"'> AND s.status = #{status} </if>"
          + " <if test='keyword != null and keyword != \"\"'>"
          + "   AND (s.std_code LIKE CONCAT('%', COALESCE(#{keyword}, ''), '%') OR s.std_name LIKE CONCAT('%', COALESCE(#{keyword}, ''), '%'))"
          + " </if>"
          + " <if test='kind == null or kind == \"CODE\"'>"
          + " UNION ALL"
          + " SELECT NULL AS id, 'CODE' AS kind,"
          + "   COALESCE(NULLIF(s.code_set_code, ''), s.std_code) AS code,"
          + "   MAX(s.std_name) AS name,"
          + "   CASE WHEN SUM(CASE WHEN s.status = 'DISABLED' THEN 1 ELSE 0 END) > 0 THEN 'DISABLED' ELSE 'ENABLED' END AS status,"
          + "   MAX(s.version) AS version, MIN(s.sort_order) AS sortOrder, MAX(s.is_preset) AS preset,"
          + "   MAX(s.description) AS description, MAX(s.update_time) AS updateTime,"
          + "   COUNT(*) AS valueCount,"
          + "   COALESCE(NULLIF(s.code_set_code, ''), s.std_code) AS codeSetCode,"
          + "   NULL AS scope, NULL AS layer, NULL AS ruleExpr, NULL AS example,"
          + "   NULL AS typeCode, NULL AS stdType, NULL AS sourceMapping, NULL AS codeValue,"
          + "   NULL AS codeLabel, NULL AS unitCode, NULL AS unitType, NULL AS caliberCode,"
          + "   NULL AS calRule, NULL AS businessDesc, NULL AS levelCode, NULL AS maskRule"
          + " FROM yak_semantic_standard s"
          + " WHERE s.project_id = #{projectId} AND s.kind = 'CODE'"
          + " GROUP BY COALESCE(NULLIF(s.code_set_code, ''), s.std_code)"
          + " <if test='status != null and status != \"\" or keyword != null and keyword != \"\"'>"
          + "   HAVING"
          + "   <trim suffixOverrides=' AND '>"
          + "     <if test='status != null and status != \"\"'> CASE WHEN SUM(CASE WHEN s.status = 'DISABLED' THEN 1 ELSE 0 END) > 0 THEN 'DISABLED' ELSE 'ENABLED' END = #{status} AND </if>"
          + "     <if test='keyword != null and keyword != \"\"'>"
          + "       (COALESCE(NULLIF(s.code_set_code, ''), s.std_code) LIKE CONCAT('%', COALESCE(#{keyword}, ''), '%')"
          + "         OR SUM(CASE WHEN s.std_name LIKE CONCAT('%', COALESCE(#{keyword}, ''), '%') THEN 1 ELSE 0 END) &gt; 0"
          + "         OR SUM(CASE WHEN s.code_value LIKE CONCAT('%', COALESCE(#{keyword}, ''), '%') THEN 1 ELSE 0 END) &gt; 0"
          + "         OR SUM(CASE WHEN s.code_label LIKE CONCAT('%', COALESCE(#{keyword}, ''), '%') THEN 1 ELSE 0 END) &gt; 0)"
          + "     </if>"
          + "   </trim>"
          + " </if>"
          + " </if>"
          + ") t"
          + " ORDER BY t.sortOrder ASC, t.id ASC, t.code ASC"
          + "</script>")
  IPage<StandardListRow> selectListRows(
      Page<StandardListRow> page,
      @Param("projectId") Long projectId,
      @Param("kind") String kind,
      @Param("keyword") String keyword,
      @Param("status") String status);

  /** 字段码值引用下拉(35):启用码集选项,分组键落在 code 列(转换器读 row.code),排除存量空码集行。 */
  @Select(
      "SELECT COALESCE(NULLIF(code_set_code, ''), std_code) AS code, MAX(std_name) AS name"
          + " FROM yak_semantic_standard"
          + " WHERE project_id = #{projectId} AND kind = 'CODE'"
          + "   AND code_set_code IS NOT NULL AND code_set_code != ''"
          + " GROUP BY COALESCE(NULLIF(code_set_code, ''), std_code)"
          + " HAVING SUM(CASE WHEN status = 'DISABLED' THEN 1 ELSE 0 END) = 0"
          + " ORDER BY name ASC")
  List<StandardListRow> selectEnabledCodeSetOptions(@Param("projectId") Long projectId);
}
