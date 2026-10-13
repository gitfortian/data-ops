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
 assert.match(a.view(),/当前最新逻辑版本/);
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


test('H00: new home replaces obsolete placeholder with task-first project work area',()=>{
 const a=build('home');
 assert.match(a.view(),/订单分析项目 · 数据工作总览/);
 assert.match(a.view(),/待推进与需反馈/);
 assert.match(a.view(),/订单分析 · 从来源到交付/);
 assert.match(a.view(),/继续未完成工作/);
 assert.match(a.view(),/五个专业域/);
 assert.doesNotMatch(a.view(),/OUT OF CURRENT REBUILD SCOPE/);
});
test('H01: home always identifies simulated figures, not real workflow counters',()=>{
 const a=build('home');
 assert.match(a.view(),/可交互设计样例，不代表已连接项目的真实统计或运行回执/);
 assert.match(a.view(),/不是跨领域实时完成百分比/);
 assert.match(a.view(),/模型.*入仓/);
});
test('H02: home role selector changes actionable queue without changing professional permission',()=>{
 const a=build('home');
 const before=a.state.role;
 a.change('v7-home-role','owner');
 assert.equal(a.v6.collab.role,'owner');
 assert.equal(a.state.role,before);
 assert.match(a.view(),/业务确认人/);
});
test('H03: home provides exact collaboration record jump and project demand start',()=>{
 const a=build('home');
 a.click({v7Homeitem:'ISS-201'});
 assert.equal(a.state.page,'collab');
 assert.equal(a.v6.collab.selected,'ISS-201');
 a.click({page:'home'});a.click({v7Homeflow:'demand'});
 assert.equal(a.state.page,'collab');
 assert.equal(a.v6.collab.view,'demand');
});
test('H04: home task first entry opens real logical workbench and ODS direct route',()=>{
 const a=build('home');
 a.click({v7Homeflow:'logical'});
 assert.equal(a.state.page,'model');
 assert.equal(a.v7.view,'logical');
 assert.match(a.view(),/业务实体|业务事实/);
 a.click({page:'home'});a.click({v7Homeflow:'source'});
 assert.equal(a.state.page,'source');
});
test('H05: home resumes latest locally edited logical version at correct model',()=>{
 const a=build('model');
 a.click({v7Freeze:'1'});a.click({page:'home'});
 assert.match(a.view(),/LM-016/);
 assert.match(a.view(),/L1/);
 a.click({v7Homeflow:'logical',v7Homelogical:'LM-016'});
 assert.equal(a.state.page,'model');
 assert.equal(a.v7.view,'logical');
 assert.equal(a.logical().id,'LM-016');
});
test('H06: home resumes layer-specific physical structure without losing rows',()=>{
 const a=build('model');
 a.click({v7Design:'DWD'});
 a.click({v6Load:'1'});
 const len=a.layer().rows.length;
 a.click({page:'home'});
 assert.match(a.view(),/DWD · 未保存结构草稿/);
 a.click({v7Homeflow:'physical',v7Homelayer:'DWD'});
 assert.equal(a.v6.layer,'DWD');
 assert.equal(a.v7.view,'physical');
 assert.equal(a.layer().rows.length,len);
});
test('H07: full logical→physical version workflow visible via new homepage entry',()=>{
 const a=build('home');
 a.click({v7Homeflow:'logical'});a.click({v7Freeze:'1'});
 a.click({v7Design:'DWD'});a.click({v6Load:'1'});
 a.click({v7Mode:'mapping'});a.click({v7Candidates:'1'});a.click({v7Mapconfirm:'1'});
 assert.equal(a.mappingCurrent(),true);
 a.click({v7Mode:'physical'});a.click({v6Check:'1'});a.click({v6Save:'1'});
 assert.equal(a.layer().saved.length,1);
 assert.equal(a.layer().saved[0].logicalMapping.logicalId,'LM-016');
});
test('H08: ODS source-first remains available from project home without frozen logical model',()=>{
 const a=build('home');
 a.click({v7Homeflow:'logical'});a.click({v7Ods:'1'});
 a.click({v6Load:'1'});a.click({v6Check:'1'});
 assert.equal(a.logical().versions.length,0);
 assert.equal(a.layer().checked,true);
});
test('H09: responsive home layout rules exist for desktop, compact and narrow widths',()=>{
 assert.match(html,/\.v7HomeGrid\{display:grid;grid-template-columns:/);
 assert.match(html,/@media\(max-width:980px\)\{\.v7HomeGrid\{grid-template-columns:minmax\(0,1fr\)/);
 assert.match(html,/@media\(max-width:700px\)\{\.v7StageGrid\{grid-template-columns:repeat\(2/);
 assert.match(html,/@media\(max-width:440px\)\{\.v7HomeStats\{grid-template-columns:/);
});


test('H10: home project scale and operation counters explicitly require live owner APIs',()=>{
 const a=build('home');
 assert.match(a.view(),/项目规模与运行概况/);
 assert.match(a.view(),/DataSource \/ Metadata/);
 assert.match(a.view(),/Modeling/);
 assert.match(a.view(),/Metric/);
 assert.match(a.view(),/DataDev \/ Runtime/);
 assert.match(a.view(),/不伪造数量/);
});
test('L13: new logical model starts empty and must define attributes before freezing',()=>{
 const a=build('model');a.click({v7Newlogic:'1'});
 assert.equal(a.v7.selected,'LOCAL-LM-001');
 assert.equal(a.logical().attrs.length,0);
 a.click({v7Freeze:'1'});assert.equal(a.logical().versions.length,0);
 assert.match(a.view(),/归属业务过程/);
});
test('L14: new logical entity from Semantic process freezes its own editable business model',()=>{
 const a=build('model');a.click({v7Newlogic:'1'});
 a.input('v7-name','售后退款业务事实');
 a.change('v7-process','PRC-103');
 a.input('v7-grain','一行代表一次退款明细');
 a.click({v7Addattr:'1'});
 a.click({v7Freeze:'1'});
 assert.equal(a.logical().name,'售后退款业务事实');
 assert.equal(a.logical().process,'PRC-103');
 assert.equal(a.logical().versions[0].process,'PRC-103');
});
test('L15: logical relation target and cardinality support explicit edit, never inferred',()=>{
 const a=build('model');a.click({v7Addrelation:'1'});
 a.input('x','订单退款事实',{v7Rel:'1',v7Key:'to'});
 a.change('x','一对多 · 人工确认',{v7Rel:'1',v7Key:'cardinality'});
 assert.equal(a.logical().relations[1].to,'订单退款事实');
 assert.equal(a.logical().relations[1].cardinality,'一对多 · 人工确认');
});
test('L16: physical layer can explicitly choose another logical model before mapping',()=>{
 const a=build('model');
 a.click({v7Freeze:'1'});a.click({v7Design:'DWD'});
 a.click({v6Load:'1'});a.click({v7Mode:'mapping'});
 a.change('v7-layer-model','LM-019');
 assert.equal(a.v7.targetByLayer.DWD,'LM-019');
 assert.equal(a.mappingCurrent(),false);
 assert.match(a.view(),/会员业务实体/);
});
test('L17: modified logical draft must freeze another version before mapping review',()=>{
 const a=build('model');a.click({v7Freeze:'1'});a.click({v7Design:'DWD'});
 a.click({v6Load:'1'});a.click({v7Mode:'mapping'});
 a.click({v7Candidates:'1'});a.click({v7Mapconfirm:'1'});
 assert.equal(a.mappingCurrent(),true);
 a.click({v7Mode:'logical'});a.input('v7-grain','修订后的订单事实粒度');
 a.click({v7Mode:'mapping'});a.click({v7Candidates:'1'});a.click({v7Mapconfirm:'1'});
 assert.equal(a.mappingCurrent(),false);
 assert.equal(a.mapping().reviewed,false);
});
