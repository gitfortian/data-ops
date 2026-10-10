package io.yak.ops.business.semantic.adoption;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.stp.StpUtil;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi.Batch;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi.Candidate;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi.Receipt;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
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

class SemanticSourceAdoptionServiceTest {
  private final SemanticAdoptionItemWriter writer=mock(SemanticAdoptionItemWriter.class);
  private final SourceSchemaAdoptionProof proof=mock(SourceSchemaAdoptionProof.class);
  private final CurrentProject project=()->Optional.of(new ProjectContext(31L,"test"));
  private final SemanticSourceAdoptionService service=
      new SemanticSourceAdoptionService(writer,proof,project);
  private MockedStatic<YakSecurityContext> security;
  private MockedStatic<StpUtil> permissions;
  private final String root="c_"+UUID.randomUUID();
  private final String child="c_"+UUID.randomUUID();
  private final SourceSchemaAdoptionProof.Expected expected =
      new SourceSchemaAdoptionProof.Expected(31L,7L,"capture-1","a".repeat(64),
          List.of("orders-key"));

  @BeforeEach void setup() {
    security=mockStatic(YakSecurityContext.class);
    security.when(YakSecurityContext::getCurrentUserId).thenReturn(42L);
    permissions=mockStatic(StpUtil.class);
  }
  @AfterEach void close() {permissions.close();security.close();}

  private Candidate domain() {
    return new Candidate(root,"DOMAIN","SALES","销售",null,null,"业务域",
        null,null,null,null,List.of());
  }
  private Candidate process() {
    return new Candidate(child,"PROCESS","ORDERS","订单","FACT","订单ID","订单事件",
        null,null,null,null,List.of(root));
  }
  private Batch batch(Candidate... candidates) {
    return new Batch(UUID.randomUUID().toString(),31L,42L,3,"b".repeat(64),expected,
        List.of(candidates));
  }

  @Test void dependencyOrderIsIndependentOfRequestOrderAndKeepsSuccesses() {
    var request=batch(process(),domain());
    when(writer.apply(eq(request),eq(domain()),anyMap())).thenReturn(
        new Receipt(root,"CREATED",51L,null,"DOMAIN","committed"));
    when(writer.apply(eq(request),eq(process()),anyMap())).thenReturn(
        new Receipt(child,"CREATED",52L,null,"PROCESS","committed"));
    var result=service.adopt(request);
    assertEquals(List.of(root,child),result.stream().map(Receipt::candidateId).toList());
    verify(writer).apply(eq(request),eq(domain()),eq(Map.of()));
    verify(writer).apply(eq(request),eq(process()),eq(Map.of(root,result.get(0))));
    verify(proof,atLeast(3)).assertCurrent(expected);
  }

  @Test void missingDependencyFailsBeforeAnyWrite() {
    var request=batch(process());
    assertThrows(IllegalArgumentException.class,()->service.adopt(request));
    verify(writer,never()).apply(any(),any(),anyMap());
  }

  @Test void sourceDriftBlocksBusinessWrite() {
    var request=batch(domain());
    doThrow(new IllegalStateException("[F039_SOURCE_DRIFT]")).when(proof).assertCurrent(expected);
    assertThrows(IllegalStateException.class,()->service.adopt(request));
    verify(writer,never()).apply(any(),any(),anyMap());
  }

  @Test void unverifiedWriteStopsNewItemsWithoutClaimingFailureOrSuccess() {
    var request=batch(domain(),process());
    when(writer.apply(eq(request),eq(domain()),anyMap()))
        .thenThrow(new IllegalStateException("timeout after write attempt"));
    var returned=service.adopt(request);
    assertEquals(1,returned.size());
    assertEquals("NEEDS_RECONCILIATION",returned.get(0).status());
    verify(writer,never()).apply(eq(request),eq(process()),anyMap());
  }

  @Test void unsupportedStandardIsNotMarkedCreated() {
    var standard=new Candidate(root,"STANDARD_CODE","ORDER_STATE","订单状态",
        null,null,"码值尚未完整核对",null,null,null,null,List.of());
    var result=service.adopt(batch(standard));
    assertEquals("NOT_EXECUTED",result.get(0).status());
    assertNull(result.get(0).semanticId());
    verify(writer,never()).apply(any(),any(),anyMap());
  }

  @Test void readbackDoesNotRequireSemanticCreatePermission() {
    service.receipts(UUID.randomUUID().toString());
    permissions.verify(()->StpUtil.checkPermission(SemanticPermissionCode.READ));
    permissions.verify(()->StpUtil.checkPermission(SemanticPermissionCode.CREATE),never());
  }

  @Test void foreignProjectCannotWriteOrRead() {
    var other=new Batch(UUID.randomUUID().toString(),99L,42L,2,"b".repeat(64),
        new SourceSchemaAdoptionProof.Expected(99L,7L,"capture-1","a".repeat(64),
            List.of("orders-key")),List.of(domain()));
    assertThrows(IllegalArgumentException.class,()->service.adopt(other));
    verifyNoInteractions(writer);
  }
}
