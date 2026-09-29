package io.yak.ops.business.semantic.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.semantic.api.StandardField;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for the standard field library. */
public interface SemanticFieldRepository {

  StandardField insert(StandardField field, String operator);

  /** Finds one field by id within the current project. */
  Optional<StandardField> findById(Long id);

  /** Uniqueness of field_code among project rows. */
  boolean existsByCode(String code);

  PageData<StandardField> page(int pageNo, int pageSize, String role, String keyword);

  /** 启用态字段清单(关键字可空=全部,按 id 稳定排序);供 38/44 字段匹配与关联选择。 */
  List<StandardField> listEnabled(String keyword);

  StandardField update(StandardField field);

  boolean changeStatus(Long id, String status);

  boolean deleteById(Long id);

  /** 引用计数:绑定指定码集(std_code_set_code)的字段数;码集删除阻断用(32.1)。 */
  long countByCodeSet(String codeSetCode);
}
