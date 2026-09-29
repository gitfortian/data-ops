package io.yak.ops.common.bean.po.mdm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 主数据合并日志持久化对象(追加式,记录每次合并的可追溯信息)。 */
@Data
@TableName("yak_mdm_merge_log")
public class MdmMergeLogPO {

  /** 主键。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属 Project Space。 */
  private Long projectId;

  /** 主数据实体。 */
  private Long entityId;

  /** 触发合并的去重规则(可空)。 */
  private Long ruleId;

  /** 保留的主记录。 */
  private Long masterRecordId;

  /** 被合并记录 ID 列表(JSON 数组)。 */
  private String mergedRecordIds;

  /** 合并结果摘要。 */
  private String result;

  /** 操作人。 */
  private String createdBy;

  /** 创建时间。 */
  private LocalDateTime createTime;
}
