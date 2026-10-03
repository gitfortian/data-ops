package io.yak.ops.business.sync.offline.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.sync.offline.dao.model.OfflineJobRevisionPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 离线同步任务发布版本 Mapper（append-only，只追加不更新）。 */
public interface OfflineJobRevisionMapper extends BaseMapper<OfflineJobRevisionPO> {

  @Select(
      "SELECT COALESCE(MAX(version_no), 0) + 1 FROM yak_offline_job_revision"
          + " WHERE job_definition_id = #{jobDefinitionId}")
  int nextVersionNo(@Param("jobDefinitionId") Long jobDefinitionId);
}
