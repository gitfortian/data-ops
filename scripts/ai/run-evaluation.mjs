import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { createHash, randomUUID } from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { resolve, dirname } from 'node:path';
import { validateScenarioTarget, validateScenarioBinding, observeDelivery } from './scenario-evidence.mjs';
import { readApiData as resultJson } from './evidence-http.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
export const suiteFile = resolve(root, 'docs/ai/evaluation/cases/task-scope.json');
export const qualitySuiteFile = resolve(root, 'docs/ai/evaluation/cases/quality-troubleshooting.json');
export const scenarioSuiteFile = resolve(root, 'docs/ai/evaluation/cases/skill-scenarios.json');
const suites = { 'task-scope': suiteFile, 'quality-troubleshooting': qualitySuiteFile, 'skill-scenarios': scenarioSuiteFile };
const manualQualityCases = new Set(['qp11', 'qp12', 'qp15', 'qp16']);
const manualScenarioCases = new Set(['sm04', 'sm05', 'mm04', 'me04', 'md06']);
const scenarioIds = ['sm01', 'sm02', 'sm03', 'sm04', 'sm05', 'mm01', 'mm02', 'mm03', 'mm04', 'me01', 'me02', 'me03', 'me04', 'me05', 'md01', 'md02', 'md03', 'md04', 'md05', 'md06'];
const hash = (value) => createHash('sha256').update(value).digest('hex');

export function validateSuite(suite) {
  if (!['F-011-v1', 'F-012-v1', 'F-030-v1'].includes(suite.version) || !Array.isArray(suite.cases) || !suite.cases.length) throw new Error('Invalid suite');
  const ids = new Set();
  for (const item of suite.cases) {
    if (!/^[a-z0-9-]+$/.test(item.caseId) || ids.has(item.caseId) || !item.question || !item.scenario
        || !item.probeTool || typeof item.allowed !== 'boolean' || !item.account || !item.fixture
        || !Array.isArray(item.review) || !item.review.length) throw new Error('Invalid case contract');
    const target = item.target;
    if (suite.version === 'F-030-v1') {
      const contract = validateScenarioTarget(target);
      const purpose = { sm: 'STANDARD_MATCH', mm: 'MODEL_MAPPING', me: 'METRIC_EXPLANATION', md: 'METRIC_DRAFT' }[item.caseId.slice(0, 2)];
      if (!scenarioIds.includes(item.caseId) || target.purpose !== purpose || item.probeTool !== contract.tool
          || item.executionMode !== (manualScenarioCases.has(item.caseId) ? 'manual' : 'single-turn')
          || !Array.isArray(item.fixture.sourceConditions) || !item.fixture.sourceConditions.length
          || item.fixture.sourceConditions.some(v => typeof v !== 'string' || !v.trim())
          || (item.executionMode === 'manual' && (!Array.isArray(item.manualSteps) || !item.manualSteps.length
            || item.manualSteps.some(v => typeof v !== 'string' || !v.trim())))) throw new Error('Invalid scenario case');
    } else if (target != null) {
      const keys = ['assetId', 'qualityExecutionNo', 'qualityMonitorId'].filter(k => target[k] != null);
      if (keys.length !== 1 || (target.qualityMonitorId != null && target.purpose !== 'QUALITY_RULES')
          || (target.purpose && !(target.assetId != null && target.purpose === 'ASSET_DESCRIPTION')
            && !(target.qualityMonitorId != null && target.purpose === 'QUALITY_RULES'))) throw new Error('Invalid target');
    }
    if (suite.version === 'F-012-v1') {
      if (!/^qp(?:0[1-9]|1[0-6])$/.test(item.caseId) || item.scenario !== item.caseId.toUpperCase()
          || typeof target?.qualityExecutionNo !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(target.qualityExecutionNo)
          || Object.keys(target).length !== 1
          || item.executionMode !== (manualQualityCases.has(item.caseId) ? 'manual' : 'single-turn')
          || !Array.isArray(item.fixture.sourceConditions) || !item.fixture.sourceConditions.length
          || item.fixture.sourceConditions.some(value => typeof value !== 'string' || !value.trim())
          || (item.executionMode === 'manual' && (!Array.isArray(item.manualSteps) || !item.manualSteps.length
            || item.manualSteps.some(value => typeof value !== 'string' || !value.trim())))) throw new Error('Invalid troubleshooting case');
    }
    ids.add(item.caseId);
  }
  if (suite.version === 'F-012-v1' && ids.size !== 16) throw new Error('Troubleshooting suite requires QP01..QP16');
  if (suite.version === 'F-030-v1' && ids.size !== scenarioIds.length) throw new Error('Scenario suite requires all 20 cases');
  return suite;
}

export function decodeFrame(frame) {
  const data = frame.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n');
  return data ? JSON.parse(data) : null;
}

