package io.yak.ops.common.enums.metadata;

import io.yak.framework.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 元数据中心模块错误码(49001~49099 段)。 */
@Getter
@RequiredArgsConstructor
public enum MetadataErrorCode implements ErrorCode {

  ENTITY_NOT_FOUND(49001, "实体不存在或已被撤销"),
  TYPE_NOT_FOUND(49002, "实体类型未注册"),
  TYPE_NAME_DUPLICATE(49003, "类型名已存在"),
  TYPE_DEPRECATED(49004, "类型已废弃,不可再登记新实体"),
  LINEAGE_ASSET_TYPE_INVALID(49005, "lineage_asset_type 不是 LineageAssetType 常量名"),
  ASSET_KEY_INVALID(49006, "asset_key 不合法:必填、长度≤512 且前缀须与类型登记的 key_prefix 一致"),
  PROJECT_CONTEXT_REQUIRED(49007, "缺少可信项目上下文,目录行必须归属到真实项目"),
  FIELD_NAME_DUPLICATE(49008, "该类型下字段名已存在"),
  FIELD_SLOT_REQUIRED(49009, "searchable=1 的字段必须分配存储槽位"),
  SLOT_CONFLICT(49010, "槽位已被其它类型占用,拒绝静默复用"),
  SLOT_NOT_FOUND(49011, "槽位不存在或已耗尽"),
  ATTRIBUTE_NOT_DEFINED(49012, "属性未在 field_def 登记,拒绝写入与筛选"),
  FIELD_TYPE_REF_INVALID(49013, "字段类型引用不合法"),
  ENTITY_REFERENCE_TYPE_INVALID(49014, "ENTITY_REFERENCE 引用的实体类型不存在"),
  REGISTER_COMMAND_INVALID(49015, "登记命令不合法"),
  COLLECT_JOB_NOT_FOUND(49016, "采集/对账任务不存在"),
  COLLECT_JOB_DISABLED(49017, "任务已停用,需先启用或改用手工触发"),
  COLLECT_JOB_SCOPE_INVALID(49018, "采集作用域不合法"),
  HARVEST_RUNNING(49019, "同一作用域的任务正在执行,请稍后再试"),
  DATASOURCE_UNAVAILABLE(49020, "数据源不可达或目录读取失败"),
  PROVIDER_UNAVAILABLE(49021, "来源域 provider 暂不可用"),
  DRY_RUN_REQUIRED(49022, "任务必须先 dry-run 通过才能启用"),
  SEARCH_SORT_NOT_ALLOWED(49023, "排序字段不在白名单内"),
  SEARCH_FILTER_INVALID(49024, "筛选条件不合法"),
  DETAIL_SECTION_UNAVAILABLE(49025, "详情分区暂不可用"),
  LABEL_DUPLICATE(49026, "该实体已打过同一标签"),
  LABEL_REASON_REQUIRED(49027, "机器/继承标签必须说明理由"),
  LABEL_NOT_FOUND(49028, "标签不存在"),
  CERTIFICATION_EXPIRY_REQUIRED(49029, "认证类标签必须给出过期时间"),
  TASK_NOT_FOUND(49030, "治理待办不存在"),
  TASK_ALREADY_RESOLVED(49031, "待办已办结,不可重复操作"),
  TASK_TRANSITION_INVALID(49032, "待办状态迁移不合法"),
  SELF_APPROVAL_FORBIDDEN(49033, "提单人不能自审"),
  RETRY_TASK_NOT_FOUND(49034, "登记重试任务不存在"),
  RETRY_TASK_NOT_REPLAYABLE(49035, "登记重试任务当前状态不可重放"),
  PERSISTENCE_CONFLICT(49036, "数据写入冲突,请刷新后重试"),
  INVALID_ARGUMENT(49037, "参数不合法");

  private final Integer code;
  private final String message;
}
