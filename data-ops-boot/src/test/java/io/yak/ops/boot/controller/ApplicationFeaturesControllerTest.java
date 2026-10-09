package io.yak.ops.boot.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class ApplicationFeaturesControllerTest {

  @ParameterizedTest
  @ValueSource(strings = {"", "false", "true"})
  void reportsDeploymentSwitchWithoutRequiringAgentBeans(String setting) {
    var runner = new WebApplicationContextRunner().withUserConfiguration(WebConfiguration.class);
    if (!setting.isEmpty()) runner = runner.withPropertyValues("yak.agent.enabled=" + setting);
    runner.run(context -> MockMvcBuilders.webAppContextSetup(context).build()
        .perform(get("/api/v1/system/features"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.agentEnabled").value("true".equals(setting))));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @Import(ApplicationFeaturesController.class)
  static class WebConfiguration {}
}
