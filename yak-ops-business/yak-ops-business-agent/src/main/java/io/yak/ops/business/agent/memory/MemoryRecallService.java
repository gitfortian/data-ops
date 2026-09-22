package io.yak.ops.business.agent.memory;

import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 记忆检索与注入装配（记忆线 M1，设计稿 §五）：
 * 候选过滤（置信度门槛）→ 打分（type_weight × confidence × 时间衰减）→ topK →
 * 字符预算截断 → 注入段落文本。纯函数 + 仓储读取，无框架依赖。
 */
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class MemoryRecallService {

  private final MemoryRepository memoryRepository;

  /** 类型权重：口径与教训优先于一般事实/偏好（设计稿 §5.2）。 */
  static double typeWeightOf(String memoryType) {
    return switch (memoryType) {
      case "GLOSSARY", "LESSON" -> 1.5;
      case "PREFERENCE" -> 1.2;
      default -> 1.0;
    };
  }

  /** 时间衰减：半衰期指数衰减；lastHitAt 缺省按满权重（新条目不惩罚）。 */
  static double decay(LocalDateTime lastHitAt, LocalDateTime now, long halfLifeHours) {
    if (lastHitAt == null || halfLifeHours <= 0) {
      return 1.0;
    }
    double hours = Duration.between(lastHitAt, now).toHours();
    return Math.pow(0.5, Math.max(0, hours) / halfLifeHours);
  }

  /**
   * 召回装配：返回注入段落（无候选返回空串）。
   *
   * @param queryText 当前用户消息（MVP LIKE 检索词源；空则按分数取全量 topK）
   * @param hitSink   命中的条目 id 收集器（调用方异步回写命中计数）
   */
  public String recallSection(
      String userId, String queryText, AgentProperties.Memory config, List<Long> hitSink) {
    List<MemoryRecord> candidates = memoryRepository.listActiveForUser(userId);
    if (candidates.isEmpty()) {
      return "";
    }
    LocalDateTime now = LocalDateTime.now();
    String keyword = queryText == null ? "" : queryText.trim();
    List<Scored> scored = new ArrayList<>();
    for (MemoryRecord record : candidates) {
      if (record.confidence() < config.getMinConfidence()) {
        continue;
      }
      if (!keyword.isEmpty() && !matches(record, keyword)) {
        continue;
      }
      double score = typeWeightOf(record.memoryType())
          * record.confidence()
          * decay(record.lastHitAt(), now, config.getDecayHalfLifeHours());
      scored.add(new Scored(record, score));
    }
    scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());

    StringBuilder section = new StringBuilder();
    int chars = 0;
    for (Scored s : scored.subList(0, Math.min(config.getRecallTopK(), scored.size()))) {
      String line = "- [" + s.record.memoryType() + "] " + s.record.content()
          + "（来源:" + (s.record.sourceTurnId() == null ? "历史" : s.record.sourceTurnId()) + "）";
      if (chars + line.length() > config.getRecallMaxChars()) {
        break;
      }
      if (section.length() == 0) {
        section.append("## 长期记忆（历史沉淀，仅供参考；数字必须来自当轮工具结果）\n");
      }
      section.append(line).append('\n');
      chars += line.length();
      hitSink.add(s.record.id());
    }
    return section.toString();
  }

  /** MVP 关键词匹配：keywords/content 任一含查询词即命中（对齐语义层 MVP LIKE 级务实基准）。 */
  static boolean matches(MemoryRecord record, String keyword) {
    String lower = keyword.toLowerCase();
    String[] tokens = lower.split("\\s+");
    for (String token : tokens) {
      if (token.isEmpty()) {
        continue;
      }
      boolean inKeywords = record.keywords() != null
          && record.keywords().toLowerCase().contains(token);
      boolean inContent = record.content() != null
          && record.content().toLowerCase().contains(token);
      if (inKeywords || inContent) {
        return true;
      }
    }
    return false;
  }

  private record Scored(MemoryRecord record, double score) {}
}
