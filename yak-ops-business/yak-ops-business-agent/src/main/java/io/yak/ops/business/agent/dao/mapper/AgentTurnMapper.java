package io.yak.ops.business.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.agent.dao.model.AgentTurnPO;
import org.apache.ibatis.annotations.Mapper;

/** Agent 推理轮次 Mapper。 */
@Mapper
public interface AgentTurnMapper extends BaseMapper<AgentTurnPO> {
}
