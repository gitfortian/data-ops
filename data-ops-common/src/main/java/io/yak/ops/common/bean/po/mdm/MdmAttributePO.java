package io.yak.ops.common.bean.po.mdm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 主数据属性持久化对象(引用标准为松散 ID,无物理外键)。 */
@Data
@TableName("yak_mdm_attribute")
public class MdmAttributePO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 主数据实体。 */
  private Long entityId;

  /** 属性编码,实体内唯一,创建后不可改。 */
  private String attrCode;

  /** 属性名称。 */
  private String attrName;

  /** 属性角色:PK/ATTR/RELATION。 */
  private String attrType;

  /** 数据类型(快照)。 */
  private String dataType;

  /** 类型标准引用(松散 ID)。 */
  private Long stdTypeId;

  /** 单位标准引用(松散 ID)。 */
  private Long stdUnitId;

  /** 码值标准引用(码集编码)。 */
  private String stdCodeSetCode;

  /** 安全标准引用(松散 ID)。 */
  private Long stdSecurityId;

  /** 是否必填。 */
  private Boolean isRequired;

  /** 业务描述。 */
  private String businessDesc;

  /** 排序,小在前。 */
  private Integer sortOrder;

  /** 状态:ENABLED/DISABLED。 */
  private String status;

  /** 创建人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;

  /** 更新时间。 */
  private LocalDateTime updateTime;
}
