import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const html = readFileSync('docs/product/experience/prototypes/IA-550-task-flows-v0.4.html', 'utf8');
const code = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(code, 'v0.4 must contain its own standalone interaction implementation');
const fixture = code.replace('var start=decodeURIComponent',
  'document.__testContext={state,v3,v4,v3Rows,v3Selected};var start=decodeURIComponent');

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
    rows: document.__testContext.v3Rows,
    view: () => byId('main').innerHTML,
    dialog: () => byId('dialog').innerHTML,
    click: data => dispatch('click', { target: { closest() { return { dataset: data }; } } }),
    input: (id, value) => dispatch('input', { target: { id, value, selectionStart: value.length } }),
    change: (id, value) => dispatch('change', { target: { id, value } }),
    key: key => dispatch('keydown', { key, ctrlKey: false, metaKey: false }),
  };
}

test('v0.4 standalone HTML script parses and all complete-menu entries can open', () => {
  assert.doesNotThrow(() => new Function(code));
  for (const id of [
    'home','tasks','source','sync','file','semantic','standard','model','metric',
    'dev','workflow','release','asset','governance','quality','security','lifecycle',
    'mdm','product','dataset','api','visual','userSetting','departmentSetting',
    'roleSetting','projectSetting','approvalSetting','logSetting','metricImpact',
    'metricLineage','layerConfig','metadata','lineage','qualityRun','qualityRule',
    'taxonomy','securityAudit','harvestExternal','sqlAudit','syncRun',
    'runHistory','instance','releaseDiag','callers','apiObserve','apiLog',
  ]) assert.ok(build(id).view().length > 300, 'empty navigation: ' + id);
});

test('1: published payment metric and refund metric keep independent state', () => {
  const a = build();
  a.click({page:'metric'}); a.input('v3-condition','status = PAID');
  for (const action of ['metric-validate','metric-impact','metric-to-publish','metric-publish'])
    a.click({v3Action:action});
  assert.match(a.view(), /DEMO-METRIC-PUBLICATION-MET-026/);
  a.input('localSearch','退款');
  assert.equal(a.v3.metric.id,'MET-035');
  assert.equal(a.v3.metric.formula,'SUM(refund_amount)');
  assert.doesNotMatch(a.view(), /DEMO-METRIC-PUBLICATION-MET-026/);
  a.input('localSearch','');a.click({select:'0'});
  assert.equal(a.v3.metric.receipts.length,1);
  assert.equal(a.v3.metric.receipts[0].metricId,'MET-026');
});

test('2: switching a partially completed governance task requires explicit resolution', () => {
  const a = build();
  a.click({page:'governance'}); a.click({select:'0'});
  a.click({v3Action:'queue-resolve'});a.change('v3-queue-decision','transfer');
  a.change('v3-transfer','业务负责人');a.input('v3-queue-note','确认订单分类并转交处理');
  a.input('localSearch','商品');
  assert.match(a.view(),/保留当前草稿并切换/);
  a.click({v4Switch:'keep'});
  assert.equal(a.v4.active.governance,'GOV-314');
  assert.equal(a.v3.note,'');
  a.input('localSearch','');a.click({select:'0'});
  assert.equal(a.v3.note,'确认订单分类并转交处理');
  assert.equal(a.v3.transferTo,'业务负责人');
});

test('3: editing source invalidates current model validation, not historical saved revision', () => {
  const a = build();a.click({page:'model'});a.click({model:'PM-701'});
  for (const action of ['model-check','model-fix','model-recheck'])
    a.click({v3Action:action});
  a.click({v3Tab:'版本差异'});a.click({v3Action:'model-save'});
  assert.equal(a.v3.build.savedVersions.length,1);
  a.change('v3-source','crm.customer');
  assert.equal(a.v3.build.checked,false);
  assert.equal(a.v3.build.savedVersions.length,1);
  a.click({v3Tab:'版本差异'});
  assert.match(a.view(),/当前草稿未校验或已更改/);
});

