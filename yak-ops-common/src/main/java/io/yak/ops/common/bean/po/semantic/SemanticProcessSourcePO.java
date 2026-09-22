package io.yak.ops.common.bean.po.semantic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 业务过程-源表关联持久化对象(datasource 松散引用)。 */
@Data
@TableName("yak_semantic_process_source")
public class SemanticProcessSourcePO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 业务过程。 */
  private Long processId;

  /** 数据源 ID(datasource 模块松散引用)。 */
  private Long datasourceId;

  /** 源表名。 */
  private String sourceTable;

  /** 表角色:MAIN/DETAIL/DIM。 */
  private String tableRole;

  /** 关联条件。 */
  private String joinCondition;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
