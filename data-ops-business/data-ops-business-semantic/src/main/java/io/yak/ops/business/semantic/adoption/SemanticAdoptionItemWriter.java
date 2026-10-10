package io.yak.ops.business.semantic.adoption;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.dev33.satoken.stp.StpUtil;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.business.semantic.api.SemanticFieldApi;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi.Candidate;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi.Receipt;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.catalog.StandardCatalogService;
import io.yak.ops.business.semantic.binding.SemanticProcessBindingService;
import io.yak.ops.business.semantic.domain.BusinessDomainService;
import io.yak.ops.business.semantic.field.SemanticFieldService;
import io.yak.ops.business.semantic.process.BusinessProcessService;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.semantic.SourceSchemaAdoptionProof;
import java.time.LocalDateTime;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Business object and durable success receipt commit in the SAME business transaction. */
@Component
@ConditionalOnProperty(prefix="yak.agent.source-semantic",name="adoption-enabled",havingValue="true")
public class SemanticAdoptionItemWriter {
  private final AdoptionReceiptMapper receipts;
  private final BusinessDomainService domains;
  private final BusinessProcessService processes;
  private final SemanticFieldService fields;
  private final StandardCatalogService standards;
  private final SemanticProcessBindingService bindings;
  private final CurrentProject currentProject;
  private final SourceSchemaAdoptionProof source;

  public SemanticAdoptionItemWriter(AdoptionReceiptMapper receipts,
      BusinessDomainService domains, BusinessProcessService processes,
      SemanticFieldService fields, StandardCatalogService standards,
      SemanticProcessBindingService bindings, CurrentProject currentProject,
      SourceSchemaAdoptionProof source) {
    this.receipts=receipts;this.domains=domains;this.processes=processes;
    this.fields=fields;this.standards=standards;this.bindings=bindings;
    this.currentProject=currentProject;this.source=source;
  }

  public Receipt find(long project,String taskId,String candidateId) {
    var found=receipts.selectOne(new LambdaQueryWrapper<AdoptionReceiptPO>()
        .eq(AdoptionReceiptPO::getProjectId,project)
        .eq(AdoptionReceiptPO::getTaskId,taskId)
        .eq(AdoptionReceiptPO::getCandidateId,candidateId));
    return found==null?null:map(found);
  }

  public AdoptionReceiptPO stored(long project,String taskId,String candidateId) {
    return receipts.selectOne(new LambdaQueryWrapper<AdoptionReceiptPO>()
        .eq(AdoptionReceiptPO::getProjectId,project)
        .eq(AdoptionReceiptPO::getTaskId,taskId)
        .eq(AdoptionReceiptPO::getCandidateId,candidateId));
  }

  public List<Receipt> list(long project,String taskId,String operator) {
    return receipts.selectList(new LambdaQueryWrapper<AdoptionReceiptPO>()
        .eq(AdoptionReceiptPO::getProjectId,project)
        .eq(AdoptionReceiptPO::getTaskId,taskId)
        .eq(AdoptionReceiptPO::getOperatorId,operator)
        .orderByAsc(AdoptionReceiptPO::getCreateTime)).stream()
        .map(SemanticAdoptionItemWriter::map).toList();
  }

  public Receipt replay(SemanticSourceAdoptionApi.Batch batch,Candidate candidate,
      AdoptionReceiptPO receipt) {
    if (!Long.toString(batch.userId()).equals(receipt.getOperatorId())
        || !Objects.equals(receipt.getPayloadDigest(),commitment(batch,candidate)))
      throw new IllegalStateException("[F039_IDEMPOTENCY_DIGEST_CONFLICT]");
    if (!List.of("CREATED","REUSED","LINKED").contains(receipt.getStatus()))
      throw new IllegalStateException("[F039_RECEIPT_NOT_COMMITTED]");
    return map(receipt);
  }

