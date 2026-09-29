package io.yak.ops.business.approval.api;

/** 平台内置审批流程编码;流程在审批中心配置,业务代码只引用常量(D8)。 */
public final class ApprovalFlowCodes {

  public static final String MODEL_PUBLISH = "MODEL_PUBLISH";
  public static final String STANDARD_PUBLISH = "STANDARD_PUBLISH";
  public static final String ACCESS_GRANT = "ACCESS_GRANT";
  public static final String MDM_CHANGE = "MDM_CHANGE";
  public static final String ASSET_PUBLISH = "ASSET_PUBLISH";

  private ApprovalFlowCodes() {}
}
