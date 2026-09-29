package io.yak.ops.business.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.agent.repository.AgentDynamicConfigService;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 记忆提取服务（记忆线 M1，设计稿 §四）：轮次自然完成终态后异步提取长期记忆。
 * 闸门（总开关/THROTTLED/实质内容）→ LLM 提取（数据域 prompt）→ JSON 解析（失败放弃）→
 * 内容校验 → LEDGER 入库；成功/失败/跳过全落 KIND_MEMORY_FLUSH 步骤；
 * 提取是机会性的，任何失败不影响主链路（best-effort 不变式）。
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class MemoryFlushService {

  /** 记忆类型白名单（M1 收敛四类；TEMPLATE/EXAMPLE 属 M3）。 */
  static final Set<String> ALLOWED_TYPES = Set.of("PREFERENCE", "FACT", "GLOSSARY", "LESSON");
  static final int MAX_CONTENT_CHARS = 2000;
  static final int MAX_KEYWORDS_CHARS = 255;

  private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
      new com.fasterxml.jackson.databind.ObjectMapper();

  private static final String EXTRACT_SYSTEM_PROMPT =
      """
      你是数据分析助手的记忆提取器。从一轮对话中提取值得跨会话记住的信息，输出 JSON 数组，
      每项 {"type":"PREFERENCE|FACT|GLOSSARY|LESSON","content":"...","keywords":"空格分隔关键词","confidence":0.6}。
      准则：记口径与语义不记数值结果；HITL 口径澄清答案优先（GLOSSARY）；"报错→修正→成功"记 LESSON；
      相对时间还原为绝对日期；一条一事实、自包含；没有值得记的输出 []。禁止输出任何查询结果明细数值。
      """;

  private final MemoryRepository memoryRepository;
  private final MemoryCompletionPort completionPort;
  private final AgentProperties properties;
  private final AgentDynamicConfigService dynamicConfig;

  /** 提取结果监听器：步骤记录（KIND_MEMORY_FLUSH）由 conversation 执行器经采集器落账。 */
  public interface FlushListener {

    void onFlushed(String turnId, String outcome, String detail, int extracted, int inserted);
  }

  /** THROTTLED 闸门：会话 -> 最近一次提取时刻（进程内即可，重启清零只是多提取一次）。 */
  private final Map<String, Long> lastFlushAtBySession = new ConcurrentHashMap<>();

  /** 轮终态异步提取入口（executor 在 COMPLETED 终态调用；不阻塞、不外抛）。 */
  public void submitAfterTurn(
      long userId, String sessionId, String turnId, String userMessage,
      String assistantAnswer, List<String> toolNames, FlushListener listener) {
    java.util.concurrent.CompletableFuture.runAsync(() -> {
      try {
        FlushResult result = flush(userMessage, assistantAnswer, toolNames,
            String.valueOf(userId), sessionId, turnId);
        if (result != null && listener != null) {
          listener.onFlushed(turnId, result.outcome(), result.detail(),
              result.extracted(), result.inserted());
        }
      } catch (RuntimeException e) {
        log.warn("memory flush failed: turnId={}, {}", turnId, e.getMessage());
      }
    });
  }

  private FlushResult flush(
      String userMessage, String assistantAnswer, List<String> toolNames,
      String userId, String sessionId, String turnId) {
    AgentProperties.Memory config = properties.getMemory();
    if (!dynamicConfig.enabled(
        AgentDynamicConfigService.KEY_MEMORY_ENABLED, config.isEnabled())) {
      return null;
    }
    long now = System.currentTimeMillis();
    Long last = lastFlushAtBySession.get(sessionId);
    if (last != null && now - last < config.getFlushMinIntervalMillis()) {
      return new FlushResult("SKIPPED", "THROTTLED", 0, 0);
    }
    if (!substantive(assistantAnswer, toolNames)) {
      return null;
    }
    lastFlushAtBySession.put(sessionId, now);

    String extraction = runExtraction(userMessage, assistantAnswer, toolNames);
    List<MemoryRecord> records = parseEntries(extraction, userId, sessionId, turnId);
    int inserted = 0;
    for (MemoryRecord record : records) {
      if (memoryRepository.insertLedger(record) != null) {
        inserted += 1;
      }
    }
    log.info("memory flush: turnId={}, extracted={}, inserted={}", turnId, records.size(), inserted);
    return new FlushResult("COMPLETED", "extract=" + records.size(), records.size(), inserted);
  }

  /** 一次提取的结果（监听器载荷）。 */
  record FlushResult(String outcome, String detail, int extracted, int inserted) {}

  private boolean substantive(String answer, List<String> toolNames) {
    return (answer != null && !answer.isBlank()) || (toolNames != null && !toolNames.isEmpty());
  }

  /** LLM 提取：失败抛出（上层记 FAILED step 后放弃，不做回喂重试——机会性提取不值得复杂度）。 */
  private String runExtraction(String userMessage, String answer, List<String> toolNames) {
    String user = "用户消息：" + clip(userMessage, 800) + "\n最终回答：" + clip(answer, 1200)
        + "\n本轮工具：" + String.join(",", toolNames == null ? List.of() : toolNames);
    return completionPort.complete(EXTRACT_SYSTEM_PROMPT, user);
  }

  /** 解析提取输出（容忍 markdown 代码栅栏）；非法项逐条过滤，全部非法返回空列表。 */
  List<MemoryRecord> parseEntries(
      String extraction, String userId, String sessionId, String turnId) {
    List<MemoryRecord> records = new ArrayList<>();
    if (extraction == null || extraction.isBlank()) {
      return records;
    }
    String text = extraction.trim();
    int start = text.indexOf('[');
    int end = text.lastIndexOf(']');
    if (start < 0 || end <= start) {
      return records;
    }
    try {
      JsonNode array = JSON.readTree(text.substring(start, end + 1));
      if (!array.isArray()) {
        return records;
      }
      for (JsonNode item : array) {
        String type = item.path("type").asText("").toUpperCase();
        String content = item.path("content").asText("").trim();
        if (!ALLOWED_TYPES.contains(type) || content.isEmpty()) {
          continue;
        }
        double confidence = item.path("confidence").asDouble(0.6);
        records.add(new MemoryRecord(
            null,
            MemoryRecord.SCOPE_USER,
            userId,
            type,
            MemoryRecord.LAYER_LEDGER,
            content.length() > MAX_CONTENT_CHARS ? content.substring(0, MAX_CONTENT_CHARS) : content,
            clip(item.path("keywords").asText(""), MAX_KEYWORDS_CHARS),
            Math.max(0, Math.min(1, confidence)),
            0,
            null,
            turnId,
            MemoryRecord.STATUS_ACTIVE));
      }
    } catch (Exception e) {
      log.warn("memory extraction parse failed: turnId={}, {}", turnId, e.getMessage());
    }
    return records;
  }

  private static String clip(String text, int max) {
    if (text == null) {
      return "";
    }
    return text.length() <= max ? text : text.substring(0, max) + "…";
  }
}
