package io.yak.ops.business.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.agent.dao.model.AgentStepPO;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** yak_agent_step 数据访问（写入唯一经 AgentStepRecorder；读路径含 O4 存储量聚合）。 */
@Mapper
public interface AgentStepMapper extends BaseMapper<AgentStepPO> {

  /** 存储量聚合（O4 保留策略观测：行数/平均载荷/最老记录，供容量趋势日志）。 */
  @Select("SELECT COUNT(*) AS rowsCount, "
      + "AVG(LENGTH(request_json)) AS avgRequestChars, "
      + "AVG(LENGTH(response_json)) AS avgResponseChars, "
      + "MIN(create_time) AS oldestCreateTime "
      + "FROM yak_agent_step")
  Map<String, Object> selectStorageStats();
}
