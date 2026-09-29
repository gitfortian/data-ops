package io.yak.ops.business.lineage.registration;

import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationDraft;
import io.yak.ops.business.lineage.registration.LineageRegistrationService.RegisterRelationCommand;
import io.yak.ops.business.lineage.repository.LineageRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Registers relations and owns relation-identity deduplication for batches. */
@Component
public class LineageRelationRegistrar {

  private final LineageRepository repository;
  private final LineageRegistrationDraftFactory draftFactory;

  public LineageRelationRegistrar(
      LineageRepository repository, LineageRegistrationDraftFactory draftFactory) {
    this.repository = repository;
    this.draftFactory = draftFactory;
  }

  public LineageRelation register(RegisterRelationCommand command) {
    return repository.upsertRelation(draftFactory.relation(command, true));
  }

  public void registerBatch(List<RegisterRelationCommand> commands, int batchSize) {
    LineageRegistrationDraftFactory.requireBatchSize(batchSize);
    if (commands == null || commands.isEmpty()) return;
    Map<String, LineageRelationDraft> drafts = new LinkedHashMap<>();
    for (RegisterRelationCommand command : commands) {
      LineageRelationDraft draft = draftFactory.relation(command, true);
      String identity =
          draft.sourceAssetId()
              + "\u0000"
              + draft.targetAssetId()
              + "\u0000"
              + draft.relationType()
              + "\u0000"
              + draft.sourceType()
              + "\u0000"
              + draft.sourceId()
              + "\u0000"
              + draft.version();
      drafts.putIfAbsent(identity, draft);
    }
    repository.upsertRelations(List.copyOf(drafts.values()), batchSize);
  }
}
