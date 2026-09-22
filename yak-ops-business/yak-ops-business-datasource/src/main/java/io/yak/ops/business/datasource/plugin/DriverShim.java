package io.yak.ops.business.datasource.plugin;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * DriverManager 桥接:DriverManager 只把「调用方类加载器可见」的驱动纳入候选,
 * 子 URLClassLoader 里的外置驱动必须以本类(应用类加载器可见)包装后才可被选中。
 */
final class DriverShim implements Driver {

  private final Driver delegate;

  DriverShim(Driver delegate) {
    this.delegate = delegate;
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    return delegate.connect(url, info);
  }

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    return delegate.acceptsURL(url);
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return delegate.getPropertyInfo(url, info);
  }

  @Override
  public int getMajorVersion() {
    return delegate.getMajorVersion();
  }

  @Override
  public int getMinorVersion() {
    return delegate.getMinorVersion();
  }

  @Override
  public boolean jdbcCompliant() {
    return delegate.jdbcCompliant();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return delegate.getParentLogger();
  }
}
