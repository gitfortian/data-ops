package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.runtime.SourceSemanticCandidateLedger;
import io.yak.ops.business.agent.runtime.SourceSemanticCandidateLedger.Candidate;
import io.yak.ops.business.agent.runtime.SourceSemanticCandidateLedger.Evidence;
import io.yak.ops.business.agent.runtime.SourceSemanticCandidateLedger.Review;
import io.yak.ops.business.agent.runtime.SourceSemanticStateBridge;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskState;
import io.yak.ops.business.semantic.api.SemanticCandidateCatalogApi;
import io.yak.ops.business.semantic.api.SemanticCandidateCatalogApi.Entry;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * F-039 (3/5): source-grounded, human-reviewed candidates with zero business writes.
 * Business definitions are never inferred from column names as facts. Every candidate
 * starts unselected and incomplete; Semantic performs the catalog lookup itself.
 */
@Service
@ConditionalOnAgentEnabled
@ConditionalOnProperty(prefix = "yak.agent.source-semantic", name = "enabled", havingValue = "true")
public class SourceSemanticCandidateService {
  private final SourceSemanticTaskFacade sources;
  private final SemanticCandidateCatalogApi catalog;
  private final SourceSemanticCandidateLedger reviews;

  public record Edit(long expectedRevision, String candidateId, String code, String name,
      String role, String grain, String description, Long typeId, Long unitId,
      Long reuseId, Integer reuseVersion) {}
  public record Selection(long expectedRevision, List<String> ids) {}
  public record Answer(long expectedRevision, String questionId, String value) {}
  public record Match(String candidateId, List<Entry> matches, boolean ambiguous) {}
  public record View(Review review, List<Match> matches) {}
  public record Preflight(long revision, String payloadDigest, String ticket,
      List<String> selected, List<String> closure, List<String> blockers, boolean ready) {}

  public SourceSemanticCandidateService(SourceSemanticTaskFacade sources,
      SemanticCandidateCatalogApi catalog, SourceSemanticStateBridge state) {
    this.sources = sources;
    this.catalog = catalog;
    this.reviews = state.candidates();
  }

  public View read(String taskId) {
    var input = sources.verifiedCandidateInput(taskId);
    var task = input.task();
    var currentCatalog = catalog.read();
    verifyProject(task,currentCatalog);
    Review review = reviews.read(task.ownerId(),task.projectId(),taskId);
    if (review==null) {
      Review proposed = new Review(taskId,task.projectId(),task.ownerId(),
          task.scopeFingerprint(),task.planSha256(),task.resultDigests(),
          digestCatalog(currentCatalog),1,generate(input),List.of(),Map.of());
      review=reviews.create(task.ownerId(),proposed);
    }
    verifySource(review,task);
    return new View(review, match(review,currentCatalog));
  }

  public View edit(String taskId, Edit change) {
    var input = sources.verifiedCandidateInput(taskId);
    var task = input.task();
    var snapshot = read(taskId).review();
    // Evidence/dependencies/kind and server-assigned IDs are immutable even under edits.
    reviews.change(task.ownerId(),task.projectId(),taskId,change.expectedRevision(),old -> {
      verifySource(old,task);
      var changed = new ArrayList<Candidate>();
      boolean found=false;
      for (Candidate item : old.candidates()) {
        if (item.id().equals(change.candidateId())) {
          found=true;
          changed.add(new Candidate(item.id(),item.kind(),change.code(),
              change.name(),change.role(),change.grain(),change.description(),
              change.typeId(),change.unitId(),change.reuseId(),change.reuseVersion(),
              item.dependencies(),item.evidence()));
        } else changed.add(item);
      }
      if (!found) throw new IllegalArgumentException("[F039_CANDIDATE_ID_UNKNOWN]");
      return old.revised(changed,old.selectedIds(),old.answers());
    });
    return read(taskId);
  }

  public View select(String taskId, Selection selection) {
    var task = sources.verifiedCandidateInput(taskId).task();
    read(taskId);
    if (selection.ids()==null || selection.ids().size()>1200
        || new HashSet<>(selection.ids()).size()!=selection.ids().size())
      throw new IllegalArgumentException("[F039_SELECTION_INVALID]");
    reviews.change(task.ownerId(),task.projectId(),taskId,selection.expectedRevision(),old -> {
      verifySource(old,task);
      var known = old.candidates().stream().map(Candidate::id).collect(java.util.stream.Collectors.toSet());
      if (!known.containsAll(selection.ids()))
        throw new IllegalArgumentException("[F039_SELECTION_UNKNOWN]");
      return old.revised(old.candidates(),selection.ids(),old.answers());
    });
    return read(taskId);
  }

