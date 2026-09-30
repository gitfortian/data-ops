package io.yak.ops.business.security.api;

import java.util.List;

/** 数据级访问裁决 SPI:在平台 RBAC 之上决定能否访问指定物理数据对象。 */
public interface SecurityAccessDecisionApi {

  /**
   * 给定服务端认证的访问者(用户 + 角色)对某数据对象的某动作,产出访问裁决。
   *
   * @param actor 访问者用户名
   * @param roles 访问者角色码集合(可空)
   * @param objectKey 对象自然键
   * @param action READ / WRITE / EXPORT
   */
  AccessDecision decide(String actor, List<String> roles, String objectKey, String action);

  /**
   * Records an access attempt or completed consumption after the caller knows the actual outcome.
   * Simulation callers must not call this method.
   */
  void recordAccess(
      String actor,
      String objectKey,
      String action,
      AccessDecision decision,
      boolean maskingApplied,
      String source);
}
