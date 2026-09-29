package io.yak.ops.business.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.agent.dao.model.AgentSkillPO;
import org.apache.ibatis.annotations.Mapper;

/** 智能体技能持久化访问（yak_agent_skill）。 */
@Mapper
public interface AgentSkillMapper extends BaseMapper<AgentSkillPO> {
}