package io.yak.ops.common.bean.po.lifecycle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** TTL 下发流水(D7:重试的业务事实源)。 */
@Data
@TableName("yak_lc_dispatch_record")
public class LifecycleDispatchRecordPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private Long modelId;
  private Long policyId;
  /** 下发时策略 update_time 快照,漂移判定基准(D5)。 */
  private LocalDateTime policyUpdatedAt;
  /** MANUAL/BATCH/RETRY。 */
  private String triggerType;
  private Long datasourceId;
  private String databaseName;
  private String tableName;
  /** DORIS/PAIMON(解析结果)。 */
  private String storageType;
  private String statement;
  /** SUCCESS/FAILED/RETRYING/EXHAUSTED。 */
  private String status;
  private Integer attempts;
  private LocalDateTime nextRetryTime;
  private String errorMessage;
  /** 下发时分区归类快照(预览"清理推算"用)。 */
  private Integer partitionHot;
  private Integer partitionCold;
  private Integer partitionDeleted;
  private String operator;
  private LocalDateTime createTime;
  private LocalDateTime finishTime;
}
