package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.mdm.domain.clean.MdmDedupKey;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import java.util.List;
import java.util.Optional;

/**
 * Project-scoped boundary for the master data record. Records are written by
 * master-data processing tasks in data-development (plan A, D-M11); MDM reads
 * them for display/governance and updates them only for explicit merges (56).
 */
public interface MdmRecordRepository {

  /**
   * 记录分页(keyword 语义见 review P0-1.4):keyword 非空时匹配 master_id LIKE
   * 或任一 attributeCodes(已由调用方做安全标识符校验)对应的 attributes JSON 值 LIKE。
   */
  PageData<MdmRecord> page(
      Long entityId,
      int pageNo,
      int pageSize,
      String keyword,
      MdmRecordStatus status,
      List<String> attributeCodes);

  /**
   * 去重发现 DB 聚合:按匹配键 GROUP BY,返回重复键(组内 ≥2)。
   * 该规则下已忽略的组键在 SQL 侧排除,ruleId 为空时不排除(无规则即无忽略语义)。
   */
  List<MdmDedupKey> countDedupKeys(
      Long entityId, Long ruleId, String keyExpr, String valueCondition);

  /** 重复组内成员(ACTIVE,按 id 升序,limit 截断组内展示)。 */
  List<MdmRecord> listByDedupKey(Long entityId, String keyExpr, String key, int limit);

  /** 按 ID 批量取 ACTIVE 记录(合并预览/执行的目标记录)。 */
  List<MdmRecord> listActiveByIds(Long entityId, List<Long> ids);

  /** 以 record.version()-1 做 compare-and-set;并发更新只能有一个写入成功。 */
  boolean update(MdmRecord record);

  /** 列出实体全部 ACTIVE 记录(清洗预览/执行用,有安全上限防止无界查询)。 */
  List<MdmRecord> listActiveByEntity(Long entityId);

  /** 按 master_id 查询单条 ACTIVE 记录(ticket 59 查询 API)。 */
  Optional<MdmRecord> findByMasterId(Long entityId, String masterId);

  /** 统计实体 ACTIVE 记录数(总览/治理聚合,ticket 62)。 */
  long countActiveByEntity(Long entityId);

  /** 统计实体指定状态记录数(总览聚合,ticket 62)。 */
  long countByEntity(Long entityId, MdmRecordStatus status);
}
