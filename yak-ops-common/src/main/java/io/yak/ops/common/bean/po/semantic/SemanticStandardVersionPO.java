package io.yak.ops.common.bean.po.semantic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 业务语义数据标准版本快照持久化对象(修改前完整状态)。 */
@Data
@TableName("yak_semantic_standard_version")
public class SemanticStandardVersionPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 所属标准。 */
  private Long standardId;

  /** 快照对应的版本号(修改前的版本)。 */
  private Integer version;

  /** 修改前完整状态 JSON。 */
  private String payloadJson;

  /** 执行修改的用户。 */
  private String operatedBy;

  /** 快照时间。 */
  private LocalDateTime createTime;
}
