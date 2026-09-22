package io.yak.ops.business.mdm.notification;

import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.service.UserService;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 订阅方编码 → 平台用户 ID 的解析口(R6)。
 *
 * <p>一期站内信只能投递给平台用户,而 {@code yak_mdm_subscription.subscriber_code} 记的是订阅方
 * 系统/人员编码,因此"订阅是否真能收到信"必须显式判定:解析不到就如实标不可达,
 * 而不是悄悄改投项目所有者(那会让订阅表变成摆设)。</p>
 */
@Slf4j
@Component
public class MdmUserDirectory {

  private final ObjectProvider<UserService> userServices;

  public MdmUserDirectory(ObjectProvider<UserService> userServices) {
    this.userServices = userServices;
  }

  /** 按平台用户名解析用户 ID;安全服务缺席、用户不存在或异常均返回空。 */
  public Optional<Long> userIdOf(String userCode) {
    if (!StringUtils.hasText(userCode)) {
      return Optional.empty();
    }
    UserService userService = userServices.getIfAvailable();
    if (userService == null) {
      return Optional.empty();
    }
    try {
      UserBriefVO user = userService.getUserBriefByUsername(userCode.trim());
      if (user == null || user.getId() == null || user.getId() <= 0L) {
        return Optional.empty();
      }
      return Optional.of(user.getId());
    } catch (RuntimeException failure) {
      log.debug("订阅方编码解析平台用户失败: {}", userCode, failure);
      return Optional.empty();
    }
  }
}
