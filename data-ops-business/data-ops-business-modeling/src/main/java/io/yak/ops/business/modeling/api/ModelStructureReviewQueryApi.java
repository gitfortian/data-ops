package io.yak.ops.business.modeling.api;

import java.util.List;

/** Saved structure versus one immutable version; current mappings are never historical evidence. */
public interface ModelStructureReviewQueryApi {
  int COLUMN_LIMIT = 100;
  int INDEX_LIMIT = 20;
  int MAPPING_LIMIT = 100;
  int PAYLOAD_LIMIT = 24000;

  /** Null definition prepares a comparison; otherwise fail if any saved input has changed. */
  Context read(long modelId, int baselineVersionNo, String expectedDefinition);

  record Change(String area, String name, String before, String after) {}
  record MappingCheck(String targetColumn, boolean mappingPresent, boolean transformPresent, List<String> reasons) {}
  record Context(String projectId, String modelId, int baselineVersionNo, String baselineVersionId,
      String definition, int baselineColumnCount, int savedColumnCount,
      List<Change> changes, List<MappingCheck> mappingChecks, List<String> coverageGaps) {}
}
