package io.yak.ops.business.development.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.development.DevelopmentTaskRevisionPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DevelopmentTaskRevisionMapper extends BaseMapper<DevelopmentTaskRevisionPO> {

  @Select("SELECT COALESCE(MAX(revision_no), 0) FROM yak_dev_task_revision WHERE node_id = #{nodeId}")
  Integer selectMaxRevisionNo(@Param("nodeId") Long nodeId);
}
