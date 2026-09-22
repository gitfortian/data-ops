package io.yak.ops.business.metric.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.metric.api.MetricApi;
import io.yak.ops.business.metric.dao.mapper.MetricTagRelMapper;
import io.yak.ops.business.metric.dao.mapper.MetricUsageMapper;
import io.yak.ops.business.metric.dao.mapper.MetricVersionMapper;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricStatus;
import io.yak.ops.business.metric.domain.MetricType;
import io.yak.ops.business.metric.domain.StatPeriod;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.lineage.MetricLineageRegistrationService;
import io.yak.ops.business.metric.repository.MetricCompositionRepository;
import io.yak.ops.business.metric.repository.MetricDependencyRepository;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.business.metric.support.CodeGenerator;
import io.yak.ops.common.bean.po.metric.MetricCompositionPO;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 指标目录规则单测：类型差异化校验、复合 token 流与环检测、引用阻断。 */
class MetricCatalogServiceTest {

  private MetricRepository repository;
  private MetricCompositionRepository compositionRepository;
  private MetricDependencyRepository dependencyRepository;
  private MetricVersionRepository versionRepository;
  private MetricUsageMapper usageMapper;
  private CurrentProject currentProject;
  private MetricReferenceResolver referenceResolver;
  private MetricLineageRegistrationService lineageRegistrationService;
  private MetricCatalogService service;

  @BeforeEach
  void setUp() {
    repository = mock(MetricRepository.class);
    compositionRepository = mock(MetricCompositionRepository.class);
    dependencyRepository = mock(MetricDependencyRepository.class);
    versionRepository = mock(MetricVersionRepository.class);
    usageMapper = mock(MetricUsageMapper.class);
    currentProject = mock(CurrentProject.class);
    referenceResolver = mock(MetricReferenceResolver.class);
    lineageRegistrationService = mock(MetricLineageRegistrationService.class);
    AuditOperationHandle auditHandle = mock(AuditOperationHandle.class);
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    when(auditService.start(any())).thenReturn(auditHandle);
    when(currentProject.requireProjectId()).thenReturn(1L);
    when(referenceResolver.modelReference(anyLong()))
        .thenReturn(MetricReferenceResolver.Reference.EMPTY);
    when(referenceResolver.standardReference(anyLong()))
        .thenReturn(MetricReferenceResolver.Reference.EMPTY);
    when(referenceResolver.metricsById(any())).thenReturn(java.util.Map.of());
    service = new MetricCatalogService(
        repository, compositionRepository, dependencyRepository, versionRepository,
        mock(MetricTagRelMapper.class), usageMapper, mock(MetricVersionMapper.class),
        currentProject, auditService, lineageRegistrationService, referenceResolver,
        new DerivedMetricAssembler());
  }

  private static Metric metric(Long id, String code, MetricType type, int version) {
    return new Metric(id, code, code, 1L, 1L, type, null, null, "SUM(amount)", null, null,
        null, null, null, 9L, null, StatPeriod.DAY, null, null, "tester",
        MetricStatus.ENABLED, version, "tester", "tester",
        LocalDateTime.now(), LocalDateTime.now());
  }

  private static MetricApi.CompositionItem ref(Long subId, int sort) {
    return new MetricApi.CompositionItem(subId, "REF", null, sort);
  }

  private static MetricApi.CompositionItem symbol(String operator, int sort) {
    return new MetricApi.CompositionItem(0L, operator, operator, sort);
  }

  private static MetricApi.CreateRequest create(MetricType type,
      List<MetricApi.CompositionItem> compositions) {
    return new MetricApi.CreateRequest(
        "测试指标", "test_metric", 1L, 1L, type.name(), null, null,
        type == MetricType.ATOMIC ? "SUM(amount)" : null, null, null,
        type == MetricType.DERIVED ? 20L : null, null, null,
        type == MetricType.ATOMIC ? 9L : null, null, "DAY", null, null, null, compositions);
  }

  // ── 编码自动生成(M-1) ──

  @Test
  void createWithChineseNameAutoGeneratesUniqueCode() {
    String base = CodeGenerator.fromName("订单量", "metric");
    when(repository.existsByCode(base)).thenReturn(true);
    when(repository.existsByCode(base + "_2")).thenReturn(false);
    when(repository.insert(any(), any())).thenAnswer(call ->
        call.getArgument(0, Metric.class).withPersisted(77L, "tester", LocalDateTime.now()));
    when(dependencyRepository.listByMetric(77L)).thenReturn(List.of());
    MetricApi.CreateRequest request = new MetricApi.CreateRequest(
        "订单量", null, 1L, 1L, "ATOMIC", null, null, "SUM(amount)", null, null, null, null, null,
        9L, null, "DAY", null, null, null, null);

    Metric created = service.create(request, "tester");

    assertThat(created.metricCode()).isEqualTo(base + "_2");
  }

