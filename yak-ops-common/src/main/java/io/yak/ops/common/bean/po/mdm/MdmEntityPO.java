package io.yak.ops.common.bean.po.mdm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 主数据实体持久化对象(属性/来源/记录的归属根)。 */
@Data
@TableName("yak_mdm_entity")
public class MdmEntityPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 实体编码,项目内唯一,创建后不可改。 */
  private String entityCode;

  /** 实体名称。 */
  private String entityName;

  /** 状态:DRAFT 草稿 / ACTIVE 生效 / DISABLED 停用。 */
  private String status;

  /** 负责人。 */
  private String owner;

  /** 描述。 */
  private String description;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
