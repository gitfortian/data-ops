package io.yak.ops.business.agent.toolset;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** 日期事实工具：相对日期换算前先取当前日期，避免模型凭空猜测。 */
@ConditionalOnAgentEnabled
@Component
public class CurrentDateInfoTool implements AgentToolBox {

  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

  private final Clock clock = Clock.system(DEFAULT_ZONE);

  @Tool(
      name = "current_date_info",
      description =
          "获取当前日期、星期、年份等事实信息。涉及今天/昨天/本周/上周/本月等相对时间时必须先调用本工具。")
  public String currentDateInfo(
      @ToolParam(name = "timezone", description = "可选 IANA 时区，默认 Asia/Shanghai", required = false)
          String timezone) {
    ZoneId zone = resolveZone(timezone);
    LocalDate today = LocalDate.now(clock.withZone(zone));
    return "当前时区: "
        + zone.getId()
        + "\n今天: "
        + today
        + " ("
        + today.getDayOfWeek()
        + ")\n昨天: "
        + today.minusDays(1)
        + "\n明天: "
        + today.plusDays(1)
        + "\n本周一: "
        + today.with(java.time.DayOfWeek.MONDAY)
        + "\n本月第一天: "
        + today.withDayOfMonth(1)
        + "\n今年: "
        + today.getYear();
  }

  private static ZoneId resolveZone(String timezone) {
    if (timezone == null || timezone.isBlank()) {
      return DEFAULT_ZONE;
    }
    try {
      return ZoneId.of(timezone.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException("非法时区 '" + timezone + "'，示例：Asia/Shanghai");
    }
  }
}