  // ── 类型差异化校验(决策 4) ──

  @Test
  void createAtomicRequiresMeasureExpr() {
    when(repository.existsByCode("test_metric")).thenReturn(false);
    MetricApi.CreateRequest request = new MetricApi.CreateRequest(
        "原子", "test_metric", 1L, 1L, "ATOMIC", null, null, null, null, null, null, null, null,
        9L, null, "DAY", null, null, null, null);

    assertThatThrownBy(() -> service.create(request, "tester"))
        .isInstanceOf(MetricException.class)
        .hasMessageContaining("度量表达式");
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createDerivedRejectsReferenceToNonAtomic() {
    when(repository.existsByCode("test_metric")).thenReturn(false);
    when(repository.findById(20L)).thenReturn(Optional.of(metric(20L, "sub", MetricType.DERIVED, 1)));

    assertThatThrownBy(() -> service.create(create(MetricType.DERIVED, null), "tester"))
        .isInstanceOf(MetricException.class)
        .hasMessageContaining("只能引用原子指标");
  }

  @Test
  void createDerivedAcceptsAtomicReferenceAndRegistersSnapshot() {
    when(repository.existsByCode("test_metric")).thenReturn(false);
    Metric atomic = metric(20L, "atomic_a", MetricType.ATOMIC, 3);
    when(repository.findById(20L)).thenReturn(Optional.of(atomic));
    when(repository.insert(any(), any())).thenAnswer(call ->
        call.getArgument(0, Metric.class).withPersisted(10L, "tester", LocalDateTime.now()));
    when(dependencyRepository.listByMetric(10L)).thenReturn(List.of());

    Metric created = service.create(create(MetricType.DERIVED, null), "tester");

    assertThat(created.id()).isEqualTo(10L);
    verify(dependencyRepository).syncDependencies(eq(created),
        org.mockito.ArgumentMatchers.argThat(specs -> specs.stream().anyMatch(
            spec -> "REF_METRIC".equals(spec.dependencyType())
                && "atomic_a".equals(spec.dependencyCode())
                && Integer.valueOf(3).equals(spec.dependencyVersion()))));
    verify(lineageRegistrationService).registerMetric(eq(created), anyList());
  }

  // ── 复合指标 token 流与环检测(决策 1) ──

  @Test
  void createCompositeRejectsSymbolOnlyFormula() {
    when(repository.existsByCode("test_metric")).thenReturn(false);

    assertThatThrownBy(() -> service.create(
        create(MetricType.COMPOSITE, List.of(symbol("MUL", 0), symbol("LPAREN", 1))), "tester"))
        .isInstanceOf(MetricException.class)
        .hasMessageContaining("至少需要一个子指标");
  }

  @Test
  void createCompositeRejectsUnknownOperator() {
    when(repository.existsByCode("test_metric")).thenReturn(false);
    when(repository.findById(20L)).thenReturn(Optional.of(metric(20L, "sub", MetricType.ATOMIC, 1)));

    assertThatThrownBy(() -> service.create(
        create(MetricType.COMPOSITE,
            List.of(new MetricApi.CompositionItem(20L, "MOD", null, 0))), "tester"))
        .isInstanceOf(MetricException.class)
        .hasMessageContaining("不支持的运算方式");
  }

  @Test
  void createCompositeAcceptsTokenStreamWithSymbolItems() {
    when(repository.existsByCode("test_metric")).thenReturn(false);
    when(repository.findById(anyLong()))
        .thenReturn(Optional.of(metric(20L, "sub", MetricType.ATOMIC, 1)));
    when(repository.insert(any(), any())).thenAnswer(call ->
        call.getArgument(0, Metric.class).withPersisted(10L, "tester", LocalDateTime.now()));

    List<MetricApi.CompositionItem> items = List.of(
        ref(20L, 0), symbol("DIV", 1), ref(21L, 2), symbol("MUL", 3), symbol("LPAREN", 4),
        ref(22L, 5), symbol("ADD", 6), ref(23L, 7), symbol("RPAREN", 8));

    Metric created = service.create(create(MetricType.COMPOSITE, items), "tester");

    assertThat(created.metricType()).isEqualTo(MetricType.COMPOSITE);
    verify(compositionRepository).replaceCompositions(10L, items);
  }

  @Test
  void updateCompositeRejectsSelfReference() {
    Metric existing = metric(10L, "c", MetricType.COMPOSITE, 1);
    when(repository.findById(10L)).thenReturn(Optional.of(existing));

    MetricApi.UpdateRequest request = new MetricApi.UpdateRequest(
        "复合", 1L, null, "COMPOSITE", null, null, null, null, null, null, null, null, null, null,
        "DAY", null, null, null, 1, List.of(ref(10L, 0)));

    assertThatThrownBy(() -> service.update(10L, request, "tester"))
        .isInstanceOf(MetricException.class)
        .hasMessageContaining("不能引用自身");
  }

  @Test
  void updateCompositeDetectsCycle() {
    Metric existing = metric(10L, "c", MetricType.COMPOSITE, 1);
    when(repository.findById(10L)).thenReturn(Optional.of(existing));
    when(repository.findById(20L)).thenReturn(Optional.of(metric(20L, "mid", MetricType.COMPOSITE, 1)));
    MetricCompositionPO loopBack = new MetricCompositionPO();
    loopBack.setMetricId(20L);
    loopBack.setSubMetricId(10L);
    loopBack.setOperator("REF");
    when(compositionRepository.listByMetric(20L)).thenReturn(List.of(loopBack));

    MetricApi.UpdateRequest request = new MetricApi.UpdateRequest(
        "复合", 1L, null, "COMPOSITE", null, null, null, null, null, null, null, null, null, null,
        "DAY", null, null, null, 1, List.of(ref(20L, 0)));

    assertThatThrownBy(() -> service.update(10L, request, "tester"))
        .isInstanceOf(MetricException.class)
        .hasMessageContaining("循环引用");
  }

  @Test
  void updateRejectsStaleVersion() {
    when(repository.findById(10L)).thenReturn(Optional.of(metric(10L, "m", MetricType.ATOMIC, 5)));

    MetricApi.UpdateRequest request = new MetricApi.UpdateRequest(
        "原子", 1L, 1L, "ATOMIC", null, null, "SUM(x)", null, null, null, null, null, 9L, null,
        "DAY", null, null, null, 2, null);

    assertThatThrownBy(() -> service.update(10L, request, "tester"))
        .isInstanceOf(MetricException.class)
        .satisfies(e -> assertThat(((MetricException) e).getErrorCode())
            .isEqualTo(MetricErrorCode.VERSION_CONFLICT));
  }

  // ── 引用阻断(决策 2 / DOMAIN 不变量 4、5) ──

  @Test
  void changeStatusToDisabledBlockedByDerivedReference() {
    Metric existing = metric(10L, "a", MetricType.ATOMIC, 1);
    when(repository.findById(10L)).thenReturn(Optional.of(existing));
    when(repository.listReferring(10L)).thenReturn(List.of(metric(30L, "d", MetricType.DERIVED, 1)));

    assertThatThrownBy(() -> service.changeStatus(10L, "DISABLED", "tester"))
        .isInstanceOf(MetricException.class)
        .satisfies(e -> assertThat(((MetricException) e).getErrorCode())
            .isEqualTo(MetricErrorCode.METRIC_REFERENCED));
    verify(repository, never()).update(any());
  }

  @Test
  void changeStatusWritesSnapshotWithTransition() {
    Metric existing = metric(10L, "a", MetricType.ATOMIC, 1);
    when(repository.findById(10L)).thenReturn(Optional.of(existing));
    when(repository.listReferring(10L)).thenReturn(List.of());
    when(compositionRepository.countBySubMetric(10L)).thenReturn(0L);
    when(repository.update(any())).thenReturn(true);

    Metric updated = service.changeStatus(10L, "DISABLED", "tester");

    assertThat(updated.status()).isEqualTo(MetricStatus.DISABLED);
    assertThat(updated.version()).isEqualTo(2);
    verify(versionRepository).saveSnapshot(eq(updated),
        org.mockito.ArgumentMatchers.contains("状态变更:ENABLED→DISABLED"), eq("tester"));
  }

  @Test
  void deleteBlockedByCompositionReference() {
    Metric existing = metric(10L, "a", MetricType.ATOMIC, 1);
    when(repository.findById(10L)).thenReturn(Optional.of(existing));
    when(compositionRepository.countBySubMetric(10L)).thenReturn(2L);

    assertThatThrownBy(() -> service.delete(10L))
        .isInstanceOf(MetricException.class)
        .satisfies(e -> assertThat(((MetricException) e).getErrorCode())
            .isEqualTo(MetricErrorCode.METRIC_REFERENCED));
    verify(repository, never()).deleteById(any());
  }

  @Test
  void deleteBlockedByUsageCount() {
    Metric existing = metric(10L, "a", MetricType.ATOMIC, 1);
    when(repository.findById(10L)).thenReturn(Optional.of(existing));
    when(compositionRepository.countBySubMetric(10L)).thenReturn(0L);
    when(repository.listReferring(10L)).thenReturn(List.of());
    when(usageMapper.countByMetric(1L, 10L)).thenReturn(3L);

    assertThatThrownBy(() -> service.delete(10L))
        .isInstanceOf(MetricException.class)
        .hasMessageContaining("3 处使用");
    verify(repository, never()).deleteById(any());
  }
}
