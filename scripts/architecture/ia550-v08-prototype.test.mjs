import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const html = readFileSync('docs/product/experience/prototypes/IA-550-task-flows-v0.8.html', 'utf8');
const code = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(code);
const fixture = code.replace('var start=decodeURIComponent',
  'document.__context={state,v6,v7,v8,v7Current,v7MappingState,v7MappingCurrent,v6Layer,v8CanHandoff,v8MappingFindings,v6Sources};var start=decodeURIComponent');

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
