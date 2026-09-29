package io.yak.ops.business.modeling.recommend;

import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.semantic.api.StandardRecommendApi;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import org.springframework.stereotype.Component;

/**
 * Modeling-side consumer of the semantic recommendation SPI (ticket 39).
 * Thin by design: model-ownership check lives here, matching rules live in
 * semantic (StandardRecommendationService). Never blocks editing.
 */
@Component
public class ModelingStandardApplyService {

  private final StandardRecommendApi recommendApi;
  private final ModelRepository modelRepository;

  public ModelingStandardApplyService(StandardRecommendApi recommendApi, ModelRepository modelRepository) {
    this.recommendApi = recommendApi;
    this.modelRepository = modelRepository;
  }

  public StandardRecommendApi.RecommendationReport recommend(
      Long modelId, String columnName, String dataType, String role) {
    modelRepository
        .findById(modelId)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
    return recommendApi.recommend(new StandardRecommendApi.RecommendRequest(columnName, dataType, role));
  }
}
