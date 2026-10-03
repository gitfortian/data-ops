package io.yak.ops.business.sync.offline.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.ops.business.sync.offline.dao.model.OfflineJobDefinitionPO;
import io.yak.ops.business.sync.offline.dao.model.OfflineJobExecutionPO;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class OfflineSyncContractTest {

  @Test
  void successfulResponseUsesFrameworkCode200() {
    assertThat(Result.success().getCode()).isEqualTo(200);
  }

  @Test
  void controllersUseFrameworkResultContracts() throws Exception {
    Method detail = OfflineJobDefinitionController.class.getMethod("get", Long.class);
    Method page = OfflineJobDefinitionController.class.getMethod(
        "page",
        io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionQueryDTO.class);

    assertThat(detail.getReturnType()).isEqualTo(Result.class);
    assertThat(page.getGenericReturnType().getTypeName())
        .contains(Result.class.getName(), PagingData.class.getName());
  }

  @Test
  void persistenceObjectsBelongToOwningModulePackage() {
    assertThat(OfflineJobDefinitionPO.class.getPackageName())
        .isEqualTo("io.yak.ops.business.sync.offline.dao.model");
    assertThat(OfflineJobExecutionPO.class.getPackageName())
        .isEqualTo("io.yak.ops.business.sync.offline.dao.model");
  }
}
