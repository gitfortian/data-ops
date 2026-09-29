package io.yak.ops.business.quality.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.controller.v1.converter.QualityMonitorConverter;
import io.yak.ops.business.quality.domain.QualityDomain.AlertEvent;
import io.yak.ops.business.quality.monitor.QualityMonitorReader.AlertOverview;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.common.bean.vo.quality.QualityMonitorVO;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** M2-4 工作台告警卡读侧：limit 钳制 + VO 直通，不做全量拉取。 */
class QualityMonitorAlertOverviewTest {

  private final QualityMonitorRepository repository = mock(QualityMonitorRepository.class);
  private final QualityMonitorReader reader = new QualityMonitorReader(repository);

  @Test
  void clampsRecentLimitIntoOneToFifty() {
    when(repository.countAlertEventsSince(any())).thenReturn(3L);
    when(repository.recentAlertEvents(anyInt())).thenReturn(List.of());

    reader.alertOverview(0);
    ArgumentCaptor<Integer> lower = ArgumentCaptor.forClass(Integer.class);
    verify(repository).recentAlertEvents(lower.capture());
    assertThat(lower.getValue()).isEqualTo(1);

    reader.alertOverview(100);
    ArgumentCaptor<Integer> upper = ArgumentCaptor.forClass(Integer.class);
    verify(repository, org.mockito.Mockito.times(2)).recentAlertEvents(upper.capture());
    assertThat(upper.getAllValues()).containsExactly(1, 50);

    reader.alertOverview(10);
    verify(repository).recentAlertEvents(eq(10));
  }

  @Test
  void countWindowIsLast24Hours() {
    LocalDateTime before = LocalDateTime.now();
    reader.alertOverview(5);
    ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(repository).countAlertEventsSince(since.capture());
    assertThat(since.getValue())
        .isAfterOrEqualTo(before.minusHours(24).minusMinutes(1))
        .isBeforeOrEqualTo(before.minusHours(24).plusMinutes(1));
  }

  @Test
  void converterMapsDomainVerbatim() {
    AlertEvent event = new AlertEvent(1L, 7L, "订单监控", "EXE-1",
        CheckResult.NOT_PASSED, AlertLevel.CRITICAL, NotifyChannel.WEBHOOK,
        "FAILED", "规则未通过", LocalDateTime.now());
    QualityMonitorVO.AlertOverview vo =
        new QualityMonitorConverter().alertOverview(new AlertOverview(1L, List.of(event)));
    assertThat(vo.last24hCount()).isEqualTo(1L);
    assertThat(vo.recent()).singleElement().satisfies(mapped -> {
      assertThat(mapped.monitorName()).isEqualTo("订单监控");
      assertThat(mapped.checkResult()).isEqualTo(CheckResult.NOT_PASSED);
      assertThat(mapped.deliveryStatus()).isEqualTo("FAILED");
      assertThat(mapped.alertMessage()).isEqualTo("规则未通过");
    });
  }
}
