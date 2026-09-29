package io.yak.ops.business.agent.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Agent 数据集查询证据留痕 PO。 */
@Data
@TableName("yak_agent_query_log")
public class AgentQueryLogPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 会话ID。 */
  private String sessionId;

  /** 目标数据集ID。 */
  private Long datasetId;

  /** Dataset 查询运行时返回的 queryId。 */
  private String queryId;

  /** 结构化查询参数投影 JSON。 */
  private String requestJson;

  /** 状态：SUCCESS / FAILED / REJECTED。 */
  private String status;

  /** 失败原因摘要。 */
  private String errorMessage;

  /** 返回行数。 */
  private Integer returnedRows;

  /** 是否被截断。 */
  private Boolean truncated;

  /** 耗时毫秒。 */
  private Long elapsedMillis;

  /** 创建时间。 */
  private LocalDateTime createTime;
}
