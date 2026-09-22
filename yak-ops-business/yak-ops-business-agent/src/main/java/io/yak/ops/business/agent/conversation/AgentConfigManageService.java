package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.repository.AgentDynamicConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 运行时配置治理服务（Phase 2 P2#2 治理界面后端）：键位清单与更新。
 * 仅登记键位可写（登记处见 AgentDynamicConfigService）；更新即热生效（1s 微过期）。
 */
@ConditionalOnAgentEnabled
@Service
@RequiredArgsConstructor
public class AgentConfigManageService {

  private final AgentDynamicConfigService dynamicConfigService;

  public List<AgentDynamicConfigService.RegisteredKeyValue> list() {
    return dynamicConfigService.listRegistered();
  }

  /** 更新键位值；value 空串/null 表示回退种子默认值（删除 DB 行）。 */
  public void update(String key, String value) {
    dynamicConfigService.upsert(key, value);
  }
}
