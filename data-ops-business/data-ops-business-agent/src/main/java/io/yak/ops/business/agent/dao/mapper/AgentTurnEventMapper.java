package io.yak.ops.business.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.agent.dao.model.AgentTurnEventPO;
import org.apache.ibatis.annotations.Mapper;

/** Agent 轮次事件投递日志 Mapper。 */
@Mapper
public interface AgentTurnEventMapper extends BaseMapper<AgentTurnEventPO> {
}
