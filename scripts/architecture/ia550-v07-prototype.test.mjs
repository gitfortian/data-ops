import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const html = readFileSync('docs/product/experience/prototypes/IA-550-task-flows-v0.7.html', 'utf8');
const code = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(code, 'v0.7 must contain its own standalone interaction implementation');
const fixture = code.replace('var start=decodeURIComponent',
  'document.__testContext={state,v3,v4,v5,v51,v6,v7,v7Current,v7MappingState,v7MappingCurrent,v6Layer,v6Sources,v6CurrentCase,v6RoleNames,v3Rows,v3Selected};var start=decodeURIComponent');

function build(page = 'semantic') {
  const elements = new Map();
  const listeners = new Map();
  const byId = id => {
    if (!elements.has(id)) {
      elements.set(id, {
        innerHTML: '', textContent: '', value: '',
        classList: { add() {}, remove() {} }, setAttribute() {},
        addEventListener() {}, focus() {}, setSelectionRange() {},
        querySelector() { return null; },
      });
    }
    return elements.get(id);
  };
  const document = {
    getElementById: byId,
    addEventListener(name, handler) {
      const values = listeners.get(name) ?? [];
      values.push(handler);
      listeners.set(name, values);
    },
  };
  const window = {
    location: { hash: '#' + page }, scrollY: 0,
    addEventListener() {}, scrollTo() {},
  };
  new Function('document', 'window', 'setTimeout', 'clearTimeout', fixture)(
    document, window, () => 0, () => {}
  );
  const dispatch = (kind, event) => {
    for (const listener of listeners.get(kind) ?? []) listener(event);
  };
  return {
    state: document.__testContext.state,
    v3: document.__testContext.v3,
    v4: document.__testContext.v4,
    v5: document.__testContext.v5,
    v51: document.__testContext.v51,
    v6: document.__testContext.v6,
    v7: document.__testContext.v7,
    logical: document.__testContext.v7Current,
    mapping: document.__testContext.v7MappingState,
    mappingCurrent: document.__testContext.v7MappingCurrent,
    layer: document.__testContext.v6Layer,
    collabCase: document.__testContext.v6CurrentCase,
    sources: document.__testContext.v6Sources,
    rows: document.__testContext.v3Rows,
    view: () => byId('main').innerHTML,
    dialog: () => byId('dialog').innerHTML,
    click: data => dispatch('click', { target: { closest() { return { dataset: data }; } } }),
    input: (id, value, dataset) => dispatch('input', { target: { id, value, dataset, selectionStart: value.length } }),
    change: (id, value) => dispatch('change', { target: { id, value } }),
    key: key => dispatch('keydown', { key, ctrlKey: false, metaKey: false }),
  };
}



