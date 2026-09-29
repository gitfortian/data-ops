package io.yak.ops.business.development.task;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskDraftRepository;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Owns mutable task Draft persistence; the facade owns transaction and conflict translation. */
@Component
public class DevelopmentTaskDraftManager {

  private final DevelopmentTaskDraftRepository draftRepository;
  private final DevelopmentNodeRepository nodeRepository;
  private final TaskPluginRegistry pluginRegistry;

  public DevelopmentTaskDraftManager(
      DevelopmentTaskDraftRepository draftRepository,
      DevelopmentNodeRepository nodeRepository,
      TaskPluginRegistry pluginRegistry) {
    this.draftRepository = draftRepository;
    this.nodeRepository = nodeRepository;
    this.pluginRegistry = pluginRegistry;
  }

  public DevelopmentTaskDraft get(DevelopmentNode node) {
    return draftRepository.findByNodeId(node.id())
        .orElseGet(() -> emptyDraft(node));
  }

  /** Empty means the optimistic expected revision no longer matches persisted Draft state. */
  public Optional<DevelopmentTaskDraft> save(
      DevelopmentNode node,
      TaskDefinition definition,
      long expectedRevision) {
    Optional<DevelopmentTaskDraft> saved =
        draftRepository.save(node.id(), definition, expectedRevision);
    saved.ifPresent(ignored -> nodeRepository.updateConfigured(node.id(), true));
    return saved;
  }

  public DevelopmentTaskDraft lockForPublish(DevelopmentNode node) {
    return draftRepository.findByNodeIdForUpdate(node.id())
        .orElseThrow(() -> new IllegalArgumentException("节点尚未保存草稿：" + node.id()));
  }

  private DevelopmentTaskDraft emptyDraft(DevelopmentNode node) {
    int schemaVersion = pluginRegistry.find(node.type())
        .map(plugin -> plugin.descriptor().schemaVersion())
        .orElse(1);
    return new DevelopmentTaskDraft(
        node.id(),
        new TaskDefinition(node.type(), schemaVersion, "", "{}"),
        0L,
        null,
        null);
  }
}
