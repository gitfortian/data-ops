import {
  AGENT_SKILL_MANAGE,
  AGENT_SKILL_READ,
  classifySkillError,
  parseSkillMetadata,
  requireSkillVersion,
  SKILL_DESCRIPTION_MAX,
  SKILL_ERROR_CONFLICT,
  SKILL_ERROR_EXISTS,
  SKILL_ERROR_NOT_FOUND,
  SKILL_ID_MAX,
  SKILL_ID_PATTERN,
  SKILL_NAME_MAX,
  shouldShowSkillTab,
  skillEditorInitialValues,
  skillTagVisual,
  validateSkillInput,
} from './skill-runtime';

describe('shouldShowSkillTab（文档 §1/§4，验收项 1）', () => {
  it('无 read 权限不显示 Tab', () => {
    expect(shouldShowSkillTab([])).toBe(false);
    expect(shouldShowSkillTab(['agent:chat:run'])).toBe(false);
    expect(shouldShowSkillTab(['agent:skill:manage'])).toBe(false);
  });

  it('有 read 权限显示 Tab', () => {
    expect(shouldShowSkillTab([AGENT_SKILL_READ])).toBe(true);
    expect(shouldShowSkillTab(['agent:chat:run', AGENT_SKILL_READ])).toBe(true);
  });

  it('security:root 通配可见', () => {
    expect(shouldShowSkillTab(['security:root'])).toBe(true);
  });
});

describe('validateSkillInput（文档 §3.3 表单校验）', () => {
  const base = { skillId: 'asset-yoy', name: '资产同比分析', content: '当用户询问资产同比时按口径输出。' };

  it('完全合法时返回 null', () => {
    expect(validateSkillInput(base)).toBeNull();
  });

  it('skillId 为空返回错误', () => {
    expect(validateSkillInput({ ...base, skillId: '  ' })).toBe('技能标识不能为空');
  });

  it('skillId 超过 64 返回错误', () => {
    expect(validateSkillInput({ ...base, skillId: 'a'.repeat(65) })).toBe(`技能标识长度不能超过 ${SKILL_ID_MAX}`);
  });

  it.each(['asset_yoy', '资产-yoy', 'asset.yoy', 'asset yoy'])('skillId 非法字符 %s 返回错误', (skillId) => {
    expect(SKILL_ID_PATTERN.test(skillId)).toBe(false);
    expect(validateSkillInput({ ...base, skillId })).toBe('技能标识仅支持字母、数字与中划线');
  });

  it('name 为空返回错误', () => {
    expect(validateSkillInput({ ...base, name: '' })).toBe('技能名称不能为空');
  });

  it('name 超过 128 返回错误', () => {
    expect(validateSkillInput({ ...base, name: 'n'.repeat(129) })).toBe(`技能名称长度不能超过 ${SKILL_NAME_MAX}`);
  });

  it('description 超过 512 返回错误', () => {
    expect(validateSkillInput({ ...base, description: 'd'.repeat(513) })).toBe(
      `技能描述长度不能超过 ${SKILL_DESCRIPTION_MAX}`,
    );
  });

  it('content 为空返回错误', () => {
    expect(validateSkillInput({ ...base, content: '  ' })).toBe('技能正文不能为空');
  });
});

describe('parseSkillMetadata（JSON 编辑区校验）', () => {
  it('空文本视为未填写', () => {
    expect(parseSkillMetadata('')).toEqual({ value: undefined });
    expect(parseSkillMetadata('   ')).toEqual({ value: undefined });
  });

  it('非法 JSON 返回错误文案', () => {
    expect(parseSkillMetadata('{not-json')).toEqual({ error: '元数据不是合法 JSON' });
  });

  it('非对象（数组）返回对象约束错误', () => {
    expect(parseSkillMetadata('[1,2]')).toEqual({ error: expect.stringContaining('JSON 对象') });
  });

  it('null 也视为非对象', () => {
    expect(parseSkillMetadata('null')).toEqual({ error: expect.stringContaining('JSON 对象') });
  });

  it('合法对象返回解析值', () => {
    expect(parseSkillMetadata('{"tags":["资产"],"owner":"数据组"}')).toEqual({
      value: { tags: ['资产'], owner: '数据组' },
    });
  });
});

