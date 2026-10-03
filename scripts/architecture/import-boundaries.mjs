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
