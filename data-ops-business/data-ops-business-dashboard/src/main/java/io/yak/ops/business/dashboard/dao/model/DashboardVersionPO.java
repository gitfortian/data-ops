package io.yak.ops.business.dashboard.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Data;

@Data
@TableName("yak_dashboard_version")
public class DashboardVersionPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long dashboardId;
  private Integer versionNo;
  private String nameSnapshot;
  private String descriptionSnapshot;
  private Long activeDatasetId;
  private String themeJson;
  private Timestamp createTime;
}
