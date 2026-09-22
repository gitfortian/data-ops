package io.yak.ops.business.dataset.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Data;

/** yak_dataset_query_performance table row. */
@Data
@TableName("yak_dataset_query_performance")
public class DatasetQueryPerformancePO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String queryId;
  private Long datasetId;
  private String datasetName;
  private Long datasetVersionId;
  private Integer datasetVersionNo;
  private String sourceType;
  private String dataSourceId;
  private String sqlPreview;
  private String sqlHash;
  private String status;
  private String failureStage;
  private String errorType;
  private String errorMessage;
  private Long waitMillis;
  private Long prepareMillis;
  private Long executeMillis;
  private Long transferMillis;
  private Long totalMillis;
  private Integer returnedRows;
  private Boolean truncated;
  private Timestamp startedAt;
  private Timestamp finishedAt;
}