  private static String commitment(SemanticSourceAdoptionApi.Batch batch,Candidate c) {
    try {
      var bytes=new ByteArrayOutputStream();
      try(var writer=new DataOutputStream(bytes)) {
        var fields=new java.util.ArrayList<String>();
        fields.add(batch.taskId());fields.add(Long.toString(batch.projectId()));
        fields.add(Long.toString(batch.userId()));
        // Item payload, not mutable batch selection/revision: old successful items
        // remain replayable while newly selected independent items can proceed.
        fields.add(batch.evidence().evidenceFingerprint());
        fields.add(c.id());fields.add(c.kind());
        fields.add(Objects.toString(c.code(),""));fields.add(Objects.toString(c.name(),""));
        fields.add(Objects.toString(c.role(),""));fields.add(Objects.toString(c.grain(),""));
        fields.add(Objects.toString(c.description(),""));
        fields.add(Objects.toString(c.typeId(),""));fields.add(Objects.toString(c.unitId(),""));
        fields.add(Objects.toString(c.reuseId(),""));fields.add(Objects.toString(c.reuseVersion(),""));
        fields.add(Objects.toString(c.sourceAssetKey(),""));
        fields.addAll(c.dependencies());
        for(String value:fields) {
          byte[] encoded=value.getBytes(StandardCharsets.UTF_8);
          writer.writeInt(encoded.length);writer.write(encoded);
        }
      }
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(bytes.toByteArray()));
    } catch(Exception failure) {
      throw new IllegalStateException("[F039_ADOPTION_DIGEST_FAILED]",failure);
    }
  }

  static Receipt map(AdoptionReceiptPO receipt) {
    return new Receipt(receipt.getCandidateId(),receipt.getStatus(),
        receipt.getSemanticId(),receipt.getSemanticVersion(),
        receipt.getKind(),receipt.getMessage());
  }

  /**
   * If another node won the UNIQUE(project,task,candidate) race, the entire
   * losing transaction rolls back. Caller MUST requery the winning receipt.
   */
  @Transactional(transactionManager="yakBusinessTransactionManager",rollbackFor=Exception.class)
  public Receipt apply(SemanticSourceAdoptionApi.Batch request,Candidate candidate,
      Map<String,Receipt> completed) {
    verifyActor(request);
    source.assertCurrent(request.evidence());
    var old=stored(request.projectId(),request.taskId(),candidate.id());
    if(old!=null) return replay(request,candidate,old);
    String actor=Long.toString(request.userId());
    var rec=new AdoptionReceiptPO();
    rec.setReceiptId(UUID.randomUUID().toString());
    rec.setProjectId(request.projectId());
    rec.setTaskId(request.taskId());
    rec.setCandidateId(candidate.id());
    rec.setPayloadDigest(commitment(request,candidate));
    rec.setOperatorId(actor);
    rec.setKind(candidate.kind());
    rec.setStatus("PENDING");
    rec.setCreateTime(LocalDateTime.now());
    rec.setUpdateTime(rec.getCreateTime());
    receipts.insert(rec); // UNIQUE reservation before ANY business create.
    Long id;Integer version=null;String outcome;
    if ("PROCESS_FIELD".equals(candidate.kind())) {
      StpUtil.checkPermission(SemanticPermissionCode.UPDATE);
      if (candidate.reuseId()!=null)
        throw new IllegalArgumentException("[F039_BINDING_CANNOT_REUSE_FORMAL_ID]");
      Long processId=dependency(completed,candidate,"PROCESS");
      Long fieldId=dependency(completed,candidate,"FIELD");
      fields.bindToProcess(processId,fieldId,false,actor);
      id=fieldId;outcome="LINKED";
    } else if ("SOURCE_LINK".equals(candidate.kind())) {
      StpUtil.checkPermission(SemanticPermissionCode.UPDATE);
      if(candidate.reuseId()!=null || candidate.sourceAssetKey()==null
          || !List.of("MAIN","DETAIL","DIM").contains(candidate.role()))
        throw new IllegalArgumentException("[F039_SOURCE_LINK_REVIEW_REQUIRED]");
      if(candidate.dependencies().size()!=1)
        throw new IllegalArgumentException("[F039_SOURCE_LINK_DEPENDENCY_INVALID]");
      String fieldLinkId=candidate.dependencies().get(0);
      Candidate fieldLink=request.candidates().stream()
          .filter(item->item.id().equals(fieldLinkId)
              && "PROCESS_FIELD".equals(item.kind()))
          .findFirst().orElseThrow(()->new IllegalStateException("[F039_PROCESS_FIELD_REQUIRED]"));
      Long processId=dependency(completed,fieldLink,"PROCESS");
      String physicalTable=source.verifiedTableName(request.evidence(),candidate.sourceAssetKey());
      if(physicalTable==null || physicalTable.isBlank()||physicalTable.length()>128)
        throw new IllegalStateException("[F039_SOURCE_TABLE_INVALID]");
      var existing=bindings.listByProcess(processId).stream()
          .filter(v->request.evidence().dataSourceId()==v.datasourceId()
              && physicalTable.equals(v.sourceTable())).toList();
      var compatible=existing.stream().filter(v->candidate.role().equals(v.tableRole())
          && (v.joinCondition()==null || v.joinCondition().isBlank())).findFirst();
      if(compatible.isPresent()) {
        id=compatible.get().id();outcome="REUSED";
      } else if(!existing.isEmpty()) {
        throw new IllegalStateException("[F039_SOURCE_BINDING_ROLE_CONFLICT]");
      } else {
        var binding=bindings.bind(processId,request.evidence().dataSourceId(),physicalTable,
            candidate.role(),null,actor);
        id=binding.id();outcome="LINKED";
      }
    } else if(candidate.reuseId()!=null) {
      id=assertReuse(candidate,completed);
      version=candidate.reuseVersion();
      outcome="REUSED";
    } else {
      String code=requireCode(candidate.code());
      String name=requireName(candidate.name());
      switch(candidate.kind()) {
        case "DOMAIN" -> {
          var result=domains.create(null,code,name,null,candidate.description(),null,actor);
          id=result.id();
          outcome="CREATED";
        }
        case "PROCESS" -> {
          Long domainId=dependency(completed,candidate,"DOMAIN");
          if(candidate.grain()==null||candidate.grain().isBlank()
              || !List.of("FACT","DIMENSION").contains(candidate.role()))
            throw new IllegalArgumentException("[F039_PROCESS_GRAIN_REQUIRED]");
          var result=processes.create(domainId,code,name,candidate.grain(),candidate.role(),
              null,candidate.description(),null,actor);
          id=result.id();
          outcome="CREATED";
        }
        case "FIELD" -> {
          if(candidate.typeId()==null) throw new IllegalArgumentException("[F039_TYPE_REQUIRED]");
          var result=fields.create(new SemanticFieldApi.CreateRequest(
              code,name,candidate.role(),null,candidate.typeId(),candidate.unitId(),
              null,null,null,candidate.description()),actor);
          id=result.id();version=result.version();outcome="CREATED";
        }
        default -> throw new IllegalArgumentException("[F039_UNSUPPORTED_ADOPTION_KIND]");
      }
    }
    rec.setStatus(outcome);
    rec.setSemanticId(id);
    rec.setSemanticVersion(version);
    rec.setMessage("Semantic application owner confirmed");
    rec.setUpdateTime(LocalDateTime.now());
    if(receipts.updateById(rec)!=1)
      throw new IllegalStateException("[F039_RECEIPT_WRITE_FAILED]");
    verifyActor(request);
    source.assertCurrent(request.evidence());
    return map(rec);
  }

  private Long assertReuse(Candidate c,Map<String,Receipt> completed) {
    Long id=c.reuseId();
    String code=requireCode(c.code());
    String name=requireName(c.name());
    if(c.reuseVersion()==null || c.reuseVersion()<0)
      throw new IllegalArgumentException("[F039_REUSE_VERSION_REQUIRED]");
    switch(c.kind()) {
      case "DOMAIN" -> {
        var value=domains.get(id);
        if(!value.code().equals(code)||!value.name().equals(name)||c.reuseVersion()!=0)
          throw new IllegalStateException("[F039_REUSE_CHANGED]");
      }
      case "PROCESS" -> {
        var value=processes.get(id);
        if(!value.code().equals(code)||!value.name().equals(name)
            || !Objects.equals(value.domainId(),dependency(completed,c,"DOMAIN"))
            || !Objects.equals(value.grain(),c.grain())
            || !Objects.equals(value.bizType(),c.role())||c.reuseVersion()!=0)
          throw new IllegalStateException("[F039_REUSE_CHANGED]");
      }
      case "FIELD" -> {
        var value=fields.get(id);
        if(!value.code().equals(code)||!value.name().equals(name)||!value.isEnabled()
            || !Objects.equals(value.role(),c.role())
            || !Objects.equals(value.stdTypeId(),c.typeId())
            || !Objects.equals(value.stdUnitId(),c.unitId())
            || value.version()!=c.reuseVersion())
          throw new IllegalStateException("[F039_REUSE_CHANGED]");
      }
      case "STANDARD_TYPE","STANDARD_UNIT" -> {
        var value=standards.get(id);
        StandardKind kind="STANDARD_TYPE".equals(c.kind())?StandardKind.TYPE:StandardKind.UNIT;
        if(value.kind()!=kind || value.status()!=StandardStatus.ENABLED
            || !value.code().equals(code)||!value.name().equals(name)
            || value.version()!=c.reuseVersion())
          throw new IllegalStateException("[F039_REUSE_CHANGED]");
      }
      default -> throw new IllegalArgumentException("[F039_REUSE_KIND_UNSUPPORTED]");
    }
    return id;
  }

  private static Long dependency(Map<String,Receipt> completed,Candidate c,String requiredKind) {
    var resolved=c.dependencies().stream().map(completed::get)
        .filter(Objects::nonNull).filter(r->requiredKind.equals(r.kind())).toList();
    if(resolved.size()!=1||resolved.get(0).semanticId()==null)
      throw new IllegalStateException("[F039_ADOPTION_DEPENDENCY_MISSING]");
    return resolved.get(0).semanticId();
  }
  static String requireCode(String s) {
    if(s==null||!s.matches("[A-Za-z0-9_]{1,64}"))
      throw new IllegalArgumentException("[F039_ADOPTION_CODE_INVALID]");
    return s;
  }
  static String requireName(String s) {
    if(s==null||s.isBlank()||s.length()>128)
      throw new IllegalArgumentException("[F039_ADOPTION_NAME_INVALID]");
    return s;
  }
  private void verifyActor(SemanticSourceAdoptionApi.Batch request) {
    Long actual=YakSecurityContext.getCurrentUserId();
    if(actual==null || actual!=request.userId()
        || currentProject.requireProjectId()!=request.projectId())
      throw new IllegalArgumentException("[F039_ADOPTION_SCOPE_MISMATCH]");
    StpUtil.checkPermission(SemanticPermissionCode.READ);
    StpUtil.checkPermission(SemanticPermissionCode.CREATE);
  }
}
