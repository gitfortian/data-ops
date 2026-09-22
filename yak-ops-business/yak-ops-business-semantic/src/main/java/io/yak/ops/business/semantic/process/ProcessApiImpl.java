package io.yak.ops.business.semantic.process;

import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.binding.SemanticProcessBindingService;
import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.business.semantic.field.SemanticFieldService;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.repository.SemanticDomainRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Default ProcessApi implementation backed by the semantic repositories. */
@Component
@RequiredArgsConstructor
public class ProcessApiImpl implements ProcessApi {

  private final SemanticDomainRepository domainRepository;
  private final SemanticProcessRepository processRepository;
  private final SemanticFieldService fieldService;
  private final SemanticProcessBindingService bindingService;

  @Override
  public List<BusinessDomain> listDomains() {
    return domainRepository.findAll();
  }

  @Override
  public List<BusinessProcess> listProcesses(Long domainId) {
    if (domainId == null) {
      return domainRepository.findAll().stream()
          .flatMap(domain -> processRepository.listByDomain(domain.id()).stream())
          .toList();
    }
    return processRepository.listByDomain(domainId);
  }

  @Override
  public List<StandardField> getFieldSets(Long processId) {
    return fieldService.fieldsOfProcess(processId);
  }

  @Override
  public StandardField getField(Long fieldId) {
    return fieldService.get(fieldId);
  }

  @Override
  public List<StandardField> listFields(String keyword) {
    return fieldService.listEnabled(keyword);
  }

  @Override
  public List<ProcessSourceView> listProcessSources(Long processId) {
    if (processId == null) {
      return List.of();
    }
    return bindingService.listByProcess(processId).stream()
        .map(
            binding ->
                new ProcessSourceView(
                    binding.id(),
                    binding.datasourceId(),
                    binding.sourceTable(),
                    binding.tableRole(),
                    binding.joinCondition()))
        .toList();
  }
}
