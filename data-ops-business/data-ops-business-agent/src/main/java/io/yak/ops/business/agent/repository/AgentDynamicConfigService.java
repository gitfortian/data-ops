package io.yak.ops.business.agent.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.dao.mapper.AgentConfigMapper;
import io.yak.ops.business.agent.dao.model.AgentConfigPO;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 运行时动态配置读取（Phase 2 P2#2 最小版）：yak_config per-key 表，
 * DB 行存在即覆盖 AgentProperties 种子值。运行路径每次现读（缓存仅 1s 微过期，
 * 热更可见延迟 ≤1s，满足"改值无重启生效"，非"配置不缓存"纪律禁止的模块级静态缓存）。
 * 查询失败按种子值兜底（配置面故障不阻断执行事实）。
 *
 * <p>键位登记处（新键必须在此登记，禁止散落魔法键名）：</p>
 * <ul>
 *   <li>{@code yak.agent.memory.enabled} — 记忆提取/召回总开关（已接线）；</li>
 *   <li>{@code yak.agent.observability.enabled} — 步骤记录总开关（已接线）；</li>
 *   <li>{@code yak.agent.llm.timeout} — 单次模型调用超时秒数（已接线，每次尝试现读）；</li>
 *   <li>{@code yak.agent.llm.max-iters} — ReAct 最大迭代：builder 级参数无热更缝（GenerateOptions
 *       无此字段），DB 键尚未接线；实际值来自启动配置 chat.max-iters；</li>
 *   <li>{@code yak.agent.approval.query-execution} — 查询执行人工确认预留键，尚未接线。</li>
 * </ul>
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
public class AgentDynamicConfigService {

  /** 首批纳管键位（登记处）：值语义均为 boolean 开关。 */
  public static final String KEY_MEMORY_ENABLED = "yak.agent.memory.enabled";
  public static final String KEY_OBSERVABILITY_ENABLED = "yak.agent.observability.enabled";
  public static final String KEY_LLM_TIMEOUT_SECONDS = "yak.agent.llm.timeout";
  public static final String KEY_LLM_MAX_ITERS = "yak.agent.llm.max-iters";
  public static final String KEY_APPROVAL_QUERY_EXECUTION = "yak.agent.approval.query-execution";

  private static final long MICRO_CACHE_MILLIS = 1000L;

  /** 登记键位元数据（治理界面列表的权威来源；新增键位必须登记）。 */
  public record RegisteredKey(String key, String kind, String description) {
    public static RegisteredKey bool(String key, String description) {
      return new RegisteredKey(key, "bool", description);
    }

    public static RegisteredKey intKey(String key, String description) {
      return new RegisteredKey(key, "int", description);
    }
  }

  private static final Map<String, RegisteredKey> REGISTERED = new java.util.LinkedHashMap<>();

  static {
    register(RegisteredKey.bool(KEY_MEMORY_ENABLED, "长期记忆提取与召回总开关（关闭即零提取零注入）"));
    register(RegisteredKey.bool(KEY_OBSERVABILITY_ENABLED, "步骤记录总开关（关闭即零写入，读路径不受影响）"));
    register(RegisteredKey.intKey(KEY_LLM_TIMEOUT_SECONDS, "单次模型调用超时秒数（每次尝试现读）"));
    register(RegisteredKey.intKey(KEY_LLM_MAX_ITERS, "预留键尚未接入；实际最大迭代由启动配置 chat.max-iters 控制"));
    register(RegisteredKey.bool(KEY_APPROVAL_QUERY_EXECUTION, "查询执行人工确认预留键（尚未接入）"));
  }

  private static void register(RegisteredKey meta) {
    REGISTERED.put(meta.key(), meta);
  }

  /** 治理界面列表：登记键位 + DB 当前值（无 DB 行返回 null = 使用种子默认值）。 */
  public List<RegisteredKeyValue> listRegistered() {
    List<RegisteredKeyValue> out = new java.util.ArrayList<>();
    for (RegisteredKey meta : REGISTERED.values()) {
      out.add(new RegisteredKeyValue(meta.key(), meta.kind(), meta.description(), lookup(meta.key())));
    }
    return out;
  }

  /** 治理更新：仅允许登记键位；清空 value 即回退种子默认值。 */
  public void upsert(String key, String value) {
    if (!REGISTERED.containsKey(key)) {
      throw new IllegalArgumentException("未登记的动态配置键：" + key);
    }
    AgentConfigPO existing = configMapper.selectOne(
        Wrappers.<AgentConfigPO>lambdaQuery().eq(AgentConfigPO::getConfigKey, key).last("LIMIT 1"));
    if (value == null || value.isBlank()) {
      if (existing != null) {
        configMapper.deleteById(existing.getId());
      }
      cache.remove(key);
      return;
    }
    if (existing == null) {
      AgentConfigPO po = new AgentConfigPO();
      po.setConfigKey(key);
      po.setConfigValue(value.trim());
      po.setDescription("治理界面更新");
      configMapper.insert(po);
    } else {
      configMapper.update(null, Wrappers.<AgentConfigPO>lambdaUpdate()
          .eq(AgentConfigPO::getId, existing.getId())
          .set(AgentConfigPO::getConfigValue, value.trim()));
    }
    cache.remove(key);
  }

  /** 治理列表条目。 */
  public record RegisteredKeyValue(String key, String kind, String description, String dbValue) {}

  private final AgentConfigMapper configMapper;
  private final Clock clock;

  /** 显式构造（测试注入 Clock）。 */
  public AgentDynamicConfigService(AgentConfigMapper configMapper) {
    this.configMapper = configMapper;
    this.clock = Clock.systemDefaultZone();
  }

  /** 整型读取：DB 行存在即覆盖种子默认值；值非法回落种子值。 */
  public int lookupInt(String key, int propertyDefault) {
    String row = lookup(key);
    if (row == null) {
      return propertyDefault;
    }
    try {
      return Integer.parseInt(row.trim());
    } catch (NumberFormatException e) {
      log.warn("dynamic config is not an int, falling back to property default: key={}, value={}", key, row);
      return propertyDefault;
    }
  }

  /** 布尔开关：DB 行存在即覆盖种子默认值。 */
  public boolean enabled(String key, boolean propertyDefault) {
    String row = lookup(key);
    if (row == null) {
      return propertyDefault;
    }
    return "true".equalsIgnoreCase(row.trim());
  }

  /** 单键读取（1s 微过期点查）。 */
  String lookup(String key) {
    Instant now = clock.instant();
    Cached cached = cache.get(key);
    if (cached != null && now.isBefore(cached.expiresAt())) {
      return cached.value();
    }
    try {
      AgentConfigPO po = configMapper.selectOne(
          Wrappers.<AgentConfigPO>lambdaQuery().eq(AgentConfigPO::getConfigKey, key).last("LIMIT 1"));
      String value = po == null ? null : po.getConfigValue();
      cache.put(key, new Cached(value, now.plusMillis(MICRO_CACHE_MILLIS)));
      return value;
    } catch (Exception e) {
      log.warn("dynamic config lookup failed, falling back to property default: key={}", key);
      cache.put(key, new Cached(null, now.plusMillis(MICRO_CACHE_MILLIS)));
      return null;
    }
  }

  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  private record Cached(String value, Instant expiresAt) {}
}
