package io.yak.ops.business.agent.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Agent 长期记忆 PO（两层：LEDGER/CURATED；truth owner 是 yak_agent_memory）。 */
@Data
@TableName("yak_agent_memory")
public class AgentMemoryPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 归属范围：USER/PROJECT/GLOBAL。 */
  private String scope;

  /** 作用域键：userId / projectId / -（global）。 */
  private String scopeKey;

  /** 类型：PREFERENCE/FACT/GLOSSARY/LESSON/TEMPLATE/EXAMPLE。 */
  private String memoryType;

  /** 层：LEDGER/CURATED。 */
  private String layer;

  /** 记忆正文（自包含；不含查询结果明细）。 */
  private String content;

  /** 检索关键词（空格分隔）。 */
  private String keywords;

  /** 置信度 0~1。 */
  private java.math.BigDecimal confidence;

  /** 命中次数。 */
  private Integer hitCount;

  /** 最后命中时刻。 */
  private LocalDateTime lastHitAt;

  /** 来源轮次。 */
  private String sourceTurnId;

  /** 来源会话。 */
  private String sourceSession;

  /** 状态：ACTIVE/MERGED/ARCHIVED/DISABLED。 */
  private String status;

  /** MERGED 时指向主条目。 */
  private Long mergedInto;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
