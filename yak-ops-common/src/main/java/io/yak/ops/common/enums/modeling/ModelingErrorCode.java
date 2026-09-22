package io.yak.ops.common.enums.modeling;

import io.yak.framework.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 数仓建模业务错误码。 */
@Getter
@RequiredArgsConstructor
public enum ModelingErrorCode implements ErrorCode {

  NOT_FOUND(41901, "模型不存在"),
  DUPLICATE_CODE(41902, "模型编码已存在"),
  INVALID_DIALECT(41903, "模型目标方言不合法"),
  CREATE_FAILED(41904, "创建模型失败"),
  DELETE_FAILED(41905, "删除模型失败"),
  INVALID_CODE(41906, "模型编码格式不合法"),
  UPDATE_FAILED(41907, "更新失败"),
  INVALID_DIRECTORY_TARGET(41908, "目标目录不合法"),
  DIRECTORY_NOT_EMPTY(41909, "目录不为空"),
  INVALID_DIRECTORY_NAME(41910, "目录名称不合法"),
  INVALID_TAG_NAME(41911, "标签名称不合法"),
  INVALID_TABLE_NAME(41912, "物理表名不合法"),
  INVALID_COLUMN(41913, "字段定义不合法"),
  INVALID_SEARCH(41914, "查询条件不合法"),
  /** 44 派生防重:同一业务过程 + 分层已有模型。 */
  DERIVE_CONFLICT(41915, "派生冲突"),
  /** 49 分层能力矩阵:该目标分层暂不支持派生(ODS 走导入;DWS/ADS 待聚合/应用绑定)。 */
  LAYER_DERIVE_UNSUPPORTED(41916, "该分层暂不支持派生");

  private final Integer code;
  private final String message;
}
