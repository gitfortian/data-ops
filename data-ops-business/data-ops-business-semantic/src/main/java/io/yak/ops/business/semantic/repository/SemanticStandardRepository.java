package io.yak.ops.business.semantic.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.dao.StandardListRow;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for the semantic standard catalog. */
public interface SemanticStandardRepository {

  Standard insert(Standard standard, String operator);

  /** Finds one standard by id within the current project. */
  Optional<Standard> findById(Long id);

  /** Lock the definition while submitting, editing, deleting or approving. */
  default Optional<Standard> findByIdForUpdate(Long id) { return findById(id); }

  /** Uniqueness of (kind, code) among project rows. */
  boolean existsByCode(StandardKind kind, String code);

  /** Finds one standard by (kind, code) — capture idempotency lookup (40). */
  Optional<Standard> findByCode(StandardKind kind, String code);

  /** 批量按 ID 取标准(字段列表引用名称解析,2026-09-16;项目内)。 */
  List<Standard> findByIds(java.util.Collection<Long> ids);

  /**
   * 统一分页(32.1):五类原始行 + CODE 按码集 SQL GROUP BY 组行(全部视图/码值分类页均聚合),
   * 组行 id 为 NULL、code=分组键、valueCount=组内码值数。
   */
  PageData<StandardListRow> pageListRows(
      int pageNo, int pageSize, StandardKind kind, String keyword, StandardStatus status);

  /** 字段码值引用下拉(35):启用码集(code_set_code + 码集名称)。 */
  List<StandardListRow> listEnabledCodeSetOptions();

  /** ENABLED standards of one kind (recommendation SPI input, ticket 39). */
  List<Standard> listEnabledByKind(StandardKind kind);

  /** 40 决策一:项目内是否存在 ENABLED 且 code_set_code 匹配的 CODE 行。 */
  boolean existsEnabledByCodeSet(String codeSetCode);

  /** Full editable-fields update, conditional on the version read by the caller. */
  Standard update(Standard standard, Integer expectedVersion, String operator);

  /** Code-set row update uses its immutable pre-state version as the expected version. */
  default Standard update(Standard standard, String operator) {
    return update(standard, standard.version() - 1, operator);
  }

  boolean updateStatus(Long id, StandardStatus status, String operator);

  /** Physical delete (dictionary row); reference checks are owned by the service. */
  boolean deleteById(Long id);

  /** Whether the project holds any standard row (31 空态/初始化判定)。 */
  long countByProject();

  /** 码集(32.1):按组键列出全部码值行——常规码集=code_set_code;存量空码集行=std_code 独立成组。 */
  List<Standard> listByCodeSetCode(String codeSetCode);

  default List<Standard> lockCodeSet(String codeSetCode) { return listByCodeSetCode(codeSetCode); }

  /** 码集:code_set_code 是否已存在(严格匹配,不含存量空码集行);创建判重用。 */
  boolean existsByCodeSetCode(String codeSetCode);

  /** 码集:按组键批量删除全部码值行。 */
  int deleteByCodeSetCode(String codeSetCode);

  /** 码集:按组键批量更新全部码值行状态(整组启停)。 */
  int updateStatusByCodeSetCode(String codeSetCode, StandardStatus status, String operator);
}