describe('classifySkillError（文档 §2.2 错误分流）', () => {
  it('404 一律提示技能不存在并要求刷新', () => {
    for (const context of ['register', 'update', 'toggle', 'remove', 'detail', 'list'] as const) {
      expect(classifySkillError({ code: 404 }, context)).toEqual({
        message: SKILL_ERROR_NOT_FOUND,
        reload: true,
      });
    }
  });

  it('409 注册时提示技能已存在', () => {
    expect(classifySkillError({ code: 409 }, 'register')).toEqual({
      message: SKILL_ERROR_EXISTS,
      reload: true,
    });
  });

  it('409 更新/删除时提示版本冲突并要求刷新', () => {
    for (const context of ['update', 'remove'] as const) {
      expect(classifySkillError({ code: 409 }, context)).toEqual({
        message: SKILL_ERROR_CONFLICT,
        reload: true,
      });
    }
  });

  it('409 启停失败不强制刷新（开关回滚即可）', () => {
    expect(classifySkillError({ code: 409 }, 'toggle')).toEqual({
      message: SKILL_ERROR_CONFLICT,
      reload: false,
    });
  });

  it('400 透出后端 message 兜底', () => {
    expect(classifySkillError({ code: 400, message: '技能标识长度不能超过 64' }, 'register')).toEqual({
      message: '技能标识长度不能超过 64',
      reload: false,
    });
    expect(classifySkillError({ code: 400 }, 'register').message).toBe('参数不合法，请检查输入内容');
  });

  it('未知错误透出错误 message，缺省用通用文案', () => {
    expect(classifySkillError({ code: 500, message: '服务器内部错误' }, 'list').message).toBe('服务器内部错误');
    expect(classifySkillError({}, 'list')).toEqual({ message: '操作失败，请稍后重试', reload: false });
  });
});

describe('skillTagVisual（列表 Tag 视觉）', () => {
  it('启用为绿色「启用」', () => {
    expect(skillTagVisual({ skillId: 'asset-yoy', enabled: true })).toEqual({
      id: 'asset-yoy',
      statusText: '启用',
      statusColor: 'green',
    });
  });

  it('停用为默认色「停用」', () => {
    expect(skillTagVisual({ skillId: 'asset-yoy', enabled: false })).toEqual({
      id: 'asset-yoy',
      statusText: '停用',
      statusColor: 'default',
    });
  });
});

describe('skillEditorInitialValues（编辑器回填）', () => {
  it('注册模式返回空表单', () => {
    expect(skillEditorInitialValues(null)).toEqual({
      skillId: '',
      name: '',
      description: '',
      content: '',
      metadata: '',
    });
  });

  it('更新模式回填字段并将 metadata 格式化为 JSON 文本', () => {
    expect(
      skillEditorInitialValues({
        skillId: 'asset-yoy',
        name: '资产同比分析',
        description: '口径描述',
        content: '正文',
        enabled: true,
        version: 3,
        createTime: '',
        updateTime: '',
        metadata: { tags: ['资产'] },
      }),
    ).toEqual({
      skillId: 'asset-yoy',
      name: '资产同比分析',
      description: '口径描述',
      content: '正文',
      metadata: JSON.stringify({ tags: ['资产'] }, null, 2),
    });
  });

  it('metadata 为空对象时编辑区留空', () => {
    const values = skillEditorInitialValues({
      skillId: 'x',
      name: 'x',
      description: '',
      content: 'x',
      enabled: true,
      version: 1,
      createTime: '',
      updateTime: '',
      metadata: {},
    });
    expect(values.metadata).toBe('');
  });
});

describe('requireSkillVersion（更新乐观 CAS 前置）', () => {
  it('列表项带 version 时回传', () => {
    expect(requireSkillVersion({ version: 3 })).toBe(3);
  });

  it('缺省/无 version 时返回 null', () => {
    expect(requireSkillVersion(undefined)).toBeNull();
    expect(requireSkillVersion(null)).toBeNull();
    expect(requireSkillVersion({})).toBeNull();
  });
});

describe('权限码常量（文档 §4 权限集成）', () => {
  it('read 控制 Tab 可见性，manage 控制操作', () => {
    expect(AGENT_SKILL_READ).toBe('agent:skill:read');
    expect(AGENT_SKILL_MANAGE).toBe('agent:skill:manage');
  });
});
