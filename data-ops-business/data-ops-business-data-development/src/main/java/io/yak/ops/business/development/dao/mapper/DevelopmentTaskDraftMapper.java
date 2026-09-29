package io.yak.ops.business.development.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.development.DevelopmentTaskDraftPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DevelopmentTaskDraftMapper extends BaseMapper<DevelopmentTaskDraftPO> {

  @Select("""
      SELECT node_id, task_type, schema_version, content, config_json,
             draft_revision, create_time, update_time
      FROM yak_dev_task_draft
      WHERE node_id = #{nodeId}
      FOR UPDATE
      """)
  DevelopmentTaskDraftPO selectForUpdate(@Param("nodeId") Long nodeId);
}
