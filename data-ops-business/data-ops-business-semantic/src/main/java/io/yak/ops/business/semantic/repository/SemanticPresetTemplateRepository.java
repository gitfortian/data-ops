package io.yak.ops.business.semantic.repository;

import io.yak.ops.business.semantic.preset.PresetTemplate;
import java.util.List;

/** Read boundary over the platform preset template table (org-wide, no project scope). */
public interface SemanticPresetTemplateRepository {

  /** All templates ordered by kind then sort order. */
  List<PresetTemplate> findAll();
}
