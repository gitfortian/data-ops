package io.yak.ops.business.sync.realtime.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeDefinitionVersionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RealtimeDefinitionVersionMapper extends BaseMapper<RealtimeDefinitionVersionPO> {

  /** DefinitionVersion inherits Project ownership from yak_realtime_job_definition. */
  @Select("""
      SELECT v.*
      FROM yak_realtime_definition_version v
      JOIN yak_realtime_job_definition d ON d.id = v.task_id
      WHERE v.id = #{versionId}
        AND d.project_id = #{projectId}
      """)
  RealtimeDefinitionVersionPO selectByIdAndProject(
      @Param("versionId") long versionId, @Param("projectId") long projectId);
}
