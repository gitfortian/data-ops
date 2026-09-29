package io.yak.ops.business.semantic.api;

import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.api.BusinessProcess;
import java.util.List;

/**
 * Read-only SPI for modeling (and future consumers): business-domain,
 * business-process and standard-field catalogs (tickets 44/47 and 43).
 * Implementations must not leak internal types.
 */
public interface ProcessApi {

  /** 全量业务域(扁平列表,含 parentId;供 44 选择/47 聚合)。 */
  List<BusinessDomain> listDomains();

  /** 指定业务域(或全部)的业务过程清单。 */
  List<BusinessProcess> listProcesses(Long domainId);

  /** 某业务过程的标准字段集(按过程内顺序;35)。 */
  List<StandardField> getFieldSets(Long processId);

  /** 单个标准字段(35)。 */
  StandardField getField(Long fieldId);

  /** 启用态标准字段库(35;keyword 可空=全部,按 id 稳定排序);38/44 字段匹配与关联选择用。 */
  List<StandardField> listFields(String keyword);

  /** 业务过程关联源表(36):角色与关联条件,供 44 按角色决定字段继承范围。 */
  List<ProcessSourceView> listProcessSources(Long processId);

  /** 过程源表绑定视图(只暴露消费方需要的字段,不含审计列)。 */
  record ProcessSourceView(
      Long id,
      Long datasourceId,
      String sourceTable,
      String tableRole,
      String joinCondition) {}
}
