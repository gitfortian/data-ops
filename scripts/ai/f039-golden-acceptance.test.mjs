import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { evaluateGolden, template, SCENARIOS } from './f039-golden-acceptance.mjs';

const C='c'.repeat(64), S='a'.repeat(40);
const make = () => {
  const root=mkdtempSync(join(tmpdir(),'f039-acceptance-'));
  const body='Local structural test witness, not a real pilot execution';
  writeFileSync(join(root,'witness.txt'),body);
  return {root, evidence:{
    path:'witness.txt',
    sha256:createHash('sha256').update(body).digest('hex'),
    observedAt:'2026-10-10T01:00:00Z',
  }};
};
const validContext=() => ({
  realEnvironment:true,environment:'test-only-stub-context',gitSha:S,projectId:2,
  dataSourceId:5,sourceFingerprint:C,collectJobId:'test-harvest',
  taskId:'task-from-test-not-real',originalTurnIds:['synthetic-turn'],
  modelProvider:'test-double',modelName:'synthetic',skillVersions:'test-version',
  startedAt:'2026-10-10T00:00:00Z',finishedAt:'2026-10-10T02:00:00Z',
});
const measured = {
  manualMinutes:{understand:30,author:30,review:20,rework:10},
  assistedHumanMinutes:{understand:10,author:20,review:25,rework:5},
  aiWaitMinutes:12,modelTokens:4500,sameScopeJustification:'Same authorized captured source range',
};
function fullSynthetic(evidence) {
  const data=template();
  data.context=validContext();
  data.measurement=measured;
  data.expert={independent:true,reviewerId:'synthetic-domain-reviewer',
    role:'business-owner',decision:'APPROVED',
    reviewedAt:'2026-10-10T03:00:00Z',assessment:'Synthetic unit test only',
    reviewedScenarioIds:['SI03','SI04','SI13']};
  for(const [id,[,required]] of Object.entries(SCENARIOS))
    data.scenarios[id]={status:'PASS',operatorId:'test-user',
      observedResult:'Synthetic runner witness; NEVER a production claim',
      evidence:required.map(kind=>({kind,...evidence}))};
  return data;
}

test('template has exactly SI01–SI15 and defaults to NOT_RUN',()=>{
  const data=template();
  const keys=Object.keys(SCENARIOS);
  assert.deepEqual(Object.keys(data.scenarios),keys);
  assert.equal(keys.length,15);
  const out=evaluateGolden(data,tmpdir());
  assert.equal(out.counts.NOT_RUN,15);
  assert.equal(out.counts.PASS,0);
  assert.equal(out.acceptance,'NOT_ACCEPTED');
  assert.equal(out.benefits,null);
});

test('a PASS without real file and provenance cannot pass',()=>{
  const d=template();
  d.scenarios.SI01={status:'PASS',operatorId:'test',observedResult:'claimed',
    evidence:[{kind:'api',path:'nonexistent.json',sha256:C,observedAt:'2026-10-10T00:00:00Z'}]};
  const result=evaluateGolden(d,tmpdir());
  assert.equal(result.acceptance,'NOT_ACCEPTED');
  assert.equal(result.scenarios[0].ready,false);
  assert.match(result.problems.find(p=>p.code==='SI01').reason,/missing|required/);
});

test('synthetic fixtures can exercise structural checks but must not be committed as E2E',()=>{
  const {root,evidence}=make();
  try {
    const all=fullSynthetic(evidence);
    const result=evaluateGolden(all,root);
    assert.equal(result.counts.PASS,15);
    assert.equal(result.acceptance,'EVIDENCE_COMPLETE_REQUIRES_SIGNOFF');
    assert.equal(result.benefits.manualHumanMinutes,90);
    assert.equal(result.benefits.assistedHumanMinutes,60);
    assert.equal(result.benefits.humanMinutesSaved,30);
    assert.equal(result.benefits.humanReductionPercent,33.33);
    assert.equal(result.benefits.aiWaitMinutes,12);
    // independent real-world verification is still required by the report contract
    assert.match(result.evidenceAssurance,/independent real-world verification/);
  } finally {rmSync(root,{recursive:true,force:true});}
});

test('tampered evidence SHA or path traversal is rejected even with all cases PASS',()=>{
  const {root,evidence}=make();
  try {
    const all=fullSynthetic(evidence);
    all.scenarios.SI01.evidence[0].sha256='0'.repeat(64);
    assert.equal(evaluateGolden(all,root).scenarios[0].ready,false);
    all.scenarios.SI01.evidence[0]={...evidence,kind:'api',path:'../../secrets.txt'};
    assert.match(evaluateGolden(all,root).problems.find(p=>p.code==='SI01').reason,/path escapes root/);
  } finally {rmSync(root,{recursive:true,force:true});}
});

test('independent expert, real source context and same-scope measured benefits are mandatory',()=>{
  const {root,evidence}=make();
  try {
    const all=fullSynthetic(evidence);
    all.expert.independent=false;
    all.context.realEnvironment=false;
    all.measurement.aiWaitMinutes=null;
    const result=evaluateGolden(all,root);
    assert.equal(result.acceptance,'NOT_ACCEPTED');
    assert.deepEqual(result.problems.filter(p=>p.code.endsWith('PENDING')).map(p=>p.code).sort(),
      ['EXPERT_REVIEW_PENDING','MEASUREMENT_PENDING','REAL_CONTEXT_PENDING']);
  } finally {rmSync(root,{recursive:true,force:true});}
});

test('FAIL/BLOCKED/NOT_RUN are preserved explicitly instead of success inference',()=>{
  const {root,evidence}=make();
  try {
    const all=fullSynthetic(evidence);
    all.scenarios.SI09={status:'BLOCKED',reason:'No revocation environment',evidence:[]};
    all.scenarios.SI10={status:'FAIL',reason:'Cross project returned data',evidence:[]};
    const result=evaluateGolden(all,root);
    assert.equal(result.counts.BLOCKED,1);
    assert.equal(result.counts.FAIL,1);
    assert.equal(result.counts.PASS,13);
    assert.equal(result.acceptance,'NOT_ACCEPTED');
  } finally {rmSync(root,{recursive:true,force:true});}
});
