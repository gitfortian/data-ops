package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.repository.AgentDynamicConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 运行时配置治理服务（Phase 2 P2#2 治理界面后端）：键位清单与更新。
 * 展示已接线键的有效值与来源；登记但未接线的键不宣称生效。
 */
@ConditionalOnAgentEnabled
@Service
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
public class AgentConfigManageService {

  private final AgentDynamicConfigService dynamicConfigService;
  private final io.yak.ops.business.agent.config.AgentProperties properties;

  public record RuntimeConfigValue(String key, String kind, String description, String dbValue,
      String effectiveValue, String valueSource, String updateMode) {}

  public List<RuntimeConfigValue> list() {
    return dynamicConfigService.listRegistered().stream().map(item -> {
      String fallback = switch (item.key()) {
        case AgentDynamicConfigService.KEY_MEMORY_ENABLED -> String.valueOf(properties.getMemory().isEnabled());
        case AgentDynamicConfigService.KEY_OBSERVABILITY_ENABLED -> String.valueOf(properties.getObservability().isEnabled());
        case AgentDynamicConfigService.KEY_LLM_TIMEOUT_SECONDS -> String.valueOf(properties.getChat().getLlmCallTimeoutSeconds());
        default -> null;
      };
      if (fallback == null) return new RuntimeConfigValue(item.key(), item.kind(), item.description(),
          item.dbValue(), null, "NOT_CONNECTED", "NOT_CONNECTED");
      String effective = fallback;
      boolean overridden = false;
      if (item.dbValue() != null) {
        if (item.kind().equals("bool")) {
          effective = String.valueOf("true".equalsIgnoreCase(item.dbValue().trim()));
          overridden = true;
        } else {
          try {
            effective = String.valueOf(Integer.parseInt(item.dbValue().trim()));
            overridden = true;
          } catch (NumberFormatException invalidOverride) {
            // Same fallback as the runtime, including integer overflow.
          }
        }
      }
      return new RuntimeConfigValue(item.key(), item.kind(), item.description(), item.dbValue(),
          effective, overridden ? "DYNAMIC" : "STARTUP", "HOT");
    }).toList();
  }

  /** 更新键位值；value 空串/null 表示回退种子默认值（删除 DB 行）。 */
  public void update(String key, String value) {
    if (AgentDynamicConfigService.KEY_LLM_MAX_ITERS.equals(key)
        || AgentDynamicConfigService.KEY_APPROVAL_QUERY_EXECUTION.equals(key)) {
      throw new IllegalArgumentException("预留键尚未接入运行时，不能作为有效配置调整");
    }
    dynamicConfigService.upsert(key, value);
  }
}
