import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const html = readFileSync('docs/product/experience/prototypes/IA-550-task-flows-v0.8.1.html', 'utf8');
const code = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(code);
const fixture = code.replace('var start=decodeURIComponent',
  'document.__context={state,v6,v7,v8,v81,v81Current,v7Current,v7MappingState,v7MappingCurrent,v6Layer,v8CanHandoff,v8MappingFindings,v6Sources};var start=decodeURIComponent');

function build(page = 'model') {
  const nodes = new Map(), listeners = new Map();
  const dom = id => {
    if (!nodes.has(id)) nodes.set(id, {
      innerHTML: '', textContent: '', value: '',
      classList: { add() {}, remove() {} }, setAttribute() {},
      addEventListener() {}, focus() {}, setSelectionRange() {}, querySelector() { return null; },
    });
    return nodes.get(id);
  };
  const document = {
    getElementById: dom,
    addEventListener(kind, fn) { listeners.set(kind, [...(listeners.get(kind) || []), fn]); },
  };
  const window = { location: { hash: '#' + page }, addEventListener() {}, scrollTo() {}, scrollY: 0 };
  new Function('document', 'window', 'setTimeout', 'clearTimeout', fixture)(document, window, () => 0, () => {});
  const dispatch = (kind, target) => { for (const fn of listeners.get(kind) || []) fn({ target }); };
  return {
    ...document.__context,
    view: () => dom('main').innerHTML,
    toast: () => dom('toast').textContent,
    click: dataset => dispatch('click', { closest() { return { dataset }; } }),
    input: (id, value, dataset = {}) => dispatch('input', { id, value, dataset }),
    change: (id, value, dataset = {}) => dispatch('change', { id, value, dataset }),
  };
}

function dwd(a) {
  a.click({ v7Freeze: '1' }); a.click({ v7Design: 'DWD' });
  a.click({ v6Load: '1' }); a.click({ v7Mode: 'mapping' }); a.click({ v7Candidates: '1' });
}
function explainMissing(a) {
  const p = a.v7MappingState();
  a.v6Layer().rows.forEach((r, i) => {
    if (!p.selection[i]) {
      a.change('', 'deferred', { v8Kind: String(i) });
      a.input('', '需核实该列业务语义与合法标准', { v8Reason: String(i) });
    }
  });
}
function save(a) { a.click({ v7Mapconfirm: '1' }); a.click({ v7Mode: 'physical' }); a.click({ v6Check: '1' }); a.click({ v6Save: '1' }); }

