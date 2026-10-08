package io.yak.ops.business.consumption.relationship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.core.project.ProjectScope;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class ConsumerImpactRecoveryControllerContractTest {

  @Test
  void recoveryIsExplicitPostCommandUnderExistingProjectAndPermissionBoundary()
      throws NoSuchMethodException {
    assertNotNull(ConsumerImpactController.class.getAnnotation(ProjectScope.class));
    assertNotNull(ConsumerImpactController.class.getAnnotation(RequiresPermission.class));
    Method endpoint = ConsumerImpactController.class.getMethod(
        "recoverDatasetVersionPage", String.class, String.class, Long.class, int.class);
    PostMapping mapping = endpoint.getAnnotation(PostMapping.class);
    assertNotNull(mapping);
    assertEquals("/dataset-version-recovery", mapping.value()[0]);

    ConsumerImpactService service = mock(ConsumerImpactService.class);
    ProductKey product = ProductKey.parse("DATASET:101");
    DatasetAuditRecoveryView expected = new DatasetAuditRecoveryView(
        product.value(), "9007199254740993", null, 50, 0, 0, 0, 0, null, false, true);
    when(service.recoverDatasetVersionPage(product, "9007199254740993", null, 50))
        .thenReturn(expected);

    var response = new ConsumerImpactController(service)
        .recoverDatasetVersionPage("DATASET:101", "9007199254740993", null, 50);

    assertNotNull(response);
    verify(service).recoverDatasetVersionPage(product, "9007199254740993", null, 50);
  }
}
