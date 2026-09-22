package io.yak.ops.business.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.agent.dao.model.AgentMemoryPO;
import org.apache.ibatis.annotations.Mapper;

/** yak_agent_memory 数据访问（写入口唯一经 MemoryFlushService；读经 MemoryQueryService）。 */
@Mapper
public interface AgentMemoryMapper extends BaseMapper<AgentMemoryPO> {
}
