package io.yak.ops.business.lineage.registration;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetDraft;
import io.yak.ops.business.lineage.registration.LineageRegistrationService.RegisterAssetCommand;
import io.yak.ops.business.lineage.repository.LineageRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Registers assets and owns asset-batch deduplication. */
@Component
public class LineageAssetRegistrar {

  private final LineageRepository repository;
  private final LineageRegistrationDraftFactory draftFactory;

  public LineageAssetRegistrar(
      LineageRepository repository, LineageRegistrationDraftFactory draftFactory) {
    this.repository = repository;
    this.draftFactory = draftFactory;
  }

  public LineageAsset register(RegisterAssetCommand command) {
    return repository.upsertAsset(draftFactory.asset(command, true));
  }

  public Map<String, LineageAsset> registerBatch(
      List<RegisterAssetCommand> commands, int batchSize) {
    LineageRegistrationDraftFactory.requireBatchSize(batchSize);
    if (commands == null || commands.isEmpty()) return Map.of();
    Map<String, LineageAssetDraft> drafts = new LinkedHashMap<>();
    for (RegisterAssetCommand command : commands) {
      LineageAssetDraft draft = draftFactory.asset(command, true);
      drafts.putIfAbsent(draft.assetKey(), draft);
    }
    return repository.upsertAssets(List.copyOf(drafts.values()), batchSize);
  }
}
