package io.yak.ops.business.semantic.repository;

import io.yak.ops.business.semantic.binding.ProcessSourceBinding;
import java.util.List;

/** Project-scoped persistence boundary for process-source bindings. */
public interface SemanticProcessSourceRepository {

  ProcessSourceBinding insert(ProcessSourceBinding binding, String operator);

  List<ProcessSourceBinding> listByProcess(Long processId);

  boolean existsByProcess(Long processId);

  boolean deleteById(Long id);
}
