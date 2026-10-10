#!/usr/bin/env node
/**
 * F-039 #498: STRICT, evidence-backed SI01–SI15 verification/report.
 * NO environment login, mock result generation, LLM claim-as-truth or business writes.
 * An offline hash verifies artifact integrity, NOT the truth of a human assertion;
 * an independent domain reviewer and real-environment artifacts remain mandatory.
 */
import { readFileSync, existsSync, writeFileSync } from 'node:fs';
import { resolve, relative, isAbsolute, dirname } from 'node:path';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';

export const SCENARIOS = Object.freeze({
  SI01: ['端到端来源到正式回读', ['api','db','browser']],
  SI02: ['来源缺口与真实覆盖', ['api','browser']],
  SI03: ['跨片同义概念与人工归并', ['trace','expert']],
  SI04: ['同名异义、标准与语义不足', ['browser','expert']],
  SI05: ['Plan 确认/拒绝/修订', ['trace','api']],
  SI06: ['跨用户、跨项目与双任务隔离', ['api','security']],
  SI07: ['总额度与超限阻断', ['trace','api']],
  SI08: ['刷新/取消/停止/重启恢复', ['api','browser']],
  SI09: ['采集及 Skill 漂移失效', ['api','db']],
  SI10: ['撤权及源域 ACL 拒绝', ['security','api']],
  SI11: ['幂等与并发同码冲突', ['db','api']],
  SI12: ['丢回执、部分保存及人工继续', ['db','api']],
  SI13: ['发布审批等待及有效引用', ['approval','db']],
  SI14: ['消息压缩/清理与交付材料留存', ['trace','api']],
  SI15: ['原助手 F-023/F-026/F-028/F-029 无回归', ['regression','api']],
});
const STATES = new Set(['PASS','FAIL','BLOCKED','NOT_RUN']);
const HEX40 = /^[a-f0-9]{40}$/;
const HEX64 = /^[a-f0-9]{64}$/;
const ISO = (x) => typeof x === 'string' && Number.isFinite(Date.parse(x))
  && /^\d{4}-\d{2}-\d{2}T/.test(x);
const positive = (x) => Number.isSafeInteger(x) && x > 0;
const str = (x) => typeof x === 'string' && x.trim().length > 0;
const error = (code, reason) => ({ code, reason });
const hasString = (obj, key) => str(obj?.[key]);
const PERMITTED = new Set(['api','db','browser','trace','security',
  'expert','approval','regression','timing','audit']);

