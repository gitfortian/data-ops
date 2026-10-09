import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
export const catalogPath = resolve(root, 'data-ops-ui/src/services/agent/skillTemplates.generated.json');
const materials = [
  { id: 'standard-match', entry: '模型字段标准助手与批量标准辅助' },
  { id: 'model-field-mapping', entry: '模型来源映射编辑' },
  { id: 'metric-caliber-explanation', entry: '指标详情与指标编辑中的口径说明' },
  { id: 'metric-definition-draft', entry: '指标新建或编辑中的定义草稿' },
  { id: 'metric-change-review', entry: '指标详情中的版本变更核对' },
  { id: 'asset-interpretation', name: '资产解读', description: '依据所选资产的授权证据解释定义、来源和治理缺口。', entry: '资产详情中的 AI 解读' },
  { id: 'quality-explanation', name: '质量执行解读与排查', description: '围绕固定历史执行区分事实、假设和证据缺口，准备人工排查步骤。', entry: '质量执行详情中的 AI 解读与排查' },
  { id: 'candidate-review', name: '治理候选检查', description: '核对质量规则或资产描述候选，交回原页面人工采纳。', entry: '质量监控规则建议与资产描述建议' },
];

const read = path => readFileSync(path, 'utf8').replace(/\r\n/g, '\n');

/** Build-time display material only. Runtime registration continues to be owned by the DB. */
export function buildSkillTemplateCatalog() {
  return materials.map(material => {
    const directory = resolve(root, 'docs/ai/skills', material.id);
    const content = read(resolve(directory, 'SKILL.md'));
    const registration = material.name ? {
      skillId: material.id, name: material.name, description: material.description, metadata: {}, content,
    } : JSON.parse(read(resolve(directory, 'register.json')));
    if (registration.skillId !== material.id || registration.content.replace(/\r\n/g, '\n') !== content) {
      throw new Error(`Skill material drift: ${material.id}`);
    }
    return { ...registration, content, entry: material.entry, category: material.name ? '治理方法候选' : '场景模板' };
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const rendered = `${JSON.stringify(buildSkillTemplateCatalog(), null, 2)}\n`;
  if (process.argv.includes('--write')) {
    writeFileSync(catalogPath, rendered);
    console.log('Skill template catalog generated.');
  } else {
    if (read(catalogPath) !== rendered) throw new Error('Skill template catalog is stale; run node scripts/ai/skill-template-catalog.mjs --write');
    console.log('Skill template catalog matches source materials.');
  }
}
