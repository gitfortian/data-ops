import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { extractFlywayOrdering, auditFlywayOrdering, REQUIRED_FLYWAY_EDGES } from './flyway-startup-order.mjs';

const own = 'data-ops-business/data-ops-business-metric/src/main/java/io/yak/ops/business/metric/config/MetricPersistenceConfiguration.java';
const source = (name, dependency, explicit = true) =>
  '@Bean(' + (explicit ? 'name = "' + name + '", ' : '') + 'initMethod = "migrate")\n'
  + (dependency ? '@DependsOn("' + dependency + '")\n' : '')
  + 'public Flyway ' + name + '(DataSource source) {\n'
  + '  return Flyway.configure().dataSource(source).load();\n}';

test('reads @Bean explicit names and @DependsOn source without modifying Spring runtime', () => {
  const nodes = extractFlywayOrdering(own, source('yakMetricFlyway','yakDatasetFlyway'));
  assert.deepEqual(nodes, [{file:own,bean:'yakMetricFlyway',method:'yakMetricFlyway',
    dependencies:['yakDatasetFlyway']}]);
});

test('detects bean method name fallback for unnamed Flyway beans', () => {
  const nodes = extractFlywayOrdering(own, source('opsDataSourceFlyway',null,false));
  assert.equal(nodes[0].bean,'opsDataSourceFlyway');
  assert.deepEqual(nodes[0].dependencies, []);
});

test('reports missing dependency, duplicate bean and dependency cycles', () => {
  const cycle=auditFlywayOrdering([
    {file:own,content:source('a','b')},
    {file:own.replace('metric','asset'),content:source('b','a')},
  ],[]);
  assert.match(cycle.failures.join('\n'),/cycle/);
  const duplicate=auditFlywayOrdering([
    {file:own,content:source('a',null)},
    {file:own.replace('metric','asset'),content:source('a',null)},
  ],[]);
  assert.match(duplicate.failures.join('\n'),/declared by both/);
  const missing=auditFlywayOrdering([{file:own,content:source('a','absent')}],[]);
  assert.match(missing.failures.join('\n'),/missing Flyway dependency bean absent/);
});

test('detects regression if a product migration dependency is silently removed', () => {
  const files=[
    {file:own,content:source('yakMetricFlyway',null)},
    {file:own.replace('metric','dataset'),content:source('yakDatasetFlyway',null)},
  ];
  const result=auditFlywayOrdering(files,[['yakMetricFlyway','yakDatasetFlyway']]);
  assert.match(result.failures.join('\n'),/must depend on yakDatasetFlyway/);
});

test('Framework Security Flyway is independent and not incorrectly folded into Boot', () => {
  const p='data-ops-framework/data-security/src/main/java/io/yak/framework/security/config/DataSourceConfig.java';
  assert.deepEqual(extractFlywayOrdering(p,source('yakSecurityFlyway',null)),[]);
});

test('real main reactor retains validated domain Flyway startup chains', () => {
  const root=resolve(fileURLToPath(new URL('../../',import.meta.url)));
  const files=execFileSync('git',['ls-files','-z','--','*.java'],{cwd:root,encoding:'utf8'})
    .split('\0').filter(file=>file.includes('/src/main/java/')
      && (file.startsWith('data-ops-business/')||file.startsWith('data-ops-boot/')))
    .map(file=>({file,content:readFileSync(resolve(root,file),'utf8')}))
    .filter(row=>row.content.includes('Flyway.configure()') && row.content.includes('public Flyway'));
  const audit=auditFlywayOrdering(files);
  assert.ok(audit.nodes.length>=15,'unexpected shrinkage of domain Flyway inventory');
  assert.deepEqual(audit.failures,[]);
  for(const [bean,depends] of REQUIRED_FLYWAY_EDGES) {
    const node=audit.nodes.find(row=>row.bean===bean);
    assert.ok(node && node.dependencies.includes(depends),bean+' -> '+depends);
  }
});
