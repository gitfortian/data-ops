package io.yak.ops.business.agent.domain;

import java.util.List;

/** Local human-command port: no Semantic DTO/DAO leaks into Agent conversation. */
public interface SourceSemanticAdoption {
  record Candidate(String id,String kind,String code,String name,String role,String grain,
      String description,Long typeId,Long unitId,Long reuseId,Integer reuseVersion,
      List<String> dependencies) {
    public Candidate { dependencies=List.copyOf(dependencies); }
  }
  record Scope(long projectId,long dataSourceId,String captureId,
      String evidenceFingerprint,List<String> tableKeys) {
    public Scope { tableKeys=List.copyOf(tableKeys); }
  }
  record Batch(String taskId,long projectId,long userId,long revision,
      String payloadDigest,Scope scope,List<Candidate> candidates) {
    public Batch { candidates=List.copyOf(candidates); }
  }
  record Receipt(String candidateId,String status,Long semanticId,Integer semanticVersion,
      String kind,String message) {}
  List<Receipt> adopt(Batch batch);
  List<Receipt> receipts(String taskId);
}
