package io.yak.ops.business.sync.offline.repository;

import static io.yak.ops.business.sync.offline.OfflineProjectTestContext.currentProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineScheduleRepositoryAdapterTest {

  @Mock private OfflineJobDefinitionDao dao;

  @Test
  void saveScheduleUsesScopedUpdateAndPreservesLastFireHistory() {
    OfflineScheduleRepositoryAdapter repository =
        new OfflineScheduleRepositoryAdapter(dao, currentProject());
    LocalDateTime previousLastFire = LocalDateTime.of(2026, 8, 8, 9, 0);
    LocalDateTime nextFire = LocalDateTime.of(2026, 8, 10, 9, 0);

    OfflineJobDefinitionPO stored = new OfflineJobDefinitionPO();
    stored.setId(42L);
    stored.setCronExpression("0 0 9 * * ?");
    stored.setScheduleEnabled(true);
    stored.setRetryMaxAttempts(3);
    stored.setRetryBackoffSeconds(60);
    stored.setScheduleLastFireTime(previousLastFire);
    stored.setScheduleNextFireTime(nextFire);
    stored.setScheduleJson("{\"enabled\":true}");

    when(dao.updateSchedule(
            eq(42L),
            eq("{\"enabled\":true}"),
            eq(true),
            eq("0 0 9 * * ?"),
            eq(3),
            eq(60),
            eq(nextFire),
            any(LocalDateTime.class)))
        .thenReturn(true);
    when(dao.selectById(42L)).thenReturn(stored);

    OfflineSchedule result = repository.saveSchedule(
        new OfflineSchedule(
            42L,
            "0 0 9 * * ?",
            true,
            3,
            60,
            nextFire,
            null,
            "{\"enabled\":true}"));

    assertThat(result.lastFireTime()).isEqualTo(previousLastFire);
    verify(dao, never()).updateById(any(OfflineJobDefinitionPO.class));
  }
}