test('all existing prototype entrypoints render and inline JavaScript parses', () => {
  assert.doesNotThrow(() => new Function(code));
  for (const page of ['home','model','semantic','standard','metric','asset','governance','quality','security','lifecycle','mdm','collab','product']) assert.ok(build(page).view().length > 300, page);
});
test('one accepted attribute cannot make an unexplained whole table reviewed', () => {
  const a = build(); dwd(a); a.click({ v7Mapconfirm: '1' });
  assert.equal(a.v7MappingCurrent(), false);
  assert.match(a.toast(), /未说明/);
  a.click({ v6Check: '1' }); a.click({ v6Save: '1' });
  assert.equal(a.v6Layer().saved.length, 0);
});
test('documented unmapped fields can be saved as a draft, never handed off as complete', () => {
  const a = build(); dwd(a); explainMissing(a); save(a);
  const x = a.v6Layer(); assert.equal(x.saved.length, 1);
  assert.equal(x.saved[0].logicalMapping.mappings.length, x.rows.length);
  assert.match(x.saved[0].logicalMapping.status, /缺口/);
  assert.equal(a.v8CanHandoff(), false);
  a.click({ v6Handoff: '1' }); assert.equal(x.handoff, false);
});
test('invalid attribute IDs and unexplained aggregations are rejected', () => {
  const a = build(); dwd(a); explainMissing(a);
  a.change('', 'other-version-attribute', { v7Row: '0' }); a.click({ v7Mapconfirm: '1' });
  assert.equal(a.v7MappingCurrent(), false);
  a.change('', 'LA-101', { v7Row: '0' }); a.change('', 'aggregate', { v8Kind: '0' });
  a.click({ v7Mapconfirm: '1' }); assert.equal(a.v7MappingCurrent(), false);
});
test('saved mapping carries exact source identity and retains immutable explanations', () => {
  const a = build(); dwd(a); explainMissing(a); save(a);
  const rec = a.v6Layer().saved[0].logicalMapping;
  assert.equal(rec.mappings[0].sourceRef, 'PM-701'); assert.equal(rec.mappings[0].sourceVersion, 'V2.1');
  const old = rec.mappings[1].explanation;
  a.input('', '新原因', { v8Reason: '1' });
  assert.equal(rec.mappings[1].explanation, old); assert.equal(a.v7MappingCurrent(), false);
});
test('logical snapshot includes name and ID, old version does not follow current edits', () => {
  const a = build(); a.click({ v7Freeze: '1' }); const old = a.v7Current().versions[0];
  a.input('v7-name', '订单业务的新名称'); a.click({ v7Freeze: '1' });
  assert.equal(old.modelId, 'LM-016'); assert.equal(old.name, '订单支付业务事实');
  assert.equal(a.v7Current().versions[1].name, '订单业务的新名称');
  a.click({ v7Freeze: '1' }); assert.equal(a.v7Current().versions.length, 2);
});
test('duplicate logical attribute codes prevent a misleading frozen version', () => {
  const a = build(); a.input('', 'ORDER_ID', { v7Attr: '1', v7Key: 'code' }); a.click({ v7Freeze: '1' });
  assert.equal(a.v7Current().versions.length, 0);
});
test('ODS retains source-first path; stale saved versions cannot prepare handoff', () => {
  const a = build(); a.click({ v7Ods: '1' }); a.click({ v6Load: '1' }); a.click({ v6Check: '1' }); a.click({ v6Save: '1' });
  assert.equal(a.v7Current().versions.length, 0); assert.equal(a.v8CanHandoff(), true);
  a.click({ v6Handoff: '1' }); assert.equal(a.v6Layer().handoff, true);
  a.input('', 'order_business_id', { v6Row: '0', v6Key: 'name' });
  assert.equal(a.v8CanHandoff(), false); assert.doesNotMatch(a.view(), /交接包引用/);
});
test('duplicate target names introduced after load are rejected', () => {
  const a = build(); a.click({ v7Ods: '1' }); a.click({ v6Load: '1' });
  a.input('', 'ORDER_ID', { v6Row: '1', v6Key: 'name' }); a.click({ v6Check: '1' }); a.click({ v6Save: '1' });
  assert.equal(a.v6Layer().saved.length, 0);
});
test('DWD does not silently inherit source-owned ODS technical fields', () => {
  const a = build(); dwd(a);
  assert.equal(a.v6Layer().rows.some(r => r.name === 'etl_time'), false);
  assert.equal(a.v6Layer().rows.some(r => r.name === 'item_id'), true);
});
test('read-only role does not alter dependencies, fields, candidates or grain', () => {
  const a = build(); dwd(a); const x = a.v6Layer(), before = JSON.stringify(x), map = JSON.stringify(a.v7MappingState());
  a.state.role = 'reader';
  a.click({ v6Dep: 'PM-710' }); a.click({ v6Load: '1' }); a.click({ v7Candidates: '1' });
  a.input('v6-grain', '非法更改'); a.change('', 'deferred', { v8Kind: '0' }); a.change('', '', { v7Row: '0' });
  assert.equal(JSON.stringify(x), before); assert.equal(JSON.stringify(a.v7MappingState()), map);
});
test('source version and DIM history policy are part of the design signature', () => {
  const a = build(); a.click({ v7Ods: '1' }); a.click({ v6Load: '1' }); a.click({ v6Check: '1' });
  a.v6Sources['src.order_detail'].version = 'HARVEST-NEW'; a.click({ v6Save: '1' }); assert.equal(a.v6Layer().saved.length, 0);
  const b = build(); b.click({ v7Design: 'DIM' }); b.change('v6-scd', 'SCD1');
  assert.equal(b.v6Layer().revision, 1); assert.match(b.view(), /<option selected>SCD1/);
});
test('home offers independent consumer and operator entries without fictitious percentages', () => {
  const a = build('home'); assert.doesNotMatch(a.view(), /v7Meter|width:.*%/);
  a.click({ v8Mode: 'consume' }); a.click({ v8Next: 'product' }); assert.equal(a.state.page, 'product');
  a.click({ page: 'home' }); a.click({ v8Mode: 'operate' }); a.state.select.governance = 1;
  a.click({ v8Next: 'drift' }); assert.match(a.view(), /处理当前任务 · GOV-320/);
});
test('home excludes other peoples work from my queue and removes obsolete placeholder badge', () => {
  const a = build('home'); a.change('v7-home-role', 'business');
  assert.match(a.view(), /没有匹配的演示待办/); assert.doesNotMatch(a.view(), /v7HomeStats/);
});
test('Dataset and Service present separate contracts and simulated receipts bind exact versions', () => {
  const a = build('product'); assert.match(a.view(), /stat_date/); a.click({ v8Consume: '1' });
  assert.equal(a.v8.receipts[0].key, 'DATASET:DEMO-DS-901'); assert.equal(a.v8.receipts[0].version, 'DEMO-DV-3');
  a.click({ v8Product: 'service' }); assert.equal(a.v8.result, null); assert.match(a.view(), /GET \/demo\/order-stats/);
  a.click({ v8Consume: '1' }); assert.equal(a.v8.receipts[1].key, 'DATA_SERVICE:DEMO-SVC-902');
});
test('unknown runtime, forbidden access and unavailable access provider never generate a receipt', () => {
  for (const [id, value] of [['v8-runtime','unknown'],['v8-access','forbidden'],['v8-access','unavailable']]) {
    const a = build('product'); a.change(id, value); a.click({ v8Consume: '1' }); assert.equal(a.v8.receipts.length, 0);
    if (value === 'unavailable') assert.match(a.view(), /不能解释为拒绝/);
  }
});
test('usage outage preserves successful mock receipt while reporting unknown actual usage', () => {
  const a = build('product'); a.click({ v8Consume: '1' }); a.change('v8-usage', 'unavailable');
  assert.equal(a.v8.receipts.length, 1); assert.match(a.view(), /暂不可读取，数量未知/);
  assert.doesNotMatch(a.view(), /实际 Usage：0/);
});


