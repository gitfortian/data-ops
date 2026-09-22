package io.yak.ops.common.bean.po.metadata;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 采集/对账运行历史：四类计数 + SUSPECT/FAILED + dry-run + 游标水位。 */
@Data
@TableName("yak_md_collect_run")
public class MdCollectRunPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private Long jobId;
  private String providerType;
  /** SCHEDULE|MANUAL|DRY_RUN。 */
  private String triggerType;
  private Boolean dryRun;
  /** RUNNING|SUCCESS|FAILED|SUSPECT。 */
  private String status;
  private Integer cntTotal;
  private Integer cntNew;
  private Integer cntChanged;
  private Integer cntUnchanged;
  private Integer cntGone;
  /** 单表列读取失败/为空记 PARTIAL，不静默当空表。 */
  private Integer cntPartialFailed;
  private String cursorWatermark;
  /** 本轮作用域快照，JSON 文本。 */
  private String scopeSnapshot;
  private String errorMessage;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
  private Long durationMs;
  private String createdBy;
}