// Fixed API routes only. No source-domain save/execute commands or automatic HITL answers.
export async function runRealCase(item, binding, config, request = fetch) {
  // A single chat request cannot prove revocation, UI navigation, HITL continuation or cancellation.
  if (item.executionMode === 'manual') return { caseId: item.caseId, status: 'MANUAL_REQUIRED',
    expertReview: 'PENDING', sourceAuditStatus: 'PENDING_EXTERNAL_REVIEW',
    note: 'No request made. Complete the documented multi-step scenario in the authorized application.' };
  if (!binding || binding.account !== item.account || !Number.isSafeInteger(binding.projectId) || binding.projectId <= 0) throw new Error('Missing account/project mapping');
  const scenario = ['STANDARD_MATCH', 'MODEL_MAPPING', 'METRIC_EXPLANATION', 'METRIC_DRAFT'].includes(item.target?.purpose);
  if (scenario) validateScenarioBinding(item.target, binding.target);
  else if (item.target != null) {
    const key = ['assetId', 'qualityExecutionNo', 'qualityMonitorId'].find(k => item.target[k] != null);
    if (binding.target?.[key] == null || binding.target?.purpose !== item.target.purpose
        || Object.keys(binding.target).some(k => !Object.keys(item.target).includes(k))) throw new Error('Invalid target mapping');
  } else if (binding.target != null) throw new Error('Ordinary case cannot acquire a governance target');
  const sessionId = `ai-eval-${randomUUID()}`;
  const headers = { 'Content-Type': 'application/json', 'X-YAK-SECURITY-PROJECT-ID': String(binding.projectId), ...config.authHeaders };
  const url = route => new URL(`/api/v1/agent${route}`, config.baseUrl);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), config.timeoutMs);
  const options = { headers, redirect: 'error', signal: controller.signal };
  let turnId;
  let terminal = false;
  let reader;
  const events = [];
  const tools = new Map();
  const statuses = [];
  let text = '';
  const started = Date.now();
  try {
    const submitted = await resultJson(await request(url('/chat/turns'), { ...options, method: 'POST', body: JSON.stringify({ sessionId, message: item.question, governanceTarget: binding.target ?? null }) }));
    turnId = submitted?.turnId;
    if (!turnId) throw new Error('MISSING_TURN');
    const stream = await request(url(`/chat/turns/${encodeURIComponent(turnId)}/events`), options);
    if (!stream.ok || !stream.body) throw new Error('STREAM_UNAVAILABLE');
    reader = stream.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      buffer += decoder.decode(chunk.value, { stream: true }).replace(/\r/g, '');
      if (buffer.length > 2_000_000) throw new Error('FRAME_TOO_LARGE');
      let boundary;
      while ((boundary = buffer.indexOf('\n\n')) >= 0) {
        const event = decodeFrame(buffer.slice(0, boundary));
        buffer = buffer.slice(boundary + 2);
        if (!event) continue;
        events.push(event.type);
        if (event.type === 'TEXT_MESSAGE_CONTENT') text += event.delta ?? '';
        if (text.length > 2_000_000 || events.length > 20_000) throw new Error('OUTPUT_TOO_LARGE');
        if (event.type === 'TOOL_CALL_START') tools.set(event.toolCallId, event.toolCallName);
        if (event.rawEvent?.toolStatus) statuses.push({ tool: tools.get(event.toolCallId) ?? 'unknown', status: event.rawEvent.toolStatus });
        if (event.type === 'RUN_ERROR' || event.type === 'RUN_FINISHED') {
          terminal = true;
          const status = event.type === 'RUN_ERROR' ? 'RUN_ERROR' : event.outcome?.type === 'interrupt' ? 'AWAITING_HITL_REVIEW' : 'AWAITING_EXPERT_REVIEW';
          const trace = await resultJson(await request(url(`/turns/${encodeURIComponent(turnId)}/trace`), options));
          // RUN_FINISHED alone never proves a persisted, correlated scene delivery.
          let delivery;
          if (scenario) {
            if (trace?.turnId !== turnId || trace?.sessionId !== sessionId) throw new Error('TRACE_CONTEXT_MISMATCH');
            const continuation = await resultJson(await request(url(`/sessions/${encodeURIComponent(sessionId)}/continuation`), options));
            const history = await resultJson(await request(url(`/sessions/${encodeURIComponent(sessionId)}/history`), options));
            delivery = observeDelivery(binding.target, sessionId, turnId, continuation, history);
          }
          const totalTokens = Number.isSafeInteger(trace?.totalTokens) && trace.totalTokens >= 0 ? trace.totalTokens : null;
          return { caseId: item.caseId, status, turnId, sessionId, elapsedMs: Date.now() - started,
            totalTokens, usageStatus: totalTokens == null ? 'UNKNOWN' : trace?.completeness?.complete === false ? 'PARTIAL' : 'AVAILABLE',
            ...(delivery ? { delivery } : {}),
            toolsAttempted: [...tools.values()], toolStatuses: statuses, outputHash: hash(text),
            traceHash: hash(JSON.stringify(trace)), events,
            sourceAuditStatus: 'PENDING_EXTERNAL_REVIEW', expertReview: 'PENDING',
            note: 'Tool start is an attempt, not proof of source execution. Raw trace and answers remain in the authorized application.' };
        }
      }
    }
    throw new Error('INCOMPLETE_STREAM');
  } finally {
    clearTimeout(timer);
    await reader?.cancel().catch(() => {});
    // Cancel only the acknowledged turn, never a later session turn. The reply is not terminal evidence.
    if (turnId && !terminal) await request(url(`/chat/turns/${encodeURIComponent(turnId)}/cancel`),
      { method: 'POST', headers, body: '{}', redirect: 'error', signal: AbortSignal.timeout(10000) }).catch(() => {});
  }
}

