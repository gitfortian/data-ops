-- 数据订正:预置 CODE 行的 std_name 曾按「码集名-码值标签」逐行写入(V2 模板),
-- 导致码集列表(MAX(std_name))与详情(首行)名称不一致(issue I-4)。
-- 统一回码集名:剥离行尾的「-{code_label}」后缀;非该模式的行不受影响,可重复执行。
UPDATE yak_semantic_preset_template
SET std_name = LEFT(std_name, CHAR_LENGTH(std_name) - CHAR_LENGTH(code_label) - 1)
WHERE kind = 'CODE'
  AND code_label IS NOT NULL
  AND code_label <> ''
  AND std_name LIKE CONCAT('%-', code_label)
  AND CHAR_LENGTH(std_name) > CHAR_LENGTH(code_label) + 1;

UPDATE yak_semantic_standard
SET std_name = LEFT(std_name, CHAR_LENGTH(std_name) - CHAR_LENGTH(code_label) - 1)
WHERE kind = 'CODE'
  AND code_label IS NOT NULL
  AND code_label <> ''
  AND std_name LIKE CONCAT('%-', code_label)
  AND CHAR_LENGTH(std_name) > CHAR_LENGTH(code_label) + 1;
