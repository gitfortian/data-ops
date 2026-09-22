package io.yak.ops.business.dashboard.dao.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("yak_dashboard_filter_binding")
public class DashboardFilterBindingPO {
  private Long dashboardVersionId;
  private String filterKey;
  private String widgetKey;
  private String fieldId;
  private Integer sortOrder;
}
