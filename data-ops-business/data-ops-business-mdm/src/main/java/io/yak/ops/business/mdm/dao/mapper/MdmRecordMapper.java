package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.mdm.domain.clean.MdmDedupKey;
import io.yak.ops.business.mdm.dao.model.MdmRecordPO;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper for the master data record. Dedup discovery runs the grouping
 * at the DB level (home-overview-contract: server-side aggregation, no unbounded
 * list() then in-memory stats); {@code keyExpr}/{@code valueCondition} are built
 * by {@code DedupSql} from already-validated attribute codes.
 */
@Mapper
public interface MdmRecordMapper extends BaseMapper<MdmRecordPO> {

  @Select("""
      SELECT ${keyExpr} AS match_key, COUNT(*) AS match_count
      FROM yak_mdm_record
      WHERE project_id = #{projectId}
        AND entity_id = #{entityId}
        AND status = 'ACTIVE'
        AND ${valueCondition}
        AND ${keyExpr} NOT IN (
          SELECT match_key FROM yak_mdm_dedup_ignore
          WHERE project_id = #{projectId} AND rule_id = #{ruleId}
        )
      GROUP BY match_key
      HAVING match_count >= 2
      """)
  // record 无 setter,结果映射必须走显式构造器参数,否则 MyBatis 反射注入直接抛
  // "There is no setter for property named 'matchKey'"。
  // 忽略键的排除留在 SQL 侧:先聚合再过滤会让分页总数与页内容口径不一致。
  @ConstructorArgs({
    @Arg(column = "match_key", javaType = String.class),
    @Arg(column = "match_count", javaType = long.class)
  })
  List<MdmDedupKey> countDedupKeys(
      @Param("projectId") Long projectId,
      @Param("entityId") Long entityId,
      @Param("ruleId") Long ruleId,
      @Param("keyExpr") String keyExpr,
      @Param("valueCondition") String valueCondition);

  @Select("""
      SELECT id, entity_id, master_id, attributes, source_ids, status, version,
             create_time, update_time
      FROM yak_mdm_record
      WHERE project_id = #{projectId}
        AND entity_id = #{entityId}
        AND status = 'ACTIVE'
        AND ${keyExpr} = #{key}
      ORDER BY id
      LIMIT #{limit}
      """)
  List<MdmRecordPO> selectByDedupKey(
      @Param("projectId") Long projectId,
      @Param("entityId") Long entityId,
      @Param("keyExpr") String keyExpr,
      @Param("key") String key,
      @Param("limit") int limit);
}
