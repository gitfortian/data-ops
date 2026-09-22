package io.yak.ops.business.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.agent.dao.model.AgentMessagePO;
import org.apache.ibatis.annotations.Mapper;

/** Agent 消息树 Mapper。 */
@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessagePO> {
}
