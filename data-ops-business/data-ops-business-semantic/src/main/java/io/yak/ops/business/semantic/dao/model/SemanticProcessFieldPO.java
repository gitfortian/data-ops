package io.yak.ops.business.semantic.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 业务过程-标准字段引用持久化对象(过程内有序;is_required 驱动 44 派生默认勾选)。 */
@Data
@TableName("yak_semantic_process_field")
public class SemanticProcessFieldPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 业务过程。 */
  private Long processId;

  /** 标准字段。 */
  private Long fieldId;

  /** 是否过程必需字段(44 派生默认勾选)。 */
  private Boolean isRequired;

  /** 过程内字段顺序,0 起。 */
  private Integer sortOrder;

  /** 绑定人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;
}