  /** Shared business answers always invalidate older selections/preflight via CAS revision. */
  public View answer(String taskId, Answer answer) {
    var task=sources.verifiedCandidateInput(taskId).task();
    read(taskId);
    if (answer.questionId()==null || !answer.questionId().matches("[A-Za-z0-9_.:-]{1,100}")
        || answer.value()==null || answer.value().isBlank() || answer.value().length()>512)
      throw new IllegalArgumentException("[F039_ANSWER_INVALID]");
    reviews.change(task.ownerId(),task.projectId(),taskId,answer.expectedRevision(),old -> {
      verifySource(old,task);
      var answers=new HashMap<>(old.answers());
      answers.put(answer.questionId(),answer.value());
      return old.revised(old.candidates(),old.selectedIds(),answers);
    });
    return read(taskId);
  }

  /** Pure read-only preflight; NO Semantic create/update or source SQL. */
  public Preflight preflight(String taskId,long expectedRevision) {
    var task=sources.verifiedCandidateInput(taskId).task();
    var current=catalog.read();
    verifyProject(task,current);
    var review=reviews.read(task.ownerId(),task.projectId(),taskId);
    if (review==null) throw new IllegalStateException("[F039_REVIEW_MISSING]");
    verifySource(review,task);
    if (review.revision()!=expectedRevision)
      throw new IllegalStateException("[F039_REVIEW_STALE]");
    var errors=new LinkedHashSet<String>();
    if (!review.catalogDigest().equals(digestCatalog(current)))
      errors.add("[F039_SEMANTIC_CATALOG_CHANGED]");
    var byId=new LinkedHashMap<String,Candidate>();
    review.candidates().forEach(c -> byId.put(c.id(),c));
    var closure=new LinkedHashSet<String>();
    var visiting=new HashSet<String>();
    for(String id:review.selectedIds()) visit(id,byId,closure,visiting,errors);
    // Dependencies are visible, never silently included in a save selection.
    for(String required:closure)
      if(!review.selectedIds().contains(required))
        errors.add("[F039_DEPENDENCY_NOT_SELECTED] "+required);
    for (String id:review.selectedIds()) {
      Candidate c=byId.get(id);
      if (c==null) { errors.add("[F039_UNKNOWN_SELECTION] "+id); continue; }
      validate(c,current,errors);
    }
    var normalized=review.selectedIds().stream().sorted().toList();
    var bindings=new ArrayList<String>();
    bindings.add(taskId);bindings.add(review.sourceFingerprint());
    bindings.add(review.planSha256());bindings.add(review.catalogDigest());
    bindings.add(Long.toString(review.revision()));
    review.resultDigests().entrySet().stream().sorted(Map.Entry.comparingByKey())
        .forEach(e -> {bindings.add(e.getKey());bindings.add(e.getValue());});
    for(String id:normalized) {
      Candidate c=byId.get(id);
      if (c!=null) {
        bindings.add(c.id());bindings.add(c.kind());bindings.add(Objects.toString(c.code(),""));
        bindings.add(Objects.toString(c.name(),""));bindings.add(Objects.toString(c.role(),""));
        bindings.add(Objects.toString(c.grain(),""));bindings.add(Objects.toString(c.description(),""));
        bindings.add(Objects.toString(c.typeId(),""));bindings.add(Objects.toString(c.unitId(),""));
        bindings.add(Objects.toString(c.reuseId(),""));bindings.add(Objects.toString(c.reuseVersion(),""));
        bindings.addAll(c.dependencies().stream().sorted().toList());
      }
    }
    String payload=digest(bindings);
    // Ticket is only a preview binding for 4/5; it authorizes no write.
    String ticket=errors.isEmpty()?digest(List.of("F039_PREFLIGHT_READ_ONLY",payload,
        Long.toString(task.projectId()),task.ownerId())):null;
    if (normalized.isEmpty()) errors.add("[F039_NOTHING_SELECTED]");
    return new Preflight(review.revision(),payload,ticket,normalized,
        List.copyOf(closure),List.copyOf(errors),errors.isEmpty());
  }

  private static void visit(String id,Map<String,Candidate> all,Set<String> closure,
      Set<String> visiting,Set<String> errors) {
    if(closure.contains(id)) return;
    Candidate item=all.get(id);
    if(item==null) { errors.add("[F039_UNKNOWN_DEPENDENCY] "+id);return; }
    if(!visiting.add(id)) { errors.add("[F039_CYCLIC_DEPENDENCY] "+id);return; }
    for(String dep:item.dependencies()) visit(dep,all,closure,visiting,errors);
    visiting.remove(id);
    closure.add(id);
  }

