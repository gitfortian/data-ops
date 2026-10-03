package io.yak.ops.business.semantic.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 业务语义业务过程持久化对象。 */
@Data
@TableName("yak_semantic_process")
public class SemanticProcessPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 业务过程编码,项目内唯一,创建后不可改。 */
  private String processCode;

  /** 业务过程名称。 */
  private String processName;

  /** 所属业务域。 */
  private Long domainId;

  /** 粒度(如 单据/明细/天)。 */
  private String grain;

  /** 类型:FACT/DIMENSION。 */
  private String bizType;

  /** 负责人。 */
  private String owner;

  /** 描述。 */
  private String description;

  /** 排序,小在前。 */
  private Integer sortOrder;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
