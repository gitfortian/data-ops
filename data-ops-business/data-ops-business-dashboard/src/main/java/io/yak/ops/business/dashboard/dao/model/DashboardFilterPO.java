package io.yak.ops.business.dashboard.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("yak_dashboard_filter")
public class DashboardFilterPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long dashboardVersionId;
  private String filterKey;
  private String name;
  private String operator;
  private String defaultValueJson;
  private Integer sortOrder;
}
