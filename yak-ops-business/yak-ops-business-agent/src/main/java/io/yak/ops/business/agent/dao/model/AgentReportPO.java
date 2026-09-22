package io.yak.ops.business.agent.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** AI 分析报告 PO。 */
@Data
@TableName("yak_agent_report")
public class AgentReportPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 来源会话ID。 */
  private String sessionId;

  /** 归属用户ID。 */
  private Long userId;

  /** 报告标题。 */
  private String title;

  /** 报告正文（Markdown + ECharts 配置块）。 */
  private String content;

  /** 逻辑删除：0-未删除 1-已删除。 */
  private Integer isDeleted;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
