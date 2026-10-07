/** Decode only the server-validated delivery saved in history, never live model text. */
export function readStructuredReceipt(text: string, marker: 'yak-standard-match' | 'yak-model-mapping' | 'yak-metric-explanation'): unknown {
  const matches = [...text.matchAll(new RegExp('```' + marker + '\\s*\\n([\\s\\S]*?)\\n```', 'g'))];
  if (matches.length !== 1) throw new Error('场景交付不唯一');
  return JSON.parse(matches[0][1]);
}
