package io.yak.ops.business.datasource.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.config.DataSourceProperties.Credential;
import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.datasource.dao.mapper.DataSourceMapper;
import io.yak.ops.business.datasource.gateway.adapter.AesGcmCredentialCipher;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 加解密钩子必须落在 DAO 读写口上(Ticket 05)：同步模块直接注入本 DAO 取连接参数，
 * 只有这里保证"落库密文 / 内存明文"对所有消费者同时成立。
 */
@ExtendWith(MockitoExtension.class)
class DataSourceDaoImplCredentialTest {

  private static final String PLAIN_JSON = "{\"password\":\"Root@123456\"}";
  private static final String BASE64_KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

  @Mock private DataSourceMapper mapper;

  @BeforeAll
  static void initLambdaCache() {
    // 纯单元测试无 MyBatis 运行时：lambdaWrapper 需要 TableInfo 缓存才能解析 PO 列名
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), DataSourcePO.class);
  }

  @Test
  void storesCiphertextAndHandsPlainTextBackToReaders() {
    DataSourceDao dao = dao(BASE64_KEY);
    when(mapper.insert(any(DataSourcePO.class))).thenReturn(1);

    DataSourcePO definition = row(42L, PLAIN_JSON, PLAIN_JSON);
    dao.addDataSource(definition);

    ArgumentCaptor<DataSourcePO> stored = ArgumentCaptor.forClass(DataSourcePO.class);
    verify(mapper).insert(stored.capture());
    DataSourcePO written = stored.getValue();
    assertThat(written.getConnectionParams()).startsWith("ENC:").isNotEqualTo(PLAIN_JSON);
    assertThat(written.getOriginalJson()).startsWith("ENC:").isNotEqualTo(PLAIN_JSON);

    when(mapper.selectOne(any(Wrapper.class))).thenReturn(written);
    DataSourcePO read = dao.selectById(7L, 42L);
    assertThat(read.getConnectionParams()).isEqualTo(PLAIN_JSON);
    assertThat(read.getOriginalJson()).isEqualTo(PLAIN_JSON);
  }

  @Test
  void decryptsEveryRowOfListAndPageReads() {
    DataSourceDao dao = dao(BASE64_KEY);
    AesGcmCredentialCipher cipher = cipher(BASE64_KEY);
    List<DataSourcePO> storedRows =
        new ArrayList<>(
            List.of(
                row(41L, cipher.encrypt(PLAIN_JSON), cipher.encrypt(PLAIN_JSON)),
                row(42L, cipher.encrypt(PLAIN_JSON), null)));
    when(mapper.selectList(any(Wrapper.class))).thenReturn(storedRows);

    assertThat(dao.selectAll(7L, null))
        .allSatisfy(row -> assertThat(row.getConnectionParams()).isEqualTo(PLAIN_JSON));

    Page<DataSourcePO> page = new Page<>(1, 10);
    page.setRecords(new ArrayList<>(List.of(row(43L, cipher.encrypt(PLAIN_JSON), null))));
    when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

    DataSourcePO paged = dao.selectPage(null).getRecords().getFirst();
    assertThat(paged.getConnectionParams()).isEqualTo(PLAIN_JSON);
  }

  @Test
  void backfillRewritesOnlyPlainRows() {
    DataSourceDao dao = dao(BASE64_KEY);
    AesGcmCredentialCipher cipher = cipher(BASE64_KEY);
    when(mapper.selectList(any(Wrapper.class)))
        .thenReturn(
            new ArrayList<>(
                List.of(
                    row(41L, PLAIN_JSON, null),
                    row(42L, cipher.encrypt(PLAIN_JSON), cipher.encrypt(PLAIN_JSON)),
                    row(43L, null, null))));
    when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

    assertThat(dao.encryptPlainCredentials(7L)).isEqualTo(1);

    verify(mapper, times(1)).update(isNull(), any(Wrapper.class));
  }

  @Test
  void keepsWritingPlaintextWhenNoKeyIsConfigured() {
    DataSourceDao dao = dao("");
    when(mapper.insert(any(DataSourcePO.class))).thenReturn(1);

    dao.addDataSource(row(42L, PLAIN_JSON, PLAIN_JSON));

    ArgumentCaptor<DataSourcePO> stored = ArgumentCaptor.forClass(DataSourcePO.class);
    verify(mapper).insert(stored.capture());
    assertThat(stored.getValue().getConnectionParams()).isEqualTo(PLAIN_JSON);
  }

  private DataSourceDao dao(String secretKey) {
    return new DataSourceDaoImpl(mapper, project(7L), cipher(secretKey));
  }

  private static AesGcmCredentialCipher cipher(String secretKey) {
    DataSourceProperties properties = new DataSourceProperties();
    Credential credential = properties.getCredential();
    credential.setSecretKey(secretKey);
    return new AesGcmCredentialCipher(properties);
  }

  private static DataSourcePO row(Long id, String connectionParams, String originalJson) {
    DataSourcePO row = new DataSourcePO();
    row.setId(id);
    row.setProjectId(7L);
    row.setConnectionParams(connectionParams);
    row.setOriginalJson(originalJson);
    return row;
  }

  private static CurrentProject project(long projectId) {
    return () -> Optional.of(new ProjectContext(projectId, "Project " + projectId));
  }
}
