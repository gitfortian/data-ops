package io.yak.ops.business.sync.offline.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import java.time.LocalDateTime;
import java.util.List;

/** 离线同步任务定义数据访问接口，只暴露持久化模型和 DAO 查询条件。 */
public interface OfflineJobDefinitionDao {

  OfflineJobDefinitionPO selectById(Long id);

  boolean insert(OfflineJobDefinitionPO definitionPO);

  boolean updateById(OfflineJobDefinitionPO definitionPO);

  boolean deleteById(Long id);

  boolean existsByName(String jobName, Long excludeId);

  IPage<OfflineJobDefinitionPO> selectPage(PageQuery query);

  List<OfflineJobDefinitionPO> selectWithCron();

  /** Explicit cross-Project startup dispatcher scan. Business IO must restore each row's Project. */
  List<OfflineJobDefinitionPO> selectWithCronForReconciliation();

  Long lockById(Long id);

  boolean updateSchedule(
      Long id,
      String scheduleJson,
      boolean enabled,
      String cronExpression,
      int retryMaxAttempts,
      int retryBackoffSeconds,
      LocalDateTime nextFireTime,
      LocalDateTime updateTime);

  void updateScheduleRuntime(
      Long id,
      LocalDateTime lastFireTime,
      LocalDateTime nextFireTime,
      LocalDateTime updateTime);

  void clearSchedule(Long id, LocalDateTime updateTime);

  record PageQuery(
      int current,
      int pageSize,
      Long id,
      String jobName,
      String status,
      String sourceType,
      String sinkType,
      String sourceTable,
      String sinkTable,
      LocalDateTime createTimeStart,
      LocalDateTime createTimeEnd) {}
}
