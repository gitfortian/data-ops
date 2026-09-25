package io.yak.ops.business.consumption.config;

import io.yak.ops.business.consumption.persistence.SubscriptionMapper;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Consumption MyBatis registration on the shared Yak business SQL session. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
@MapperScan(
    basePackageClasses = SubscriptionMapper.class,
    sqlSessionTemplateRef = "yakBusinessSqlSessionTemplate")
public class ConsumptionMybatisConfiguration {}
