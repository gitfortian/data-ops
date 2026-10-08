/**
 * A2.2: read-only consumers of Boot's shared persistence bean aliases.
 *
 * This is an evidence inventory, not a full Java parser and not a reason to
 * delete aliases whose consumers are only present in runtime configuration.
 */
export const PERSISTENCE_ALIASES = Object.freeze({
  dataSource: Object.freeze(['yakBusinessDataSource', 'opsDataSource', 'opsResourceDataSource', 'offlineSyncDataSource']),
  transactionManager: Object.freeze(['yakBusinessTransactionManager', 'opsDataSourceTransactionManager', 'opsResourceTransactionManager', 'offlineSyncTransactionManager']),
  sqlSessionFactory: Object.freeze(['yakBusinessSqlSessionFactory', 'opsDataSourceSqlSessionFactory', 'opsResourceSqlSessionFactory', 'offlineSyncSqlSessionFactory']),
  sqlSessionTemplate: Object.freeze(['yakBusinessSqlSessionTemplate', 'opsDataSourceSqlSessionTemplate', 'opsResourceSqlSessionTemplate', 'offlineSyncSqlSessionTemplate']),
});

const ENTRY_PATTERN = /@Qualifier\s*\(\s*"([^"]+)"\s*\)|@Resource\s*\(\s*name\s*=\s*"([^"]+)"\s*\)|\b(?:sqlSessionFactoryRef|sqlSessionTemplateRef|transactionManager)\s*=\s*"([^"]+)"|\bgetBean\s*\(\s*"([^"]+)"|\b@DependsOn\s*\(\s*"([^"]+)"/g;
const ALIASES = new Map(Object.entries(PERSISTENCE_ALIASES).flatMap(([type, names]) =>
  names.map((name) => [name, type])));

/** Extracts known bean references with their source location. */
export function findPersistenceConsumers(file, source) {
  if (!file.endsWith('.java') || !file.includes('/src/main/java/')
      || file.startsWith('data-ops-framework/legacy/')) return [];
  const consumers = [];
  for (const match of source.matchAll(ENTRY_PATTERN)) {
    const name = match.slice(1).find(Boolean);
    const kind = ALIASES.get(name);
    if (!kind) continue;
    const line = source.slice(0, match.index).split('\n').length;
    consumers.push({ file, line, bean: name, kind });
  }
  return consumers;
}

/** Proves the full historical alias contract remains declared by the Boot assembly. */
export function checkDeclaredPersistenceAliases(sources) {
  const boot = sources
    .filter(({file}) => file.startsWith('data-ops-boot/src/main/java/io/yak/ops/boot/config/persistence/'))
    .map(({content}) => content)
    .join('\n');
  const declarations = [...boot.matchAll(/@Bean\s*\(([\s\S]*?)\)/g)].map((match) => match[1]);
  const missing = [];
  const duplicate = [];
  for (const [type, aliases] of Object.entries(PERSISTENCE_ALIASES)) {
    for (const name of aliases) {
      // Includes @Bean(name = {...}) and @Bean("..."); matching the literal is
      // deliberate so splitting the Boot configurations remains source compatible.
      const matches = declarations.flatMap((args) => [...args.matchAll(new RegExp('"' + name + '"', 'g'))]);
      if (matches.length === 0) missing.push({ kind: type, bean: name });
      if (matches.length > 1) duplicate.push({ kind: type, bean: name, occurrences: matches.length });
    }
  }
  return { missing, duplicate };
}

export function persistenceInventory(sources) {
  const consumers = sources.flatMap(({file, content}) => findPersistenceConsumers(file, content))
    .sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line);
  const counts = Object.fromEntries(Object.values(PERSISTENCE_ALIASES)
    .flat().map((name) => [name, consumers.filter((entry) => entry.bean === name).length]));
  const byModule = new Map();
  for (const row of consumers) {
    const module = row.file.split('/src/main/')[0];
    if (!byModule.has(module)) byModule.set(module, new Set());
    byModule.get(module).add(row.bean);
  }
  return {
    declared: checkDeclaredPersistenceAliases(sources),
    consumers,
    counts,
    modules: [...byModule].map(([module, names]) => ({ module, aliases: [...names].sort() }))
      .sort((a, b) => a.module.localeCompare(b.module)),
  };
}
