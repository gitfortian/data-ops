package io.yak.ops.business.agent.telemetry;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.dao.mapper.AgentStepMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 步骤表存储量观测（O4 保留策略的数据采集侧，设计稿 §八 O4#2）：
 * 每日错峰聚合行数/平均载荷/最老记录并日志上报——TTL/归档策略"只设计不实现"，
 * 待本 job 产出量级证据后再立项（防过度设计）。反高基数：只聚合，无用户维度。
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class AgentStepMetricsJob {

  private final AgentStepMapper stepMapper;

  @Scheduled(cron = "0 13 6 * * ?")
  public void reportStorageStats() {
    try {
      Map<String, Object> stats = stepMapper.selectStorageStats();
      log.info("agent step storage metrics: rows={}, avgRequestChars={}, avgResponseChars={}, oldest={}",
          stats.get("rowsCount"), stats.get("avgRequestChars"),
          stats.get("avgResponseChars"), stats.get("oldestCreateTime"));
    } catch (Exception e) {
      log.warn("agent step storage metrics failed: {}", e.getMessage());
    }
  }
}
