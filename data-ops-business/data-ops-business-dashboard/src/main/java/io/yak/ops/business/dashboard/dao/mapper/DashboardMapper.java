package io.yak.ops.business.dashboard.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dashboard.dao.model.DashboardPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DashboardMapper extends BaseMapper<DashboardPO> {

  @Select("""
      SELECT COUNT(*)
      FROM yak_dashboard_widget w
      JOIN yak_dashboard_version v ON v.id = w.dashboard_version_id
      JOIN yak_dashboard d ON d.id = v.dashboard_id
      WHERE d.project_id = #{projectId}
        AND w.analysis_id = #{analysisId}
      """)
  long countAnalysisReferences(
      @Param("projectId") long projectId,
      @Param("analysisId") long analysisId);
}