  private static void validate(Candidate c,SemanticCandidateCatalogApi.Snapshot catalog,Set<String> errors) {
    String tag=" "+c.id();
    boolean codeNeeded=!("PROCESS_FIELD".equals(c.kind())||"SOURCE_LINK".equals(c.kind()));
    if (codeNeeded&&(c.code()==null||!c.code().matches("[A-Za-z0-9_]{1,64}")))
      errors.add("[F039_CODE_REQUIRED]"+tag);
    if ("PROCESS".equals(c.kind()) &&
        (c.grain()==null||c.grain().isBlank()||
            !List.of("FACT","DIMENSION").contains(c.role())))
      errors.add("[F039_PROCESS_GRAIN_OR_TYPE_UNKNOWN]"+tag);
    if ("FIELD".equals(c.kind())) {
      if (!List.of("PROCESS","DIMENSION","METRIC").contains(c.role()))
        errors.add("[F039_FIELD_ROLE_UNKNOWN]"+tag);
      if (c.typeId()==null || catalog.entries().stream().noneMatch(e ->
            "TYPE".equals(e.kind()) && "ENABLED".equals(e.status())
            && c.typeId().equals(e.id())))
        errors.add("[F039_ENABLED_TYPE_REQUIRED]"+tag);
      if ("METRIC".equals(c.role())&&(c.unitId()==null ||
          catalog.entries().stream().noneMatch(e ->
              "UNIT".equals(e.kind()) && "ENABLED".equals(e.status())
              && c.unitId().equals(e.id()))))
        errors.add("[F039_METRIC_UNIT_REQUIRED]"+tag);
      if (!"METRIC".equals(c.role())&&c.unitId()!=null)
        errors.add("[F039_UNIT_NOT_APPLICABLE]"+tag);
    }
    if ("STANDARD_TYPE".equals(c.kind()) || "STANDARD_UNIT".equals(c.kind())
        || "STANDARD_CODE".equals(c.kind()))
      errors.add("[F039_NEW_STANDARD_REQUIRES_SEMANTIC_REVIEW]"+tag);
    if (c.reuseId()!=null) {
      String matchKind=switch(c.kind()) {
        case "STANDARD_TYPE"->"TYPE"; case "STANDARD_UNIT"->"UNIT";
        case "STANDARD_CODE"->"CODE";default->c.kind();};
      boolean found=catalog.entries().stream().anyMatch(e -> matchKind.equals(e.kind())
          && c.reuseId().equals(e.id()) && c.name().equals(e.name())
          && Objects.equals(c.code(),e.code())
          && (c.reuseVersion()==null||c.reuseVersion()==e.version())
          && (!List.of("FIELD","TYPE","UNIT","CODE").contains(matchKind)
              || "ENABLED".equals(e.status())));
      if (!found) errors.add("[F039_REUSE_NOT_VERIFIED]"+tag);
    } else if(codeNeeded && c.code()!=null) {
      String kind = c.kind().startsWith("STANDARD_")?c.kind().substring(9):c.kind();
      if(catalog.entries().stream().anyMatch(e -> kind.equals(e.kind())
          && c.code().equalsIgnoreCase(Objects.toString(e.code(),""))))
        errors.add("[F039_CODE_CONFLICT]"+tag);
    }
  }

  private static List<Candidate> generate(SourceSemanticTaskFacade.CandidateInput input) {
    var task=input.task();
    var items=new ArrayList<Candidate>();
    var tableMap=new HashMap<String,io.yak.ops.business.metadata.api.PhysicalScopeEvidenceQueryApi.Table>();
    input.evidence().tables().forEach(t->tableMap.put(t.assetKey(),t));
    String domainId=id(task.taskId(),"DOMAIN","","");
    var any=task.sourceManifest().tables().get(0);
    items.add(new Candidate(domainId,"DOMAIN",null,"待确认业务域",null,null,
        "请人工命名并选择复用或新增",null,null,null,null,List.of(),
        List.of(new Evidence(any.assetKey(),null,task.chunkIds().get(0)))));
    for(var table:task.sourceManifest().tables()) {
      var physical=tableMap.get(table.assetKey());
      if (physical==null) throw new IllegalStateException("[F039_TABLE_NOT_COLLECTED]");
      String processId=id(task.taskId(),"PROCESS",table.assetKey(),"");
      String chunk=chunkFor(task,table.assetKey(),table.columns().get(0));
      items.add(new Candidate(processId,"PROCESS",null,physical.name(),null,null,
          "物理表只支持提出过程假设；业务过程类型和粒度须人工确认",null,null,null,null,
          List.of(domainId),List.of(new Evidence(table.assetKey(),null,chunk))));
      for(String col:table.columns()) {
        if(items.size()+3>1200) throw new IllegalStateException("[F039_CANDIDATES_TOO_MANY]");
        var field=physical.columns().stream().filter(f->col.equals(f.name())).findFirst()
            .orElseThrow(()->new IllegalStateException("[F039_COLUMN_NOT_COLLECTED]"));
        String evidenceChunk=chunkFor(task,table.assetKey(),col);
        String fieldId=id(task.taskId(),"FIELD",table.assetKey(),col);
        String bindingId=id(task.taskId(),"PROCESS_FIELD",table.assetKey(),col);
        String linkId=id(task.taskId(),"SOURCE_LINK",table.assetKey(),col);
        var evidence=List.of(new Evidence(table.assetKey(),col,evidenceChunk));
        items.add(new Candidate(fieldId,"FIELD",null,col,null,null,
            "SQL类型="+field.dataType()+"；注释="+Objects.toString(field.comment(),"")+
            "。业务含义、TYPE与角色均待审阅",null,null,null,null,List.of(),evidence));
        items.add(new Candidate(bindingId,"PROCESS_FIELD",null,col,null,null,
            "待确认的过程字段关联",null,null,null,null,List.of(processId,fieldId),evidence));
        items.add(new Candidate(linkId,"SOURCE_LINK",null,col,null,null,
            "来源列到过程字段的证据关联（非推测血缘）",null,null,null,null,
            List.of(bindingId),evidence));
      }
    }
    return List.copyOf(items);
  }

