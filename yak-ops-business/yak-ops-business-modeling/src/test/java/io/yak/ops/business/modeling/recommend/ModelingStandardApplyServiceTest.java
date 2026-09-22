package io.yak.ops.business.modeling.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.semantic.api.StandardRecommendApi;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 建模侧标准推荐消费单元测试:模型归属校验 + SPI 透传。 */
class ModelingStandardApplyServiceTest {

  private StandardRecommendApi recommendApi;
  private ModelRepository modelRepository;
  private ModelingStandardApplyService service;

  @BeforeEach
  void setUp() {
    recommendApi = mock(StandardRecommendApi.class);
    modelRepository = mock(ModelRepository.class);
    service = new ModelingStandardApplyService(recommendApi, modelRepository);
  }

  @Test
  void recommendRejectsUnknownModel() {
    when(modelRepository.findById(9L)).thenReturn(Optional.empty());
    ModelingException exception =
        assertThrows(
            ModelingException.class,
            () -> service.recommend(9L, "user_phone", "VARCHAR", "DIMENSION"));
    assertEquals(ModelingErrorCode.NOT_FOUND, exception.getErrorCode());
  }

  @Test
  void recommendPassesThroughToSemanticSpi() {
    Model model =
        new Model(
            1L, "m1", "订单", ModelDialect.MYSQL, null, ModelStatus.DRAFT, null, null, null,
            null, null, null, null, null, null);
    when(modelRepository.findById(1L)).thenReturn(Optional.of(model));
    StandardRecommendApi.RecommendationReport report =
        new StandardRecommendApi.RecommendationReport(
            new StandardRecommendApi.NamingCheck(false, false, null, null, null, null),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of());
    when(recommendApi.recommend(anyRequest())).thenReturn(report);

    StandardRecommendApi.RecommendationReport result =
        service.recommend(1L, "user_phone", "VARCHAR", "DIMENSION");

    assertEquals(report, result);
  }

  private static StandardRecommendApi.RecommendRequest anyRequest() {
    return org.mockito.ArgumentMatchers.any(StandardRecommendApi.RecommendRequest.class);
  }
}
