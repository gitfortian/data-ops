package io.yak.ops.business.datasource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 数据源管理模块配置。 */
@ConfigurationProperties(prefix = "yak.datasource")
public class DataSourceProperties {

  private boolean enabled = true;
  private final Database database = new Database();
  private final ConnectionTest connectionTest = new ConnectionTest();
  private final Catalog catalog = new Catalog();
  private final Driver driver = new Driver();
  private final HealthProbe healthProbe = new HealthProbe();
  private final Credential credential = new Credential();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Database getDatabase() {
    return database;
  }

  public ConnectionTest getConnectionTest() {
    return connectionTest;
  }

  public Catalog getCatalog() {
    return catalog;
  }

  public Driver getDriver() {
    return driver;
  }

  public HealthProbe getHealthProbe() {
    return healthProbe;
  }

  public Credential getCredential() {
    return credential;
  }

  /**
   * 连接凭证静态加密参数(Ticket 05)。
   *
   * <p>密钥丢失 = 已加密凭证不可恢复，只能逐个数据源重新录入；启用前请先备份密钥。
   * 轮换不在本能力范围内。
   */
  public static class Credential {

    /** 加密密钥；推荐 32 字节 Base64，其它字符串按 SHA-256 派生。留空即明文兼容模式。 */
    private String secretKey = "";

    /** 启动时是否把存量明文凭证洗成密文；仅在密钥已配置时执行。 */
    private boolean migrateOnStartup = true;

    public String getSecretKey() {
      return secretKey;
    }

    public void setSecretKey(String secretKey) {
      this.secretKey = secretKey;
    }

    public boolean isMigrateOnStartup() {
      return migrateOnStartup;
    }

    public void setMigrateOnStartup(boolean migrateOnStartup) {
      this.migrateOnStartup = migrateOnStartup;
    }
  }

  /** 连接健康定时巡检参数(Ticket 03)。节奏值由 @Scheduled 占位符直接读 Environment,此处只绑定开关。 */
  public static class HealthProbe {

    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  /** 外置 JDBC 驱动包上传参数。 */
  public static class Driver {

    /** 驱动 jar 落盘根目录。 */
    private String directory = "./data/drivers";

    /** 单个驱动包大小上限,字节。 */
    private long maxFileSizeBytes = 100L * 1024 * 1024;

    public String getDirectory() {
      return directory;
    }

    public void setDirectory(String directory) {
      this.directory = directory;
    }

    public long getMaxFileSizeBytes() {
      return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
      this.maxFileSizeBytes = maxFileSizeBytes;
    }
  }

  /** 数据源管理元数据数据库配置。 */
  public static class Database {

    private String url =
        "jdbc:mariadb://127.0.0.1:3306/yak_security"
            + "?useUnicode=true&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
            + "&useSSL=false&serverTimezone=Asia/Shanghai";
    private String username = "root";
    private String password = "123456";
    private String driverClassName = "org.mariadb.jdbc.Driver";
    private int minimumIdle = 1;
    private int maximumPoolSize = 8;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getDriverClassName() {
      return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
      this.driverClassName = driverClassName;
    }

    public int getMinimumIdle() {
      return minimumIdle;
    }

    public void setMinimumIdle(int minimumIdle) {
      this.minimumIdle = minimumIdle;
    }

    public int getMaximumPoolSize() {
      return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
      this.maximumPoolSize = maximumPoolSize;
    }
  }

  /** 用户配置的数据源连接测试参数。 */
  public static class ConnectionTest {

    private int timeoutSeconds = 5;

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }
  }

  /** Catalog 元数据和轻量读取参数。 */
  public static class Catalog {

    /** 建立用户数据源连接的超时时间。 */
    private int connectionTimeoutSeconds = 5;

    /** SQL describe / preview / count 的 statement 级超时时间。 */
    private int queryTimeoutSeconds = 15;

    /** 数据库 / Schema / 表 / 字段元数据缓存 TTL；小于等于 0 时关闭缓存。 */
    private int metadataCacheTtlSeconds = 60;

    /** 下拉远程搜索一次最多返回的表数量。 */
    private int tableSearchLimit = 100;

    /** 单次物理 Catalog 访问超过该阈值时记录慢操作；单位毫秒。 */
    private long slowOperationThresholdMillis = 1000L;

    public int getConnectionTimeoutSeconds() {
      return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
      this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public int getQueryTimeoutSeconds() {
      return queryTimeoutSeconds;
    }

    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
      this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public int getMetadataCacheTtlSeconds() {
      return metadataCacheTtlSeconds;
    }

    public void setMetadataCacheTtlSeconds(int metadataCacheTtlSeconds) {
      this.metadataCacheTtlSeconds = metadataCacheTtlSeconds;
    }

    public int getTableSearchLimit() {
      return tableSearchLimit;
    }

    public void setTableSearchLimit(int tableSearchLimit) {
      this.tableSearchLimit = tableSearchLimit;
    }

    public long getSlowOperationThresholdMillis() {
      return slowOperationThresholdMillis;
    }

    public void setSlowOperationThresholdMillis(long slowOperationThresholdMillis) {
      this.slowOperationThresholdMillis = slowOperationThresholdMillis;
    }
  }
}
