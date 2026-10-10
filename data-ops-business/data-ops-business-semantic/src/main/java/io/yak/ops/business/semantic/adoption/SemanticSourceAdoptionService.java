package io.yak.ops.business.semantic.adoption;

import cn.dev33.satoken.stp.StpUtil;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi.Batch;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi.Candidate;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi.Receipt;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.semantic.SourceSchemaAdoptionProof;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Distinct explicitly enabled application command, NEVER an Agent tool.
 * One item/receipt per source-domain transaction so an unrelated later failure
 * cannot roll back or duplicate a previously committed definition.
 */
@Component
@ConditionalOnProperty(prefix="yak.agent.source-semantic",name="adoption-enabled",havingValue="true")
public class SemanticSourceAdoptionService implements SemanticSourceAdoptionApi {
  private final SemanticAdoptionItemWriter writer;
  private final SourceSchemaAdoptionProof proof;
  private final CurrentProject project;
  public SemanticSourceAdoptionService(SemanticAdoptionItemWriter writer,
      SourceSchemaAdoptionProof proof,CurrentProject project) {
    this.writer=writer;this.proof=proof;this.project=project;
  }

  @Override public List<Receipt> adopt(Batch batch) {
    verifyScope(batch.projectId(),batch.userId());
    if(batch.taskId()==null||!batch.taskId().matches("[a-f0-9-]{36}")
        || batch.payloadDigest()==null||!batch.payloadDigest().matches("[a-f0-9]{64}")
        || batch.reviewRevision()<1 || batch.candidates()==null
        || batch.candidates().isEmpty()||batch.candidates().size()>200
        || batch.evidence()==null||batch.evidence().projectId()!=batch.projectId())
      throw new IllegalArgumentException("[F039_ADOPTION_INVALID_REQUEST]");
    // Source and project access must be independently rechecked in Semantic, not
    // inherited from Agent's old preflight. Permission failures abort ALL new writes.
    proof.assertCurrent(batch.evidence());
    var entries=new LinkedHashMap<String,Candidate>();
    for(Candidate candidate:batch.candidates()) {
      if(candidate==null || candidate.id()==null
          || !candidate.id().matches("c_[a-f0-9-]{36}")
          || candidate.kind()==null||candidate.dependencies()==null
          || entries.putIfAbsent(candidate.id(),candidate)!=null)
        throw new IllegalArgumentException("[F039_ADOPTION_INVALID_CANDIDATE]");
    }
    var ordered=new ArrayList<Candidate>();
    var visiting=new HashSet<String>();
    var seen=new HashSet<String>();
    for(String id:entries.keySet()) visit(id,entries,visiting,seen,ordered);
    var completed=new LinkedHashMap<String,Receipt>();
    for (Candidate candidate:ordered) {
      verifyScope(batch.projectId(),batch.userId());
      proof.assertCurrent(batch.evidence());
      if(candidate.dependencies().stream().anyMatch(id ->
          !completed.containsKey(id) || !isSuccessful(completed.get(id)))) {
        completed.put(candidate.id(),new Receipt(candidate.id(),"NOT_EXECUTED",
            null,null,candidate.kind(),"Dependency not committed"));
        continue;
      }
      // TYPE/UNIT/CODE new definitions need independent category-specific human
      // values. Never fabricate SQL type, business unit or code labels.
      if(candidate.kind().startsWith("STANDARD_") && candidate.reuseId()==null) {
        completed.put(candidate.id(),new Receipt(candidate.id(),"NOT_EXECUTED",
            null,null,candidate.kind(),"Confirmed category-specific standard details required"));
        continue;
      }
      if("SOURCE_LINK".equals(candidate.kind())||"STANDARD_CODE".equals(candidate.kind())) {
        completed.put(candidate.id(),new Receipt(candidate.id(),"NOT_EXECUTED",
            null,null,candidate.kind(),"Source-column or code-set contract not yet authorized"));
        continue;
      }
      var previous=writer.stored(batch.projectId(),batch.taskId(),candidate.id());
      if(previous!=null) {
        if(!previous.getOperatorId().equals(Long.toString(batch.userId())))
          throw new IllegalStateException("[F039_ADOPTION_RECEIPT_OWNER_CHANGED]");
        completed.put(candidate.id(),writer.replay(batch,candidate,previous));
        continue;
      }
      try {
        completed.put(candidate.id(),writer.apply(batch,candidate,completed));
      } catch(IllegalStateException invalid) {
        if(invalid.getMessage()!=null && invalid.getMessage().contains("IDEMPOTENCY_DIGEST_CONFLICT"))
          throw invalid;
        // Only a COMMITTED receipt is authoritative after a uniqueness race,
        // timeout, or lost response. Never infer rollback from an exception.
        var committed=writer.stored(batch.projectId(),batch.taskId(),candidate.id());
        if(committed!=null) {
          completed.put(candidate.id(),writer.replay(batch,candidate,committed));
        } else {
          completed.put(candidate.id(),new Receipt(candidate.id(),"NEEDS_RECONCILIATION",
              null,null,candidate.kind(),invalid.getClass().getSimpleName()));
          // An unverified write attempt halts ALL subsequent side effects.
          break;
        }
      } catch(RuntimeException failure) {
        var committed=writer.stored(batch.projectId(),batch.taskId(),candidate.id());
        if(committed!=null) {
          completed.put(candidate.id(),writer.replay(batch,candidate,committed));
        } else {
          completed.put(candidate.id(),new Receipt(candidate.id(),"NEEDS_RECONCILIATION",
              null,null,candidate.kind(),failure.getClass().getSimpleName()));
          break;
        }
      }
    }
    return List.copyOf(completed.values());
  }

