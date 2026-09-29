package io.yak.ops.business.dataset.architecture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.dataset.config.DatasetPersistenceConfiguration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

class DatasetPersistenceMapperScanContractTest {

  @Test
  void persistenceConfigurationScansDatasetMappersWithTheSharedBusinessFactory() {
    MapperScan mapperScan =
        DatasetPersistenceConfiguration.class.getAnnotation(MapperScan.class);

    assertNotNull(mapperScan, "Dataset persistence must own its mapper scan");
    assertTrue(
        Arrays.asList(mapperScan.basePackages())
            .contains("io.yak.ops.business.dataset.dao.mapper"));
    assertTrue(
        Arrays.asList(mapperScan.sqlSessionFactoryRef(), mapperScan.sqlSessionTemplateRef())
            .contains("yakBusinessSqlSessionFactory"));
  }
}
