import {
  modelingMetricNextSteps, verifiedSourceSemanticHandoff,
} from './sourceSemanticHandoff';
import type {
  AdoptionReceipt, CandidateReview, SemanticCandidate,
} from './sourceSemanticCandidates';
import {
  getSemanticDomainTree, getSemanticProcess, getSemanticField,
  getSemanticStandard, listSemanticProcessFields, listSemanticProcessSources,
} from '@/services/semantic/api';

jest.mock('@/services/semantic/api', () => ({
  getSemanticDomainTree: jest.fn(),
  getSemanticProcess: jest.fn(),
  getSemanticField: jest.fn(),
  getSemanticStandard: jest.fn(),
  listSemanticProcessFields: jest.fn(),
  listSemanticProcessSources: jest.fn(),
}));

const candidate = (id: string, kind: string, dependencies: string[] = []):
  SemanticCandidate => ({
  id,kind,code:id,name:id,role:null,grain:null,description:null,
  typeId:null,unitId:null,reuseId:null,reuseVersion:null,dependencies,
  evidence:[{tableAssetKey:'physical-key',column:null,sourceChunkId:'chunk'}],
});
const receipt = (id: string, kind: string, semanticId: number, status='CREATED'):
  AdoptionReceipt => ({candidateId:id,kind,semanticId,status,semanticVersion:1,message:null});
const review = (items: SemanticCandidate[]): CandidateReview => ({
  taskId:'task-1',projectId:9,sourceFingerprint:'source',planSha256:'plan',
  revision:2,candidates:items,selectedIds:items.map(x=>x.id),answers:{},
});

beforeEach(()=>jest.resetAllMocks());

it('does not trust candidate ids or client receipts without current committed Semantic receipts',async()=>{
  const r=review([candidate('a','PROCESS')]);
  const forged=receipt('a','PROCESS',22);
  expect(await verifiedSourceSemanticHandoff(forged,r,[])).toBeNull();
  expect(await verifiedSourceSemanticHandoff({...forged,status:'NOT_EXECUTED'},r,[forged])).toBeNull();
  expect(await verifiedSourceSemanticHandoff(forged,r,[{...forged,semanticId:23}])).toBeNull();
  expect(getSemanticProcess).not.toHaveBeenCalled();
});

it('re-reads canonical process and goes only to original Semantic owner',async()=>{
  const r=review([candidate('a','PROCESS')]);
  const saved=receipt('a','PROCESS',22);
  (getSemanticProcess as jest.Mock).mockResolvedValue({id:22,name:'订单',code:'ORDERS'});
  const path=await verifiedSourceSemanticHandoff(saved,r,[saved]);
  expect(path?.path).toBe('/semantic/processes?processId=22');
  expect(path?.detail).toContain('ID 22');
  expect(getSemanticProcess).toHaveBeenCalledWith(22);
});

it('cross-project or revoked canonical detail fails, never opens an invented target',async()=>{
  const r=review([candidate('a','PROCESS')]);
  const saved=receipt('a','PROCESS',22);
  (getSemanticProcess as jest.Mock).mockResolvedValue({id:999});
  expect(await verifiedSourceSemanticHandoff(saved,r,[saved])).toBeNull();
  (getSemanticProcess as jest.Mock).mockRejectedValue(new Error('403'));
  await expect(verifiedSourceSemanticHandoff(saved,r,[saved])).rejects.toThrow('403');
});

it('domain and field handoff use exact original IDs',async()=>{
  const domain=receipt('d','DOMAIN',8);
  (getSemanticDomainTree as jest.Mock).mockResolvedValue([
    {id:7,name:'A',code:'A',children:[{id:8,name:'销售',code:'SALE',children:[]}]}]);
  const d=await verifiedSourceSemanticHandoff(domain,review([candidate('d','DOMAIN')]),[domain]);
  expect(d?.path).toBe('/semantic/domains?domainId=8');
  const field=receipt('f','FIELD',90);
  (getSemanticField as jest.Mock).mockResolvedValue({id:90,name:'金额',code:'AMOUNT'});
  const f=await verifiedSourceSemanticHandoff(field,review([candidate('f','FIELD')]),[field]);
  expect(f?.path).toBe('/semantic/fields?fieldId=90');
});

it('standard reference enforces category, not just a coincidentally matching ID',async()=>{
  const standard=receipt('t','STANDARD_TYPE',60,'REUSED');
  (getSemanticStandard as jest.Mock).mockResolvedValue({id:60,kind:'UNIT',
    name:'坏类型',code:'WRONG',status:'ENABLED'});
  expect(await verifiedSourceSemanticHandoff(standard,
    review([candidate('t','STANDARD_TYPE')]),[standard])).toBeNull();
  (getSemanticStandard as jest.Mock).mockResolvedValue({id:60,kind:'TYPE',
    name:'整数',code:'INTEGER',status:'ENABLED'});
  expect((await verifiedSourceSemanticHandoff(standard,
    review([candidate('t','STANDARD_TYPE')]),[standard]))?.path)
    .toBe('/semantic/standards?standardId=60');
});

it('process field/source association must exist in original project-scoped owner',async()=>{
  const candidates=[
    candidate('p','PROCESS'), candidate('f','FIELD'),
    candidate('pf','PROCESS_FIELD',['p','f']),
    candidate('sl','SOURCE_LINK',['pf']),
  ];
  const receipts=[
    receipt('p','PROCESS',27),receipt('f','FIELD',31),
    receipt('pf','PROCESS_FIELD',31,'LINKED'),
    receipt('sl','SOURCE_LINK',301,'LINKED'),
  ];
  (getSemanticProcess as jest.Mock).mockResolvedValue({id:27,name:'订单',code:'ORDERS'});
  (listSemanticProcessFields as jest.Mock).mockResolvedValue([{id:31}]);
  (listSemanticProcessSources as jest.Mock).mockResolvedValue([{id:301}]);
  expect((await verifiedSourceSemanticHandoff(receipts[2],review(candidates),receipts))?.path)
    .toBe('/semantic/processes/27/edit');
  expect((await verifiedSourceSemanticHandoff(receipts[3],review(candidates),receipts))?.path)
    .toBe('/semantic/processes/27/edit');
  (listSemanticProcessSources as jest.Mock).mockResolvedValue([]);
  expect(await verifiedSourceSemanticHandoff(receipts[3],review(candidates),receipts)).toBeNull();
});

it('modeling/metric steps are original pages, never a generated model or published metric',()=>{
  expect(modelingMetricNextSteps(null)).toEqual([]);
  const next=modelingMetricNextSteps(27,8);
  expect(next[0].path).toBe('/modeling/mainline');
  expect(next[1].path).toBe('/metric/manage?processId=27&domainId=8');
  expect(next[1].note).toContain('不表示');
});
