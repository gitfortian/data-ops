package io.yak.ops.business.job.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/** Job 模块 MyBatis 持久化配置，注册 Mapper 到共享业务 SqlSessionFactory。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@MapperScan(
    basePackages = "io.yak.ops.business.job.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class JobPersistenceConfiguration {
}
