package io.yak.ops.business.semantic.api;

import io.yak.ops.spi.semantic.SourceSchemaAdoptionProof;
import java.util.List;

/**
 * Semantic-owned F-039 independent human-command boundary. NOT an LLM tool.
 * The caller is the authorized Agent application handoff, never untrusted JSON object IDs.
 * Formal writes/approval/idempotency and original Semantic validation stay here.
 */
public interface SemanticSourceAdoptionApi {
  record Candidate(String id, String kind, String code, String name, String role,
      String grain, String description, Long typeId, Long unitId,
      Long reuseId, Integer reuseVersion, List<String> dependencies,String sourceAssetKey) {
    public Candidate { dependencies = List.copyOf(dependencies); }
    public Candidate(String id,String kind,String code,String name,String role,
        String grain,String description,Long typeId,Long unitId,Long reuseId,
        Integer reuseVersion,List<String> dependencies) {
      this(id,kind,code,name,role,grain,description,typeId,unitId,reuseId,
          reuseVersion,dependencies,null);
    }
  }
  record Batch(String taskId, long projectId, long userId, long reviewRevision,
      String payloadDigest, SourceSchemaAdoptionProof.Expected evidence,
      List<Candidate> candidates) {
    public Batch { candidates = List.copyOf(candidates); }
  }
  record Receipt(String candidateId, String status, Long semanticId,
      Integer semanticVersion, String kind, String message) {}

  /** One dependency-ordered item per transaction. No implicit selection or auto-approval. */
  List<Receipt> adopt(Batch request);
  /** Read only Semantic's committed receipt truth; result may be empty. */
  List<Receipt> receipts(String taskId);
}