  @Override public List<Receipt> receipts(String taskId) {
    Long actor=YakSecurityContext.getCurrentUserId();
    if(actor==null||actor<=0) throw new IllegalArgumentException("[F039_LOGIN_REQUIRED]");
    verifyReadScope(project.requireProjectId(),actor);
    if(taskId==null||!taskId.matches("[a-f0-9-]{36}"))
      throw new IllegalArgumentException("[F039_TASK_ID_INVALID]");
    // A foreign user in the same project is not allowed to enumerate any receipt.
    return writer.list(project.requireProjectId(),taskId,Long.toString(actor));
  }

  private void verifyReadScope(long projectId,long actor) {
    Long current=YakSecurityContext.getCurrentUserId();
    if(current==null||current!=actor||project.requireProjectId()!=projectId)
      throw new IllegalArgumentException("[F039_ADOPTION_SCOPE_MISMATCH]");
    StpUtil.checkPermission(SemanticPermissionCode.READ);
  }
  private void verifyScope(long projectId,long actor) {
    verifyReadScope(projectId,actor);
    Long current=YakSecurityContext.getCurrentUserId();
    if(current==null||current!=actor||project.requireProjectId()!=projectId)
      throw new IllegalArgumentException("[F039_ADOPTION_SCOPE_MISMATCH]");
    StpUtil.checkPermission(SemanticPermissionCode.CREATE);
  }
  private static boolean isSuccessful(Receipt receipt) {
    return receipt!=null && List.of("CREATED","REUSED","LINKED").contains(receipt.status());
  }
  private static void visit(String id,Map<String,Candidate> byId,Set<String> visiting,
      Set<String> seen,List<Candidate> result) {
    if(seen.contains(id)) return;
    Candidate item=byId.get(id);
    if(item==null||!visiting.add(id))
      throw new IllegalArgumentException("[F039_ADOPTION_MISSING_OR_CYCLIC_DEPENDENCY]");
    for(String dep:item.dependencies()) visit(dep,byId,visiting,seen,result);
    visiting.remove(id);seen.add(id);result.add(item);
  }
}
