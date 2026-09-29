package io.yak.ops.boot.project;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers Project Space request context infrastructure without changing legacy endpoints. */
@Configuration
public class ProjectSpaceWebConfiguration implements WebMvcConfigurer {

  private final ProjectScopeInterceptor projectScopeInterceptor;

  public ProjectSpaceWebConfiguration(ProjectScopeInterceptor projectScopeInterceptor) {
    this.projectScopeInterceptor = projectScopeInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(projectScopeInterceptor).addPathPatterns("/api/**");
  }
}
