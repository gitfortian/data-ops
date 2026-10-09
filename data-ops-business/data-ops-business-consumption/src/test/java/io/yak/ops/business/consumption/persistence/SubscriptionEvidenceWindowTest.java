package io.yak.ops.business.consumption.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SubscriptionEvidenceWindowTest {
  private final SubscriptionMapper mapper = mock(SubscriptionMapper.class);
  private final MybatisSubscriptionRepository repository = new MybatisSubscriptionRepository(mapper);

  @ParameterizedTest @CsvSource({"0,1", "20,20", "200,200", "201,200", "2147483647,200"})
  void appliesProjectProductActiveStableOrderingAndLimitInsideDatabase(int requested, int limit) {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SubscriptionPO.class);
    when(mapper.selectList(any())).thenAnswer(invocation -> {
      LambdaQueryWrapper<SubscriptionPO> query = invocation.getArgument(0);
      String sql = query.getSqlSegment();
      assertTrue(sql.contains("project_id ="));
      assertTrue(sql.contains("product_key ="));
      assertTrue(sql.contains("status ="));
      assertTrue(sql.contains("ORDER BY updated_at DESC,id DESC"));
      assertTrue(sql.endsWith("LIMIT " + limit));
      assertTrue(query.getParamNameValuePairs().values().containsAll(List.of(42L, "DATASET:101", "ACTIVE")));
      return List.of();
    });
    repository.listRecentActive(42L, ProductKey.parse("DATASET:101"), requested);
    verify(mapper).selectList(any());
    verifyNoMoreInteractions(mapper);
  }

  @Test void missingProjectOrProductCannotBecomeAnUnscopedQuery() {
    ProductKey product = ProductKey.parse("DATASET:101");
    assertThrows(IllegalArgumentException.class, () -> repository.listRecentActive(null, product, 200));
    assertThrows(IllegalArgumentException.class, () -> repository.listRecentActive(0L, product, 200));
    assertThrows(IllegalArgumentException.class, () -> repository.listRecentActive(42L, null, 200));
    verifyNoInteractions(mapper);
  }

  @ParameterizedTest @CsvSource({"0,1", "20,20", "200,200", "201,200", "2147483647,200"})
  void usageWindowAlsoLimitsInsideDatabaseWithStableObservationOrdering(int requested, int limit) {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UsageEvidencePO.class);
    UsageEvidenceMapper usageMapper = mock(UsageEvidenceMapper.class);
    when(usageMapper.selectList(any())).thenAnswer(invocation -> {
      LambdaQueryWrapper<UsageEvidencePO> query = invocation.getArgument(0);
      String sql = query.getSqlSegment();
      assertTrue(sql.contains("project_id ="));
      assertTrue(sql.contains("product_key ="));
      assertTrue(sql.contains("ORDER BY observed_at DESC,id DESC"));
      assertTrue(sql.endsWith("LIMIT " + limit));
      assertTrue(query.getParamNameValuePairs().values().containsAll(List.of(42L, "DATASET:101")));
      return List.of();
    });
    new MybatisUsageEvidenceRepository(usageMapper).list(42L, ProductKey.parse("DATASET:101"), null, requested);
    verify(usageMapper).selectList(any());
    verifyNoMoreInteractions(usageMapper);
  }
}
