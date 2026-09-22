package io.yak.ops.business.dashboard.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("yak_dashboard_interaction")
public class DashboardInteractionPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long dashboardVersionId;
  private String interactionKey;
  private String eventType;
  private String sourceWidgetKey;
  private String sourceFieldId;
  private String targetFilterKey;
  private Integer sortOrder;
}
