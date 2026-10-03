package io.yak.ops.business.metadata.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 采集/对账任务：物理采集与投影对账共用，区别只在 providerType。 */
@Data
@TableName("yak_md_collect_job")
public class MdCollectJobPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  /** 任务编码，自动生成可改，项目内唯一。 */
  private String jobCode;
  private String jobName;
  /** HARVESTED=物理采集 / REGISTERED=投影对账。 */
  private String providerType;
  /** REGISTERED 必填：对账的实体类型；HARVESTED 为 NULL。 */
  private String typeName;
  private Long dataSourceId;
  private String databaseName;
  private String schemaName;
  /** 表名匹配式（% 通配），NULL=全部。 */
  private String tablePattern;
  private Boolean collectColumns;
  private String cronExpression;
  /** 新建默认关。 */
  private Boolean enabled;
  /** 未 dry-run 通过不可启用。 */
  private Boolean dryRunPassed;
  private Integer collapseThresholdPct;
  private Integer missingRounds;
  private Long lastRunId;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Boolean deleted;
}
