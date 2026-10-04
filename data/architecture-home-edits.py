from pathlib import Path
p=Path('data-ops-business/data-ops-business-home/src/main/java/io/yak/ops/business/home/cockpit/HomeCockpitReader.java');s=p.read_text(encoding='utf-8')
s=s.replace('import java.time.LocalDateTime;','import java.time.LocalDateTime;\nimport java.util.Map;\nimport java.util.function.ToLongFunction;')
a=s.index('    HeaderStats header = new HeaderStats(');s=s[:a]+'''    CountObservation datasource = observe(dataSourceReaderProvider, reader -> reader.summary().total(), "datasource");
    CountObservation offline = observe(offlineReaderProvider, reader -> reader.metrics(start, end).runningCount(), "offline");
    CountObservation workflow = observe(workflowReaderProvider, reader -> reader.metrics(start, end).runningCount(), "workflow");
    CountObservation quality = observe(qualityExecutionReaderProvider, reader -> reader.metrics(start, end).runningCount(), "quality");
    HeaderStats header = new HeaderStats(datasource.value(), offline.value() + workflow.value() + quality.value(),
        datasource.available(), offline.available() && workflow.available() && quality.available(), end,
        Map.of("datasource", datasource, "offline", offline, "workflow", workflow, "quality", quality));
    return new CockpitResponse(header);
  }

  private <T> CountObservation observe(ObjectProvider<T> provider, ToLongFunction<T> query, String source) {
    try {
      T reader = provider.getIfAvailable();
      if (reader == null) return new CountObservation(0L, false, "MODULE_DISABLED");
      return new CountObservation(query.applyAsLong(reader), true, null);
    } catch (RuntimeException exception) {
      LOG.warn("Home cockpit source unavailable: {}", source, exception);
      return new CountObservation(0L, false, "QUERY_UNAVAILABLE");
    }
  }

  public record CockpitResponse(HeaderStats header) {}

  /** Legacy numeric fields retain their fallback; consumers must use availability to interpret them. */
  public record HeaderStats(long dataSourceCount, long runningCount,
      boolean dataSourceAvailable, boolean runningAvailable, LocalDateTime observedAt,
      Map<String, CountObservation> sources) {}

  public record CountObservation(long value, boolean available, String unavailableReason) {}
}
'''
p.write_text(s,encoding='utf-8')
p=Path('data-ops-business/data-ops-business-home/REQUIREMENTS.md');s=p.read_text(encoding='utf-8')
a=s.index('> Cockpit 当前历史 contract');b=s.index('\n',a)
s=s[:a]+'> Cockpit 的历史 numeric 字段继续使用 `0` 降级以兼容旧消费者。2026-10-03 用户批准架构方案 A09 后新增 `dataSourceAvailable`、`runningAvailable`、`observedAt` 和逐来源 `sources`。真实零值必须 available=true；部分失败时聚合 runningAvailable=false，来源观测只表示读取可用性，不成为业务状态或新 Truth Owner。新前端在不可用时展示占位，不把部分合计展示成完整数量。'+s[b:];p.write_text(s,encoding='utf-8')
p=Path('data-ops-ui/src/services/home/types.ts');s=p.read_text(encoding='utf-8');needle='  dataSourceCount: number;';assert needle in s
s=s.replace(needle,needle+'\n  dataSourceAvailable?: boolean;\n  runningAvailable?: boolean;\n  observedAt?: string;\n  sources?: Record<string, { value: number; available: boolean; unavailableReason?: string }>;');p.write_text(s,encoding='utf-8')
p=Path('data-ops-ui/src/pages/home/components/HomeHeader.tsx');s=p.read_text(encoding='utf-8').replace("const dataSourceCount = stats?.dataSourceCount ?? '--';", "const dataSourceCount = stats?.dataSourceAvailable === false ? '--' : stats?.dataSourceCount ?? '--';").replace("const runningCount = stats?.runningCount ?? '--';", "const runningCount = stats?.runningAvailable === false ? '--' : stats?.runningCount ?? '--';")
s=s.replace("  const runningCount =", "  const runningCount =")
s=s.replace('          <div className="mt-2.5', '          {(stats?.dataSourceAvailable === false || stats?.runningAvailable === false) && <span role="status" className="text-xs text-gray-500">部分统计暂不可用</span>}\n          <div className="mt-2.5')
p.write_text(s,encoding='utf-8')
p=Path('data-ops-business/data-ops-business-home/src/test/java/io/yak/ops/business/home/cockpit/HomeCockpitReaderTest.java');s=p.read_text(encoding='utf-8')
s=s.replace('assertThat(response.header().runningCount()).isEqualTo(4);','assertThat(response.header().runningCount()).isEqualTo(4);\n    assertThat(response.header().dataSourceAvailable()).isTrue();\n    assertThat(response.header().runningAvailable()).isTrue();')
s=s.replace('assertThat(response.header().runningCount()).isZero();','assertThat(response.header().runningCount()).isZero();\n    assertThat(response.header().dataSourceAvailable()).isFalse();\n    assertThat(response.header().runningAvailable()).isFalse();\n    assertThat(response.header().sources().get("workflow").unavailableReason()).isEqualTo("MODULE_DISABLED");')
p.write_text(s,encoding='utf-8')
