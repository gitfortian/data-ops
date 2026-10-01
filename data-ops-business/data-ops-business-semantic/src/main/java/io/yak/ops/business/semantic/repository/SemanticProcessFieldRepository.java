package io.yak.ops.business.semantic.repository;

import io.yak.ops.common.bean.po.modeling.ModelingColumnMappingPO;
import java.util.List;

/** Project-scoped persistence boundary for process-field references. */
public interface SemanticProcessFieldRepository {

  /** 过程字段绑定行(fieldId + is_required + 顺序)。 */
  record ProcessFieldBinding(Long fieldId, boolean required, int sortOrder) {}

  /** 引用某字段的过程数量(删除校验)。 */
  long countByField(Long fieldId);

  /** 某过程的绑定行,过程内顺序升序。 */
  List<ProcessFieldBinding> bindingsByProcess(Long processId);

  /** 追加引用(过程内追加到末尾;required 由调用方给定)。 */
  void bind(Long processId, Long fieldId, boolean required, String operator);

  /** 移除引用。 */
  void unbind(Long processId, Long fieldId);

  boolean updateRequired(Long processId, Long fieldId, boolean required);

  /** 是否已被某过程引用。 */
  boolean existsByProcessAndField(Long processId, Long fieldId);

  /** 重排序:按给定 fieldId 顺序整体替换该过程的引用顺序。 */
  void reorder(Long processId, List<Long> orderedFieldIds);

  /** 某字段是否被指定过程之外的过程引用(过程删除前提示用)。 */
  long countByProcess(Long processId);

  /** 过程字段引用计数(47 列表页展示;服务端 group-by 聚合)。 */
  record ProcessFieldCount(Long processId, long fieldCount) {}

  List<ProcessFieldCount> countByProjectGroupedByProcess();
}
