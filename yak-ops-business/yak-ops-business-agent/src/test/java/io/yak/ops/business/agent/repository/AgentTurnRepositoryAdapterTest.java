package io.yak.ops.business.agent.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import io.yak.ops.business.agent.dao.mapper.AgentTurnMapper;
import io.yak.ops.business.agent.dao.model.AgentTurnPO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 轮次生命周期持久化适配行为：hasActiveTurn 必须覆盖 QUEUED/RUNNING/WAITING_INPUT
 * （HITL 反问轮同样占用单飞名额，DOMAIN 单飞不变量）；其余状态不重复占用。
 */
class AgentTurnRepositoryAdapterTest {

  private final AgentTurnMapper mapper = mock(AgentTurnMapper.class);
  private final AgentTurnRepositoryAdapter adapter = new AgentTurnRepositoryAdapter(mapper);

  @BeforeAll
  static void initLambdaCache() {
    // 纯单元测试无 MyBatis 运行时：lambdaWrapper 需要 TableInfo 缓存才能解析 PO 列名
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), AgentTurnPO.class);
  }

  @Test
  void hasActiveTurnCoversQueuedRunningAndWaitingInput() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Wrapper<AgentTurnPO>> captor =
        ArgumentCaptor.forClass((Class) Wrapper.class);
    when(mapper.selectCount(captor.capture())).thenReturn(1L);

    assertTrue(adapter.hasActiveTurn("s1"));

    // IN 值以参数占位（#{ew.paramNameValuePairs.MPGENVAL*)}）呈现，状态名不经 sql segment 内联；
    // 以占位数量锁定「至少 3 个状态入 IN」（回退为 2 状态即触发本断言）。
    String segment = ((AbstractWrapper<?, ?, ?>) captor.getValue()).getSqlSegment();
    assertTrue(segment.contains("status IN"),
        "hasActiveTurn 必须包含 status IN 条件：" + segment);
    int placeholders = segment.split("#\\{", -1).length - 1;
    assertTrue(placeholders >= 4,
        "HITL 反问轮必须占用单飞名额（期望 session + 3 状态 = 4 个占位，实际 " + placeholders + "）："
            + segment);
  }

  @Test
  void hasActiveTurnFalseWhenNoRows() {
    when(mapper.selectCount(any())).thenReturn(0L);
    assertFalse(adapter.hasActiveTurn("s2"));
  }

  @Test
  void requeueForResumeIsConditionalTransition() {
    // requeueForResume 是 WAITING_INPUT→QUEUED 的条件转移：受影响行数为 0（该轮已不在
    // WAITING_INPUT，如并发被应答/取消）→ 返回 false，由 service 映射 409
    when(mapper.update(any(), any())).thenReturn(0);
    assertFalse(adapter.requeueForResume("t-x", "\"resume\""));
    when(mapper.update(any(), any())).thenReturn(1);
    assertTrue(adapter.requeueForResume("t-y", "\"resume\""));
  }
}