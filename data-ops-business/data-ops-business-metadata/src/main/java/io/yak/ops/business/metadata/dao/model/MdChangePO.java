package io.yak.ops.business.metadata.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 实体级变更流水：append-only，代码层不提供 UPDATE/DELETE 路径。 */
@Data
@TableName("yak_md_change")
public class MdChangePO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private Long assetId;
  /** 冗余键：行被撤销后仍可追溯。 */
  private String assetKey;
  private String typeName;
  private String providerType;
  /** NEW|CHANGED|GONE|REVIVED|ATTR_CHANGED|STATUS_CHANGED|LABEL_CHANGED。 */
  private String changeType;
  /** 属性级变更时的字段名；实体级为 NULL。 */
  private String fieldName;
  private String beforeValue;
  private String afterValue;
  /** 差异明细，JSON 文本。 */
  private String detail;
  private LocalDateTime sourceUpdatedAt;
  /** 由哪一轮采集/对账产生；人工变更为 NULL。 */
  private Long collectRunId;
  /** 人写用户名，机器写 system。 */
  private String changedBy;
  private LocalDateTime changedAt;
}
