import type { ModelMappingTarget } from './governance';
export interface ModelMappingSuggestion {
  kind: 'MODEL_MAPPING'; target: ModelMappingTarget; expectedDefinition: string; sourceDefinition: string; targetType: string;
  skillVersion: number; skillHash: string; truncated: boolean;
  candidates: { sourceColumn: string; type: string | null; nullable: boolean; reason: string }[];
  questions: string[];
}
export function parseModelMapping(text: string): ModelMappingSuggestion | null {
  const matches = [...text.matchAll(/```yak-model-mapping\s*\n([\s\S]*?)\n```/g)];
  if (matches.length !== 1) return null;
  try {
    const value = JSON.parse(matches[0][1]) as ModelMappingSuggestion;
    if (value.kind !== 'MODEL_MAPPING' || !value.target || !/^[a-f0-9]{64}$/.test(value.expectedDefinition)
      || !/^[a-f0-9]{64}$/.test(value.sourceDefinition) || typeof value.targetType !== 'string'
      || !/^[a-f0-9]{64}$/.test(value.skillHash) || !Number.isSafeInteger(value.skillVersion) || value.skillVersion < 1
      || typeof value.truncated !== 'boolean' || !Array.isArray(value.candidates) || value.candidates.length > 3
      || !Array.isArray(value.questions) || value.questions.length > 3
      || value.questions.some(q => typeof q !== 'string' || !q.trim() || q.length > 512)
      || value.candidates.some(c => !c || typeof c.sourceColumn !== 'string' || !c.sourceColumn.trim() || c.sourceColumn.length > 128
        || (c.type !== null && (typeof c.type !== 'string' || c.type.length > 128)) || typeof c.nullable !== 'boolean'
        || typeof c.reason !== 'string' || !c.reason.trim() || c.reason.length > 512)
      || new Set(value.candidates.map(c => c.sourceColumn)).size !== value.candidates.length) return null;
    return value;
  } catch { return null; }
}
