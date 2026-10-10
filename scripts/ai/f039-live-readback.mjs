#!/usr/bin/env node
/**
 * F-039 #498 real-environment READ-ONLY evidence probe.
 * Only login is POST; all F-039 / Semantic operations are GET.
 * Never creates candidates if no committed receipt exists, never saves,
 * publishes, approves, reads physical rows, or tests destructive revocation.
 * Output is a compact diagnostic reference, NOT a SI01–SI15 PASS claim.
 */
import { writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const required = (name) => {
  const value=process.env[name]?.trim();
  if(!value) throw Error(`Missing ${name}`);
  return value;
};
export const positiveId = (n) =>
  Number.isSafeInteger(Number(n)) && Number(n)>0 && /^[1-9][0-9]*$/.test(String(n));
export const verifiedStatus = (item) =>
  item && ['CREATED','REUSED','LINKED'].includes(item.status)
    && positiveId(item.semanticId);
export function findProcessCandidate(start,candidates) {
  const byId=new Map(candidates.map(c=>[c.id,c]));
  const seen=new Set(), queue=[...(start.dependencies??[])];
  while(queue.length) {
    const id=queue.shift();
    if(seen.has(id))continue;
    seen.add(id);
    const c=byId.get(id);
    if(!c)continue;
    if(c.kind==='PROCESS') return c.id;
    queue.push(...(c.dependencies??[]));
  }
  return null;
}
const baseUrl = () => required('YAK_OPS_BASE_URL').replace(/\/+$/,'');
const COOKIE_SPLIT= /,(?=\s*[^;,=\s]+=)/g;
function session() {
  const cookies=new Map();
  return {
    remember(response) {
      const items=response.headers.getSetCookie?.() ??
        response.headers.get('set-cookie')?.split(COOKIE_SPLIT) ?? [];
      for(const part of items) {
        const segment=part.split(';')[0], n=segment.indexOf('=');
        if(n>0) cookies.set(segment.slice(0,n).trim(),segment.slice(n+1));
      }
    },
    header() {return [...cookies.entries()].map(([k,v])=>`${k}=${v}`).join('; ');}
  };
}
async function get(client,path,projectId,username,password) {
  const headers={Accept:'application/json'};
  if(projectId!==null) headers['X-YAK-SECURITY-PROJECT-ID']=String(projectId);
  if(client.header()) headers.Cookie=client.header();
  const login=path==='/yak-security/api/v1/account/login';
  if(login) headers['Content-Type']='application/json';
  const response=await fetch(new URL(path,baseUrl()+'/'),{
    method:login?'POST':'GET',headers,redirect:'manual',
    body:login?JSON.stringify({userName:username,pw:password}):undefined,
  });
  client.remember(response);
  const text=await response.text();
  let parsed;
  try{parsed=JSON.parse(text);}catch{parsed=null;}
  if(!response.ok || parsed?.success===false || parsed?.code===500) {
    throw Error(`${login?'LOGIN':'GET'} ${path} rejected (HTTP ${response.status})`);
  }
  if(parsed==null)throw Error(`No JSON from ${path}`);
  return Object.hasOwn(parsed,'data')?parsed.data:parsed;
}
function domainExists(nodes,id) {
  for(const node of nodes??[]) {
    if(node.id===id)return true;
    if(domainExists(node.children,id))return true;
  }
  return false;
}
async function inspectCanonical(receipt,candidates,byReceipt,api) {
  const id=receipt.semanticId, kind=receipt.kind;
  if(!verifiedStatus(receipt))return {status:receipt.status,kind,id:null,verified:false};
  if(kind==='DOMAIN') {
    const nodes=await api('/api/v1/semantic/domains/tree');
    return {kind,id,verified:domainExists(nodes,id),owner:'SemanticDomain'};
  }
  if(kind==='PROCESS') {
    const obj=await api(`/api/v1/semantic/processes/${id}`);
    return {kind,id,verified:obj?.id===id,owner:'SemanticProcess',
      domainId:obj?.domainId??null};
  }
  if(kind==='FIELD') {
    const obj=await api(`/api/v1/semantic/fields/${id}`);
    return {kind,id,verified:obj?.id===id,owner:'SemanticField',
      version:obj?.version??null,status:obj?.status??null};
  }
  if(kind?.startsWith('STANDARD_')) {
    const obj=await api(`/api/v1/semantic/standards/${id}`);
    return {kind,id,verified:obj?.id===id && obj?.kind===kind.substring(9),
      owner:'SemanticStandard',version:obj?.version??null,status:obj?.status??null};
  }
  if(kind==='PROCESS_FIELD'||kind==='SOURCE_LINK') {
    const candidate=candidates.find(c=>c.id===receipt.candidateId);
    const processCandidateId=candidate && findProcessCandidate(candidate,candidates);
    const related=byReceipt.get(processCandidateId);
    if(!verifiedStatus(related) || related.kind!=='PROCESS')
      return {kind,id,verified:false,reason:'Missing committed process dependency'};
    const process=await api(`/api/v1/semantic/processes/${related.semanticId}`);
    if(process?.id!==related.semanticId)
      return {kind,id,verified:false,reason:'Original process no longer resolvable'};
    const children=await api(`/api/v1/semantic/processes/${process.id}/${kind==='PROCESS_FIELD'?'fields':'sources'}`);
    return {kind,id,verified:(children??[]).some(x=>x.id===id),
      owner:kind==='SOURCE_LINK'?'SemanticProcessSource':'SemanticProcessField',processId:process.id};
  }
  return {kind,id,verified:false,reason:'Unsupported kind'};
}
export async function realReadback({taskId,projectId,username,password}) {
  if(!positiveId(projectId) || !/^[a-zA-Z0-9_-]{5,100}$/.test(taskId))
    throw Error('Invalid F-039 task/project');
  const client=session();
  await get(client,'/yak-security/api/v1/account/login',null,username,password);
  const api=(path)=>get(client,path,projectId,username,password);
  // Reject before materializing a new candidate ledger: no real receipt, no readback.
  const task=await api(`/api/v1/agent/source-semantic/tasks/${encodeURIComponent(taskId)}`);
  const receipts=await api(`/api/v1/agent/source-semantic/tasks/${encodeURIComponent(taskId)}/candidates/adoption-receipts`);
  if(!Array.isArray(receipts) || !receipts.length)
    throw Error('No authorized committed Semantic receipts; not an accepted golden pilot');
  const view=await api(`/api/v1/agent/source-semantic/tasks/${encodeURIComponent(taskId)}/candidates`);
  if(!view?.review || view.review.projectId!==Number(projectId))
    throw Error('Candidate project identity mismatch');
  const candidates=view.review.candidates.map(c=>({
    id:c.id,kind:c.kind,dependencies:c.dependencies,
  }));
  const byReceipt=new Map(receipts.map(r=>[r.candidateId,r]));
  const formal=[];
  for(const receipt of receipts) {
    try {
      formal.push({candidateId:receipt.candidateId,
        receiptStatus:receipt.status,
        ...await inspectCanonical(receipt,candidates,byReceipt,api)});
    } catch {
      formal.push({candidateId:receipt.candidateId,kind:receipt.kind,
        id:receipt.semanticId,verified:false,reason:'Canonical GET failed or access revoked'});
    }
  }
  const allCommitted=receipts.every(verifiedStatus);
  const allVerified=formal.every(r=>r.verified===true);
  return {
    schema:'f039-source-readback-v1',verifiedAt:new Date().toISOString(),
    projectId:Number(projectId),taskId,
    taskStatus:task?.status??null,
    candidateRevision:view.review.revision,
    sourceFingerprint:view.review.sourceFingerprint,
    planSha256:view.review.planSha256,
    receipts:receipts.map(r=>({
      candidateId:r.candidateId,kind:r.kind,status:r.status,
      semanticId:r.semanticId,semanticVersion:r.semanticVersion,
    })),
    formal,
    result:(task?.status==='COMPLETED' && allCommitted && allVerified)
      ? 'SOURCE_OWNER_READBACK_VERIFIED' : 'INCOMPLETE_OR_UNVERIFIED',
    disclaimer:'Read-only owner reconciliation; does NOT attest expert correctness, approvals, SI01-SI15 or measured benefits',
  };
}
async function main() {
  const taskId=required('YAK_OPS_F039_TASK_ID');
  const projectId=required('YAK_OPS_PROJECT_ID');
  const output=required('YAK_OPS_F039_OUTPUT');
  const report=await realReadback({
    taskId,projectId,username:required('YAK_OPS_USERNAME'),
    password:required('YAK_OPS_PASSWORD'),
  });
  const text=JSON.stringify(report,null,2)+'\n';
  writeFileSync(output,text,{flag:'wx',mode:0o600});
  console.log(JSON.stringify({output,sha256:createHash('sha256').update(text).digest('hex'),
    result:report.result,receiptCount:report.receipts.length}));
  if(report.result!=='SOURCE_OWNER_READBACK_VERIFIED')process.exitCode=2;
}
if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href)
  main().catch(error=>{console.error(`F039 readback incomplete: ${error.message}`);process.exitCode=2;});
