package io.yak.ops.business.metadata.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 标签溯源：label_type × state 双维度分离（机器/继承默认 SUGGESTED 等人工确认）。
 * asset_id 对所有实体类型同构引用——对一条列打标与对一张表走同一套代码。
 */
@Data
@TableName("yak_md_label")
public class MdLabelPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private Long assetId;
  /** 标签字典码，复用 asset 标签体系。 */
  private String labelCode;
  /** MANUAL|AUTOMATED|PROPAGATED|DERIVED。 */
  private String labelType;
  /** SUGGESTED|CONFIRMED。 */
  private String state;
  private String appliedBy;
  private LocalDateTime appliedAt;
  /** 为什么打这个标（机器/继承必填）。 */
  private String reason;
  /** PROPAGATED 时指向父标签行。 */
  private Long derivedFrom;
  /** 仅认证类用；绝不进 content_hash。 */
  private LocalDateTime expiresAt;
}
