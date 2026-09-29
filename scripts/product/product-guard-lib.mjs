export const PLACEHOLDER_PATTERN = /^(?:n\/a|na|none|todo|tbd|-|yes\s*\/\s*no|product\s*\/\s*technical\s*\/\s*docs\s*\/\s*ops)$/i;

export const normalize = (value) => (value ?? "").trim();

export const isMeaningful = (value) => {
  const text = normalize(value);
  return Boolean(text) && !PLACEHOLDER_PATTERN.test(text);
};

export const sectionBody = (body, heading) => {
  const lines = (body ?? "").replace(/\r/g, "").split("\n");
  const target = `## ${heading}`.toLowerCase();
  const start = lines.findIndex((line) => line.trim().toLowerCase() === target);
  if (start < 0) return "";

  const out = [];
  for (let index = start + 1; index < lines.length; index += 1) {
    if (/^##\s+/.test(lines[index])) break;
    out.push(lines[index]);
  }
  return out.join("\n");
};

export const readField = (section, label) => {
  const prefixes = [`${label}:`, `- ${label}:`, `* ${label}:`]
    .map((value) => value.toLowerCase());

  for (const rawLine of (section ?? "").split("\n")) {
    const line = rawLine.trim();
    const lower = line.toLowerCase();
    const prefix = prefixes.find((candidate) => lower.startsWith(candidate));
    if (!prefix) continue;
    const value = line.slice(prefix.length).trim();
    return isMeaningful(value) ? value : "";
  }
  return "";
};

export const parseYesNo = (value) => {
  const text = normalize(value).toLowerCase();
  if (text === "yes") return true;
  if (text === "no") return false;
  return undefined;
};

export const extractBusinessModules = (pom) =>
  new Set(
    [...(pom ?? "").matchAll(/<module>(?:data|yak)-ops-business-([^<]+)<\/module>/g)]
      .map((match) => match[1].trim())
      .filter(Boolean),
  );

export const extractTopLevelNavigationGroups = (source) => {
  const text = source ?? "";
  const start = text.indexOf("export const navigationGroups");
  if (start < 0) return new Set();

  const end = text.indexOf("];", start);
  const block = end < 0 ? text.slice(start) : text.slice(start, end);
  const groups = new Set();

  for (const object of block.match(/\{[^{}]*\}/g) ?? []) {
    if (/\bparentGroupId\s*:/.test(object)) continue;
    const id = object.match(/\bid\s*:\s*['"]([^'"]+)['"]/)?.[1];
    if (id) groups.add(id);
  }
  return groups;
};

export const parseDecisionStatus = (content) =>
  (content ?? "").match(/^Status:\s*(\S+)/mi)?.[1]?.toUpperCase();

export const parseDecisionImplementation = (content) =>
  (content ?? "").match(/^Implementation:\s*(\S+)/mi)?.[1]?.toUpperCase();

export const parseFeatureStatus = (content) =>
  (content ?? "").match(/^Status:\s*(\S+)/mi)?.[1]?.toUpperCase();

export const acceptanceFromPrBody = (body) => {
  const section = sectionBody(body, "Acceptance");
  return {
    scenario: readField(section, "Scenario"),
    evidence: readField(section, "Evidence"),
  };
};