test('L00: standalone v0.7 introduces first-class logical design while retaining five domains',()=>{
 assert.doesNotThrow(()=>new Function(code));
 for(const page of ['model','collab','semantic','standard','metric','asset','governance','tasks','home','dev','dataset']){
  assert.ok(build(page).view().length>300,'missing page '+page);
 }
 const a=build('model');
 assert.match(a.view(),/业务逻辑设计/);
 assert.match(a.view(),/逻辑属性与标准字段/);
 assert.match(a.view(),/订单支付业务事实/);
 assert.equal(a.v7.view,'logical');
});
test('L01: logical entities, attributes and relationships are independent from physical tables',()=>{
 const a=build('model'),m=a.logical();
 assert.equal(m.id,'LM-016');
 assert.equal(m.attrs[0].std,'STD-201');
 assert.match(a.view(),/实体关系及基数/);
 a.click({v7Addattr:'1'});
 assert.equal(m.attrs.length,5);
 a.click({v7Addrelation:'1'});
 assert.equal(m.relations.length,2);
 assert.equal(a.v6.layers.DWD,undefined);
});
test('L02: logical snapshot is independent, immutable and never claims production approval',()=>{
 const a=build('model');a.click({v7Freeze:'1'});
 assert.equal(a.logical().versions.length,1);
 const snapshot=a.logical().versions[0];
 a.input('v7-grain','一笔订单商品支付事件');
 a.click({v7Freeze:'1'});
 assert.equal(a.logical().versions.length,2);
 assert.equal(snapshot.version,'L1');
 assert.notEqual(snapshot.grain,a.logical().versions[1].grain);
 assert.match(a.view(),/未获业务批准/);
});
test('L03: ODS from physical source bypasses logical prerequisite, keeps source and technical columns',()=>{
 const a=build('model');assert.equal(a.logical().versions.length,0);
 a.click({v7Ods:'1'});assert.equal(a.v6.layer,'ODS');
 a.click({v6Load:'1'});assert.equal(a.layer().rows.length,7);
 a.click({v6Check:'1'});assert.equal(a.layer().checked,true);
 a.click({v6Save:'1'});assert.equal(a.layer().saved.length,1);
 assert.equal(a.layer().saved[0].logicalMapping,null);
});
test('L04: DWD cannot validate physical structure before logical version and explicit mapping',()=>{
 const a=build('model');
 a.click({v7Design:'DWD'});a.click({v6Load:'1'});
 a.click({v6Check:'1'});assert.equal(a.layer().checked,false);
 a.click({v7Mode:'mapping'});
 assert.match(a.view(),/未冻结/);
 a.click({v7Mapconfirm:'1'});assert.equal(a.mapping().reviewed,false);
});
test('L05: DWD end-to-end logical snapshot → ODS source fields → mapping review → design save',()=>{
 const a=build('model');a.click({v7Freeze:'1'});
 a.click({v7Design:'DWD'});a.click({v6Load:'1'});
 assert.equal(a.layer().rows[0].name,'order_id');
 a.click({v7Mode:'mapping'});a.click({v7Candidates:'1'});
 assert.equal(a.mapping().logicalVersion,'L1');
 assert.equal(a.mapping().selection[0],'LA-101');
 a.click({v7Mapconfirm:'1'});assert.equal(a.mappingCurrent(),true);
 a.click({v7Mode:'physical'});a.click({v6Check:'1'});a.click({v6Save:'1'});
 assert.equal(a.layer().saved[0].logicalMapping.logicalId,'LM-016');
 assert.equal(a.layer().saved[0].logicalMapping.logicalVersion,'L1');
 assert.equal(a.layer().saved[0].logicalMapping.mappings[0].logicalAttributeId,'LA-101');
});
test('L06: logical draft edits invalidate previous mapping and previously passed physical check',()=>{
 const a=build('model');a.click({v7Freeze:'1'});
 a.click({v7Design:'DWD'});a.click({v6Load:'1'});
 a.click({v7Mode:'mapping'});a.click({v7Candidates:'1'});a.click({v7Mapconfirm:'1'});
 a.click({v7Mode:'physical'});a.click({v6Check:'1'});
 assert.equal(a.layer().checked,true);
 a.click({v7Mode:'logical'});a.input('v7-grain','订单支付事件粒度修订');
 assert.equal(a.mappingCurrent(),false);
 assert.equal(a.layer().checked,false);
 assert.equal(a.v7.mapping.DWD.stale,true);
});
test('L07: physical column changes invalidate snapshot mapping, prevent stale save',()=>{
 const a=build('model');a.click({v7Freeze:'1'});
 a.click({v7Design:'DWD'});a.click({v6Load:'1'});
 a.click({v7Mode:'mapping'});a.click({v7Candidates:'1'});a.click({v7Mapconfirm:'1'});
 a.click({v7Mode:'physical'});a.click({v6Check:'1'});
 a.input('unused','order_business_key',{v6Row:'0',v6Key:'name'});
 a.click({v6Save:'1'});
 assert.equal(a.layer().saved.length,0);
 assert.equal(a.mappingCurrent(),false);
});
test('L08: DIM uses member logical identity rather than order fact',()=>{
 const a=build('model');a.click({v7Logical:'LM-019'});a.click({v7Freeze:'1'});
 a.click({v7Design:'DIM'});a.click({v6Load:'1'});
 a.click({v7Mode:'mapping'});a.click({v7Candidates:'1'});a.click({v7Mapconfirm:'1'});
 assert.equal(a.v7.mapping.DIM.logicalId,'LM-019');
 assert.equal(a.mappingCurrent(),true);
});
test('L09: DWS aggregates from metrics but reuses analytic logical view instead of cloning ODS',()=>{
 const a=build('model');a.click({v7Logical:'LM-026'});a.click({v7Freeze:'1'});
 a.click({v7Design:'DWS'});a.click({v6Load:'1'});
 assert.equal(a.layer().rows[1].rule,'COUNT(DISTINCT order_id)');
 a.click({v7Mode:'mapping'});a.click({v7Candidates:'1'});a.click({v7Mapconfirm:'1'});
 assert.equal(a.mappingCurrent(),true);
 assert.equal(a.v7.mapping.DWS.logicalId,'LM-026');
});
test('L10: independent standard-field to model confirmation remains legacy route, direct model returns logical',()=>{
 const a=build('model');
 a.click({page:'standard'});a.click({v51View:'library'});a.click({select:'2'});
 a.click({v3Action:'standard-attach'});
 assert.equal(a.state.page,'model');assert.equal(a.v7.view,'legacy');
 assert.match(a.view(),/确认目标模型/);
 a.click({page:'model'});assert.equal(a.v7.view,'logical');
 assert.match(a.view(),/逻辑属性与标准字段/);
});
test('L11: collaboration jumps to logical design and returns to exact originating record',()=>{
 const a=build('collab');a.click({v6Pro:'model'});
 assert.equal(a.state.page,'model');assert.equal(a.v7.view,'logical');
 assert.match(a.view(),/业务逻辑设计/);
 a.click({v6Return:'1'});assert.equal(a.state.page,'collab');
 assert.equal(a.v6.collab.selected,'REQ-102');
});
test('L12: readonly user can inspect but not freeze logical model or confirm mapping',()=>{
 const a=build('model');a.state.role='reader';a.click({v7Freeze:'1'});
 assert.equal(a.logical().versions.length,0);
 a.click({v7Design:'DWD'});a.click({v6Load:'1'});
 a.click({v7Mode:'mapping'});a.click({v7Mapconfirm:'1'});
 assert.equal(a.mapping().reviewed,false);
});
