package io.yak.ops.business.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.agent.dao.model.AgentQueryLogPO;
import org.apache.ibatis.annotations.Mapper;

/** Agent 查询证据留痕 Mapper。 */
@Mapper
public interface AgentQueryLogMapper extends BaseMapper<AgentQueryLogPO> {}
