package io.yak.ops.boot.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.framework.security.common.constant.SecurityPermissionCode;
import io.yak.framework.security.web.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;

class AuditQueryControllerContractTest {

  @Test
  void reusesExistingOperationLogRbacAndDatabaseEnablementContract() {
    RequiresPermission permission = AuditQueryController.class.getAnnotation(RequiresPermission.class);
    RequestMapping mapping = AuditQueryController.class.getAnnotation(RequestMapping.class);
    ConditionalOnProperty conditional =
        AuditQueryController.class.getAnnotation(ConditionalOnProperty.class);

    assertThat(permission).isNotNull();
    assertThat(permission.value()).isEqualTo(SecurityPermissionCode.OperationLog.READ);
    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/api/v1/audit");
    assertThat(conditional).isNotNull();
    assertThat(conditional.prefix()).isEqualTo("yak.database");
    assertThat(conditional.name()).containsExactly("enabled");
    assertThat(conditional.havingValue()).isEqualTo("true");
    assertThat(conditional.matchIfMissing()).isTrue();
  }
}
