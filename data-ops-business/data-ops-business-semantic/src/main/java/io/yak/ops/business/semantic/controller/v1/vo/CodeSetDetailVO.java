package io.yak.ops.business.semantic.controller.v1.vo;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 码集详情视图对象(编辑用,含码集下全部码值行)。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeSetDetailVO extends CodeSetVO {

  /** 存量空码集行按 std_code 独立成组:编码可补全(采纳进新码集)。 */
  private boolean legacy;

  /** 组内行 std_name 不一致(历史数据):编辑保存后统一为码集名称。 */
  private boolean nameInconsistent;

  /** 码集下全部码值行。 */
  private List<StandardVO> values;
}
