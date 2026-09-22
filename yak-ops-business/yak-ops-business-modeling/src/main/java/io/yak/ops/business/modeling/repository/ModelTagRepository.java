package io.yak.ops.business.modeling.repository;

import io.yak.ops.business.modeling.domain.ModelingTag;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Project-scoped persistence boundary for modeling tags and model-tag relations. */
public interface ModelTagRepository {

  ModelingTag insert(String name);

  Optional<ModelingTag> findById(Long id);

  boolean existsByName(String name);

  List<ModelingTag> listAll();

  boolean deleteById(Long id);

  /** Replaces the tag set of one model with the given tag ids (idempotent). */
  void replaceModelTags(Long modelId, List<Long> tagIds);

  /** Tag ids attached to one model. */
  List<Long> tagIdsForModel(Long modelId);

  /** Tag ids per model id for the given models (missing keys mean no tags). */
  Map<Long, List<Long>> tagIdsForModels(List<Long> modelIds);

  /** Distinct model ids carrying any of the given tags (OR semantics). */
  List<Long> modelIdsByTagIds(List<Long> tagIds);
}
