package io.yak.ops.business.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.agent.dao.model.AgentSessionPO;
import org.apache.ibatis.annotations.Mapper;

/** AI 分析会话元数据 Mapper。 */
@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSessionPO> {}
