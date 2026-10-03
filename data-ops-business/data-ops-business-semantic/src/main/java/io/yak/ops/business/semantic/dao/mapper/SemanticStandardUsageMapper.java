package io.yak.ops.business.semantic.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.semantic.dao.model.SemanticStandardUsagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.Map;

/** MyBatis mapper for standard usage events. */
@Mapper
public interface SemanticStandardUsageMapper extends BaseMapper<SemanticStandardUsagePO> {

  @Select("SELECT COALESCE(SUM(CASE WHEN usage_type = 'APPLY' THEN 1 ELSE 0 END), 0) AS applyCount, "
      + "COALESCE(SUM(CASE WHEN usage_type = 'BYPASS' THEN 1 ELSE 0 END), 0) AS bypassCount "
      + "FROM yak_semantic_standard_usage WHERE project_id = #{projectId} AND standard_id = #{standardId}")
  Map<String, Object> selectSummary(@Param("projectId") Long projectId,
      @Param("standardId") Long standardId);
}
