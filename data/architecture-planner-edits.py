from pathlib import Path
p=Path('data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/derive/ModelDeriveService.java');s=p.read_text(encoding='utf-8')
a=s.index('    String statPeriod = normalizeStatPeriod(');b=s.index('\n  /** 指标依赖模型',a)
s=s[:a]+'''    MetricDraftPlanner.Plan plan = MetricDraftPlanner.plan(metrics);
    List<DraftMeasure> measures = plan.measures().stream()
        .map(value -> new DraftMeasure(value.sourceColumn(), value.aggregateFunc(), value.landingName()))
        .toList();
    return new MetricDraftView(
        processIds, upstreamModelIds, plan.statPeriod(), measures, plan.dimensions(), warnings);
  }
'''+s[b:]
a=s.index('  /** 指标统计周期');b=s.index('  /** 60:指标反推草稿视图',a)
segment=s[a:b].replace('private static DraftMeasure parseMeasure','private static Measure parseMeasure').replace('new DraftMeasure(', 'new Measure(')
# Keep existing interpretation, including legacy period aliases, in this structural change.
imports='import io.yak.ops.common.api.metric.MetricQueryView;\nimport java.util.*;\nimport java.util.regex.Matcher;\nimport java.util.regex.Pattern;\nimport org.springframework.util.StringUtils;\n'
planner='package io.yak.ops.business.modeling.derive;\n\n'+imports+'''\n/** Plans metric-derived dimensions and measures without writes or domain lookups. */
final class MetricDraftPlanner {
  private MetricDraftPlanner() {}
  private static final Set<String> AGGREGATE_FUNCS = Set.of("SUM", "COUNT", "COUNT_DISTINCT", "MAX", "MIN", "AVG");
  record Measure(String sourceColumn, String aggregateFunc, String landingName) {}
  record Plan(String statPeriod, List<Measure> measures, List<String> dimensions) {}

  static Plan plan(List<MetricQueryView> metrics) {
    String period = normalizeStatPeriod(metrics.stream().map(MetricQueryView::statPeriod)
        .filter(StringUtils::hasText).findFirst().orElse(null));
    List<Measure> measures = new ArrayList<>();
    Set<String> dimensions = new LinkedHashSet<>();
    for (MetricQueryView metric : metrics) {
      Measure measure = parseMeasure(metric);
      if (measure != null) measures.add(measure);
      dimensions.addAll(parseJsonStringArray(metric.statDimensions()));
    }
    return new Plan(period, List.copyOf(measures), List.copyOf(dimensions));
  }

'''+segment+'}\n'
s=s[:a]+s[b:];p.write_text(s,encoding='utf-8');p.with_name('MetricDraftPlanner.java').write_text(planner,encoding='utf-8')
# Workflow read projection has no scheduling, persistence, event publication or gateway effects.
p=Path('data-ops-business/data-ops-business-workflow/src/main/java/io/yak/ops/business/workflow/runtime/WorkflowRuntime.java');s=p.read_text(encoding='utf-8')
a=s.index('  private WorkflowInstanceVO toView(');b=s.index('  @PreDestroy',a);segment=s[a:b]
segment=segment.replace('private WorkflowInstanceVO toView','static WorkflowInstanceVO toView').replace('private NodeInstanceVO toNodeView','private static NodeInstanceVO toNodeView').replace('private Map<String, Object> receivedInput','private static Map<String, Object> receivedInput').replace('private AttemptVO toAttemptView','private static AttemptVO toAttemptView')
segment=segment.replace('WorkflowExecutionMetadata runMetadata)', 'WorkflowExecutionMetadata runMetadata, Map<String, NodeDispatch> dispatches)')
segment=segment.replace('toNodeView(execution.id(), node, runMetadata.nodes().get(node.nodeId()))','toNodeView(node, runMetadata.nodes().get(node.nodeId()), dispatches == null ? null : dispatches.get(node.nodeId()))')
segment=segment.replace('      String executionId,\n      NodeExecution node,\n      NodeMetadata nodeMetadata)', '      NodeExecution node,\n      NodeMetadata nodeMetadata,\n      NodeDispatch dispatch)')
segment=segment.replace('node.attempts().stream().map(this::toAttemptView)', 'node.attempts().stream().map(WorkflowExecutionProjection::toAttemptView)')
segment=segment.replace('    ConcurrentMap<String, NodeDispatch> dispatches = latestDispatches.get(executionId);\n    NodeDispatch dispatch = dispatches == null ? null : dispatches.get(node.nodeId());\n','')
imports='\n'.join(line for line in s.splitlines() if line.startswith('import ') and any(x in line for x in ['execution.Node','execution.WorkflowExecution','definition.TriggerRule','definition.NodeFailurePolicy','spi.NodeDispatch','task.TaskVersionSnapshot','bean.vo.workflow','java.util.List;','java.util.Map;','java.util.LinkedHashMap;']))
projection='package io.yak.ops.business.workflow.runtime;\n\n'+imports+'\nimport io.yak.ops.business.workflow.runtime.WorkflowRuntime.WorkflowExecutionMetadata;\nimport io.yak.ops.business.workflow.runtime.WorkflowRuntime.NodeMetadata;\n\n/** Builds the HTTP read model from immutable engine snapshots and runtime metadata. */\nfinal class WorkflowExecutionProjection {\n  private WorkflowExecutionProjection() {}\n'+segment+'}\n'
s=s[:a]+'''  private WorkflowInstanceVO toView(WorkflowExecution execution, WorkflowExecutionMetadata metadata) {
    return WorkflowExecutionProjection.toView(execution, metadata, latestDispatches.get(execution.id()));
  }

'''+s[b:]
s=s.replace('private record WorkflowExecutionMetadata','record WorkflowExecutionMetadata').replace('private record NodeMetadata','record NodeMetadata')
p.write_text(s,encoding='utf-8');p.with_name('WorkflowExecutionProjection.java').write_text(projection,encoding='utf-8')
