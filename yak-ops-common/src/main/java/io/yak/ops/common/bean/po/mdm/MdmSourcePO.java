package io.yak.ops.common.bean.po.mdm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 主数据来源持久化对象(数据源/实体为松散 ID,无物理外键)。 */
@Data
@TableName("yak_mdm_source")
public class MdmSourcePO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 主数据实体。 */
  private Long entityId;

  /** 数据源(松散 ID)。 */
  private Long datasourceId;

  /** 源库。 */
  private String sourceDatabase;

  /** 源模式。 */
  private String sourceSchema;

  /** 源表。 */
  private String sourceTable;

  /** 属性编码→源列名映射(JSON 原文),NULL=同名回退。 */
  private String fieldMapping;

  /** 角色:MAIN/AUXILIARY。 */
  private String sourceRole;

  /** 状态:ENABLED/DISABLED。 */
  private String status;

  /** 排序,小在前。 */
  private Integer sortOrder;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
