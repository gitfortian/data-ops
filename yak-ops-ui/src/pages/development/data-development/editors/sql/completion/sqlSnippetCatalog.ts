export interface SqlSnippetDefinition {
  prefix: string;
  label: string;
  detail: string;
  body: string;
}

export const SQL_SNIPPETS: SqlSnippetDefinition[] = [
  {
    prefix: 'sel',
    label: 'SELECT … FROM',
    detail: '基础查询模板',
    body: 'SELECT ${1:*}\nFROM ${2:table_name}\n${0}',
  },
  {
    prefix: 'selw',
    label: 'SELECT … WHERE',
    detail: '带过滤条件的查询模板',
    body: 'SELECT ${1:*}\nFROM ${2:table_name}\nWHERE ${3:condition};\n${0}',
  },
  {
    prefix: 'joinq',
    label: 'SELECT … JOIN',
    detail: '双表 JOIN 查询模板',
    body:
      'SELECT ${1:a.*}\nFROM ${2:table_a} ${3:a}\nJOIN ${4:table_b} ${5:b}\n  ON ${6:a.id = b.id}\nWHERE ${7:1 = 1};\n${0}',
  },
  {
    prefix: 'cte',
    label: 'WITH CTE',
    detail: '公共表表达式模板',
    body:
      'WITH ${1:cte_name} AS (\n  SELECT ${2:*}\n  FROM ${3:table_name}\n)\nSELECT ${4:*}\nFROM ${1:cte_name};\n${0}',
  },
  {
    prefix: 'casew',
    label: 'CASE WHEN',
    detail: '条件表达式模板',
    body:
      'CASE\n  WHEN ${1:condition} THEN ${2:value}\n  ELSE ${3:fallback}\nEND${0}',
  },
  {
    prefix: 'ins',
    label: 'INSERT INTO',
    detail: '插入数据模板',
    body:
      'INSERT INTO ${1:table_name} (${2:column_name})\nVALUES (${3:value});\n${0}',
  },
  {
    prefix: 'upd',
    label: 'UPDATE … SET',
    detail: '更新数据模板',
    body:
      'UPDATE ${1:table_name}\nSET ${2:column_name} = ${3:value}\nWHERE ${4:condition};\n${0}',
  },
];
