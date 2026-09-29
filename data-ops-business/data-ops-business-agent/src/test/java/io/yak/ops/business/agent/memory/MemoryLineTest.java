package io.yak.ops.business.agent.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.telemetry.AgentKindRegistry;
import io.yak.ops.business.agent.telemetry.AgentStepRecorder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 记忆线 M1 可证伪验收：提取闸门（开关/THROTTLED/实质内容）、解析白名单与钳制、
 * LEDGER 入库、召回排序（类型权重×置信度×衰减）、预算截断、命中回写。
 */
class MemoryLineTest {

  private final MemoryRepository repository = mock(MemoryRepository.class);
  private final MemoryCompletionPort port = mock(MemoryCompletionPort.class);
  private final AgentProperties properties = new AgentProperties();
  private final io.yak.ops.business.agent.repository.AgentDynamicConfigService dynamicConfig =
      new io.yak.ops.business.agent.repository.AgentDynamicConfigService(
          mock(io.yak.ops.business.agent.dao.mapper.AgentConfigMapper.class));

  private MemoryFlushService flushService() {
    return new MemoryFlushService(repository, port, properties, dynamicConfig);
  }

  /** 轮询等待异步提取完成（CompletableFuture 无回调阻塞缝；最多 2s）。 */
  private static void eventually(Supplier<Boolean> condition) {
    long deadline = System.currentTimeMillis() + 2000;
    while (!condition.get() && System.currentTimeMillis() < deadline) {
      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  @Test
  void globalSwitchOffMeansNoFlushAndNoSteps() {
    properties.getMemory().setEnabled(false);
    flushService().submitAfterTurn(1L, "s1", "t1", "问", "答", List.of(),
        (t, o, d, e, i) -> { throw new IllegalStateException("开关关闭不得有任何回调"); });
    eventually(() -> false);
    verify(repository, never()).insertLedger(any());
  }

  @Test
  void hollowTurnIsNotExtracted() {
    flushService().submitAfterTurn(1L, "s1", "t1", "问", "  ", List.of(),
        (t, o, d, e, i) -> { throw new IllegalStateException("空洞轮次不应触发提取"); });
    eventually(() -> true);
    verify(repository, never()).insertLedger(any());
  }

  @Test
  void throttledSecondFlushWithinWindowIsSkipped() {
    MemoryFlushService service = flushService();
    when(port.complete(anyString(), anyString())).thenReturn(
        "[{\"type\":\"GLOSSARY\",\"content\":\"营收=含税GMV\",\"keywords\":\"营收 口径\",\"confidence\":0.9}]");
    List<String> outcomes = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    service.submitAfterTurn(1L, "s1", "t1", "问", "答", List.of(),
        (t, o, d, e, i) -> outcomes.add(t + ":" + o));
    eventually(() -> outcomes.size() >= 1);
    service.submitAfterTurn(1L, "s1", "t2", "问", "答", List.of(),
        (t, o, d, e, i) -> outcomes.add(t + ":" + o));
    eventually(() -> outcomes.size() >= 2);
    // 第二次在 5min 窗口内：THROTTLED 跳过，只入库一次
    verify(repository, timeout(1000)).insertLedger(any());
    org.junit.jupiter.api.Assertions.assertTrue(outcomes.get(1).endsWith("SKIPPED"),
        "第二次提取必须被 THROTTLED 跳过：" + outcomes);
  }

  @Test
  void extractionParsesWhitelistedTypesAndClampsConfidence() {
    when(port.complete(anyString(), anyString())).thenReturn(
        "```json\n[{\"type\":\"GLOSSARY\",\"content\":\"营收=含税口径\",\"keywords\":\"营收\",\"confidence\":0.9},"
            + "{\"type\":\"EVIL\",\"content\":\"非法类型应被过滤\",\"confidence\":1},"
            + "{\"type\":\"FACT\",\"content\":\"" + "长".repeat(2500) + "\",\"confidence\":5}]"
            + "\n```");
    flushService().submitAfterTurn(1L, "s1", "t1", "问", "答", List.of(),
        (t, o, d, e, i) -> { });
    org.mockito.ArgumentCaptor<MemoryRecord> captor =
        org.mockito.ArgumentCaptor.forClass(MemoryRecord.class);
    verify(repository, timeout(3000).times(2)).insertLedger(captor.capture());
    List<MemoryRecord> inserted = captor.getAllValues();
    org.junit.jupiter.api.Assertions.assertEquals("GLOSSARY", inserted.get(0).memoryType());
    org.junit.jupiter.api.Assertions.assertEquals("FACT", inserted.get(1).memoryType());
    org.junit.jupiter.api.Assertions.assertTrue(inserted.get(1).content().length() <= 2000,
        "超长内容必须截断");
    org.junit.jupiter.api.Assertions.assertEquals(1.0, inserted.get(1).confidence(), 0.001,
        "置信度必须钳制到 1.0");
  }

  @Test
  void brokenExtractionJsonIsAbandonedWithoutLedgerWrite() {
    when(port.complete(anyString(), anyString())).thenReturn("这不是 JSON");
    flushService().submitAfterTurn(1L, "s1", "t1", "问", "答", List.of(),
        (t, o, d, e, i) -> { });
    eventually(() -> true);
    verify(repository, never()).insertLedger(any());
  }

  @Test
  void recallOrdersByTypeWeightConfidenceAndDecay() {
    when(repository.listActiveForUser("42")).thenReturn(List.of(
        new MemoryRecord(1L, "USER", "42", "FACT", "CURATED", "订单宽表每日更新", "订单", 0.8, 0, null, null, "ACTIVE"),
        new MemoryRecord(2L, "USER", "42", "GLOSSARY", "CURATED", "营收=含税口径", "营收 口径", 0.8, 0, null, null, "ACTIVE"),
        new MemoryRecord(3L, "USER", "42", "PREFERENCE", "CURATED", "偏好表格输出", "表格", 0.8, 0, null, null, "ACTIVE"),
        new MemoryRecord(4L, "USER", "42", "LESSON", "CURATED", "refund_status 需先查字典", "字典", 0.5, 0, null, null, "ACTIVE"),
        new MemoryRecord(5L, "USER", "42", "FACT", "CURATED", "低置信度不进候选", "x", 0.2, 0, null, null, "ACTIVE")));
    AgentProperties.Memory config = properties.getMemory();
    List<Long> hits = new java.util.ArrayList<>();
    // 空查询 = 关闭关键词过滤，纯验证 score 排序路径
    String section = new MemoryRecallService(repository)
        .recallSection("42", "", config, hits);
    // GLOSSARY 权重 1.5 最高 → 营收口径排第一；0.2 置信度被门槛过滤
    org.junit.jupiter.api.Assertions.assertTrue(section.startsWith("## 长期记忆"));
    org.junit.jupiter.api.Assertions.assertTrue(
        section.indexOf("营收=含税口径") < section.indexOf("订单宽表"),
        "GLOSSARY 类型权重应使其排最前：" + section);
    org.junit.jupiter.api.Assertions.assertFalse(section.contains("低置信度"));
    org.junit.jupiter.api.Assertions.assertEquals(List.of(2L, 3L, 1L, 4L), hits);
  }

  @Test
  void recallBudgetTruncatesSection() {
    when(repository.listActiveForUser("42")).thenReturn(java.util.stream.IntStream.range(0, 20)
        .mapToObj(i -> new MemoryRecord((long) i, "USER", "42", "FACT", "CURATED",
            "事实条目-" + i + "-" + "x".repeat(200), "事实", 0.9, 0, null, null, "ACTIVE"))
        .toList());
    properties.getMemory().setRecallMaxChars(500);
    String section = new MemoryRecallService(repository).recallSection("42", "", properties.getMemory(),
        new java.util.ArrayList<>());
    org.junit.jupiter.api.Assertions.assertTrue(section.length() <= 600, "注入必须受预算截断");
    org.junit.jupiter.api.Assertions.assertTrue(section.contains("## 长期记忆"));
  }

  @Test
  void decayHalvesScoreAfterOneHalfLife() {
    LocalDateTime now = LocalDateTime.now();
    double fresh = MemoryRecallService.decay(null, now, 720);
    double half = MemoryRecallService.decay(now.minusHours(720), now, 720);
    org.junit.jupiter.api.Assertions.assertEquals(1.0, fresh);
    org.junit.jupiter.api.Assertions.assertEquals(0.5, half, 0.001);
  }

  @Test
  void stepKindConstantsRegistered() {
    for (String kind : new String[] {
        AgentStepRecorder.KIND_MEMORY_FLUSH,
        AgentStepRecorder.KIND_MEMORY_RECALL,
        AgentStepRecorder.KIND_MEMORY_CONSOLIDATE}) {
      org.junit.jupiter.api.Assertions.assertTrue(AgentKindRegistry.isRegistered(kind));
    }
  }
}
