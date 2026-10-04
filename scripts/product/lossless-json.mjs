/** Keep source-owned BIGINT identities exact without changing ordinary numeric results. */
export function parseEvidenceJson(text) {
  // Validate the original syntax before rewriting integer tokens (quoted text stays untouched).
  JSON.parse(text);
  const exact = text.replace(/("(?:\\.|[^"\\])*")|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g,
    (token, quoted, number) => {
      if (quoted || !/^-?\d+$/.test(number) || Number.isSafeInteger(Number(number))) return token;
      return `"${number}"`;
    });
  return JSON.parse(exact);
}
