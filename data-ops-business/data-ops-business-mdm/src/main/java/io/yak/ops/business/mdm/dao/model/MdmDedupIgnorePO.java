package io.yak.ops.business.mdm.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 主数据去重「忽略组」持久化对象:同一去重规则下被管理员标记为「已知非重复」的组键。
 * 去重发现时这些键在 SQL 侧被排除,不再反复出现在待处理列表里。
 */
@Data
@TableName("yak_mdm_dedup_ignore")
public class MdmDedupIgnorePO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 主数据实体。 */
  private Long entityId;

  /** 忽略所属的去重规则(键语义随规则变,不跨规则共享)。 */
  private Long ruleId;

  /** 重复组匹配键(与去重发现 GROUP BY 结果一致)。 */
  private String matchKey;

  /** 忽略时的匹配依据快照(仅用于展示/审计)。 */
  private String matchBasis;

  /** 忽略原因。 */
  private String reason;

  /** 忽略人。 */
  private String createdBy;

  /** 忽略时间。 */
  private LocalDateTime createTime;
}
