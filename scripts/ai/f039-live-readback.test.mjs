import { test } from 'node:test';
import assert from 'node:assert/strict';
import { positiveId, verifiedStatus, findProcessCandidate } from './f039-live-readback.mjs';

test('formal IDs require positive safe integer, never trust generated candidate ids',()=>{
  assert.equal(positiveId(23),true);
  assert.equal(positiveId('23'),true);
  for(const v of [0,-1,1.5,'003','999999999999999999999','c_forged',null])
    assert.equal(positiveId(v),false);
});
test('only original committed receipts allow formal readback',()=>{
  for(const status of ['NOT_EXECUTED','WAITING_APPROVAL','NEEDS_RECONCILIATION','FAIL','PENDING'])
    assert.equal(verifiedStatus({status,semanticId:7}),false);
  assert.equal(verifiedStatus({status:'CREATED',semanticId:0}),false);
  assert.equal(verifiedStatus({status:'REUSED',semanticId:7}),true);
  assert.equal(verifiedStatus({status:'LINKED',semanticId:7}),true);
});
test('source association follows reviewed explicit PROCESS_FIELD dependencies, not a guessed process id',()=>{
  const candidates=[
    {id:'p',kind:'PROCESS',dependencies:[]},
    {id:'f',kind:'FIELD',dependencies:[]},
    {id:'pf',kind:'PROCESS_FIELD',dependencies:['p','f']},
    {id:'sl',kind:'SOURCE_LINK',dependencies:['pf']},
  ];
  assert.equal(findProcessCandidate(candidates[3],candidates),'p');
  assert.equal(findProcessCandidate({...candidates[3],dependencies:['missing']},candidates),null);
  assert.equal(findProcessCandidate({id:'cycle',kind:'FIELD',dependencies:['cycle']},
    [{id:'cycle',kind:'FIELD',dependencies:['cycle']}]),null);
});
