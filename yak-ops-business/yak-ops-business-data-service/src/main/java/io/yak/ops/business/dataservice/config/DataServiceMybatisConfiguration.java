package io.yak.ops.business.dataservice.config;

import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Data Service MyBatis mapper registration on the shared Yak business SQL session. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
@MapperScan(
    basePackageClasses = DataServiceApiMapper.class,
    sqlSessionTemplateRef = "yakBusinessSqlSessionTemplate")
public class DataServiceMybatisConfiguration {
}
