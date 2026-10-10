package io.yak.ops.business.agent.gateway;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.SourceSemanticAdoption;
import io.yak.ops.business.semantic.api.SemanticSourceAdoptionApi;
import io.yak.ops.spi.semantic.SourceSchemaAdoptionProof;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** No LLM tools: translates trusted application DTO into Semantic command. */
@Component
@ConditionalOnAgentEnabled
public class SourceSemanticAdoptionGateway implements SourceSemanticAdoption {
  private final ObjectProvider<SemanticSourceAdoptionApi> semantic;
  public SourceSemanticAdoptionGateway(ObjectProvider<SemanticSourceAdoptionApi> semantic) {
    this.semantic=semantic;
  }
  private SemanticSourceAdoptionApi owner() {
    var api=semantic.getIfAvailable();
    if(api==null) throw new IllegalStateException("[F039_ADOPTION_NOT_ENABLED]");
    return api;
  }
  @Override public List<Receipt> adopt(Batch request) {
    var scope=request.scope();
    var proof=new SourceSchemaAdoptionProof.Expected(scope.projectId(),scope.dataSourceId(),
        scope.captureId(),scope.evidenceFingerprint(),scope.tableKeys());
    var candidates=request.candidates().stream().map(c->new SemanticSourceAdoptionApi.Candidate(
        c.id(),c.kind(),c.code(),c.name(),c.role(),c.grain(),c.description(),
        c.typeId(),c.unitId(),c.reuseId(),c.reuseVersion(),c.dependencies())).toList();
    var cmd=new SemanticSourceAdoptionApi.Batch(request.taskId(),request.projectId(),
        request.userId(),request.revision(),request.payloadDigest(),proof,candidates);
    return owner().adopt(cmd).stream().map(SourceSemanticAdoptionGateway::map).toList();
  }
  @Override public List<Receipt> receipts(String taskId) {
    return owner().receipts(taskId).stream().map(SourceSemanticAdoptionGateway::map).toList();
  }
  private static Receipt map(SemanticSourceAdoptionApi.Receipt item) {
    return new Receipt(item.candidateId(),item.status(),item.semanticId(),
        item.semanticVersion(),item.kind(),item.message());
  }
}