test('S01: #513 seven independent role/task entries expose correct journey and story IDs',()=>{
 const a=build('home'),modes=[
 ['build','GM-J1-01—16'],['consume','GM-J3-01—11'],['operate','GM-J4-01—07'],
 ['define','GM-J2-01—09'],['secure','GM-J5-01—07'],['assist','GM-J1-16'],
 ['ask','GM-J6-01—08'],['mdm','GM-MDM-01—06']
 ];
 for(const [mode,story] of modes){a.click({v8Mode:mode});assert.equal(a.v8.mode,mode);assert.ok(a.view().includes(story),mode+': '+story);}
 assert.match(a.view(),/黄金记录需审批/);
});
test('S02: J1 AI-assisted construction is distinct from J6 protected evidence-only Q&A',()=>{
 const a=build('home');a.click({v8Mode:'assist'});
 assert.match(a.view(),/HARVEST-20261012/);assert.match(a.view(),/人工确认/);
 assert.equal(a.v81.aiAnswers.review,undefined);
 a.click({v81Ai:'review'});assert.equal(a.v81.aiAnswers.review,true);
 assert.equal(a.v6.records.length,3); // no new Truth or process mutation
 a.click({v8Mode:'ask'});
 assert.match(a.view(),/没有授权查询响应/);
 assert.match(a.view(),/不生成/);
 assert.doesNotMatch(a.view(),/真实成功的 AI 查询回执/);
});
test('S03: independent physical model target identities isolate same DWD layer drafts',()=>{
 const a=build('model');a.click({v7Design:'DWD'});const first=a.v81Current().id;
 a.click({v6Load:'1'});const firstCount=a.v6Layer().rows.length;
 a.click({v81New:'1'});const second=a.v81Current().id;
 assert.notEqual(first,second);assert.equal(a.v6Layer().rows.length,0);
 a.click({v6Load:'1'});assert.equal(a.v6Layer().rows.length,firstCount);
 a.input('', 'second_model_field',{v6Row:'0',v6Key:'name'});
 a.click({v81Target:first});
 assert.equal(a.v81Current().id,first);assert.equal(a.v6Layer().rows[0].name,'order_id');
 a.click({v81Target:second});assert.equal(a.v6Layer().rows[0].name,'second_model_field');
});
test('S04: two same-layer targets cannot borrow each others reviewed mapping',()=>{
 const a=build('model');a.click({v7Design:'DWD'});const first=a.v81Current().id;
 a.click({v7Freeze:'1'});a.click({v6Load:'1'});
 a.click({v7Mode:'mapping'});a.click({v7Candidates:'1'});
 const mapFirst=a.v7MappingState();a.click({v81New:'1'});
 assert.equal(a.v7MappingState().reviewed,false);
 assert.notEqual(a.v7MappingState(),mapFirst);
 a.click({v81Target:first});assert.equal(a.v7MappingState(),mapFirst);
});
test('S05: no saved and unresolved design means no jump pretending to be DataDev task',()=>{
 const a=build('model');a.click({v7Design:'DWD'});a.click({v81Dev:'1'});
 assert.equal(a.v81.handoff,null);assert.equal(a.state.page,'model');
});
test('S06: ODS can hand off only a snapshot with project, physical ID and missing execution',()=>{
 const a=build('model');a.click({v7Ods:'1'});
 a.click({v6Load:'1'});a.click({v6Check:'1'});a.click({v6Save:'1'});
 assert.equal(a.v6Layer().saved[0].physicalModelId,a.v81Current().id);
 a.click({v81Dev:'1'});assert.equal(a.state.page,'dev');
 assert.equal(a.v81.handoff.projectId,'DEMO-RETAIL');
 assert.equal(a.v81.handoff.physicalModelId,a.v81Current().id);
 assert.match(a.view(),/没有 TaskRevision/);
 assert.match(a.view(),/目标连接/);
});
test('S07: DataDev handoff can return to exact source model and no other target',()=>{
 const a=build('model');a.click({v7Ods:'1'});a.click({v6Load:'1'});
 a.click({v6Check:'1'});a.click({v6Save:'1'});
 const key=a.v81Current().id;a.click({v81Dev:'1'});
 a.click({v81Backmodel:key});assert.equal(a.state.page,'model');
 assert.equal(a.v81Current().id,key);
});
test('S08: consumer feedback carries exact ProductKey/type/version instead of generic issue',()=>{
 const a=build('product');
 a.click({v81Productissue:'1'});
 assert.equal(a.state.page,'collab');
 assert.equal(a.v6.collab.view,'issue');
 assert.deepEqual(a.v6.collab.source,{id:'DATASET:DEMO-DS-901',version:'DEMO-DV-3',kind:'Dataset'});
 assert.match(a.view(),/DATASET:DEMO-DS-901/);
});
test('S09: service feedback preserves Service ID and version separately from Dataset',()=>{
 const a=build('product');a.click({v8Product:'service'});
 a.click({v81Productissue:'1'});
 assert.equal(a.v6.collab.source.id,'DATA_SERVICE:DEMO-SVC-902');
 assert.equal(a.v6.collab.source.version,'DEMO-SR-2');
});
test('S10: homepage can resume precise second DWD target rather than just DWD layer',()=>{
 const a=build('model');a.click({v7Design:'DWD'});a.click({v81New:'1'});
 const id=a.v81Current().id;a.click({v6Load:'1'});
 a.click({page:'home'});assert.match(a.view(),new RegExp(id));
 a.click({v81Resume:id});assert.equal(a.state.page,'model');
 assert.equal(a.v81Current().id,id);
});
test('S11: readonly professional cannot add an independent physical target',()=>{
 const a=build('model');a.state.role='reader';
 const n=Object.keys(a.v81.byId).length;
 a.click({v81New:'1'});assert.equal(Object.keys(a.v81.byId).length,n);
});
test('S12: homepage cross-journey actions reach correct domain instead of another story by default',()=>{
 const a=build('home');
 a.click({v8Mode:'define'});a.click({v8Next:'metric'});assert.equal(a.state.page,'metric');
 a.click({page:'home'});a.click({v8Mode:'secure'});a.click({v8Next:'security'});assert.equal(a.state.page,'security');
 a.click({page:'home'});a.click({v8Mode:'mdm'});a.click({v8Next:'mdm'});assert.equal(a.state.page,'mdm');
});

