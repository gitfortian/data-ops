package io.yak.ops.business.dashboard.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Data;

@Data
@TableName("yak_dashboard")
public class DashboardPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String name;
  private String description;
  private Long currentVersionId;
  private Integer currentVersionNo;
  private Long publishedVersionId;
  private Integer publishedVersionNo;
  private Timestamp publishedTime;
  private Timestamp createTime;
  private Timestamp updateTime;
}
