package io.yak.ops.business.modeling.derive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.repository.ModelVersionRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureFingerprint;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DwsGrainDesignServiceTest {
  private final ModelRepository models = mock(ModelRepository.class);
  private final ModelVersionRepository versions = mock(ModelVersionRepository.class);
  private final ModelStructureService structures = mock(ModelStructureService.class);
  private final CurrentProject project = () -> Optional.of(new ProjectContext(42L, null));
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ObjectMapper json = new ObjectMapper();
  private final DwsGrainDesignService service =
      new DwsGrainDesignService(models, versions, structures, project, jdbc, json);

  private final Model dws = new Model(
      10L, "dws_order_daily", "订单日汇总", ModelDialect.MYSQL, null,
      ModelStatus.DRAFT, "alice", null, null, "DWS", 50L, null, null, null, null);
  private final Model dwd = new Model(
      20L, "dwd_order_detail", "订单明细", ModelDialect.MYSQL, null,
      ModelStatus.PUBLISHED, "alice", null, null, "DWD", 50L, null, null, null, null)
      .withPublishState(ModelStatus.PUBLISHED, 30L, 2);

  private StructureView.ColumnView column(String name, String type, String role,
      String aggregation, Long stdFieldId) {
    return new StructureView.ColumnView(null, name, type, null, null, false,
        null, null, null, 0, null, null, null, null, null, null,
        stdFieldId, role, aggregation, null);
  }
  private StructureView target() {
    return new StructureView(10L, "dws_order_daily", "订单日汇总", "MYSQL", "DRAFT",
        null, "dws_order_daily", null,
        List.of(column("order_day", "DATE", "DIMENSION", null, 8L),
            column("order_city", "VARCHAR", "DIMENSION", null, 9L),
            column("order_count", "BIGINT", "MEASURE", "COUNT_DISTINCT", 10L)),
        List.of("order_day", "order_city"), List.of(), null, Map.of());
  }
  private DwsGrainDesignService.Request request() {
    return new DwsGrainDesignService.Request("1d", "Asia/Shanghai", "event_time",
        "order_day", List.of("order_day", "order_city"),
        List.of(new DwsGrainDesignService.Measure("order_count", "COUNT_DISTINCT")),
        List.of(20L), StructureFingerprint.of(target()));
  }

  @BeforeEach
  void prepare() throws Exception {
    when(models.findByIdForUpdate(10L)).thenReturn(Optional.of(dws));
    when(models.findById(10L)).thenReturn(Optional.of(dws));
    when(models.listByProcessLayer(50L, "DWD")).thenReturn(List.of(dwd));
    when(structures.getForUpdate(10L)).thenReturn(target());
    var upstream = new StructureView(20L, "dwd_order_detail", "订单明细",
        "MYSQL", "PUBLISHED", null, "dwd_order_detail", null,
        List.of(column("event_time", "DATETIME", null, null, null)),
        List.of(), List.of(), null, Map.of());
    when(versions.findById(30L, 20L)).thenReturn(Optional.of(
        new ModelVersion(30L, 20L, 2, json.writeValueAsString(upstream),
            "{}", 1, "a".repeat(64), "alice", LocalDateTime.now())));
    when(jdbc.query(anyString(), any(RowMapper.class), anyLong(), anyLong()))
        .thenReturn(List.of());
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
  }

  @Test
  void savesPinnedUpstreamVersionAndGrainWithoutCreatingAnySqlExecution() {
    var saved = service.save(10L, request(), "alice");
    assertThat(saved.design().statPeriod()).isEqualTo("1d");
    assertThat(saved.design().timeZone()).isEqualTo("Asia/Shanghai");
    assertThat(saved.design().upstreamVersions()).containsExactly(
        new DwsGrainDesignService.UpstreamVersion(20L, 30L, 2, "a".repeat(64)));
    assertThat(saved.designSha256()).matches("[a-f0-9]{64}");
    verify(jdbc).update(startsWith("INSERT INTO yak_modeling_dws_grain_design"), any(Object[].class));
  }

  @Test
  void refusesStaleEditorStructureWithoutDatabaseWrite() {
    var stale = new DwsGrainDesignService.Request("1d", "Asia/Shanghai",
        "event_time", "order_day", List.of("order_day"),
        List.of(new DwsGrainDesignService.Measure("order_count", "COUNT_DISTINCT")),
        List.of(20L), "f".repeat(64));
    assertThatThrownBy(() -> service.save(10L, stale, "alice"))
        .hasMessageContaining("表结构已发生变化");
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void requiresTimeBucketToBeDimensionToPreventCrossDayAggregation() {
    var bad = new DwsGrainDesignService.Request("1d", "Asia/Shanghai",
        "event_time", "order_day", List.of("order_city"),
        request().measures(), List.of(20L), request().expectedStructureFingerprint());
    assertThatThrownBy(() -> service.save(10L, bad, "alice"))
        .hasMessageContaining("周期桶");
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void rejectsIncompatibleMeasureAndInvalidIanaZone() {
    var badMeasure = new DwsGrainDesignService.Request("1d", "Asia/Shanghai",
        "event_time", "order_day", request().dimensions(),
        List.of(new DwsGrainDesignService.Measure("order_count", "SUM")),
        List.of(20L), request().expectedStructureFingerprint());
    assertThatThrownBy(() -> service.save(10L, badMeasure, "alice"))
        .hasMessageContaining("聚合函数不一致");
    var invalidZone = new DwsGrainDesignService.Request("1d", "Moon/Future",
        "event_time", "order_day", request().dimensions(),
        request().measures(), List.of(20L), request().expectedStructureFingerprint());
    assertThatThrownBy(() -> service.save(10L, invalidZone, "alice"))
        .hasMessageContaining("无效的统计时区");
  }

  @Test
  void rejectsForeignUnpublishedOrRepeatedUpstreamModels() {
    var other = new DwsGrainDesignService.Request("1d", "Asia/Shanghai",
        "event_time", "order_day", request().dimensions(),
        request().measures(), List.of(99L), request().expectedStructureFingerprint());
    assertThatThrownBy(() -> service.save(10L, other, "alice"))
        .hasMessageContaining("上游模型重复、跨过程");
    var duplicated = new DwsGrainDesignService.Request("1d", "Asia/Shanghai",
        "event_time", "order_day", request().dimensions(),
        request().measures(), List.of(20L, 20L), request().expectedStructureFingerprint());
    assertThatThrownBy(() -> service.save(10L, duplicated, "alice"))
        .hasMessageContaining("上游模型重复、跨过程");
    when(versions.findById(30L, 20L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.save(10L, request(), "alice"))
        .hasMessageContaining("发布版本缺失");
  }

  @Test
  void rejectsBusinessTimeNotInExactPublishedUpstream() {
    var altered = new DwsGrainDesignService.Request("1d", "Asia/Shanghai",
        "missing_time", "order_day", request().dimensions(),
        request().measures(), List.of(20L), request().expectedStructureFingerprint());
    assertThatThrownBy(() -> service.save(10L, altered, "alice"))
        .hasMessageContaining("有效业务时间字段");
  }
}
