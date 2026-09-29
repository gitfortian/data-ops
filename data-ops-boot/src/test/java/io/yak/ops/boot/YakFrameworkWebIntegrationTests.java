package io.yak.ops.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mongodb.client.MongoClient;
import io.yak.ops.boot.config.OpenApiConfiguration;
import io.yak.ops.boot.config.JacksonConfiguration;
import io.yak.ops.boot.controller.TestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.models.GroupedOpenApi;

@WebMvcTest(TestController.class)
@ContextConfiguration(classes = YakFrameworkWebIntegrationTests.WebSliceConfiguration.class)
@ActiveProfiles("test")
class YakFrameworkWebIntegrationTests {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private Environment environment;

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private java.util.Map<String, GroupedOpenApi> openApiGroups;

  @Test
  void testControllerShouldReturnFrameworkResult() throws Exception {
    mockMvc.perform(get("/api/test/ping"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.application").value("yak-ops"))
        .andExpect(jsonPath("$.data.status").value("UP"))
        .andExpect(jsonPath("$.data.framework").value("data-ops-framework"));
  }

  @Test
  void openApiGroupsShouldIncludeYakOpsAndYakSecurity() {
    assertTrue(openApiGroups.values().stream()
        .anyMatch(group -> "yak-ops".equals(group.getGroup())));
    assertTrue(openApiGroups.values().stream()
        .anyMatch(group -> "yak-security".equals(group.getGroup())));
  }

  @Test
  void shouldUseSingleSaTokenAuthenticationConfiguration() {
    assertEquals(
        "30m",
        environment.getProperty("yak.security.authentication.idle-timeout"));
    assertEquals(
        "memory",
        environment.getProperty("yak.security.authentication.storage"));
    assertNull(environment.getProperty("yak.security.authentication.mode"));
    assertNull(environment.getProperty("yak.security.session.timeout"));
  }

  @Test
  void shouldNotCreateDefaultMongoClientAtStartup() {
    assertTrue(applicationContext.getBeansOfType(MongoClient.class).isEmpty());
  }

  @Configuration(proxyBeanMethods = false)
  @Import({TestController.class, OpenApiConfiguration.class, JacksonConfiguration.class})
  static class WebSliceConfiguration {
  }

}
