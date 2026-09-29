package io.yak.ops.business.dashboard.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("yak_dashboard_widget")
public class DashboardWidgetPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long dashboardVersionId;
  private String widgetKey;
  private Long analysisId;
  private String title;
  private String inlineAnalysisJson;
  private Integer gridX;
  private Integer gridY;
  private Integer gridW;
  private Integer gridH;
  private Integer minW;
  private Integer minH;
  private Integer sortOrder;
}
