package io.yak.ops.business.lifecycle.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.lifecycle.dao.mapper.LifecycleDispatchRecordMapper;
import io.yak.ops.business.lifecycle.dao.mapper.LifecycleSettingMapper;
import io.yak.ops.business.lifecycle.dao.mapper.LifecycleStorageSnapshotMapper;
import io.yak.ops.business.lifecycle.dao.model.LifecycleStorageSnapshotPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A missing snapshot measurement must consistently mean zero in numeric read models. */
class StorageStatsServiceTest {

  private LifecycleStorageSnapshotMapper snapshotMapper;
  private LifecycleDispatchRecordMapper dispatchMapper;
  private StorageStatsService service;

  @BeforeEach
  void setUp() {
    snapshotMapper = mock(LifecycleStorageSnapshotMapper.class);
    dispatchMapper = mock(LifecycleDispatchRecordMapper.class);
    LifecycleSettingMapper settingMapper = mock(LifecycleSettingMapper.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(7L);
    service = new StorageStatsService(
        currentProject, snapshotMapper, dispatchMapper, settingMapper);
  }

  @Test
  void statsTreatsMissingBytesAsZeroInTotalsLayersAndHotCold() {
    LocalDate date = LocalDate.of(2026, 10, 7);
    LifecycleStorageSnapshotPO measured = snapshot(date, "DWD", "orders", 4096L);
    LifecycleStorageSnapshotPO unknown = snapshot(date, null, "events", null);
    when(snapshotMapper.selectList(any()))
        .thenReturn(List.of(measured), List.of(measured, unknown));
    when(dispatchMapper.selectList(any())).thenReturn(List.of());

    StorageStatsService.StorageStats result = service.stats();

    assertThat(result.snapshotDate()).isEqualTo(date);
    assertThat(result.totalBytes()).isEqualTo(4096L);
    assertThat(result.byLayer()).containsExactly(
        new StorageStatsService.LayerVolume("DWD", 4096L, 0.0),
        new StorageStatsService.LayerVolume("UNKNOWN", 0L, 0.0));
    assertThat(result.hotCold())
        .isEqualTo(new StorageStatsService.HotColdSplit(0L, 4096L, true));
    assertThat(result.monthlyCost()).isNull();
  }

  @Test
  void trendAggregatesNullAndMeasuredRowsOnTheSameDate() {
    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);
    when(snapshotMapper.selectList(any())).thenReturn(List.of(
        snapshot(yesterday, "ODS", "a", null),
        snapshot(yesterday, "ODS", "b", 512L),
        snapshot(today, "ODS", "c", 2048L)));

    assertThat(service.trend(2)).containsExactly(
        new StorageStatsService.TrendPoint(yesterday, 512L),
        new StorageStatsService.TrendPoint(today, 2048L));
  }

  @Test
  void tableStorageStillExposesZeroForMissingMeasurement() {
    LocalDate date = LocalDate.of(2026, 10, 7);
    LifecycleStorageSnapshotPO row = snapshot(date, "DWD", "orders", null);
    row.setDatasourceId(17L);
    row.setDatabaseName("warehouse");
    when(snapshotMapper.selectList(any())).thenReturn(List.of(row));

    assertThat(service.latestTableStorage(17L, "warehouse", "orders"))
        .contains(new StorageStatsService.TableStorage(
            date, "DWD", 17L, "warehouse", "orders", 0L, 0.0));
  }

  private static LifecycleStorageSnapshotPO snapshot(
      LocalDate date, String layer, String table, Long bytes) {
    LifecycleStorageSnapshotPO po = new LifecycleStorageSnapshotPO();
    po.setProjectId(7L);
    po.setSnapshotDate(date);
    po.setLayerCode(layer);
    po.setTableName(table);
    po.setSizeBytes(bytes);
    return po;
  }
}
