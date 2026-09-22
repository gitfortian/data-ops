package io.yak.ops.business.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.agent.dao.model.AgentConfigPO;
import org.apache.ibatis.annotations.Mapper;

/** yak_config 动态配置数据访问（只读；写入经治理界面/运维 SQL）。 */
@Mapper
public interface AgentConfigMapper extends BaseMapper<AgentConfigPO> {
}
