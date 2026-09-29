package io.yak.ops.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Yak Ops application entry point.
 *
 * <p>Yak Ops modules are scanned from their shared root package. Yak Framework integrations are
 * loaded through their Spring Boot auto-configuration metadata.</p>
 *
 * <p>MongoDB is managed by the datasource plugin and creates clients only for explicit datasource
 * operations. Spring Boot's generic Mongo auto-configuration is disabled so merely having the
 * MongoDB driver on the assembled application classpath does not create a default localhost
 * client during startup.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = MongoAutoConfiguration.class)
@ComponentScan(
    basePackages = "io.yak.ops",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "io\\.yak\\.ops\\.business\\.metric\\..*"))
public class YakOpsApplication {

  public static void main(String[] args) {
    SpringApplication.run(YakOpsApplication.class, args);
  }
}
