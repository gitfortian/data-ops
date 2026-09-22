package io.yak.ops.business.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.agent.dao.model.AgentReportPO;
import org.apache.ibatis.annotations.Mapper;

/** AI 分析报告 Mapper。 */
@Mapper
public interface AgentReportMapper extends BaseMapper<AgentReportPO> {}
