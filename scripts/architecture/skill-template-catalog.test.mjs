import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { buildSkillTemplateCatalog, catalogPath } from '../ai/skill-template-catalog.mjs';

test('shipped Skill display templates match the canonical instructions and registration materials', () => {
  const templates = buildSkillTemplateCatalog();
  assert.deepEqual(JSON.parse(readFileSync(catalogPath, 'utf8')), templates);
  assert.equal(templates.length, 8);
  assert.equal(new Set(templates.map(value => value.skillId)).size, templates.length);
  for (const value of templates) {
    assert.match(value.skillId, /^[a-zA-Z0-9-]{1,64}$/);
    assert.ok(value.content.trim());
    assert.ok(value.name.length <= 128);
    assert.ok(value.description.length <= 512);
    assert.ok(!('enabled' in value) && !('version' in value) && !('expectedVersion' in value));
  }
});
