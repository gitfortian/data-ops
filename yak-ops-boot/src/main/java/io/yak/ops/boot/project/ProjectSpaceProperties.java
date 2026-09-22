package io.yak.ops.boot.project;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Compatibility settings used while business data is moved into the default Project Space. */
@Component
@ConfigurationProperties(prefix = "yak.project-space")
public class ProjectSpaceProperties {

  private final Compatibility compatibility = new Compatibility();

  public Compatibility getCompatibility() {
    return compatibility;
  }

  public static class Compatibility {

    private boolean bootstrapDefaultProject;
    private String defaultProjectName = "默认空间";
    private String defaultOwnerUsername = "root";
    private String defaultDepartmentName = "默认部门";

    public boolean isBootstrapDefaultProject() {
      return bootstrapDefaultProject;
    }

    public void setBootstrapDefaultProject(boolean bootstrapDefaultProject) {
      this.bootstrapDefaultProject = bootstrapDefaultProject;
    }

    public String getDefaultProjectName() {
      return defaultProjectName;
    }

    public void setDefaultProjectName(String defaultProjectName) {
      this.defaultProjectName = defaultProjectName;
    }

    public String getDefaultOwnerUsername() {
      return defaultOwnerUsername;
    }

    public void setDefaultOwnerUsername(String defaultOwnerUsername) {
      this.defaultOwnerUsername = defaultOwnerUsername;
    }

    public String getDefaultDepartmentName() {
      return defaultDepartmentName;
    }

    public void setDefaultDepartmentName(String defaultDepartmentName) {
      this.defaultDepartmentName = defaultDepartmentName;
    }
  }
}