test('VR01 feedback draft cannot edit the prior issue',()=>{
 const a=build('product');a.click({v8Product:'service'});
 const before=JSON.stringify(a.v6.records);a.click({v81Productissue:'1'});
 assert.match(a.view(),/当前操作：新建数据问题/);
 assert.doesNotMatch(a.view(),/当前记录 · ISS-201/);
 a.v6.collab.note='接口调用一直超时，请核对合同';a.click({v6Action:'comment'});
 assert.equal(JSON.stringify(a.v6.records),before);
 a.input('v6-newdetail','接口连续超时，希望按原合同恢复');
 a.click({v6Create:'1'});
 assert.equal(a.v6.records.length,4);
 const x=a.v6.records.at(-1);
 assert.equal(x.source,'DATA_SERVICE:DEMO-SVC-902');
 assert.equal(x.sourceVersion,'DEMO-SR-2');
 assert.equal(x.sourceKind,'Data Service');
 assert.equal(a.v6.collab.selected,x.id);
 a.click({v81FeedbackReturn:'1'});
 assert.equal(a.state.page,'product');assert.equal(a.v8.product,'service');
 a.click({page:'collab'});a.click({v6Case:x.id});
 assert.match(a.view(),new RegExp(x.id));
});
test('VR02 same target logical rebind immediately updates identity and survives switch',()=>{
 const a=build('model');a.click({v7Design:'DWD'});
 const first=a.v81Current().id;
 a.click({v6Load:'1'});a.click({v7Mode:'mapping'});
 a.change('v7-layer-model','LM-019');
 assert.equal(a.v81Current().logicalId,'LM-019');
 assert.equal(a.v7.targetByLayer.DWD,'LM-019');
 assert.equal(a.v6Layer().dirty,true);
 a.click({v81New:'1'});a.click({v81Target:first});
 assert.equal(a.v81Current().logicalId,'LM-019');
 assert.match(a.view(),/LM-019/);
});

