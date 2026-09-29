package io.yak.ops.common.bean.po.semantic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 标准引用/绕过事件流水持久化对象(ticket 42,推送式)。 */
@Data
@TableName("yak_semantic_standard_usage")
public class SemanticStandardUsagePO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 标准。 */
  private Long standardId;

  /** 事件:APPLY/BYPASS。 */
  private String usageType;

  /** 场景:EDITOR/REVERSE_IMPORT/RECOMMEND。 */
  private String scene;

  /** 来源模型(松散引用)。 */
  private String modelRef;

  /** 触发用户。 */
  private String operatedBy;

  /** 时间。 */
  private LocalDateTime createTime;
}
