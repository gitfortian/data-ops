/**
 * Static import contract for Domain Service -> shared HTTP transport.
 *
 * Runtime @umijs/max / umi "request" imports bypass the existing Project Header,
 * authentication and error-handling transport boundary.
 *
 * Parse only actual, top-level import-from declarations. This guard is not a
 * TypeScript parser; if more TS constructs are needed, use the compiler AST.
 */
export function importedFrameworkRequest(source) {
  const issues = [];
  const statements = /(^|\n)[ \t]*import[ \t\r\n]+(?!type\b)([\s\S]{0,1200}?)[ \t\r\n]+from[ \t\r\n]+(['"])(@umijs\/max|umi)\3[ \t]*;?/g;

  for (const match of source.matchAll(statements)) {
    const clause = match[2];
    // Do not match through an earlier import/statement into a later one.
    if (clause.includes(';') || /(^|\n)[ \t]*(?:import|export)\b/.test(clause)) continue;

    const named = clause.match(/\{([^}]*)\}/)?.[1] ?? '';
    const importsRequest = named.split(',')
      .map((item) => item.trim())
      .filter((item) => item && !item.startsWith('type '))
      .some((item) => /^request(?:\s+as\s+[A-Za-z_$][\w$]*)?$/.test(item));

    // Default request import from Umi is not the documented public API; keep
    // this rule explicit as an extra safety net against bypassing shared HTTP.
    const defaultImport = clause.split('{')[0].trim().replace(/,\s*$/, '');
    const defaultsToRequest = defaultImport === 'request';

    if (importsRequest || defaultsToRequest) {
      const line = source.slice(0, (match.index ?? 0) + match[1].length)
        .split('\n').length;
      issues.push({ line, module: match[4] });
    }
  }
  return issues;
}

export function checkServiceTransportImports(file, content) {
  if (!/^data-ops-ui\/src\/services\/.*\.tsx?$/.test(file)
      || /\.test\.tsx?$/.test(file)) return [];
  return importedFrameworkRequest(content)
    .map(({ line, module }) => file + ':' + line + ': import request from ' + module
      + ' bypasses the shared HTTP client');
}
