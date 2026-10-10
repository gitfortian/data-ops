package io.yak.ops.business.agent.runtime;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * CAS-backed, bounded F-039 candidate review. Candidate IDs and evidence are server-owned,
 * and revisions make stale edits/selections/preflight receipts unusable.
 * This is NOT the formal Semantic catalog or a publish/save API.
 */
public final class SourceSemanticCandidateLedger {
  private static final String KEY = "f039_candidate_review_v1";
  private final AgentStateStore store;

  public record Evidence(String tableAssetKey, String column, String sourceChunkId) {}
  public record Candidate(String id, String kind, String code, String name,
      String role, String grain, String description, Long typeId, Long unitId,
      Long reuseId, Integer reuseVersion, List<String> dependencies,
      List<Evidence> evidence) {
    public Candidate {
      Objects.requireNonNull(id); Objects.requireNonNull(kind);
      dependencies = List.copyOf(dependencies);
      evidence = List.copyOf(evidence);
      if (id.length() > 100 || evidence.isEmpty() || dependencies.size() > 12
          || name == null || name.isBlank() || name.length() > 128
          || (description != null && description.length() > 512)
          || (code != null && !code.matches("[A-Za-z0-9_]{1,64}")))
        throw new IllegalArgumentException("[F039_CANDIDATE_INVALID]");
    }
  }
  public record Review(String taskId, long projectId, String ownerId,
      String sourceFingerprint, String planSha256, Map<String,String> resultDigests,
      String catalogDigest, String skillDigest, long revision, List<Candidate> candidates,
      List<String> selectedIds, Map<String,String> answers) implements State {
    public Review {
      SourceSemanticScope.required(taskId, "taskId");
      SourceSemanticScope.required(ownerId, "ownerId");
      SourceSemanticTaskState.digest(sourceFingerprint,"sourceFingerprint");
      SourceSemanticTaskState.digest(planSha256,"planSha256");
      SourceSemanticTaskState.digest(catalogDigest,"catalogDigest");
      SourceSemanticTaskState.digest(skillDigest,"skillDigest");
      resultDigests = Map.copyOf(resultDigests);
      candidates = List.copyOf(candidates);
      selectedIds = List.copyOf(selectedIds);
      answers = Map.copyOf(answers);
      if (revision < 1 || candidates.isEmpty() || candidates.size() > 2400
          || selectedIds.size() > candidates.size()
          || new java.util.HashSet<>(candidates.stream().map(Candidate::id).toList()).size() != candidates.size()
          || !new java.util.HashSet<>(candidates.stream().map(Candidate::id).toList())
              .containsAll(selectedIds)
          || new java.util.HashSet<>(selectedIds).size() != selectedIds.size()
          || answers.size() > 100)
        throw new IllegalArgumentException("[F039_REVIEW_INVALID]");
    }
    public Review revised(List<Candidate> items, List<String> selected, Map<String,String> questions) {
      return new Review(taskId,projectId,ownerId,sourceFingerprint,planSha256,resultDigests,
          catalogDigest,skillDigest,revision+1,items,selected,questions);
    }
  }
  public SourceSemanticCandidateLedger(AgentStateStore store) {
    this.store=Objects.requireNonNull(store);
    if (!store.supportsVersioning()) throw new IllegalStateException("[F039_CAS_REQUIRED]");
  }
  public Review create(String ownerId, Review review) {
    if (!review.ownerId().equals(ownerId)) throw new IllegalArgumentException("[F039_SCOPE]");
    try {
      long saved=store.saveIfVersion(ownerId,slot(review.taskId()),KEY,review,0);
      return saved==AgentStateStore.UNVERSIONED
          ? read(ownerId,review.projectId(),review.taskId()) : review;
    } catch (RuntimeException failure) {
      throw new IllegalStateException("[F039_CANDIDATE_STORE_FAILED]",failure);
    }
  }
  public Review read(String ownerId,long projectId,String taskId) {
    var review=store.getVersioned(ownerId,slot(taskId),KEY,Review.class).value();
    if (review==null) return null;
    verify(review,ownerId,projectId,taskId);
    return review;
  }
  public Review change(String ownerId,long projectId,String taskId,long expectedRevision,
      Function<Review,Review> change) {
    for (int attempt=0;attempt<5;attempt++) {
      var old=store.getVersioned(ownerId,slot(taskId),KEY,Review.class);
      if (old.value()==null) throw new IllegalArgumentException("[F039_REVIEW_MISSING]");
      verify(old.value(),ownerId,projectId,taskId);
      if (old.value().revision()!=expectedRevision)
        throw new IllegalStateException("[F039_REVIEW_STALE]");
      Review next=change.apply(old.value());
      if (next.revision()!=old.value().revision()+1
          || !next.taskId().equals(taskId) || !next.ownerId().equals(ownerId)
          || next.projectId()!=projectId
          || !next.sourceFingerprint().equals(old.value().sourceFingerprint())
          || !next.planSha256().equals(old.value().planSha256())
          || !next.resultDigests().equals(old.value().resultDigests())
          || !next.catalogDigest().equals(old.value().catalogDigest())
          || !next.skillDigest().equals(old.value().skillDigest()))
        throw new IllegalStateException("[F039_REVIEW_IDENTITY_CHANGED]");
      try {
        if (store.saveIfVersion(ownerId,slot(taskId),KEY,next,old.version())
            != AgentStateStore.UNVERSIONED) return next;
      } catch(RuntimeException failure) {
        throw new IllegalStateException("[F039_CANDIDATE_STORE_FAILED]",failure);
      }
    }
    throw new IllegalStateException("[F039_REVIEW_CAS_CONFLICT]");
  }
  private static void verify(Review review,String ownerId,long projectId,String taskId) {
    if (!review.ownerId().equals(ownerId) || review.projectId()!=projectId
        || !review.taskId().equals(taskId))
      throw new IllegalArgumentException("[F039_REVIEW_SCOPE_MISMATCH]");
  }
  private static String slot(String taskId) {
    return "f039_candidate_"+SourceSemanticScope.required(taskId,"taskId");
  }
}