test('4: step navigation cannot bypass missing transfer target or short explanation', () => {
  const a = build();a.click({page:'governance'});a.click({select:'0'});
  a.click({v3Action:'queue-resolve'});a.change('v3-queue-decision','transfer');
  a.input('v3-queue-note','一');
  a.click({v3Step:'queue:2'});a.click({v3Action:'queue-submit'});
  assert.equal(a.v3.queueStep,'resolve');
  assert.equal(a.v4.store.governance['GOV-301'].receipts.length,0);
});

test('5: drift category resolves exact source task and clearing restores the complete queue', () => {
  const a = build();a.click({page:'governance'});a.click({queue:'漂移'});
  assert.equal(a.v3.queueId,'GOV-320');
  assert.equal(a.rows('governance').length,1);
  a.click({clear:'1'});assert.equal(a.state.queue,'all');
  assert.equal(a.rows('governance').length,4);
});

test('6: customer model has no invented order-metric relation', () => {
  const a = build();a.click({page:'model'});a.click({model:'PM-733'});
  a.click({v3Action:'model-next-metric'});
  assert.equal(a.v3.scope.id,'PM-733');
  assert.equal(a.rows('metric').length,0);
  assert.match(a.view(),/暂无已核验的关联指标/);
});

test('7: transfer receipts preserve submitted facts even after changing tasks', () => {
  const a = build();a.click({page:'governance'});a.click({select:'0'});
  a.click({v3Action:'queue-resolve'});a.change('v3-queue-decision','transfer');
  a.change('v3-transfer','元数据管理员');
  a.input('v3-queue-note','核对订单分类并转交');
  a.click({v3Action:'queue-review'});a.click({v3Action:'queue-submit'});
  const receipt=a.v4.store.governance['GOV-301'].receipts[0];
  assert.ok(receipt);
  a.click({select:'2'});a.click({select:'0'});
  assert.equal(receipt.transferTo,'元数据管理员');
  assert.equal(receipt.note,'核对订单分类并转交');
  assert.match(a.view(),/DEMO-GOV-RECEIPT-GOV-301/);
});

test('8: a multi-step source trail returns through metric/model/standard/process', () => {
  const a = build();
  for (const action of ['process-next','standard-attach','model-next-metric'])
    a.click({v3Action:action});
  assert.equal(a.v4.trail.length,3);
  a.click({v3Back:'1'});assert.equal(a.state.page,'model');
  a.click({v3Back:'1'});assert.equal(a.state.page,'standard');
  a.click({v3Back:'1'});assert.equal(a.state.page,'semantic');
});

test('9: narrow layout offers a model chooser and a category chooser', () => {
  const a = build();a.click({page:'model'});
  assert.match(a.view(),/id="v4-choose"/);
  assert.match(html,/@media\(max-width:1120px\)\{\.v4AsideChoice\{display:block/);
  a.click({page:'standard'});assert.match(a.view(),/id="v4-category"/);
});

test('10: reader role cannot bypass submission / model saving / metric publication', () => {
  const a = build();a.click({page:'governance'});
  a.change('roleSwitch','reader');a.click({v3Step:'queue:1'});
  assert.equal(a.v3.queueStep,'evidence');
  a.click({v3Action:'queue-submit'});
  assert.equal(a.v4.store.governance['GOV-320'].receipts.length,0);
  a.click({page:'model'});a.click({v3Action:'model-save'});
  assert.equal(a.v3.build.savedVersions.length,0);
  a.click({page:'metric'});a.click({v3Action:'metric-publish'});
  assert.equal(a.v3.metric.receipts.length,0);
});

test('draft dialog is clean when first opened, dirty on edit, and Esc requests confirmation', () => {
  const a = build();a.click({v3Action:'new'});
  assert.equal(a.state.editDirty,false);
  a.input('v3-edit-name','退款对账过程');assert.equal(a.state.editDirty,true);
  a.key('Escape');
  assert.match(a.dialog(),/存在未保存的编辑内容/);
});
