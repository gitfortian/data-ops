/** Resolve explicit, nested/static and wildcard imports against the actual source ownership map. */
export function importedClasses(name, classes) {
  if (name.endsWith('.*')) {
    const prefix = name.slice(0, -1);
    if (classes.has(name.slice(0, -2))) return [classes.get(name.slice(0, -2))];
    return [...classes.entries()].filter(([key]) => key.startsWith(prefix)).map(([, value]) => value);
  }
  let target = name;
  while (target.includes('.') && !classes.has(target)) target = target.slice(0, target.lastIndexOf('.'));
  return classes.has(target) ? [classes.get(target)] : [];
}

export function crossesPersistenceBoundary(source, name, classes) {
  return source.module?.startsWith('data-ops-business-') &&
    /\.(dao|mapper)(\.|$)|\.repository\.impl(\.|$)/.test(name) &&
    importedClasses(name, classes).some(target => target.module?.startsWith('data-ops-business-') && target.module !== source.module);
}

/** Only Boot may compile against its internal Java packages; assembly has no Java callers. */
export function importsBootOutsideBoot(source, name) {
  return source.module !== 'data-ops-boot' && /^io\.yak\.ops\.boot(?:\.|$)/.test(name);
}

/** Distribution can package Boot; all other modules must remain upstream of Boot. */
export function dependsOnBootOutsideAssembly(module, dependencies) {
  return module !== 'data-ops-boot' && module !== 'data-ops-dist' &&
    dependencies.includes('data-ops-boot');
}
