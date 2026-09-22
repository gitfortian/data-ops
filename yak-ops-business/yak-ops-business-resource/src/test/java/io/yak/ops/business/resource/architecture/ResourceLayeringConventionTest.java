package io.yak.ops.business.resource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.framework.common.PageData;
import io.yak.ops.business.resource.content.ResourceBinarySource;
import io.yak.ops.business.resource.content.ResourceContentManager;
import io.yak.ops.business.resource.controller.v1.ResourceExceptionHandler;
import io.yak.ops.business.resource.controller.v1.ResourcesController;
import io.yak.ops.business.resource.dao.ResourceDao;
import io.yak.ops.business.resource.domain.ResourceQuery;
import io.yak.ops.business.resource.namespace.ResourceNamespaceManager;
import io.yak.ops.business.resource.repository.ResourceRepository;
import io.yak.ops.business.resource.storage.ResourceStorageGateway;
import io.yak.ops.common.bean.po.resource.ResourcePO;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceLayeringConventionTest {

  private static final List<String> HTTP_OR_PERSISTENCE_PACKAGES =
      List.of(
          "io.yak.ops.common.bean.dto.",
          "io.yak.ops.common.bean.vo.",
          "io.yak.ops.common.bean.po.",
          "com.baomidou.mybatisplus");

  @Test
  void repositoryOnlyExposesDomainContracts() {
    for (Method method : ResourceRepository.class.getDeclaredMethods()) {
      assertThat(containsAny(method.getGenericReturnType(), HTTP_OR_PERSISTENCE_PACKAGES))
          .as("Repository return type: %s", method)
          .isFalse();
      for (Type parameter : method.getGenericParameterTypes()) {
        assertThat(containsAny(parameter, HTTP_OR_PERSISTENCE_PACKAGES))
            .as("Repository parameter: %s", method)
            .isFalse();
      }
    }
  }

  @Test
  void repositoryUsesSharedPageData() throws Exception {
    Method page = ResourceRepository.class.getMethod("page", ResourceQuery.class);
    assertThat(((ParameterizedType) page.getGenericReturnType()).getRawType()).isEqualTo(PageData.class);
  }

  @Test
  void daoDoesNotDependOnHttpContracts() {
    List<String> httpPackages =
        List.of("io.yak.ops.common.bean.dto.", "io.yak.ops.common.bean.vo.");
    for (Method method : ResourceDao.class.getDeclaredMethods()) {
      assertThat(containsAny(method.getGenericReturnType(), httpPackages)).isFalse();
      for (Type parameter : method.getGenericParameterTypes()) {
        assertThat(containsAny(parameter, httpPackages)).isFalse();
      }
    }
  }

  @Test
  void namespaceAndContentDoNotInjectDaoPoHttpOrStorageOperator() {
    for (Class<?> type : List.of(ResourceNamespaceManager.class, ResourceContentManager.class)) {
      for (Field field : type.getDeclaredFields()) {
        String fieldType = field.getGenericType().getTypeName();
        assertThat(fieldType)
            .as("field %s.%s", type.getSimpleName(), field.getName())
            .doesNotContain(".resource.dao.")
            .doesNotContain(".bean.po.resource.")
            .doesNotContain(".bean.dto.resource.")
            .doesNotContain(".bean.vo.resource.")
            .doesNotContain("io.yak.ops.spi.storage.StorageOperator");
      }
    }
  }

  @Test
  void controllerDoesNotEnterPersistenceOrStorageSpi() {
    for (Field field : ResourcesController.class.getDeclaredFields()) {
      String type = field.getGenericType().getTypeName();
      assertThat(type)
          .doesNotContain(".repository.")
          .doesNotContain(".dao.")
          .doesNotContain("StorageOperator")
          .doesNotContain("ResourceStorageGateway");
    }
  }

  @Test
  void multipartFileStopsAtHttpRequestMapperBoundary() {
    for (Method method : ResourceBinarySource.class.getDeclaredMethods()) {
      String signature = method.getGenericReturnType().getTypeName();
      for (Type parameter : method.getGenericParameterTypes()) {
        signature += "|" + parameter.getTypeName();
      }
      assertThat(signature).doesNotContain("org.springframework.web.multipart.MultipartFile");
    }
  }

  @Test
  void resourceStorageGatewayDoesNotExposeStorageOperator() {
    for (Method method : ResourceStorageGateway.class.getDeclaredMethods()) {
      String signature = method.getGenericReturnType().getTypeName();
      for (Type parameter : method.getGenericParameterTypes()) {
        signature += "|" + parameter.getTypeName();
      }
      assertThat(signature).doesNotContain("io.yak.ops.spi.storage.StorageOperator");
    }
  }

  @Test
  void httpExceptionAdviceLivesAtControllerBoundary() {
    assertThat(ResourceExceptionHandler.class.getPackageName())
        .startsWith("io.yak.ops.business.resource.controller.");
  }

  @Test
  void broadBusinessBucketsAreRemoved() {
    Path root = moduleRoot().resolve("src/main/java/io/yak/ops/business/resource");
    for (String forbidden :
        List.of("service", "common", "helper", "utils", "util", "base", "persistence")) {
      assertThat(Files.exists(root.resolve(forbidden))).isFalse();
    }
  }

  @Test
  void resourcePersistenceStillUsesTheSingleResourceTable() {
    TableName tableName = ResourcePO.class.getAnnotation(TableName.class);
    assertThat(tableName).isNotNull();
    assertThat(tableName.value()).isEqualTo("yak_ops_resource");
  }

  private Path moduleRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    if (Files.isDirectory(local.resolve("src/main/java/io/yak/ops/business/resource"))) {
      return local;
    }
    return Path.of("yak-ops-business", "yak-ops-business-resource").toAbsolutePath().normalize();
  }

  private boolean containsAny(Type type, List<String> packages) {
    if (type == null) {
      return false;
    }
    if (type instanceof Class<?> clazz) {
      return packages.stream().anyMatch(clazz.getName()::startsWith);
    }
    if (type instanceof ParameterizedType parameterized) {
      if (containsAny(parameterized.getRawType(), packages)) {
        return true;
      }
      for (Type argument : parameterized.getActualTypeArguments()) {
        if (containsAny(argument, packages)) {
          return true;
        }
      }
      return false;
    }
    String value = type.getTypeName();
    return packages.stream().anyMatch(value::contains);
  }
}
