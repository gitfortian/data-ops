/**
 * A4.2 - explicit Spring Flyway startup dependency graph, read-only.
 * Preserves domain-owned migrations and never imports A4.1 before it is merged.
 */
const CLASS_PATH = '/src/main/java/';

export function extractFlywayOrdering(file, source) {
  if (!file.includes(CLASS_PATH)
      || !(file.startsWith('data-ops-business/') || file.startsWith('data-ops-boot/'))) return [];
  const result = [];
  const expression = /@Bean\s*\(([\s\S]*?)\)(?:\s*@DependsOn\s*\(([\s\S]*?)\))?\s*public\s+Flyway\s+([A-Za-z_$][\w$]*)\s*\(/g;
  for (const match of source.matchAll(expression)) {
    const [, beanArgs, dependsOn, method] = match;
    if (!source.slice(match.index).includes('Flyway.configure()')) continue;
    const declared = beanArgs.match(/\bname\s*=\s*"([^"]+)"/)?.[1]
      ?? beanArgs.match(/^\s*"([^"]+)"/)?.[1];
    const bean = declared || method;
    const dependencies = [...(dependsOn || '').matchAll(/"([^"]+)"/g)].map(x => x[1]);
    result.push({file, bean, method, dependencies});
  }
  return result;
}

export const REQUIRED_FLYWAY_EDGES = Object.freeze([
  ['yakMetricFlyway', 'yakDatasetFlyway'],
  ['yakAgentFlyway', 'yakDatasetFlyway'],
  ['yakAnalysisFlyway', 'yakDatasetFlyway'],
  ['yakDashboardFlyway', 'yakAnalysisFlyway'],
  ['yakModelingFlyway', 'yakSemanticFlyway'],
  ['dataServiceFlyway', 'opsDataSourceFlyway'],
  ['consumptionFlyway', 'opsDataSourceFlyway'],
]);

export function auditFlywayOrdering(sources) {
  const nodes = sources.flatMap(({file,content}) => extractFlywayOrdering(file,content))
    .sort((a,b) => a.bean.localeCompare(b.bean));
  const failures = [];
  const registry = new Map();
  for (const node of nodes) {
    const existing = registry.get(node.bean);
    if (existing) failures.push('Flyway bean ' + node.bean + ' declared by both ' + existing.file + ' and ' + node.file);
    registry.set(node.bean,node);
  }
  for (const node of nodes) {
    for (const dependency of node.dependencies) {
      if (!registry.has(dependency)) failures.push(node.file + ': missing Flyway dependency bean ' + dependency);
      if (dependency === node.bean) failures.push(node.file + ': self-dependency on ' + node.bean);
    }
  }
  for (const [bean, dependency] of REQUIRED_FLYWAY_EDGES) {
    const owner = registry.get(bean);
    if (!owner) failures.push('Expected Flyway bean not registered: ' + bean);
    else if (!owner.dependencies.includes(dependency)) {
      failures.push(owner.file + ': ' + bean + ' must depend on ' + dependency);
    }
  }
  const visited = new Set(), active = new Set();
  function walk(bean, trail) {
    if (active.has(bean)) {
      failures.push('Flyway dependency cycle: ' + [...trail,bean].join(' -> '));
      return;
    }
    if (visited.has(bean)) return;
    active.add(bean);
    for (const dependency of registry.get(bean)?.dependencies ?? []) {
      if (registry.has(dependency)) walk(dependency,[...trail,bean]);
    }
    active.delete(bean);
    visited.add(bean);
  }
  for (const name of registry.keys()) walk(name,[]);
  return { nodes, failures: [...new Set(failures)].sort() };
}
