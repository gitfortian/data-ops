package io.yak.ops.business.modeling.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.repository.ModelVersionRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelPublishedStructureReaderTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private ModelRepository modelRepository;
  private ModelVersionRepository versionRepository;
  private ModelVersionService versionService;
  private ModelPublishedStructureReader reader;

  @BeforeEach
  void setUp() {
    modelRepository = mock(ModelRepository.class);
    versionRepository = mock(ModelVersionRepository.class);
    versionService = mock(ModelVersionService.class);
    reader = new ModelPublishedStructureReader(modelRepository, versionRepository, versionService);
  }

  @Test
  void publishedModelAlwaysReadsSnapshotEvenWithNewerDraft() throws Exception {
    when(modelRepository.findById(1L))
        .thenReturn(Optional.of(model(ModelStatus.DRAFT, 9L)));
    when(versionRepository.findById(9L, 1L)).thenReturn(Optional.of(snapshot("v1_table")));

    StructureView structure = reader.publishedStructure(1L);

    assertThat(structure.tableName()).isEqualTo("v1_table");
    assertThat(structure.modelId()).isEqualTo(1L);
    verify(versionService, never()).liveStructure(any());
  }

  @Test
  void publishedModelWithoutVersionsSelfHealsWithBackfillV1() throws Exception {
    when(modelRepository.findById(1L))
        .thenReturn(Optional.of(model(ModelStatus.PUBLISHED, null)))
        .thenReturn(Optional.of(model(ModelStatus.PUBLISHED, 9L)));
    when(versionService.publish(1L, "system:legacy-backfill")).thenReturn(null);
    when(versionRepository.findById(9L, 1L)).thenReturn(Optional.of(snapshot("legacy_v1")));

    StructureView structure = reader.publishedStructure(1L);

    assertThat(structure.tableName()).isEqualTo("legacy_v1");
    verify(versionService).publish(1L, "system:legacy-backfill");
    verify(versionService, never()).liveStructure(any());
  }

  @Test
  void neverPublishedDraftFallsBackToLiveStructure() {
    when(modelRepository.findById(1L))
        .thenReturn(Optional.of(model(ModelStatus.DRAFT, null)));
    when(versionService.liveStructure(1L)).thenReturn(live("draft_table"));

    assertThat(reader.publishedStructure(1L).tableName()).isEqualTo("draft_table");
  }

  @Test
  void danglingPointerHealsByRepublishing() throws Exception {
    when(modelRepository.findById(1L))
        .thenReturn(Optional.of(model(ModelStatus.PUBLISHED, 404L)))
        .thenReturn(Optional.of(model(ModelStatus.PUBLISHED, 9L)));
    when(versionRepository.findById(404L, 1L)).thenReturn(Optional.empty());
    when(versionRepository.findById(9L, 1L)).thenReturn(Optional.of(snapshot("healed")));

    StructureView structure = reader.publishedStructure(1L);

    assertThat(structure.tableName()).isEqualTo("healed");
    verify(versionService).publish(1L, "system:legacy-backfill");
  }

  @Test
  void publishedColumnsCarryFullColumnDefinitionShape() throws Exception {
    when(modelRepository.findById(1L))
        .thenReturn(Optional.of(model(ModelStatus.DRAFT, 9L)));
    StructureView snapshot = new StructureView(
        null, null, null, null, null, null, "t", null,
        List.of(new StructureView.ColumnView(3L, "amount", "DECIMAL", 18, 2, false,
            "0.00", "金额", "指标金额", 1, null, null, null, null, 7L, null, null,
            "MEASURE", "SUM", null)),
        List.of(), List.of(), new StructureView.PartitionView(null, List.of(), null), Map.of());
    when(versionRepository.findById(9L, 1L)).thenReturn(Optional.of(
        new ModelVersion(9L, 1L, 2, JSON.writeValueAsString(snapshot), null,
            1, "sum", "alice", LocalDateTime.now())));

    var columns = reader.publishedColumns(1L);

    assertThat(columns).hasSize(1);
    assertThat(columns.get(0).columnName()).isEqualTo("amount");
    assertThat(columns.get(0).stdCaliberId()).isEqualTo(7L);
    assertThat(columns.get(0).aggregateFunc()).isEqualTo("SUM");
  }

  private Model model(ModelStatus status, Long publishedVersionId) {
    Model model = Model.create("trade_order", "订单", ModelDialect.MYSQL, null)
        .withPersisted(1L, "alice", null, null);
    return model.withPublishState(status, publishedVersionId,
        publishedVersionId == null ? 0 : 2);
  }

  private ModelVersion snapshot(String tableName) throws Exception {
    StructureView view = live(tableName);
    return new ModelVersion(
        9L, 1L, 2, JSON.writeValueAsString(view), null,
        view.columns() == null ? 0 : view.columns().size(), "sum", "alice", LocalDateTime.now());
  }

  private StructureView live(String tableName) {
    return new StructureView(
        1L, "trade_order", "订单", "MYSQL", "PUBLISHED", null,
        tableName, null, List.of(), List.of(), List.of(),
        new StructureView.PartitionView(null, List.of(), null), Map.of());
  }
}
