# Ticket 76：脱敏算法与策略 SPI

**目标**：维护脱敏算法字典与脱敏策略，对下游暴露脱敏裁决 + 执行。

**表**：`yak_dsec_masking_algorithm`（内置种子）、`yak_dsec_masking_policy`。
**行为**：
- 算法字典：内置 MASK_PARTIAL/HASH/FULL_MASK/NULLIFY/REPLACE/KEEP_FORMAT（`builtin=1` 不可删）；自定义可增删。
- 策略 CRUD（按等级/分类/列名匹配 → 绑定算法，priority）。
- 裁决 `resolveMasking(objectKey)`：读 classification 等级/分类 + 列名匹配策略，priority 取高 → algo_code。
- 执行 `mask(value, algoCode, params)`：纯函数式施加（参考 datasource `SensitiveTextMasker` 语义）。
**SPI**：`SecurityMaskingApi`（`resolve` + `mask`）供 data-service/预览调用。
**审计**：`MASKING_*`。
**错误码**：45060~45069。
**验收**：`mask()` 各算法单测；策略优先级/列名匹配单测；内置算法不可删。
