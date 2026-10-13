import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const html = readFileSync('docs/product/experience/prototypes/IA-550-task-flows-v0.6.html', 'utf8');
const code = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(code, 'v0.6 must contain its own standalone interaction implementation');
const fixture = code.replace('var start=decodeURIComponent',
  'document.__testContext={state,v3,v4,v5,v51,v6,v6Layer,v6Sources,v6CurrentCase,v6RoleNames,v3Rows,v3Selected};var start=decodeURIComponent');

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


test('v0.6 standalone keeps full five-domain navigation plus collaboration and new modeling',()=>{
 assert.doesNotThrow(()=>new Function(code));
 for(const id of ['home','tasks','collab','semantic','standard','model','metric','asset','governance','quality','security','mdm','dev','dataset','visual','qualityRun']){
  assert.ok(build(id).view().length>300,'blank route '+id);
 }
 assert.match(build('model').view(),/分层建模/);
 assert.match(build('collab').view(),/需求与协作/);
});
test('M01 ODS begins with physical source; imports five physical and two optional technical columns',()=>{
 const a=build('model');assert.match(a.view(),/sales\.order_detail/);
 assert.equal(a.layer().loaded,false);a.click({v6Load:'1'});
 assert.equal(a.layer().rows.length,7);
 assert.equal(a.layer().rows[0].source,'src.order_detail');
 assert.equal(a.layer().rows.find(x=>x.name==='dt').source,'系统技术列');
});
test('M02 ODS technical partition toggle preserves original source columns',()=>{
 const a=build('model');a.click({v6Load:'1'});a.click({v6Tech:'dt'});
 assert.equal(a.layer().rows.length,6);
 assert.ok(a.layer().rows.some(x=>x.name==='pay_amount'));
 assert.ok(!a.layer().rows.some(x=>x.name==='dt'));
});
test('M03 DWD loads dependency fields; manual changes survive source merging',()=>{
 const a=build('model');a.click({v6Layer:'DWD'});a.click({v6Load:'1'});
 a.input('ignored','my_manual_amount',{v6Row:'2',v6Key:'name'});
 a.click({v6Dep:'PM-710'});
 assert.ok(a.layer().pending);
 a.click({v6Decision:'merge'});
 assert.ok(a.layer().rows.some(x=>x.name==='my_manual_amount'));
 assert.ok(a.layer().rows.some(x=>x.name==='payment_id'));
});
test('M04 DWD two ODS upstream duplicate field is not silently accepted',()=>{
 const a=build('model');a.click({v6Layer:'DWD'});a.click({v6Dep:'PM-710'});a.click({v6Load:'1'});
 assert.ok(a.layer().conflicts.includes('order_id'));
 a.click({v6Check:'1'});
 assert.equal(a.layer().checked,false);
 a.click({v6Conflict:'keep'});a.click({v6Check:'1'});
 assert.equal(a.layer().conflicts.length,0);
 assert.equal(a.layer().checked,true);
});
test('M05 rerunning dependency load with unsaved edits requires explicit choice',()=>{
 const a=build('model');a.click({v6Load:'1'});
 a.input('ignored','order_manual',{v6Row:'0',v6Key:'name'});
 a.click({v6Load:'1'});
 assert.equal(a.layer().pendingReload,true);
 assert.equal(a.layer().rows[0].name,'order_manual');
 a.click({v6Reload:'cancel'});assert.equal(a.layer().rows[0].name,'order_manual');
});
test('M06 DIM has its own model and SCD path independent from DWD',()=>{
 const a=build('model');a.click({v6Layer:'DIM'});a.click({v6Load:'1'});
 assert.equal(a.layer().rows[0].name,'customer_id');
 assert.match(a.view(),/SCD/);
});
test('M07 DWS infers candidate aggregation from metric, not wholesale copied DWD columns',()=>{
 const a=build('model');a.click({v6Layer:'DWS'});a.click({v6Load:'1'});
 assert.equal(a.layer().rows[1].rule,'COUNT(DISTINCT order_id)');
 a.change('v6-metric','MET-027');assert.ok(a.layer().pendingOption);
 a.click({v6Option:'replace'});a.click({v6Load:'1'});
 assert.equal(a.layer().rows[1].rule,'SUM(pay_amount)');
});
test('M08 ADS accepts DWD direct dependency when consumer expects detail API',()=>{
 const a=build('model');a.click({v6Layer:'ADS'});
 a.click({v6Dep:'PM-832'});a.click({v6Dep:'PM-732'});
 a.change('v6-consumer','订单明细 API');a.click({v6Load:'1'});
 assert.deepEqual(a.layer().deps,['PM-732']);
 assert.ok(a.layer().rows.some(x=>x.name==='order_id'));
});
test('M09 modeling version saved does not imply deployed or executed DataDev',()=>{
 const a=build('model');a.click({v6Load:'1'});a.click({v6Check:'1'});a.click({v6Save:'1'});
 assert.equal(a.layer().saved.length,1);
 assert.equal(a.layer().handoff,false);
 a.click({v6Handoff:'1'});assert.equal(a.layer().handoff,true);
 assert.match(a.view(),/没有真实执行/);
});
test('C01 role-specific demand acceptance is not automatic delivery',()=>{
 const a=build('collab');a.change('v6-role','coordinator');a.click({v6Action:'accept'});
 a.input('v6-note','明确订单日报日期范围与业务验收标准');a.click({v6Action:'scope'});
 a.change('v6-role','owner');a.click({v6Action:'confirmScope'});
 assert.equal(a.collabCase().status,'建设中');assert.equal(a.collabCase().delivered,false);
});
test('C02 demand can create professional tasks, acceptance separate from delivery',()=>{
 const a=build('collab');a.change('v6-role','coordinator');a.click({v6Action:'accept'});
 a.input('v6-note','确认交付字段与验收范围');a.click({v6Action:'scope'});
 a.change('v6-role','owner');a.click({v6Action:'confirmScope'});
 a.change('v6-role','builder');a.click({v6Action:'addTask'});a.click({v6Action:'attach'});
 a.input('v6-note','来源校验与业务订单样例都已复查');a.click({v6Action:'test'});
 assert.equal(a.collabCase().status,'待验收');
 a.change('v6-role','owner');a.click({v6Action:'acceptDelivery'});
 assert.equal(a.collabCase().status,'待交付');
 a.change('v6-role','builder');a.input('v6-note','完成规定范围的模拟交付说明');a.click({v6Action:'deliver'});
 assert.equal(a.collabCase().status,'已完成');
});
test('C03 issue can be investigated, resolved, business reviewed and reopened',()=>{
 const a=build('collab');a.click({v6View:'issue'});a.change('v6-role','builder');
 a.input('v6-note','检查来源晚到数据与口径边界');a.click({v6Action:'investigate'});
 a.click({v6Action:'issueStart'});a.change('v6-role','steward');
 a.input('v6-note','修复样例并附带检查与复核证据');a.click({v6Action:'issueResolve'});
 a.change('v6-role','business');a.click({v6Action:'issueClose'});
 a.input('v6-note','原日期样本重新查询仍有异常');a.click({v6Action:'issueReopen'});
 assert.equal(a.collabCase().status,'核实中');
 assert.ok(a.collabCase().log.some(x=>x.what.includes('重开')));
});
test('C04 metric approver cannot confirm without analyst; new version loses prior approval',()=>{
 const a=build('collab');a.click({v6View:'caliber'});a.change('v6-role','owner');
 a.click({v6Action:'approve'});assert.equal(a.collabCase().businessConfirmed,false);
 a.change('v6-role','analyst');a.click({v6Action:'sample'});
 a.change('v6-role','owner');a.click({v6Action:'approve'});
 assert.equal(a.collabCase().businessConfirmed,true);
 a.change('v6-role','builder');a.input('v6-note','与当前版本样例和数据输出完成技术复查');
 a.click({v6Action:'caliberTest'});a.click({v6Action:'caliberHandoff'});
 a.input('v6-note','新增退款后回减要求涉及实质口径变化');a.click({v6Action:'newCaliber'});
 assert.equal(a.collabCase().businessConfirmed,false);
 assert.match(a.collabCase().version,/v1\.3/);
});
test('C05 professional reader can still submit business requirement in independent collaboration',()=>{
 const a=build('collab');a.state.role='reader';a.change('v6-role','business');
 a.input('v6-newtitle','售后退款趋势分析需求');
 a.input('v6-newdetail','业务需要按月查看实际退款金额变化');
 a.click({v6Create:'1'});
 assert.equal(a.v6.records.length,4);
 assert.equal(a.collabCase().type,'demand');
});
test('C06 collaboration opens specialized model and returns to exact original record',()=>{
 const a=build('collab');a.click({v6Pro:'model'});
 assert.equal(a.state.page,'model');assert.match(a.view(),/来自协作事项 REQ-102/);
 a.click({v6Return:'1'});
 assert.equal(a.state.page,'collab');assert.equal(a.v6.collab.selected,'REQ-102');
});
test('C07 feedback from governed asset creates scoped issue source, no asset mutation',()=>{
 const a=build('asset');a.click({v6Report:'asset'});
 assert.equal(a.state.page,'collab');assert.equal(a.v6.collab.view,'issue');
 assert.equal(a.v6.collab.source.id,'AST-208');
});
