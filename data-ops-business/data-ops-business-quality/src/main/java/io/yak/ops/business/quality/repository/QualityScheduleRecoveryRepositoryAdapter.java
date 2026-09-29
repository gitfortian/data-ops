package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityMonitorDao;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityScheduleRecoveryRepositoryAdapter
    implements QualityScheduleRecoveryRepository {
  private final QualityMonitorDao monitorDao;

  @Override
  public List<ProjectMonitorRef> listScheduledMonitors() {
    return monitorDao.selectScheduledMonitorsForRecovery().stream()
        .map(value -> new ProjectMonitorRef(value.projectId(), value.monitorId()))
        .toList();
  }
}
