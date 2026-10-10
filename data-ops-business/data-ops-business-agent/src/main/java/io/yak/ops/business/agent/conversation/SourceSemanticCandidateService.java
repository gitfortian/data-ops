package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.runtime.SourceSemanticCandidateLedger;
import io.yak.ops.business.agent.runtime.SourceSemanticCandidateLedger.Candidate;
import io.yak.ops.business.agent.runtime.SourceSemanticCandidateLedger.Evidence;
import io.yak.ops.business.agent.runtime.SourceSemanticCandidateLedger.Review;
import io.yak.ops.business.agent.runtime.SourceSemanticStateBridge;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskState;
import io.yak.ops.business.agent.domain.SourceSemanticCatalog;
import io.yak.ops.business.agent.domain.SourceSemanticCatalog.Entry;
import io.yak.ops.business.agent.domain.SourceSemanticAdoption;
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
import org.springframework.stereotype.Component;

/**
 * F-039 (3/5): source-grounded, human-reviewed candidates with zero business writes.
 * Business definitions are never inferred from column names as facts. Every candidate
 * starts unselected and incomplete; Semantic performs the catalog lookup itself.
 */
@Component
@ConditionalOnAgentEnabled
@ConditionalOnProperty(prefix = "yak.agent.source-semantic", name = "enabled", havingValue = "true")
public class SourceSemanticCandidateService {
  private final SourceSemanticTaskFacade sources;
  private final SourceSemanticCatalog catalog;
  private final AgentSkillManageService skills;
  private final SourceSemanticCandidateLedger reviews;
  private final SourceSemanticAdoption adoption;

  public record Edit(long expectedRevision, String candidateId, String code, String name,
      String role, String grain, String description, Long typeId, Long unitId,
      Long reuseId, Integer reuseVersion) {}
  public record Selection(long expectedRevision, List<String> ids) {}
  public record Answer(long expectedRevision, String questionId, String value) {}
  public record Merge(long expectedRevision, String firstId, String secondId, boolean confirmedSameMeaning) {}
  public record Split(long expectedRevision, String candidateId, String tableAssetKey, String column) {}
  public record Match(String candidateId, List<Entry> matches, boolean ambiguous) {}
  public record SaveRequest(long revision,String payloadDigest,String preflightTicket,
      boolean confirmed) {}
  public record View(Review review, List<Match> matches, List<Entry> catalogEntries) {}
  public record Preflight(long revision, String payloadDigest, String ticket,
      List<String> selected, List<String> closure, List<String> blockers, boolean ready) {}

