package io.yak.ops.business.analysis.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Data;

@Data
@TableName("yak_analysis")
public class AnalysisPO {

  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String name;
  private String description;
  private Long datasetId;
  private String chartType;
  private String querySpecJson;
  private String visualConfigJson;
  private Timestamp createTime;
  private Timestamp updateTime;
}