test('VR03 home sums all independently saved physical targets even when inactive',()=>{
 const a=build('model');a.click({v7Ods:'1'});
 a.click({v6Load:'1'});a.click({v6Check:'1'});a.click({v6Save:'1'});
 const first=a.v81Current().id;a.click({v81New:'1'});
 const second=a.v81Current().id;
 a.click({page:'home'});assert.match(a.view(),/设计：1 个本地快照/);
 a.click({v81Resume:second});
 a.click({v6Load:'1'});a.click({v6Check:'1'});a.click({v6Save:'1'});
 a.click({page:'home'});assert.match(a.view(),/设计：2 个本地快照/);
 a.click({v81Resume:first});a.click({page:'home'});
 assert.match(a.view(),/设计：2 个本地快照/);
});
test('VR04 AI main button opens actionable evidence panel with focus anchor',()=>{
 const a=build('home');a.click({v8Mode:'assist'});
 assert.doesNotMatch(a.view(),/id="v81-evidence"/);
 a.click({v8Next:'assist-desk'});
 assert.match(a.view(),/id="v81-evidence" tabindex="-1"/);
 assert.match(a.view(),/AI 建设证据与人工决策/);
 assert.match(a.view(),/aria-expanded="true"/);
 a.click({v81CloseEvidence:'1'});
 assert.doesNotMatch(a.view(),/id="v81-evidence"/);
 a.click({v8Mode:'ask'});a.click({v8Next:'ask-desk'});
 assert.match(a.view(),/可信 AI 问答 · 取证条件/);
 assert.match(a.view(),/当前结论：证据不足/);
 assert.doesNotMatch(a.view(),/真实查询成功/);
});
