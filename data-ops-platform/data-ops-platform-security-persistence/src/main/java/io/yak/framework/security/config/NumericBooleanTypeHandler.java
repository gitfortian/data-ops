package io.yak.framework.security.config;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.apache.ibatis.type.BooleanTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Platform flags retain their numeric 0/1 storage contract on PostgreSQL. */
public final class NumericBooleanTypeHandler extends BooleanTypeHandler {
  @Override
  public void setNonNullParameter(PreparedStatement statement, int index, Boolean value, JdbcType type)
      throws SQLException {
    statement.setInt(index, value ? 1 : 0);
  }
}
