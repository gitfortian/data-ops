package io.yak.ops.business.agent;

/** Agent 模块权限码。注册脚本见 boot 的 yak-security 迁移 V2004/V2005/V2012。 */
public final class AgentPermissionCode {

  public static final String CHAT_RUN = "agent:chat:run";
  public static final String SESSION_READ = "agent:session:read";
  public static final String SESSION_UPDATE = "agent:session:update";
  public static final String SESSION_DELETE = "agent:session:delete";
  public static final String REPORT_READ = "agent:report:read";
  public static final String REPORT_DELETE = "agent:report:delete";
  public static final String CONFIG_READ = "agent:config:read";
  public static final String CONFIG_MANAGE = "agent:config:manage";
  public static final String SKILL_READ = "agent:skill:read";
  public static final String SKILL_MANAGE = "agent:skill:manage";

  private AgentPermissionCode() {}
}
