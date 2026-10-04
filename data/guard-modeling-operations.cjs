const fs = require('node:fs');
const ts = require('../data-ops-ui/node_modules/typescript');
const file = 'data-ops-ui/src/pages/modeling/detail.tsx';
let text = fs.readFileSync(file, 'utf8').replaceAll('\r\n', '\n');
text = text.replace('import { useLatestOperation }', 'import { useLatestOperation, useResourceScope }');
text = text.replace('  const beginStructureLoad =', '  const captureEditorResource = useResourceScope(`${currentProject?.id ?? ""}:${modelId ?? ""}`);\n  const beginStandardSearch = useLatestOperation(`${currentProject?.id ?? ""}:${modelId ?? ""}`);\n  const beginStructureLoad =');
text = text.replace(`      setPublishApproval(
        (await findByBiz(MODEL_PUBLISH_FLOW_CODE, MODEL_PUBLISH_BIZ_TYPE, modelId)) ?? null,
      );`, `      const latest = (await findByBiz(MODEL_PUBLISH_FLOW_CODE, MODEL_PUBLISH_BIZ_TYPE, modelId)) ?? null;
      setPublishApproval(latest);`);
const source = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
const edits = [];
const insert = (at, value) => edits.push({at, value});
const asyncFunctions = [];
function variableName(node) {
  let p = node.parent;
  if (ts.isCallExpression(p) && p.expression.getText(source) === 'useCallback') p = p.parent;
  return ts.isVariableDeclaration(p) ? p.name.getText(source) : '';
}
function walk(node) {
  if (ts.isArrowFunction(node) && ts.isBlock(node.body) &&
      node.modifiers?.some(m => m.kind === ts.SyntaxKind.AsyncKeyword)) asyncFunctions.push(node);
  ts.forEachChild(node, walk);
}
walk(source);
for (const fn of asyncFunctions) {
  const name = variableName(fn);
  if (name === 'loadStructure') continue;
  const discovery = name === 'discoverStandards';
  insert(fn.body.getStart(source) + 1, discovery ?
      '\n      const isCurrent = captureEditorResource();' :
      '\n    const isCurrent = captureEditorResource();\n    if (!isCurrent()) return;');
  const statements = new Set();
  function visit(node) {
    if (node !== fn && ts.isFunctionLike(node)) return;
    if (ts.isAwaitExpression(node)) {
      let statement = node.parent;
      while (statement && !ts.isStatement(statement)) statement = statement.parent;
      if (statement && !ts.isReturnStatement(statement)) statements.add(statement);
    }
    if (!discovery && ts.isTryStatement(node)) {
      if (node.catchClause) insert(node.catchClause.block.getStart(source)+1, '\n      if (!isCurrent()) return;');
      if (node.finallyBlock) insert(node.finallyBlock.getStart(source)+1, '\n      if (!isCurrent()) return;');
    }
    ts.forEachChild(node, visit);
  }
  visit(fn.body);
  for (const statement of statements) insert(statement.end, discovery ?
      '\n        if (!isCurrent()) return { rows: targetRows, matchCount: 0 };' :
      '\n      if (!isCurrent()) return;');
  const call = fn.parent;
  if (ts.isCallExpression(call) && call.expression.getText(source) === 'useCallback') {
    const deps = call.arguments[1];
    if (deps && ts.isArrayLiteralExpression(deps))
      insert(deps.end - 1, `${deps.elements.length ? ', ' : ''}captureEditorResource`);
  }
}
function effects(node) {
  if (ts.isCallExpression(node) && node.expression.getText(source) === 'useEffect') {
    const fn = node.arguments[0];
    if (fn && ts.isArrowFunction(fn) && ts.isBlock(fn.body) && fn.getText(source).includes('.then(')) {
      insert(fn.body.getStart(source)+1, '\n    const isCurrent = captureEditorResource();');
      const deps = node.arguments[1];
      if (deps && ts.isArrayLiteralExpression(deps))
        insert(deps.end-1, `${deps.elements.length ? ', ' : ''}captureEditorResource`);
      function callbacks(child) {
        if (ts.isCallExpression(child) && ts.isPropertyAccessExpression(child.expression) &&
            ['then','catch'].includes(child.expression.name.text)) {
          for (const fn of child.arguments)
            if (ts.isArrowFunction(fn) && ts.isBlock(fn.body))
              insert(fn.body.getStart(source)+1, '\n        if (!isCurrent()) return;');
        }
        ts.forEachChild(child, callbacks);
      }
      callbacks(fn.body);
    }
  }
  ts.forEachChild(node, effects);
}
effects(source);
edits.sort((a,b) => b.at - a.at);
for (const edit of edits) text = text.slice(0,edit.at)+edit.value+text.slice(edit.at);
text = text.replace('    const text = keyword.trim();', '    const isCurrent = beginStandardSearch();\n    const text = keyword.trim();');
text = text.replace('standardFieldSearchTimerRef.current = setTimeout(() => {', 'standardFieldSearchTimerRef.current = setTimeout(() => {\n      if (!isCurrent()) return;');
text = text.replace('        .then((result) => {\n          const batch', '        .then((result) => {\n          if (!isCurrent()) return;\n          const batch');
text = text.replace('.catch(() => setStandardFieldChoices([]));', '.catch(() => { if (isCurrent()) setStandardFieldChoices([]); });');
fs.writeFileSync(file, text);
console.log(`Scoped ${asyncFunctions.length-1} async editor operations and remote option effects.`);