  private static String chunkFor(SourceSemanticTaskState task,String table,String column) {
    return task.frozenChunks().stream().filter(c->c.slices().stream().anyMatch(s->
        s.tableAssetKey().equals(table) && s.columns().contains(column)))
        .map(c->c.id()).findFirst()
        .orElseThrow(()->new IllegalStateException("[F039_CHUNK_EVIDENCE_NOT_FOUND]"));
  }
  private static String id(String taskId,String kind,String table,String column) {
    return "c_"+UUID.nameUUIDFromBytes((taskId+"|"+kind+"|"+table+"|"+column)
        .getBytes(StandardCharsets.UTF_8)).toString();
  }
  private static List<Match> match(Review review,SemanticCandidateCatalogApi.Snapshot catalog) {
    return review.candidates().stream().map(c -> {
      String kind=c.kind().startsWith("STANDARD_")?c.kind().substring(9):c.kind();
      var candidates=catalog.entries().stream().filter(e->kind.equals(e.kind())
          && (c.name().equalsIgnoreCase(e.name()) || (c.code()!=null &&
              c.code().equalsIgnoreCase(Objects.toString(e.code(),"")))))
          .limit(5).toList();
      return new Match(c.id(),candidates,candidates.size()>1);
    }).toList();
  }
  private static void verifySource(Review review,SourceSemanticTaskState task) {
    if (review.projectId()!=task.projectId()||!review.ownerId().equals(task.ownerId())
        || !review.sourceFingerprint().equals(task.scopeFingerprint())
        || !review.planSha256().equals(task.planSha256())
        || !review.resultDigests().equals(task.resultDigests()))
      throw new IllegalStateException("[F039_REVIEW_SOURCE_CHANGED]");
  }
  private static void verifyProject(SourceSemanticTaskState task,SemanticCandidateCatalogApi.Snapshot catalog) {
    if (task.projectId()!=catalog.projectId()||!catalog.complete())
      throw new IllegalStateException("[F039_CATALOG_INCOMPLETE]");
  }
  private static String digestCatalog(SemanticCandidateCatalogApi.Snapshot catalog) {
    var parts=new ArrayList<String>();
    parts.add(Long.toString(catalog.projectId()));
    for(var e:catalog.entries()) {
      parts.add(e.kind());parts.add(Objects.toString(e.id(),""));
      parts.add(Integer.toString(e.version()));parts.add(Objects.toString(e.code(),""));
      parts.add(Objects.toString(e.name(),""));parts.add(Objects.toString(e.status(),""));
      parts.add(Objects.toString(e.role(),""));parts.add(Objects.toString(e.typeId(),""));
      parts.add(Objects.toString(e.unitId(),""));
    }
    return digest(parts);
  }
  private static String digest(List<String> parts) {
    try {
      var buffer=new ByteArrayOutputStream();
      try(var writer=new DataOutputStream(buffer)) {
        for(var part:parts) {
          byte[] bytes=part.getBytes(StandardCharsets.UTF_8);
          writer.writeInt(bytes.length);writer.write(bytes);
        }
      }
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(buffer.toByteArray()));
    } catch (Exception broken) {
      throw new IllegalStateException("[F039_DIGEST_UNAVAILABLE]",broken);
    }
  }
}