export async function main(args = process.argv.slice(2), env = process.env) {
  const option = key => args.find(arg => arg.startsWith(`${key}=`))?.slice(key.length + 1);
  const mode = option('--mode') ?? 'offline';
  if (!['offline', 'real'].includes(mode)) throw new Error('Use --mode=offline or --mode=real');
  const suiteName = option('--suite') ?? 'task-scope';
  if (!Object.hasOwn(suites, suiteName)) throw new Error('Unknown suite');
  const suiteText = await readFile(suites[suiteName], 'utf8');
  const suite = validateSuite(JSON.parse(suiteText));
  const selected = option('--cases')?.split(',');
  if (selected?.some(id => !suite.cases.some(item => item.caseId === id))) throw new Error('Unknown case selection');
  const cases = suite.cases.filter(item => !selected || selected.includes(item.caseId));
  const repeats = Number(option('--repeats') ?? 1);
  if (!Number.isInteger(repeats) || repeats < 1 || repeats > 3) throw new Error('Repeats must be 1..3');
  const report = { mode, suite: suiteName, suiteVersion: suite.version, suiteHash: hash(suiteText), commit: env.AI_EVAL_COMMIT ?? 'UNKNOWN',
    model: mode === 'real' ? env.AI_EVAL_MODEL ?? 'UNKNOWN' : 'NOT_RUN', createdAt: new Date().toISOString(), results: [] };
  let config, mappings;
  if (mode === 'real') {
    if (!env.AI_EVAL_BASE_URL || !env.AI_EVAL_MAPPING_FILE || !env.AI_EVAL_AUTH_HEADERS) throw new Error('Real mode needs environment mapping and authentication');
    const base = new URL(env.AI_EVAL_BASE_URL);
    if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password || base.pathname !== '/' || base.search || base.hash) throw new Error('Use a service origin URL');
    const authHeaders = JSON.parse(env.AI_EVAL_AUTH_HEADERS);
    if (typeof authHeaders !== 'object' || !authHeaders || Array.isArray(authHeaders)
        || Object.entries(authHeaders).some(([k,v]) => !['authorization', 'cookie'].includes(k.toLowerCase()) || typeof v !== 'string')) throw new Error('Only Authorization/Cookie authentication headers allowed');
    config = { baseUrl: base.href, authHeaders, timeoutMs: 180000 };
    mappings = JSON.parse(await readFile(env.AI_EVAL_MAPPING_FILE, 'utf8'));
  }
  for (const item of cases) for (let repeat = 1; repeat <= repeats; repeat++) {
    const started = Date.now();
    let result = { caseId: item.caseId, status: 'NOT_RUN', expertReview: 'PENDING' };
    if (mode === 'real') {
      try { result = await runRealCase(item, mappings[item.caseId], config); }
      catch (error) {
        // Error messages may contain endpoints, credentials or private server text; retain only whitelisted codes.
        const code = /^(HTTP_\d+|API_REJECTED|MISSING_TURN|STREAM_UNAVAILABLE|INCOMPLETE_STREAM|FRAME_TOO_LARGE|OUTPUT_TOO_LARGE|RESPONSE_TOO_LARGE|TRACE_CONTEXT_MISMATCH)$/.test(error.message) ? error.message : 'RUNNER_ERROR';
        result = { caseId: item.caseId, status: 'ERROR', errorCode: code, elapsedMs: Date.now() - started, expertReview: 'PENDING' };
      }
    }
    report.results.push({ ...result, scenario: item.scenario, expectedToolAllowed: item.allowed, executionMode: item.executionMode ?? 'single-turn', repeat });
  }
  const output = resolve(option('--out') ?? resolve(root, '.task-ai-evaluation/report.json'));
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, JSON.stringify(report, null, 2) + '\n');
  console.log(`${mode}: ${report.results.length} cases, report: ${output}`);
  if (report.results.some(item => ['ERROR', 'RUN_ERROR'].includes(item.status))) process.exitCode = 1;
  return report;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(() => { console.error('Evaluation setup failed; check documented inputs. Sensitive error details are suppressed.'); process.exitCode = 1; });
}
