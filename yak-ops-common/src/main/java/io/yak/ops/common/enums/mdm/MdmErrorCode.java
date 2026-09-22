package io.yak.ops.common.enums.mdm;

import io.yak.framework.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 主数据管理模块业务错误码(44001+ 段;43001-43018 已被资源管理占用)。 */
@Getter
@RequiredArgsConstructor
public enum MdmErrorCode implements ErrorCode {

  ENTITY_NOT_FOUND(44001, "主数据实体不存在"),
  RECORD_NOT_FOUND(44002, "主数据记录不存在"),
  ATTRIBUTE_NOT_FOUND(44003, "主数据属性不存在"),
  SOURCE_NOT_FOUND(44004, "主数据来源不存在"),
  APPROVAL_FAILED(44005, "主数据变更审批失败"),
  DISTRIBUTE_FAILED(44006, "主数据分发失败"),
  DUPLICATE_CODE(44007, "主数据实体编码已存在"),
  INVALID_CODE(44008, "主数据实体编码格式不合法"),
  INVALID_NAME(44009, "主数据实体名称不合法"),
  INVALID_STATUS(44010, "主数据实体状态不合法"),
  DELETE_FAILED(44011, "删除主数据实体失败"),
  ENTITY_REFERENCED(44012, "主数据实体已被引用,无法执行该操作"),
  INVALID_STANDARD_REF(44013, "标准引用不合法或不可用"),
  ATTRIBUTE_REFERENCED(44014, "主数据属性已被引用,无法执行该操作"),
  DUPLICATE_ATTR_CODE(44015, "主数据属性编码已存在"),
  PK_ATTRIBUTE_EXISTS(44016, "同一实体仅允许一个 PK 属性"),
  INVALID_ATTR_ROLE(44017, "主数据属性角色不合法"),
  DUPLICATE_SOURCE(44018, "该来源已绑定到实体"),
  DATASOURCE_SCAN_FAILED(44019, "数据源扫描失败或不可用"),
  SOURCE_REFERENCED(44020, "来源已被采集配置引用,无法解绑"),
  PK_ATTRIBUTE_MISSING(44021, "实体未定义 PK 属性,无法生成加工任务"),
  CLEAN_RULE_NOT_FOUND(44022, "清洗规则不存在"),
  INVALID_CLEAN_RULE(44023, "清洗规则不合法"),
  MERGE_INVALID(44024, "合并请求不合法"),
  INVALID_FIELD_MAPPING(44025, "来源字段映射不合法"),
  LANDING_TASK_CREATE_FAILED(44026, "生成采集落地任务失败"),
  SYNC_STATUS_QUERY_FAILED(44027, "采集执行状态反查失败"),
  COLLECT_LINK_NOT_FOUND(44028, "该来源尚未生成采集落地任务"),
  SOURCE_NOT_LANDED(44029, "来源尚未生成采集落地任务，无法加工"),
  PROCESSING_TASK_REGISTER_FAILED(44030, "注册主数据加工任务失败"),
  QUALITY_MODULE_DISABLED(44031, "数据质量模块未启用，无法执行质量检查"),
  QUALITY_CHECK_FAILED(44032, "主数据质量检查执行失败"),
  LINEAGE_REGISTER_FAILED(44033, "主数据血缘登记失败"),
  CHANGE_TYPE_UNSUPPORTED(44034, "该变更类型暂不支持通过审批生效"),
  PK_CHANGE_FORBIDDEN(44035, "禁止通过变更申请修改主数据主键属性"),
  CHANGE_IN_FLIGHT(44036, "该记录已有在途变更申请，请先处理"),
  DATA_SERVICE_DISABLED(44037, "数据服务模块未启用，无法通过 API 通道分发"),
  NOTIFY_MODE_UNSUPPORTED(44038, "该通知方式尚未接入，一期仅支持站内信（EVENT）"),
  DEDUP_IGNORE_NOT_FOUND(44039, "忽略记录不存在或已被撤销");

  private final Integer code;
  private final String message;
}
