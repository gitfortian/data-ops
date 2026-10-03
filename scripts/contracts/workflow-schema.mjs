import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = new URL('../../', import.meta.url);
const source = new URL('data-ops-boot/target/contracts/workflow-http-schema.json', root);
const snapshot = new URL('scripts/contracts/workflow-http-schema.json', root);
const generated = new URL('data-ops-ui/src/services/workflow/httpContracts.generated.ts', root);
const canonical = value => Array.isArray(value) ? value.map(canonical) :
  value && typeof value === 'object' ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;
const schemas = canonical(JSON.parse(readFileSync(source, 'utf8')));
const json = JSON.stringify(schemas, null, 2) + '\n';
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
const ts = '// Generated from compiled Workflow HTTP DTOs. Run scripts/contracts/workflow-schema.mjs --write.\n' +
  Object.entries(schemas).map(([name, schema]) => `export type ${name} = ${type(schema)};`).join('\n\n') + '\n';
if (process.argv.includes('--write')) {
  mkdirSync(fileURLToPath(new URL('.', generated)), { recursive: true });
  writeFileSync(snapshot, json);
  writeFileSync(generated, ts);
  console.log('Workflow HTTP schema and TypeScript contracts generated.');
} else {
  if (readFileSync(snapshot, 'utf8').replaceAll('\r\n', '\n') !== json || readFileSync(generated, 'utf8').replaceAll('\r\n', '\n') !== ts)
    throw new Error('Workflow HTTP contract drift. Review DTO compatibility, regenerate both artifacts and update consuming services.');
  console.log('Compiled Workflow HTTP schema matches its reviewed snapshot and generated types.');
}
