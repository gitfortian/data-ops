const fs = require('node:fs');
const ts = require('../data-ops-ui/node_modules/typescript');
const file='data-ops-ui/src/pages/modeling/detail.tsx';
let text=fs.readFileSync(file,'utf8');
const source=ts.createSourceFile(file,text,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
const edits=[];
function walk(node) {
  if (ts.isStatement(node) && ts.isBlock(node.parent) &&
      /^(const (isCurrent|resourceCurrent|tableRequestCurrent) =|if \(!isCurrent\(\)\))/.test(node.getText(source))) {
    const start=node.getStart(source), line=text.lastIndexOf('\n',start)+1;
    const brace=node.parent.getStart(source), braceLine=text.lastIndexOf('\n',brace)+1;
    const indent=text.slice(braceLine,brace).match(/^\s*/)[0]+'  ';
    if (/^\s*$/.test(text.slice(line,start))) edits.push({start:line,end:start,value:indent});
  }
  ts.forEachChild(node,walk);
}
walk(source);
for(const edit of edits.sort((a,b)=>b.start-a.start)) text=text.slice(0,edit.start)+edit.value+text.slice(edit.end);
fs.writeFileSync(file,text);
