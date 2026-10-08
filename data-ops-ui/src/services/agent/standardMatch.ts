import { readStructuredReceipt } from './structuredReceipt';
import type { StandardMatchTarget } from './governance';
export interface StandardMatchSuggestion {
  kind: 'STANDARD_MATCH'; target: StandardMatchTarget; expectedDefinition: string;
  skillVersion: number; skillHash: string; truncated: boolean;
  candidates: { standardId: number; version: number; code: string; name: string; stdType: string; reason: string }[];
  questions: string[];
  fieldDescription?: string | null;
}
export function parseStandardMatch(text: string): StandardMatchSuggestion | null {
  try {
    const value = readStructuredReceipt(text, 'yak-standard-match') as StandardMatchSuggestion;
    if (value.kind !== 'STANDARD_MATCH' || !value.target || !/^[a-f0-9]{64}$/.test(value.expectedDefinition)
      || !/^[a-f0-9]{64}$/.test(value.skillHash) || !Number.isSafeInteger(value.skillVersion) || value.skillVersion < 1
      || (value.fieldDescription != null && (typeof value.fieldDescription !== 'string' || !value.fieldDescription.trim() || value.fieldDescription.length > 512))
      || !Array.isArray(value.candidates) || value.candidates.length > 3
      || !Array.isArray(value.questions) || value.questions.length > 3
      || value.questions.some(q => typeof q !== 'string' || !q.trim() || q.length > 512)
      || value.candidates.some(c => !c || !Number.isSafeInteger(c.standardId) || c.standardId < 1
        || !Number.isSafeInteger(c.version) || c.version < 1
        || [c.code, c.name, c.stdType, c.reason].some(v => typeof v !== 'string') || !c.reason.trim() || c.reason.length > 512)
      || new Set(value.candidates.map(c => c.standardId)).size !== value.candidates.length) return null;
    return value;
  } catch { return null; }
}
