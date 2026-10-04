from pathlib import Path
p=Path('data-ops-boot/src/test/java/io/yak/ops/boot/architecture/WorkflowHttpSchemaExportTest.java')
p.write_text('''package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Exports the compiled HTTP DTOs; no server, database or historical example schema is used. */
class WorkflowHttpSchemaExportTest {
  @Test
  void exportWorkflowHttpSchemas() throws Exception {
    var schemas = new TreeMap<String, io.swagger.v3.oas.models.media.Schema>();
    schemas.putAll(ModelConverters.getInstance().readAll(WorkflowRunDTO.class));
    schemas.putAll(ModelConverters.getInstance().readAll(WorkflowInstanceVO.class));
    assertThat(schemas).containsKeys("WorkflowRunDTO", "WorkflowInstanceVO");
    assertThat(schemas.get("WorkflowInstanceVO").getProperties()).containsKeys("id", "nodes", "testRun");
    Path output = Path.of("target/contracts/workflow-http-schema.json");
    Files.createDirectories(output.getParent());
    Files.writeString(output, Json.mapper().writeValueAsString(schemas), StandardCharsets.UTF_8);
  }
}
''',encoding='utf-8')
p=Path('scripts/contracts');p.mkdir(parents=True,exist_ok=True)
(p/'workflow-schema.mjs').write_text('''import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = new URL('../../', import.meta.url);
const source = new URL('data-ops-boot/target/contracts/workflow-http-schema.json', root);
const snapshot = new URL('scripts/contracts/workflow-http-schema.json', root);
const generated = new URL('data-ops-ui/src/services/workflow/httpContracts.generated.ts', root);
const canonical = value => Array.isArray(value) ? value.map(canonical) :
  value && typeof value === 'object' ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;
const schemas = canonical(JSON.parse(readFileSync(source, 'utf8')));
const json = JSON.stringify(schemas, null, 2) + '\\n';
function type(schema) {
  if (schema.$ref) return schema.$ref.split('/').at(-1);
  if (schema.enum) return schema.enum.map(value => JSON.stringify(value)).join(' | ');
  if (schema.oneOf || schema.anyOf) return (schema.oneOf || schema.anyOf).map(type).join(' | ');
  if (schema.allOf) return schema.allOf.map(type).join(' & ');
  if (schema.type === 'array') return `Array<${type(schema.items || {})}>`;
  if (schema.type === 'integer' || schema.type === 'number') return 'number';
  if (schema.type === 'boolean') return 'boolean';
  if (schema.type === 'string') return 'string';
  if (schema.properties) return '{ ' + Object.entries(schema.properties).map(([key, value]) =>
    `${JSON.stringify(key)}${schema.required?.includes(key) ? '' : '?'}: ${type(value)};`).join(' ') + ' }';
  if (schema.additionalProperties) return `Record<string, ${schema.additionalProperties === true ? 'unknown' : type(schema.additionalProperties)}>`;
  return 'unknown';
}
const ts = '// Generated from compiled Workflow HTTP DTOs. Run scripts/contracts/workflow-schema.mjs --write.\\n' +
  Object.entries(schemas).map(([name, schema]) => `export type ${name} = ${type(schema)};`).join('\\n\\n') + '\\n';
if (process.argv.includes('--write')) {
  mkdirSync(fileURLToPath(new URL('.', generated)), { recursive: true });
  writeFileSync(snapshot, json);
  writeFileSync(generated, ts);
  console.log('Workflow HTTP schema and TypeScript contracts generated.');
} else {
  if (readFileSync(snapshot, 'utf8').replaceAll('\\r\\n', '\\n') !== json || readFileSync(generated, 'utf8').replaceAll('\\r\\n', '\\n') !== ts)
    throw new Error('Workflow HTTP contract drift. Review DTO compatibility, regenerate both artifacts and update consuming services.');
  console.log('Compiled Workflow HTTP schema matches its reviewed snapshot and generated types.');
}
''',encoding='utf-8')
# CI exports from compiled DTOs during backend verify, then compares checked-in artifacts.
p=Path('.github/workflows/architecture-checks.yml');s=p.read_text();s=s.replace('      - run: bash ./mvnw -B -ntp -pl data-ops-boot -am verify','      - run: bash ./mvnw -B -ntp -pl data-ops-boot -am verify\n      - run: node scripts/contracts/workflow-schema.mjs');p.write_text(s,encoding='utf-8')
p=Path('data-ops-ui/scripts/check-type-baseline.mjs');s=p.read_text().replace("import { readFileSync }", "import { readFileSync, writeFileSync }");s=s.replace('} else console.log(',''' } else {
  if (process.argv.includes('--prune')) writeFileSync(new URL('./type-baseline.json', import.meta.url), JSON.stringify(actual, null, 2) + '\\n');
  console.log(''').replace(' existing diagnostics).`);',' existing diagnostics).`);\n}');p.write_text(s,encoding='utf-8')
