package io.yak.ops.business.consumption.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MybatisUsageEvidenceVersionRepositoryTest {

  @BeforeAll
  static void initializeLambdaMetadata() {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(configuration, "consumption-version-usage-test"),
        UsageEvidencePO.class);
  }

  @Test
  void exactImmutableVersionProjectAndProductPredicatesApplyBeforeLimit() {
    UsageEvidenceMapper mapper = mock(UsageEvidenceMapper.class);
    when(mapper.selectList(any())).thenReturn(List.of());
    var repository = new MybatisUsageEvidenceRepository(mapper);
    ProductKey product = ProductKey.parse("DATA_SERVICE:7");

    List<UsageEvidence> found = repository.listByVersion(42L, product, "9007199254740993", 999);

    assertThat(found).isEmpty();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaQueryWrapper<UsageEvidencePO>> capture =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectList(capture.capture());
    var query = capture.getValue();
    assertThat(query.getSqlSegment()).contains(
        "project_id", "product_key", "source_version_identity",
        "observed_at DESC", "id DESC", "LIMIT 200");
    assertThat(query.getParamNameValuePairs().values())
        .containsExactlyInAnyOrder(42L, "DATA_SERVICE:7", "9007199254740993");
  }

  @Test
  void exactVersionSelectIsBoundedToRequestedWindowWithoutInterpolatingIdentity() {
    UsageEvidenceMapper mapper = mock(UsageEvidenceMapper.class);
    when(mapper.selectList(any())).thenReturn(List.of());
    var repository = new MybatisUsageEvidenceRepository(mapper);

    repository.listByVersion(51L, ProductKey.parse("DATASET:9"), "old-revision", 7);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<LambdaQueryWrapper<UsageEvidencePO>> capture =
        ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(mapper).selectList(capture.capture());
    assertThat(capture.getValue().getSqlSegment()).contains("LIMIT 7");
    assertThat(capture.getValue().getSqlSegment()).doesNotContain("old-revision");
    assertThat(capture.getValue().getParamNameValuePairs().values())
        .contains("old-revision", 51L, "DATASET:9");
  }
}
