package io.yak.ops.business.dataset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** JSON contract checks for Dataset field snapshots and editor drafts. */
class DevelopmentDatasetFacadeJsonTest {

  private final ObjectMapper mapper =
      new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

  @Test
  void fieldSnapshotOmitsNullDescriptionWithNonNullInclusion() throws Exception {
    DevelopmentDatasetFacade.FieldSnapshot snapshot =
        new DevelopmentDatasetFacade.FieldSnapshot(
            "f-1", "order_id", "order_id", "STRING", false, null, "DIMENSION", 1);

    String json = mapper.writeValueAsString(snapshot);

    assertFalse(json.contains("\"description\""), json);
    assertFalse(json.contains("\"comment\""), json);
  }

  @Test
  void fieldDraftOmitsNullDescriptionWithNonNullInclusion() throws Exception {
    DevelopmentDatasetFacade.FieldDraft draft =
        new DevelopmentDatasetFacade.FieldDraft(
            null, "order_id", "order_id", "STRING", false, null, "DIMENSION");

    String json = mapper.writeValueAsString(draft);

    assertFalse(json.contains("\"description\""), json);
    assertFalse(json.contains("\"comment\""), json);
  }

  @Test
  void fieldSnapshotSerializesDescriptionValue() throws Exception {
    DevelopmentDatasetFacade.FieldSnapshot snapshot =
        new DevelopmentDatasetFacade.FieldSnapshot(
            "f-1", "order_id", "order_id", "STRING", false, "订单ID", "DIMENSION", 1);

    String json = mapper.writeValueAsString(snapshot);

    assertTrue(json.contains("\"description\":\"订单ID\""), json);
    assertFalse(json.contains("\"comment\""), json);
  }
}
