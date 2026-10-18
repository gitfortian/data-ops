package io.yak.ops.business.modeling.derive;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.repository.ModelVersionRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.version.ModelPublishedStructureReader;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Evidence;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Reference;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Status;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AdsApplicationDesignServiceTest {
  private final ModelRepository models = mock(ModelRepository.class);
  private final ModelVersionRepository versions = mock(ModelVersionRepository.class);
  private final ModelStructureService structures = mock(ModelStructureService.class);
  private final ModelPublishedStructureReader published = mock(ModelPublishedStructureReader.class);
  private final CurrentProject project = mock(CurrentProject.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final MetricPublicationEvidenceProvider metricPublicationEvidence =
      mock(MetricPublicationEvidenceProvider.class);
  private final AdsApplicationDesignService service = new AdsApplicationDesignService(
      models, versions, structures, published, project, metricPublicationEvidence,
      jdbc, new ObjectMapper());

  private final Model ads = new Model(
      10L, "ads_order_overview", "订单经营日报", ModelDialect.MYSQL, null,
      ModelStatus.DRAFT, "alice", null, null, "ADS", 50L, null, null, null, null);

  @Test
  void rejectsMissingApplicationIdentityBeforeReadingOrPersistingUpstream() {
    when(models.findByIdForUpdate(10L)).thenReturn(Optional.of(ads));
    var request = new AdsApplicationDesignService.Request(
        "", "订单经营日报", "alice", "订单运营看板", "REPORT", "DWS",
        20L, 30L, 2, "a".repeat(64),
        List.of(new AdsApplicationDesignService.OutputField("order_day", "order_day", null, "LOW")),
        List.of("order_day"), "", "", null, "DAILY", 0L, null, null);
    assertThatThrownBy(() -> service.save(10L, request, "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("应用编码");
    verify(models, never()).findById(20L);
    verifyNoInteractions(versions, published, jdbc);
  }

  @Test
  void rejectsUnsupportedConsumerTypeBeforePersisting() {
    when(models.findByIdForUpdate(10L)).thenReturn(Optional.of(ads));
    var request = new AdsApplicationDesignService.Request(
        "order_overview", "订单经营日报", "alice", "订单运营看板", "UNKNOWN", "DWS",
        20L, 30L, 2, "a".repeat(64),
        List.of(new AdsApplicationDesignService.OutputField("order_day", "order_day", null, "LOW")),
        List.of("order_day", "ORDER_DAY"), "", "", null, "DAILY", 0L, null, null);
    assertThatThrownBy(() -> service.save(10L, request, "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("消费类型");
    verify(models, never()).findById(20L);
    verifyNoInteractions(versions, published, jdbc);
  }

  private StructureView.ColumnView column(String name, Long standard) {
    return new StructureView.ColumnView(
        null, name, "DATE", null, null, false, null, null, null,
        null, null, null, null, null, null, null, standard, null, null, null);
  }

  private StructureView structure(long modelId, Long standard) {
    return new StructureView(modelId, "code", "name", "MYSQL", "DRAFT",
        null, "table_name", null, List.of(column("order_day", standard)),
        List.of(), List.of(), null, Map.of());
  }

  private AdsApplicationDesignService.Request good(Long standard, Long revision,
      List<String> keys) {
    return good(standard, revision, keys,
        io.yak.ops.business.modeling.structure.StructureFingerprint.of(structure(10L, 11L)));
  }

  private AdsApplicationDesignService.Request good(Long standard, Long revision,
      List<String> keys, String expectedTargetFingerprint) {
    return new AdsApplicationDesignService.Request(
        "order_overview", "订单经营日报", "alice", "订单运营看板", "REPORT", "DWS",
        20L, 30L, 2, "a".repeat(64),
        List.of(new AdsApplicationDesignService.OutputField("order_day", "order_day", standard, "LOW")),
        keys, "", "order_day DESC", null, "DAILY", revision, expectedTargetFingerprint, null);
  }

  private void prepareValidSource() {
    Model dws = new Model(20L, "dws_order", "订单汇总", ModelDialect.MYSQL, null,
        ModelStatus.DRAFT, "alice", null, null, "DWS", 50L, null, null, null, null)
        .withPublishState(ModelStatus.PUBLISHED, 30L, 2);
    when(models.findById(10L)).thenReturn(Optional.of(ads));
    when(models.findByIdForUpdate(10L)).thenReturn(Optional.of(ads));
    when(models.findById(20L)).thenReturn(Optional.of(dws));
    when(models.findByIdForUpdate(20L)).thenReturn(Optional.of(dws));
    when(structures.get(10L)).thenReturn(structure(10L, 11L));
    when(structures.getForUpdate(10L)).thenReturn(structure(10L, 11L));
    when(published.requirePublishedStructure(20L)).thenReturn(structure(20L, 11L));
    when(versions.findById(30L, 20L)).thenReturn(Optional.of(
        new ModelVersion(30L, 20L, 2, "{}", "{}", 1, "a".repeat(64),
            "alice", null)));
    when(project.requireProjectId()).thenReturn(1L);
  }

  private AdsApplicationDesignService.Request withMetricPin(
      AdsApplicationDesignService.Request base, Long legacyMetricVersionId,
      AdsApplicationDesignService.MetricPublicationPin pin) {
    return new AdsApplicationDesignService.Request(
        base.appCode(), base.appName(), base.owner(), base.purpose(), base.consumerType(),
        base.sourceMode(), base.upstreamModelId(), base.upstreamVersionId(),
        base.upstreamVersionNo(), base.upstreamChecksum(), base.outputFields(),
        base.businessKeys(), base.filterExpression(), base.sortExpression(),
        legacyMetricVersionId, base.refreshTarget(), base.expectedRevision(),
        base.expectedTargetStructureFingerprint(), pin);
  }

  @Test
  void omitsNullMetricPinToKeepLegacyContractHashShapeStable() throws Exception {
    prepareValidSource();
    var result = service.validate(10L, good(11L, 0L, List.of("order_day")));

    String serialized = new ObjectMapper().writeValueAsString(result.contract());

    org.assertj.core.api.Assertions.assertThat(serialized)
        .doesNotContain("metricPublicationPin")
        .contains("\"metricVersionId\":null");
  }

  @Test
  void rejectsLegacyMetricVersionIdWithoutCompletePublicationPin() {
    prepareValidSource();
    var base = good(11L, 0L, List.of("order_day"));
    var request = withMetricPin(base, 91L, null);

    var result = service.validate(10L, request);

    org.assertj.core.api.Assertions.assertThat(result.valid()).isFalse();
    org.assertj.core.api.Assertions.assertThat(result.blockers())
        .anyMatch(item -> item.contains("缺少完整发布身份"));
    verifyNoInteractions(metricPublicationEvidence);
  }

  @Test
  void requiresExactActivePublicationPinAndCanonicalizesLegacyVersionId() {
    prepareValidSource();
    var pin = new AdsApplicationDesignService.MetricPublicationPin(
        70L, 71L, 4, "b".repeat(64), 72L);
    when(metricPublicationEvidence.verifyForNewContract(any(Reference.class)))
        .thenReturn(new Evidence(Status.VERIFIED_ACTIVE));
    var request = withMetricPin(good(11L, 0L, List.of("order_day")), null, pin);

    var result = service.validate(10L, request);

    org.assertj.core.api.Assertions.assertThat(result.valid()).isTrue();
    org.assertj.core.api.Assertions.assertThat(result.contract().metricVersionId()).isEqualTo(71L);
    org.assertj.core.api.Assertions.assertThat(result.contract().metricPublicationPin()).isEqualTo(pin);
    verify(metricPublicationEvidence).verifyForNewContract(
        new Reference(70L, 71L, 4, "b".repeat(64), 72L));
  }

  @Test
  void refusesSupersededPublicationWhenFreezingNewContract() {
    prepareValidSource();
    var pin = new AdsApplicationDesignService.MetricPublicationPin(
        70L, 71L, 4, "b".repeat(64), 72L);
    when(metricPublicationEvidence.verifyForNewContract(any(Reference.class)))
        .thenReturn(new Evidence(Status.NOT_CURRENTLY_PUBLISHED));
    var request = withMetricPin(good(11L, 0L, List.of("order_day")), 71L, pin);

    var result = service.validate(10L, request);

    org.assertj.core.api.Assertions.assertThat(result.valid()).isFalse();
    org.assertj.core.api.Assertions.assertThat(result.blockers())
        .anyMatch(item -> item.contains("不再是当前有效发布"));
  }

  @Test
  void refusesMappingAcrossDifferentStandardFields() {
    prepareValidSource();
    when(published.requirePublishedStructure(20L)).thenReturn(structure(20L, 99L));

    var result = service.validate(10L, good(11L, 0L, List.of("order_day")));

    org.assertj.core.api.Assertions.assertThat(result.valid()).isFalse();
    org.assertj.core.api.Assertions.assertThat(result.blockers())
        .anyMatch(item -> item.contains("数据标准不一致"));
    org.assertj.core.api.Assertions.assertThat(result.contract()).isNull();
  }

  @Test
  void refusesSourceFieldWithoutStandardBinding() {
    prepareValidSource();
    when(published.requirePublishedStructure(20L)).thenReturn(structure(20L, null));

    var result = service.validate(10L, good(11L, 0L, List.of("order_day")));

    org.assertj.core.api.Assertions.assertThat(result.valid()).isFalse();
    org.assertj.core.api.Assertions.assertThat(result.blockers())
        .anyMatch(item -> item.contains("必须绑定数据标准"));
    org.assertj.core.api.Assertions.assertThat(result.contract()).isNull();
  }

  @Test
  void missingBusinessKeyIsAValidationBlockerNotANullPointer() {
    prepareValidSource();
    var result = service.validate(10L, good(11L, 0L,
        java.util.Arrays.asList((String) null)));
    org.assertj.core.api.Assertions.assertThat(result.valid()).isFalse();
    org.assertj.core.api.Assertions.assertThat(result.blockers()).anyMatch(
        item -> item.contains("业务键不能为空"));
  }

  @Test
  void refusesUnboundOrMismatchedStandardFieldEvenWhenPhysicalColumnExists() {
    prepareValidSource();
    var result = service.validate(10L, good(99L, 0L, List.of("order_day")));
    org.assertj.core.api.Assertions.assertThat(result.valid()).isFalse();
    org.assertj.core.api.Assertions.assertThat(result.blockers()).anyMatch(
        item -> item.contains("标准字段"));
  }

  @Test
  void staleClientRevisionCannotOverwriteAnotherSavedContract() {
    prepareValidSource();
    var stored = new AdsApplicationDesignService.Saved(10L, 5L,
        "c".repeat(64), "{}", null, "other-user", "2026-10-18");
    org.mockito.Mockito.doReturn(List.of(stored)).when(jdbc).query(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.<RowMapper<AdsApplicationDesignService.Saved>>any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    assertThatThrownBy(() -> service.save(10L, good(11L, 4L, List.of("order_day")), "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("修订已变化");
    verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void absentExpectedRevisionCannotWriteEvenForAValidContract() {
    prepareValidSource();
    org.mockito.Mockito.doReturn(List.of()).when(jdbc).query(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.<RowMapper<AdsApplicationDesignService.Saved>>any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    assertThatThrownBy(() -> service.save(10L, good(11L, null, List.of("order_day")), "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("修订已变化");
  }

  @Test
  void rejectsSaveWhenClientHasNotReviewedTheCurrentTargetStructureFingerprint() {
    prepareValidSource();
    var request = good(11L, 0L, List.of("order_day"), "stale-physical-fingerprint");

    assertThatThrownBy(() -> service.save(10L, request, "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("物理结构指纹");
    verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void firstSaveReadbackAndIdenticalRetryKeepOneImmutableRevision() throws Exception {
    prepareValidSource();
    var request = good(11L, 0L, List.of("order_day"));
    var validated = service.validate(10L, request);
    org.assertj.core.api.Assertions.assertThat(validated.valid()).isTrue();
    org.assertj.core.api.Assertions.assertThat(validated.contract().targetStructureFingerprint())
        .isEqualTo(io.yak.ops.business.modeling.structure.StructureFingerprint.of(structure(10L, 11L)));
    var json = new ObjectMapper().writeValueAsString(validated.contract());
    var digest = io.yak.ops.common.version.VersionDigests.sha256Hex(json);
    var stored = new AdsApplicationDesignService.Saved(
        10L, 1L, digest, json, validated.contract(), "alice", "2026-10-18");
    org.mockito.Mockito.doReturn(List.of(), List.of(stored)).when(jdbc).query(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.<RowMapper<AdsApplicationDesignService.Saved>>any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    org.assertj.core.api.Assertions.assertThat(service.save(10L, request, "alice").revision())
        .isEqualTo(1L);
    verify(models).findByIdForUpdate(20L);
    verify(jdbc, org.mockito.Mockito.times(2)).update(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());

    reset(jdbc);
    org.mockito.Mockito.doReturn(List.of(stored)).when(jdbc).query(
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.<RowMapper<AdsApplicationDesignService.Saved>>any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    org.assertj.core.api.Assertions.assertThat(service.save(10L, request, "alice").revision())
        .isEqualTo(1L);
    verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.<Object[]>any());
  }
}
