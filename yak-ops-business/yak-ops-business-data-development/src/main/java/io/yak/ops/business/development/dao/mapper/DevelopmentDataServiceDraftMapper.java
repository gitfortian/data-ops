package io.yak.ops.business.development.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.development.DevelopmentDataServiceDraftPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DevelopmentDataServiceDraftMapper
    extends BaseMapper<DevelopmentDataServiceDraftPO> {

  @Select("""
      SELECT node_id, definition_json, draft_revision, create_time, update_time
      FROM yak_dev_data_service_draft
      WHERE node_id = #{nodeId}
      FOR UPDATE
      """)
  DevelopmentDataServiceDraftPO selectForUpdate(@Param("nodeId") Long nodeId);
}