  @org.springframework.beans.factory.annotation.Autowired
  public SourceSemanticCandidateService(SourceSemanticTaskFacade sources,
      SourceSemanticCatalog catalog, AgentSkillManageService skills,
      SourceSemanticStateBridge state, SourceSemanticAdoption adoption) {
    this.sources=sources;this.catalog=catalog;this.skills=skills;
    this.reviews=state.candidates();this.adoption=adoption;
  }
  /** Source-only legacy unit tests: never enabled as an adoption command. */
  SourceSemanticCandidateService(SourceSemanticTaskFacade sources,
      SourceSemanticCatalog catalog, AgentSkillManageService skills,
      SourceSemanticStateBridge state) {
    this(sources,catalog,skills,state,null);
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
          digestCatalog(currentCatalog),digestSkills(),1,generate(input),List.of(),Map.of());
      review=reviews.create(task.ownerId(),proposed);
    }
    verifySource(review,task);
    return new View(review, match(review,currentCatalog),currentCatalog.entries());
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
    if (selection.ids()==null || selection.ids().size()>1600
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

  /** Human-confirmed cross-table concept merge, NEVER an automatic name-based merge. */
  public View merge(String taskId, Merge request) {
    var task=sources.verifiedCandidateInput(taskId).task();
    read(taskId);
    if (!request.confirmedSameMeaning() || Objects.equals(request.firstId(),request.secondId()))
      throw new IllegalArgumentException("[F039_MERGE_REQUIRES_EXPLICIT_CONFIRMATION]");
    reviews.change(task.ownerId(),task.projectId(),taskId,request.expectedRevision(),old -> {
      verifySource(old,task);
      var first=old.candidates().stream().filter(c->c.id().equals(request.firstId()))
          .findFirst().orElseThrow(()->new IllegalArgumentException("[F039_MERGE_ID_UNKNOWN]"));
      var second=old.candidates().stream().filter(c->c.id().equals(request.secondId()))
          .findFirst().orElseThrow(()->new IllegalArgumentException("[F039_MERGE_ID_UNKNOWN]"));
      if (!"FIELD".equals(first.kind()) || !"FIELD".equals(second.kind())
          || old.selectedIds().contains(first.id()) || old.selectedIds().contains(second.id()))
        throw new IllegalArgumentException("[F039_MERGE_NOT_ALLOWED]");
      var union=new LinkedHashSet<Evidence>(first.evidence());
      union.addAll(second.evidence());
      if(union.size()!=first.evidence().size()+second.evidence().size())
        throw new IllegalArgumentException("[F039_DUPLICATE_EVIDENCE]");
      // Reset business decisions; confirmation of equivalence is not approval of role/type/units.
      var merged=new Candidate(first.id(),"FIELD",null,first.name(),null,null,
          "用户已合并多个来源字段；业务角色、TYPE与编码须重新确认",null,null,null,null,
          List.of(),List.copyOf(union));
      var newItems=new ArrayList<Candidate>();
      for(var item:old.candidates()) {
        if(item.id().equals(second.id())) continue;
        if(item.id().equals(first.id())) {newItems.add(merged);continue;}
        if(item.dependencies().contains(second.id())) {
          var deps=item.dependencies().stream().map(d->
              d.equals(second.id())?first.id():d).distinct().toList();
          newItems.add(new Candidate(item.id(),item.kind(),item.code(),item.name(),
              item.role(),item.grain(),item.description(),item.typeId(),item.unitId(),
              item.reuseId(),item.reuseVersion(),deps,item.evidence()));
        } else newItems.add(item);
      }
      return old.revised(newItems,List.of(),old.answers());
    });
    return read(taskId);
  }

  /** Split ONE preserved source-column witness from a previously merged concept. */
  public View split(String taskId, Split request) {
    var task=sources.verifiedCandidateInput(taskId).task();
    read(taskId);
    reviews.change(task.ownerId(),task.projectId(),taskId,request.expectedRevision(),old -> {
      verifySource(old,task);
      var source=old.candidates().stream().filter(c->c.id().equals(request.candidateId()))
          .findFirst().orElseThrow(()->new IllegalArgumentException("[F039_SPLIT_ID_UNKNOWN]"));
      if(!"FIELD".equals(source.kind()) || source.evidence().size()<2
          || old.selectedIds().contains(source.id()))
        throw new IllegalArgumentException("[F039_SPLIT_NOT_ALLOWED]");
      var witnesses=source.evidence().stream().filter(e->
          e.tableAssetKey().equals(request.tableAssetKey())
              && Objects.equals(e.column(),request.column())).toList();
      if(witnesses.size()!=1) throw new IllegalArgumentException("[F039_SPLIT_EVIDENCE_MISSING]");
      var chosen=witnesses.get(0);
      String newId=id(taskId,"SPLIT_FIELD",chosen.tableAssetKey(),
          Objects.toString(chosen.column(),"")+":"+old.revision());
      var newItems=new ArrayList<Candidate>();
      for(var item:old.candidates()) {
        if(item.id().equals(source.id())) {
          var remaining=source.evidence().stream().filter(e->!e.equals(chosen)).toList();
          newItems.add(new Candidate(source.id(),"FIELD",null,source.name(),null,null,
              "拆分后需重新核对业务含义与TYPE",null,null,null,null,List.of(),remaining));
          newItems.add(new Candidate(newId,"FIELD",null,
              Objects.toString(chosen.column(),"待核对字段"),null,null,
              "来源证据由人工拆分；请明确业务角色和TYPE",null,null,null,null,
              List.of(),List.of(chosen)));
        } else if ("PROCESS_FIELD".equals(item.kind())
            && item.dependencies().contains(source.id())
            && item.evidence().contains(chosen)) {
          var deps=item.dependencies().stream().map(d->
              d.equals(source.id())?newId:d).toList();
          newItems.add(new Candidate(item.id(),item.kind(),item.code(),item.name(),
              item.role(),item.grain(),item.description(),item.typeId(),item.unitId(),
              item.reuseId(),item.reuseVersion(),deps,item.evidence()));
        } else newItems.add(item);
      }
      return old.revised(newItems,List.of(),old.answers());
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

  /** Explicit user save, distinct from Plan/selection approval; no model tools involved. */
  public List<SourceSemanticAdoption.Receipt> adopt(String taskId, SaveRequest request) {
    if(request==null || !request.confirmed())
      throw new IllegalArgumentException("[F039_EXPLICIT_SAVE_CONFIRMATION_REQUIRED]");
    var input=sources.verifiedCandidateInput(taskId);
    Preflight fresh=preflight(taskId,request.revision());
    if(!fresh.ready() || !Objects.equals(fresh.payloadDigest(),request.payloadDigest())
        || !Objects.equals(fresh.ticket(),request.preflightTicket()))
      throw new IllegalStateException("[F039_PREFLIGHT_EXPIRED]");
    var state=input.task();
    var review=reviews.read(state.ownerId(),state.projectId(),taskId);
    var byId=new LinkedHashMap<String,Candidate>();
    review.candidates().forEach(item->byId.put(item.id(),item));
    var items=fresh.closure().stream().map(byId::get).map(item->
        new SourceSemanticAdoption.Candidate(item.id(),item.kind(),item.code(),
            item.name(),item.role(),item.grain(),item.description(),item.typeId(),
            item.unitId(),item.reuseId(),item.reuseVersion(),item.dependencies(),
            "SOURCE_LINK".equals(item.kind())?item.evidence().get(0).tableAssetKey():null)).toList();
    var metadata=input.evidence();
    var scope=new SourceSemanticAdoption.Scope(state.projectId(),
        Long.parseLong(state.sourceManifest().dataSourceId()),metadata.collectJobId(),
        metadata.fingerprint(),state.sourceManifest().tables().stream()
            .map(io.yak.ops.business.agent.runtime.SourceSemanticScope.Table::assetKey).toList());
    if(adoption==null) throw new IllegalStateException("[F039_ADOPTION_NOT_ENABLED]");
    return adoption.adopt(new SourceSemanticAdoption.Batch(taskId,state.projectId(),
        Long.parseLong(state.ownerId()),review.revision(),fresh.payloadDigest(),scope,items));
  }

  public List<SourceSemanticAdoption.Receipt> adoptionReceipts(String taskId) {
    sources.read(taskId); // Current user + project + session ownership, not model content.
    if(adoption==null) throw new IllegalStateException("[F039_ADOPTION_NOT_ENABLED]");
    return adoption.receipts(taskId);
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
    if (!review.skillDigest().equals(digestSkills()))
      errors.add("[F039_SKILL_CHANGED]");
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
    bindings.add(review.skillDigest());
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
    if (normalized.isEmpty()) errors.add("[F039_NOTHING_SELECTED]");
    String ticket=errors.isEmpty()?digest(List.of("F039_PREFLIGHT_READ_ONLY",payload,
        Long.toString(task.projectId()),task.ownerId())):null;
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

  private static void validate(Candidate c,SourceSemanticCatalog.Snapshot catalog,Set<String> errors) {
    String tag=" "+c.id();
    boolean codeNeeded=!("PROCESS_FIELD".equals(c.kind())||"SOURCE_LINK".equals(c.kind()));
    if (codeNeeded&&(c.code()==null||!c.code().matches("[A-Za-z0-9_]{1,64}")))
      errors.add("[F039_CODE_REQUIRED]"+tag);
    if ("PROCESS".equals(c.kind()) &&
        (c.grain()==null||c.grain().isBlank()||
            !("FACT".equals(c.role()) || "DIMENSION".equals(c.role()))))
      errors.add("[F039_PROCESS_GRAIN_OR_TYPE_UNKNOWN]"+tag);
    if ("SOURCE_LINK".equals(c.kind()) &&
        !List.of("MAIN","DETAIL","DIM").contains(c.role()))
      errors.add("[F039_SOURCE_ROLE_REQUIRED]"+tag);
    if ("FIELD".equals(c.kind())) {
      if (!("PROCESS".equals(c.role()) || "DIMENSION".equals(c.role()) || "METRIC".equals(c.role())))
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
    if ("STANDARD_CODE".equals(c.kind()) || (c.kind().startsWith("STANDARD_")
        && c.reuseId()==null))
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
        if(items.size()+3>1600) throw new IllegalStateException("[F039_CANDIDATES_TOO_MANY]");
        var field=physical.columns().stream().filter(f->col.equals(f.name())).findFirst()
            .orElseThrow(()->new IllegalStateException("[F039_COLUMN_NOT_COLLECTED]"));
        String evidenceChunk=chunkFor(task,table.assetKey(),col);
        String fieldId=id(task.taskId(),"FIELD",table.assetKey(),col);
        String bindingId=id(task.taskId(),"PROCESS_FIELD",table.assetKey(),col);
        String linkId=id(task.taskId(),"SOURCE_LINK",table.assetKey(),col);
        var evidence=List.of(new Evidence(table.assetKey(),col,evidenceChunk));
        items.add(new Candidate(fieldId,"FIELD",null,col,null,null,
            ("SQL类型="+field.dataType()+"；注释="+Objects.toString(field.comment(),"")+
            "。业务含义、TYPE与角色均待审阅").substring(0, Math.min(512,
                ("SQL类型="+field.dataType()+"；注释="+Objects.toString(field.comment(),"")+
                "。业务含义、TYPE与角色均待审阅").length())),null,null,null,null,List.of(),evidence));
        items.add(new Candidate(bindingId,"PROCESS_FIELD",null,col,null,null,
            "待确认的过程字段关联",null,null,null,null,List.of(processId,fieldId),evidence));
        items.add(new Candidate(linkId,"SOURCE_LINK",null,col,null,null,
            "来源列到过程字段的证据关联（非推测血缘）",null,null,null,null,
            List.of(bindingId),evidence));
      }
    }
    // SQL types can suggest review questions, NEVER a verified business TYPE.
    var typeEvidence=new LinkedHashMap<String,List<Evidence>>();
    for (var table:task.sourceManifest().tables()) {
      var physical=tableMap.get(table.assetKey());
      for(String name:table.columns()) {
        var column=physical.columns().stream().filter(f->name.equals(f.name())).findFirst()
            .orElseThrow(()->new IllegalStateException("[F039_COLUMN_NOT_COLLECTED]"));
        typeEvidence.computeIfAbsent(column.dataType(), ignored->new ArrayList<>())
            .add(new Evidence(table.assetKey(),name,chunkFor(task,table.assetKey(),name)));
      }
    }
    for (var entry:typeEvidence.entrySet()) {
      var witnesses=entry.getValue();
      String typeName="SQL "+entry.getKey();
      if (typeName.length()>128) typeName=typeName.substring(0,128);
      items.add(new Candidate(id(task.taskId(),"STANDARD_TYPE",entry.getKey(),""),
          "STANDARD_TYPE",null,typeName,null,null,
          "物理SQL类型不是正式TYPE。仅供核对复用或正式标准创建前的业务评审",
          null,null,null,null,List.of(),witnesses));
    }
    var sample=List.of(new Evidence(any.assetKey(),null,task.chunkIds().get(0)));
    items.add(new Candidate(id(task.taskId(),"STANDARD_UNIT","",""),
        "STANDARD_UNIT",null,"待确认业务计量单位",null,null,
        "未读取源数据行；币种与单位没有有效证据时禁止自动创建",
        null,null,null,null,List.of(),sample));
    items.add(new Candidate(id(task.taskId(),"STANDARD_CODE","",""),
        "STANDARD_CODE",null,"待确认完整码集",null,null,
        "缺少人工确认的全量码值和标签；不得从列名猜测码集",
        null,null,null,null,List.of(),sample));
    if(items.size()>2400) throw new IllegalStateException("[F039_CANDIDATES_TOO_MANY]");
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
  private static List<Match> match(Review review,SourceSemanticCatalog.Snapshot catalog) {
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
  private static void verifyProject(SourceSemanticTaskState task,SourceSemanticCatalog.Snapshot catalog) {
    if (task.projectId()!=catalog.projectId()||!catalog.complete())
      throw new IllegalStateException("[F039_CATALOG_INCOMPLETE]");
  }
  /** Skill enabled state/version and complete prompt content are rechecked at preflight. */
  private String digestSkills() {
    var parts=new ArrayList<String>();
    skills.list().stream().sorted(java.util.Comparator.comparing(s -> s.skillId()))
        .forEach(s -> {
          parts.add(s.skillId());parts.add(s.name());
          parts.add(Integer.toString(s.version()));parts.add(Boolean.toString(s.enabled()));
          parts.add(Objects.toString(s.content(),""));
        });
    return digest(parts);
  }

  private static String digestCatalog(SourceSemanticCatalog.Snapshot catalog) {
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
