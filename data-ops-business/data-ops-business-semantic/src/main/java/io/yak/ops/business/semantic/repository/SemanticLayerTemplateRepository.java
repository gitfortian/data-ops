package io.yak.ops.business.semantic.repository;

import io.yak.ops.business.semantic.layer.LayerTemplate;
import java.util.List;

/** Read boundary over the platform layer template table (org-wide). */
public interface SemanticLayerTemplateRepository {

  List<LayerTemplate> findAll();
}
