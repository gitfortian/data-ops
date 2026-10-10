package io.yak.ops.business.semantic.adoption;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import cn.dev33.satoken.stp.StpUtil;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi.Candidate;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi.Receipt;
import io.yak.ops.business.semantic.binding.ProcessSourceBinding;
import io.yak.ops.business.semantic.binding.SemanticProcessBindingService;
import io.yak.ops.business.semantic.catalog.StandardCatalogService;
import io.yak.ops.business.semantic.domain.BusinessDomainService;
import io.yak.ops.business.semantic.field.SemanticFieldService;
import io.yak.ops.business.semantic.process.BusinessProcessService;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.spi.semantic.SourceSchemaAdoptionProof;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.transaction.annotation.Transactional;

class SemanticAdoptionItemWriterTest {
  private final AdoptionReceiptMapper mapper=mock(AdoptionReceiptMapper.class);
  private final SemanticProcessBindingService bindings=mock(SemanticProcessBindingService.class);
  private final SourceSchemaAdoptionProof source=mock(SourceSchemaAdoptionProof.class);
  private final CurrentProject project=()->Optional.of(new ProjectContext(31L,"test"));
  private final SemanticAdoptionItemWriter writer=new SemanticAdoptionItemWriter(mapper,
      mock(BusinessDomainService.class),mock(BusinessProcessService.class),
      mock(SemanticFieldService.class),mock(StandardCatalogService.class),
      bindings,project,source);
  private MockedStatic<YakSecurityContext> security;
  private MockedStatic<StpUtil> permissions;
  private final String proc="c_"+UUID.randomUUID();
  private final String field="c_"+UUID.randomUUID();
  private final String bindingId="c_"+UUID.randomUUID();
  private final String link="c_"+UUID.randomUUID();
  private final SourceSchemaAdoptionProof.Expected evidence =
      new SourceSchemaAdoptionProof.Expected(31L,7L,"capture","a".repeat(64),
          List.of("orders-key"));

  @BeforeEach void before() {
    security=mockStatic(YakSecurityContext.class);
    security.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
    permissions=mockStatic(StpUtil.class);
    when(mapper.updateById(any(AdoptionReceiptPO.class))).thenReturn(1);
    doAnswer(invocation -> {
      AdoptionReceiptPO pending=invocation.getArgument(0);
      assertEquals("PENDING",pending.getStatus());
      assertNull(pending.getSemanticId());
      return 1;
    }).when(mapper).insert(any(AdoptionReceiptPO.class));
    when(source.verifiedTableName(evidence,"orders-key")).thenReturn("orders");
    when(bindings.listByProcess(71L)).thenReturn(List.of());
    when(bindings.bind(eq(71L),eq(7L),eq("orders"),eq("MAIN"),isNull(),eq("42")))
        .thenReturn(new ProcessSourceBinding(991L,71L,7L,"orders","MAIN",
            null,"42",null,null));
  }
  @AfterEach void after() {permissions.close();security.close();}

  private Candidate candidate(String id,String kind,List<String> deps,String asset,String role) {
    return new Candidate(id,kind,null,kind,role,null,null,null,null,null,null,deps,asset);
  }
  private SemanticSourceAdoptionApi.Batch batch() {
    return new SemanticSourceAdoptionApi.Batch(UUID.randomUUID().toString(),31L,42L,2,
        "b".repeat(64),evidence,List.of(
            candidate(proc,"PROCESS",List.of(),null,"FACT"),
            candidate(field,"FIELD",List.of(),null,"PROCESS"),
            candidate(bindingId,"PROCESS_FIELD",List.of(proc,field),null,null),
            candidate(link,"SOURCE_LINK",List.of(bindingId),"orders-key","MAIN")));
  }
  private Map<String,Receipt> dependencies() {
    return Map.of(proc,new Receipt(proc,"CREATED",71L,null,"PROCESS","ok"),
        field,new Receipt(field,"CREATED",72L,1,"FIELD","ok"),
        bindingId,new Receipt(bindingId,"LINKED",72L,null,"PROCESS_FIELD","ok"));
  }

  @Test void processSourceLinkUsesMetadataVerifiedNameAndOriginalBindingService() {
    var request=batch();
    var result=writer.apply(request,request.candidates().get(3),dependencies());
    assertEquals("LINKED",result.status());
    assertEquals(991L,result.semanticId());
    verify(source,atLeastOnce()).assertCurrent(evidence);
    verify(bindings).bind(71L,7L,"orders","MAIN",null,"42");
    verify(mapper).insert(any(AdoptionReceiptPO.class));
    verify(mapper).updateById(argThat((AdoptionReceiptPO row) -> "LINKED".equals(row.getStatus())
        && Long.valueOf(991L).equals(row.getSemanticId())));
  }

  @Test void existingDifferentTableRoleRefusesBinding() {
    var request=batch();
    when(bindings.listByProcess(71L)).thenReturn(List.of(
        new ProcessSourceBinding(33L,71L,7L,"orders","DETAIL",null,"42",null,null)));
    assertThrows(IllegalStateException.class,()->writer.apply(
        request,request.candidates().get(3),dependencies()));
    verify(bindings,never()).bind(any(),any(),any(),any(),any(),any());
    verify(mapper,never()).updateById(any(AdoptionReceiptPO.class));
  }

  @Test void sourceDriftStopsBeforeTransactionReceiptInsert() {
    var request=batch();
    doThrow(new IllegalStateException("[F039_DRIFT]")).when(source).assertCurrent(evidence);
    assertThrows(IllegalStateException.class,()->writer.apply(
        request,request.candidates().get(3),dependencies()));
    verify(mapper,never()).insert(any(AdoptionReceiptPO.class));
  }

  @Test void writerUsesBusinessTransactionManager() throws Exception {
    var annotation=SemanticAdoptionItemWriter.class.getMethod("apply",
        SemanticSourceAdoptionApi.Batch.class,SemanticSourceAdoptionApi.Candidate.class,
        Map.class).getAnnotation(Transactional.class);
    assertNotNull(annotation);
    assertEquals("yakBusinessTransactionManager",annotation.transactionManager());
  }
}
