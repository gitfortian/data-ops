package io.yak.ops.business.datasource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.framework.common.PageData;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.connection.DataSourceConnectionResolver;
import io.yak.ops.business.datasource.connection.DataSourceConnectionTester;
import io.yak.ops.business.datasource.controller.v1.DataSourceCatalogController;
import io.yak.ops.business.datasource.controller.v1.DataSourceController;
import io.yak.ops.business.datasource.controller.v1.DataSourcePluginConfigController;
import io.yak.ops.business.datasource.controller.v1.SqlExecutionAuditController;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.datasource.dao.mapper.DataSourceMapper;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.management.DataSourceManager;
import io.yak.ops.business.datasource.query.DataSourcePluginReader;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataSourceLayeringConventionTest {

  @Test
  void repositoryExposesOnlyDomainContracts() {
    assertMethodsAvoid(
        DataSourceRepository.class,
        ".bean.dto.",
        ".bean.vo.",
        ".bean.po.",
        "com.baomidou.mybatisplus");
  }

  @Test
  void repositoryUsesSharedPageData() throws Exception {
    Method page = DataSourceRepository.class.getMethod("page", DataSourceQuery.class);
    assertThat(((ParameterizedType) page.getGenericReturnType()).getRawType())
        .isEqualTo(PageData.class);
  }

  @Test
  void daoDoesNotDependOnTransportModels() {
    assertMethodsAvoid(DataSourceDao.class, ".bean.dto.", ".bean.vo.");
  }

  @Test
  void businessRolesDoNotInjectDaoOrPersistenceObjects() {
    for (Class<?> type :
        List.of(
            DataSourceManager.class,
            DataSourceReader.class,
            DataSourceConnectionResolver.class,
            DataSourceConnectionTester.class,
            DataSourceCatalogReader.class,
            DataSourcePluginReader.class)) {
      for (Field field : type.getDeclaredFields()) {
        String fieldType = field.getGenericType().getTypeName();
        assertThat(fieldType)
            .as(type.getSimpleName() + "." + field.getName())
            .doesNotContain(".business.datasource.dao.")
            .doesNotContain(".bean.po.");
      }
    }
  }

  @Test
  void controllersDoNotDependOnLegacyServicesOrPersistence() {
    for (Class<?> type :
        List.of(
            DataSourceController.class,
            DataSourceCatalogController.class,
            DataSourcePluginConfigController.class,
            SqlExecutionAuditController.class)) {
      for (Field field : type.getDeclaredFields()) {
        String fieldType = field.getGenericType().getTypeName();
        assertThat(fieldType)
            .as(type.getSimpleName() + "." + field.getName())
            .doesNotContain(".business.datasource.service")
            .doesNotContain(".business.datasource.dao.")
            .doesNotContain(".bean.po.");
      }
    }
  }

  @Test
  void summaryMapperReturnsPersistenceProjectionInsteadOfVo() throws Exception {
    Method method = DataSourceMapper.class.getMethod("selectSummary");
    assertThat(method.getReturnType().getPackageName())
        .isEqualTo("io.yak.ops.business.datasource.dao.model");
  }

  @Test
  void persistenceStillUsesSingleDatasourceTable() {
    TableName tableName = DataSourcePO.class.getAnnotation(TableName.class);
    assertThat(tableName).isNotNull();
    assertThat(tableName.value()).isEqualTo("yak_ops_data_source");
  }

  private void assertMethodsAvoid(Class<?> owner, String... forbidden) {
    for (Method method : owner.getMethods()) {
      assertTypeAvoids(owner, method, method.getGenericReturnType(), forbidden);
      for (Type parameter : method.getGenericParameterTypes()) {
        assertTypeAvoids(owner, method, parameter, forbidden);
      }
    }
  }

  private void assertTypeAvoids(
      Class<?> owner, Method method, Type type, String... forbidden) {
    String signature = typeName(type);
    for (String packagePart : forbidden) {
      assertThat(signature)
          .as("%s.%s must not expose %s", owner.getSimpleName(), method.getName(), packagePart)
          .doesNotContain(packagePart);
    }
  }

  private String typeName(Type type) {
    if (type instanceof ParameterizedType parameterized) {
      StringBuilder value = new StringBuilder(parameterized.getRawType().getTypeName());
      for (Type argument : parameterized.getActualTypeArguments()) {
        value.append('<').append(typeName(argument)).append('>');
      }
      return value.toString();
    }
    return type.getTypeName();
  }
}