function safeArtifact(root, entry) {
  if (!entry || !str(entry.kind) || !PERMITTED.has(entry.kind)
      || !str(entry.path) || isAbsolute(entry.path)
      || !HEX64.test(entry.sha256 ?? '') || !ISO(entry.observedAt))
    return 'evidence schema invalid';
  const target=resolve(root,entry.path);
  const delta=relative(root,target);
  if (!delta || delta === '..' || delta.startsWith('../') || delta.startsWith('..\\')
      || isAbsolute(delta)) return 'evidence path escapes root';
  if (!existsSync(target)) return 'evidence artifact is missing';
  let data;
  try { data=readFileSync(target); } catch { return 'evidence artifact unreadable'; }
  if (data.length === 0 || data.length > 20*1024*1024) return 'evidence size invalid';
  const digest=createHash('sha256').update(data).digest('hex');
  if (entry.sha256 !== digest) return 'evidence content SHA mismatch';
  return null;
}
function sumMeasured(phases) {
  const steps=['understand','author','review','rework'];
  if (!phases || steps.some((k)=>!Number.isFinite(phases[k]) || phases[k]<0))
    return null;
  return steps.reduce((n,k)=>n+phases[k],0);
}
function measures(value, problems) {
  const manual = sumMeasured(value?.manualMinutes);
  const assisted = sumMeasured(value?.assistedHumanMinutes);
  if (manual === null || manual <= 0 || assisted === null ||
      !Number.isFinite(value?.aiWaitMinutes) || value.aiWaitMinutes<0 ||
      !Number.isSafeInteger(value?.modelTokens) || value.modelTokens<0 ||
      !str(value?.sameScopeJustification)) {
    problems.push(error('MEASUREMENT_PENDING',
      'Same-scope measured manual/assisted human minutes, wait, tokens and scope rationale needed'));
    return null;
  }
  return {
    manualHumanMinutes: manual,
    assistedHumanMinutes: assisted,
    humanMinutesSaved: manual-assisted,
    humanReductionPercent: Math.round((manual-assisted)*10000/manual)/100,
    aiWaitMinutes: value.aiWaitMinutes,
    assistedElapsedMinutes: assisted+value.aiWaitMinutes,
    modelTokens: value.modelTokens,
    comparison: 'Observed human-only comparison; do not conflate AI wait/model tokens with work saved',
  };
}
export function evaluateGolden(input, evidenceRoot) {
  const problems=[];
  const counts={PASS:0,FAIL:0,BLOCKED:0,NOT_RUN:0};
  const ctx=input?.context;
  if(input?.schema!=='f039-golden-v1') problems.push(error('SCHEMA_INVALID','Expected f039-golden-v1'));
  const contextValid = !!(ctx && ctx.realEnvironment===true && str(ctx.environment)
    && HEX40.test(ctx.gitSha??'') && positive(ctx.projectId)
    && positive(ctx.dataSourceId) && HEX64.test(ctx.sourceFingerprint??'')
    && str(ctx.collectJobId) && str(ctx.taskId)
    && str(ctx.modelProvider) && str(ctx.modelName)
    && str(ctx.skillVersions) && ISO(ctx.startedAt) && ISO(ctx.finishedAt)
    && Date.parse(ctx.finishedAt)>=Date.parse(ctx.startedAt)
    && Array.isArray(ctx.originalTurnIds) && ctx.originalTurnIds.length>0
    && ctx.originalTurnIds.every(str));
  if(!contextValid) problems.push(error('REAL_CONTEXT_PENDING',
    'Real deployment/build, scope, harvest, model/Skill and original Turn identities required'));
  const rows=[];
  for(const [key,[title,kinds]] of Object.entries(SCENARIOS)) {
    const row=input?.scenarios?.[key]??{status:'NOT_RUN',reason:'No verified evidence'};
    const status=STATES.has(row?.status)?row.status:'NOT_RUN';
    const errors=[];
    if(!STATES.has(row?.status)) errors.push('status invalid');
    if(status==='PASS') {
      if(!str(row.observedResult) || !hasString(row,'operatorId'))
        errors.push('observed real result and operator required');
      const evidence=Array.isArray(row.evidence)?row.evidence:[];
      for(const kind of kinds) {
        if(!evidence.some(e=>e?.kind===kind))
          errors.push(`required ${kind} artifact missing`);
      }
      for(const item of evidence) {
        const why=safeArtifact(evidenceRoot,item);
        if(why) errors.push(`${item?.path??'?'}: ${why}`);
      }
    } else if(!str(row.reason)) errors.push('non-PASS must explain missing/failed/blocked evidence');
    if(errors.length) problems.push(error(key,errors.join('; ')));
    counts[status]++;
    rows.push({id:key,title,status,ready:status==='PASS'&&!errors.length,
      reason:status==='PASS'?null:row.reason??null,errors});
  }
  const reviewer=input?.expert;
  const independent=!!(reviewer && reviewer.independent===true
    && str(reviewer.reviewerId) && str(reviewer.role)
    && str(reviewer.decision) && reviewer.decision==='APPROVED'
    && ISO(reviewer.reviewedAt)
    && str(reviewer.assessment)
    && Array.isArray(reviewer.reviewedScenarioIds)
    && ['SI03','SI04','SI13'].every(k=>reviewer.reviewedScenarioIds.includes(k)));
  if(!independent) problems.push(error('EXPERT_REVIEW_PENDING',
    'Independent business/governance reviewer must assess concept, grain, standard and approval'));
  const benefits=measures(input?.measurement,problems);
  const passed = Object.values(counts).reduce((a,b)=>a+b,0)===15 && counts.PASS===15
    && contextValid && independent && benefits!==null && problems.length===0;
  return {
    schema:'f039-golden-report-v1', sourceSchema:'f039-golden-v1',
    acceptance:passed?'EVIDENCE_COMPLETE_REQUIRES_SIGNOFF':'NOT_ACCEPTED',
    // Structural completeness is not a claim that real artifacts or expert content is truthful.
    evidenceAssurance:'Artifact SHA integrity and mandatory provenance only; independent real-world verification required',
    counts, scenarios:rows, problems, benefits,
  };
}
export function template() {
  const scenarios=Object.fromEntries(Object.entries(SCENARIOS).map(([id,[name]])=>
    [id,{title:name,status:'NOT_RUN',reason:'Real authorized pilot has not been executed',evidence:[]}]));
  return {
    schema:'f039-golden-v1',
    context:{realEnvironment:false,environment:null,gitSha:null,projectId:null,
      dataSourceId:null,sourceFingerprint:null,collectJobId:null,taskId:null,
      originalTurnIds:[],modelProvider:null,modelName:null,skillVersions:null,
      startedAt:null,finishedAt:null},
    scenarios,expert:{independent:false,reviewerId:null,role:null,decision:'NOT_RUN',
      reviewedAt:null,assessment:null,reviewedScenarioIds:[]},
    measurement:{manualMinutes:null,assistedHumanMinutes:null,
      aiWaitMinutes:null,modelTokens:null,sameScopeJustification:null},
  };
}
async function main() {
  const args=process.argv.slice(2);
  const get=(flag)=>{let i=args.indexOf(flag);return i<0?null:args[i+1]??null;};
  if(args.includes('--init')) {
    const path=get('--init');
    if(!path || existsSync(path)) throw new Error('Specify NEW path with --init');
    writeFileSync(path,JSON.stringify(template(),null,2)+'\n',{flag:'wx'});
    console.log(`Created explicit NOT_RUN F-039 pilot form: ${path}`);
    return;
  }
  const inputPath=get('--input');
  if(!inputPath) throw new Error('Use --input real-evidence.json or --init new-file.json');
  const root=resolve(get('--evidence-root') ?? dirname(resolve(inputPath)));
  const report=evaluateGolden(JSON.parse(readFileSync(inputPath,'utf8')),root);
  const output=get('--output');
  if(output) writeFileSync(output,JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify(report,null,2));
  if(report.acceptance!=='EVIDENCE_COMPLETE_REQUIRES_SIGNOFF' && !args.includes('--allow-pending'))
    process.exitCode=2;
}
if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(e=>{console.error(`F039 evidence gate: ${e.message}`);process.exitCode=1;});
}
