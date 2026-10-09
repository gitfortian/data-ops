package io.yak.framework.security.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.framework.security.common.po.AppBasePO;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.PermissionPO;
import io.yak.framework.security.common.po.UserPO;
import io.yak.framework.security.config.NumericBooleanTypeHandler;
import io.yak.framework.security.dao.mapper.UserMapper;
import java.sql.PreparedStatement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

class SecurityPersistenceModelContractTest {
  @Test
  void preservesHistoricalSecurityTableNamesAndPrimaryKeyIdentity() throws Exception {
    assertThat(UserPO.class.getAnnotation(TableName.class).value()).isEqualTo("yak_security_user");
    assertThat(PermissionPO.class.getAnnotation(TableName.class).value())
        .isEqualTo("yak_security_permission");
    TableId id = BasePO.class.getDeclaredField("id").getAnnotation(TableId.class);
    assertThat(id.type()).isEqualTo(IdType.AUTO);
    TableLogic deletion = BasePO.class.getDeclaredField("isDelete")
        .getAnnotation(TableLogic.class);
    assertThat(deletion.value()).isEqualTo("0");
    assertThat(deletion.delval()).isEqualTo("1");
  }

  @Test
  void preservesApplicationIsolationAndSecretRedactionInDatabaseModel() throws Exception {
    TableField app = AppBasePO.class.getDeclaredField("appName")
        .getAnnotation(TableField.class);
    assertThat(app.fill()).isEqualTo(FieldFill.INSERT);
    UserPO user = new UserPO();
    user.setAppName("security-app");
    user.setUserName("admin");
    user.setPw("hashed-password-sensitive");
    user.setSalt("hashed-salt-sensitive");
    assertThat(user.toString()).doesNotContain("hashed-password-sensitive", "hashed-salt-sensitive");
    assertThat(user.getAppName()).isEqualTo("security-app");
  }

  @Test
  void keepsOldMapperFqcnAndMybatisBeanDiscoveryAnnotation() {
    assertThat(UserMapper.class.getName())
        .isEqualTo("io.yak.framework.security.dao.mapper.UserMapper");
    assertThat(UserMapper.class.isAnnotationPresent(Mapper.class)).isTrue();
    assertThat(BaseMapper.class.isAssignableFrom(UserMapper.class)).isTrue();
  }

  @Test
  void preservesNumericZeroOneBooleanBindingForPostgresql() throws Exception {
    NumericBooleanTypeHandler handler = new NumericBooleanTypeHandler();
    PreparedStatement statement = mock(PreparedStatement.class);
    handler.setNonNullParameter(statement, 1, true, JdbcType.INTEGER);
    handler.setNonNullParameter(statement, 2, false, JdbcType.INTEGER);
    verify(statement).setInt(1, 1);
    verify(statement).setInt(2, 0);
  }
}
