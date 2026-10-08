package io.yak.framework.security.common.constant;

/**
 * Legacy FQCN compatibility facade. Canonical permission values now belong to
 * io.yak.ops.platform.security.contract.SecurityPermissionCode.
 *
 * <p>宿主应用和前端应引用相同的字符串约定，避免按钮权限与接口权限发生漂移。
 */
public final class SecurityPermissionCode {

  public static final String GROUP_CODE = io.yak.ops.platform.security.contract.SecurityPermissionCode.GROUP_CODE;
  public static final String GROUP_NAME = io.yak.ops.platform.security.contract.SecurityPermissionCode.GROUP_NAME;
  public static final String ROOT = io.yak.ops.platform.security.contract.SecurityPermissionCode.ROOT;

  private SecurityPermissionCode() {
  }

  /** 用户管理权限。 */
  public static final class User {
    public static final String READ = io.yak.ops.platform.security.contract.SecurityPermissionCode.User.READ;
    public static final String CREATE = io.yak.ops.platform.security.contract.SecurityPermissionCode.User.CREATE;
    public static final String UPDATE = io.yak.ops.platform.security.contract.SecurityPermissionCode.User.UPDATE;
    public static final String RESET_PASSWORD = io.yak.ops.platform.security.contract.SecurityPermissionCode.User.RESET_PASSWORD;
    public static final String DELETE = io.yak.ops.platform.security.contract.SecurityPermissionCode.User.DELETE;

    private User() {
    }
  }

  /** 角色管理权限。 */
  public static final class Role {
    public static final String READ = io.yak.ops.platform.security.contract.SecurityPermissionCode.Role.READ;
    public static final String CREATE = io.yak.ops.platform.security.contract.SecurityPermissionCode.Role.CREATE;
    public static final String UPDATE = io.yak.ops.platform.security.contract.SecurityPermissionCode.Role.UPDATE;
    public static final String ASSIGN = io.yak.ops.platform.security.contract.SecurityPermissionCode.Role.ASSIGN;
    public static final String DELETE = io.yak.ops.platform.security.contract.SecurityPermissionCode.Role.DELETE;

    private Role() {
    }
  }

  /** 权限目录管理权限。 */
  public static final class Permission {
    public static final String MENU_CODE = io.yak.ops.platform.security.contract.SecurityPermissionCode.Permission.MENU_CODE;
    public static final String READ = io.yak.ops.platform.security.contract.SecurityPermissionCode.Permission.READ;
    public static final String IMPORT = io.yak.ops.platform.security.contract.SecurityPermissionCode.Permission.IMPORT;
    public static final String DELETE = io.yak.ops.platform.security.contract.SecurityPermissionCode.Permission.DELETE;

    private Permission() {
    }
  }

  /** 部门管理权限。 */
  public static final class Department {
    public static final String MENU_CODE = io.yak.ops.platform.security.contract.SecurityPermissionCode.Department.MENU_CODE;
    public static final String READ = io.yak.ops.platform.security.contract.SecurityPermissionCode.Department.READ;
    public static final String CREATE = io.yak.ops.platform.security.contract.SecurityPermissionCode.Department.CREATE;
    public static final String EDIT = io.yak.ops.platform.security.contract.SecurityPermissionCode.Department.EDIT;
    public static final String DELETE = io.yak.ops.platform.security.contract.SecurityPermissionCode.Department.DELETE;
    public static final String IMPORT = io.yak.ops.platform.security.contract.SecurityPermissionCode.Department.IMPORT;

    private Department() {
    }
  }

  /** 安全项目管理权限。 */
  public static final class Project {
    public static final String MENU_CODE = io.yak.ops.platform.security.contract.SecurityPermissionCode.Project.MENU_CODE;
    public static final String READ = io.yak.ops.platform.security.contract.SecurityPermissionCode.Project.READ;

    private Project() {
    }
  }

  /** 资源授权管理权限。 */
  public static final class ResourcePermission {
    public static final String READ = io.yak.ops.platform.security.contract.SecurityPermissionCode.ResourcePermission.READ;

    private ResourcePermission() {
    }
  }

  /** 系统配置管理权限。 */
  public static final class Config {
    public static final String READ = io.yak.ops.platform.security.contract.SecurityPermissionCode.Config.READ;

    private Config() {
    }
  }

  /** 操作日志查询权限。 */
  public static final class OperationLog {
    public static final String READ = io.yak.ops.platform.security.contract.SecurityPermissionCode.OperationLog.READ;

    private OperationLog() {
    }
  }
}
