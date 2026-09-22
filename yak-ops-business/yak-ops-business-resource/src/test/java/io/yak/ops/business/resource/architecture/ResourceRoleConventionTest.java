package io.yak.ops.business.resource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.resource.content.ResourceChecksum;
import io.yak.ops.business.resource.content.ResourceContentManager;
import io.yak.ops.business.resource.content.ResourceContentPolicy;
import io.yak.ops.business.resource.content.ResourceContentReader;
import io.yak.ops.business.resource.controller.v1.ResourceExceptionHandler;
import io.yak.ops.business.resource.controller.v1.ResourcesController;
import io.yak.ops.business.resource.controller.v1.converter.ResourceRequestConverter;
import io.yak.ops.business.resource.controller.v1.converter.ResourceViewConverter;
import io.yak.ops.business.resource.namespace.ResourceNamePolicy;
import io.yak.ops.business.resource.namespace.ResourceNamespaceManager;
import io.yak.ops.business.resource.namespace.ResourceNamespaceReader;
import io.yak.ops.business.resource.namespace.ResourceParentResolver;
import io.yak.ops.business.resource.namespace.ResourceTreeReader;
import io.yak.ops.business.resource.repository.ResourceRepositoryAdapter;
import io.yak.ops.business.resource.resolution.ResourceDownloadProviderAdapter;
import io.yak.ops.business.resource.resolution.ResourceResolverAdapter;
import io.yak.ops.business.resource.storage.ResourceStorageLifecycle;
import io.yak.ops.business.resource.storage.ResourceStorageReader;
import io.yak.ops.business.resource.storage.ResourceStorageRegistry;
import io.yak.ops.business.resource.storage.StorageOperatorGatewayAdapter;
import io.yak.ops.business.resource.sync.ResourceChangeDispatcher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class ResourceRoleConventionTest {

  @Test
  void businessRolesRemainExplicitComponentsInsteadOfGenericServices() {
    for (Class<?> role : List.of(
        ResourceNamespaceManager.class,
        ResourceNamespaceReader.class,
        ResourceTreeReader.class,
        ResourceParentResolver.class,
        ResourceNamePolicy.class,
        ResourceContentManager.class,
        ResourceContentReader.class,
        ResourceContentPolicy.class,
        ResourceChecksum.class,
        ResourceStorageLifecycle.class,
        ResourceStorageReader.class,
        ResourceStorageRegistry.class,
        StorageOperatorGatewayAdapter.class,
        ResourceDownloadProviderAdapter.class,
        ResourceResolverAdapter.class,
        ResourceChangeDispatcher.class,
        ResourceRequestConverter.class,
        ResourceViewConverter.class)) {
      assertThat(role.getAnnotation(Component.class))
          .as("%s must remain an explicit Resource component role", role.getSimpleName())
          .isNotNull();
      assertThat(role.getAnnotation(Service.class))
          .as("%s must not collapse back into a generic @Service layer", role.getSimpleName())
          .isNull();
    }
  }

  @Test
  void persistenceAdapterRemainsRepositoryRole() {
    assertThat(ResourceRepositoryAdapter.class.getAnnotation(Repository.class)).isNotNull();
    assertThat(ResourceRepositoryAdapter.class.getAnnotation(Service.class)).isNull();
  }

  @Test
  void httpRolesRemainAtControllerBoundary() {
    assertThat(ResourcesController.class.getAnnotation(RestController.class)).isNotNull();
    assertThat(ResourceExceptionHandler.class.getAnnotation(RestControllerAdvice.class)).isNotNull();
    assertThat(ResourceExceptionHandler.class.getPackageName())
        .startsWith("io.yak.ops.business.resource.controller.");
  }
}
