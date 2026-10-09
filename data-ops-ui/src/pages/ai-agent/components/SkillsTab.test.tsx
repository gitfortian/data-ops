import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { message } from 'antd';
import React from 'react';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { agentSkillApi } from '@/services/agent';
import { skillTemplates } from '@/services/agent/skillTemplates';
import { AGENT_SKILL_MANAGE, AGENT_SKILL_READ, SKILL_ERROR_CONFLICT, SKILL_ERROR_EXISTS } from '../skill-runtime';
import SkillsTab from './SkillsTab';

jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: jest.fn(),
}));

// Monaco runs in the browser. Management-flow tests cross its controlled text seam.
jest.mock('./SkillMarkdownSourceEditor', () => {
  const React = jest.requireActual<typeof import('react')>('react');
  return {
    __esModule: true,
    default: React.forwardRef(
      (props: { value: string; onChange: (value: string) => void; disabled: boolean }, _ref) => (
        <textarea
          aria-label="技能 Markdown 源码"
          value={props.value}
          disabled={props.disabled}
          onChange={(event) => props.onChange(event.target.value)}
        />
      ),
    ),
  };
});

jest.mock('@/services/agent', () => ({
  agentSkillApi: {
    register: jest.fn(),
    list: jest.fn(),
    detail: jest.fn(),
    update: jest.fn(),
    setActive: jest.fn(),
    remove: jest.fn(),
  },
}));

// 注意：被 jest.mock 的具名绑定不能进入类型标注（Babel commonjs 转换限制），
// 这里统一降级为运行时值 + 中性类型，断言经 jest.Mock 属性访问。
const mockedPermission = usePermissionAccess as unknown as jest.Mock;
const mockedApi = agentSkillApi as unknown as Record<string, jest.Mock>;

const skillsFixture = [
  {
    skillId: 'asset-yoy',
    name: '资产同比分析',
    description: '按统一口径输出资产同比对比结论',
    metadata: { tags: ['资产'] },
    content: '## 指令\n当用户询问资产同比分析时，先确认资产范围与期间，再按口径对比输出。',
    enabled: true,
    version: 2,
    createTime: '2026-08-30T10:00:00',
    updateTime: '2026-08-31T09:00:00',
  },
  {
    skillId: 'sales-rank',
    name: '销量排行分析',
    description: '输出销量 Top10',
    metadata: {},
    content: '当用户询问销量排行时输出最近 30 天销量 Top10。',
    enabled: false,
    version: 1,
    createTime: '2026-08-29T10:00:00',
    updateTime: '2026-08-29T11:00:00',
  },
];

const renderTab = (permissionCodes: readonly string[] = [AGENT_SKILL_READ, AGENT_SKILL_MANAGE]) => {
  const granted = new Set(permissionCodes);
  mockedPermission.mockReturnValue({
    can: jest.fn((code: string) => granted.has(code)),
  } as { can: jest.Mock });
  return render(React.createElement(SkillsTab));
};

beforeEach(() => {
  jest.spyOn(message, 'success').mockImplementation(() => undefined as never);
  jest.spyOn(message, 'error').mockImplementation(() => undefined as never);
  jest.spyOn(message, 'warning').mockImplementation(() => undefined as never);
  mockedApi.list.mockResolvedValue(skillsFixture);
});

afterEach(() => {
  jest.restoreAllMocks();
  jest.clearAllMocks();
});

describe('SkillsTab 权限收敛（文档 §4 / §3.2，验收项 8）', () => {
  it('有 manage 权限：展示注册按钮与编辑/删除入口，Switch 可操作', async () => {
    renderTab();
    await screen.findByText('资产同比分析');
    expect(screen.getByRole('button', { name: /注册技能/ })).toBeTruthy();
    expect(screen.getAllByRole('switch')[0]).toHaveProperty('disabled', false);
    expect(screen.getAllByRole('button', { name: /编\s*辑/ })[0]).toBeTruthy();
  });

  it('无 manage 权限：列表只读（无注册按钮、无编辑/删除，Switch 禁用，仅详情）', async () => {
    renderTab([AGENT_SKILL_READ]);
    await screen.findByText('资产同比分析');
    expect(screen.queryByRole('button', { name: /注册技能/ })).toBeNull();
    expect(screen.getAllByRole('switch')[0]).toHaveProperty('disabled', true);
    expect(screen.getAllByRole('button', { name: /详\s*情/ })[0]).toBeTruthy();
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /删\s*除/ })).toBeNull();
  });
});

describe('SkillsTab 列表加载', () => {
  it('挂载即拉取技能列表并渲染 name + skillId Tag + 启停状态', async () => {
    renderTab();
    await screen.findByText('资产同比分析');
    expect(mockedApi.list).toHaveBeenCalledTimes(1);
    expect(screen.getByText('asset-yoy')).toBeTruthy();
    expect(screen.getByText('sales-rank')).toBeTruthy();
    const switches = screen.getAllByRole('switch');
    expect(switches[0].getAttribute('aria-checked')).toBe('true');
    expect(switches[1].getAttribute('aria-checked')).toBe('false');
  });
});

describe('SkillsTab 模板发现与显式注册', () => {
  const template = skillTemplates[0];
  const card = () => within(screen.getByRole('group', { name: template.name }));

  it('空表仍展示八份材料，查看正文零写入；只读用户无法注册', async () => {
    mockedApi.list.mockResolvedValue([]);
    renderTab([AGENT_SKILL_READ]);
    await screen.findByText('当前项目暂无已注册技能，请联系技能管理员注册。');
    expect(screen.getAllByRole('group')).toHaveLength(8);
    expect(screen.queryByRole('button', { name: '查看并注册' })).toBeNull();
    fireEvent.click(card().getByRole('button', { name: '查看正文' }));
    const preview = await screen.findByRole('dialog');
    fireEvent.click(within(preview).getByText('源码', { exact: true }));
    expect(preview.textContent).toContain(template.content);
    expect(mockedApi.register).not.toHaveBeenCalled();
    expect(mockedApi.update).not.toHaveBeenCalled();
    expect(mockedApi.setActive).not.toHaveBeenCalled();
  });

  it('带入原材料并保留人工编辑，只有保存后注册、刷新登记状态', async () => {
    mockedApi.list.mockResolvedValue([]);
    renderTab();
    await screen.findByText('当前项目暂无已注册技能，可从下方模板查看并注册。');
    fireEvent.click(card().getByRole('button', { name: '查看并注册' }));
    await screen.findByText('技能标识（skillId）');
    expect(screen.getByPlaceholderText('如 asset-yoy')).toHaveProperty('value', template.skillId);
    expect(screen.getByPlaceholderText('如 asset-yoy')).toHaveProperty('disabled', true);
    expect(await screen.findByRole('textbox', { name: '技能 Markdown 源码' })).toHaveProperty(
      'value',
      template.content,
    );
    expect(JSON.parse((screen.getByPlaceholderText(/"tags"/) as HTMLTextAreaElement).value)).toEqual(template.metadata);
    expect(mockedApi.register).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', { name: '技能 Markdown 源码' }), {
      target: { value: '经管理员核对的正文' },
    });
    mockedApi.register.mockResolvedValue({ ...template, enabled: true, version: 1 });
    mockedApi.list.mockResolvedValue([{ ...template, content: '经管理员核对的正文', enabled: true, version: 1 }]);
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() =>
      expect(mockedApi.register).toHaveBeenCalledWith({
        skillId: template.skillId,
        name: template.name,
        description: template.description,
        content: '经管理员核对的正文',
        metadata: template.metadata,
      }),
    );
    await waitFor(() => expect(card().getByText('已注册 · 已启用')).toBeTruthy());
    expect(card().queryByRole('button', { name: '查看并注册' })).toBeNull();
    expect(mockedApi.update).not.toHaveBeenCalled();
  });

  it('同 ID 已停用也不能重复登记或覆盖当前版本', async () => {
    mockedApi.list.mockResolvedValue([{ ...template, content: '用户维护的正文', enabled: false, version: 7 }]);
    renderTab();
    await waitFor(() => expect(card().getByText('已注册 · 已停用')).toBeTruthy());
    expect(card().queryByRole('button', { name: '查看并注册' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /编\s*辑/ }));
    await screen.findByText('技能标识（skillId）');
    expect(await screen.findByRole('textbox', { name: '技能 Markdown 源码' })).toHaveProperty(
      'value',
      '用户维护的正文',
    );
    expect(mockedApi.register).not.toHaveBeenCalled();
    expect(mockedApi.update).not.toHaveBeenCalled();
  });

  it('列表失败保持未知并禁止模板登记，重试成功才允许登记', async () => {
    mockedApi.list.mockRejectedValueOnce({ code: 500, message: '服务暂不可用' }).mockResolvedValue([]);
    renderTab();
    await screen.findByText('已注册技能加载失败，登记状态待确认');
    expect(card().getByText('登记状态待确认')).toBeTruthy();
    expect(card().getByRole('button', { name: '查看并注册' })).toHaveProperty('disabled', true);
    expect(card().queryByText('尚未注册')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
    await screen.findByText('当前项目暂无已注册技能，可从下方模板查看并注册。');
    expect(card().getByRole('button', { name: '查看并注册' })).toHaveProperty('disabled', false);
    expect(card().getByText('尚未注册')).toBeTruthy();
    expect(mockedApi.register).not.toHaveBeenCalled();
  });

  it('加载完成前不把模板当成未注册', async () => {
    mockedApi.list.mockReturnValue(new Promise(() => undefined));
    renderTab();
    expect(card().getByText('登记状态待确认')).toBeTruthy();
    expect(card().getByRole('button', { name: '查看并注册' })).toHaveProperty('disabled', true);
  });

  it('取消后切换模板或自定义登记不沿用上次表单内容', async () => {
    mockedApi.list.mockResolvedValue([]);
    renderTab();
    await screen.findByText('当前项目暂无已注册技能，可从下方模板查看并注册。');
    fireEvent.click(card().getByRole('button', { name: '查看并注册' }));
    await screen.findByText('技能标识（skillId）');
    fireEvent.change(await screen.findByRole('textbox', { name: '技能 Markdown 源码' }), {
      target: { value: '未提交的编辑' },
    });
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    const second = skillTemplates[1];
    fireEvent.click(
      within(screen.getByRole('group', { name: second.name })).getByRole('button', { name: '查看并注册' }),
    );
    await screen.findByText('技能标识（skillId）');
    expect(await screen.findByRole('textbox', { name: '技能 Markdown 源码' })).toHaveProperty('value', second.content);
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    fireEvent.click(screen.getByRole('button', { name: /注册技能/ }));
    await screen.findByText('技能标识（skillId）');
    expect(screen.getByPlaceholderText('如 asset-yoy')).toHaveProperty('value', '');
    expect(screen.getByPlaceholderText('如 asset-yoy')).toHaveProperty('disabled', false);
    expect(await screen.findByRole('textbox', { name: '技能 Markdown 源码' })).toHaveProperty('value', '');
    expect(screen.getByPlaceholderText(/"tags"/)).toHaveProperty('value', '');
    expect(mockedApi.register).not.toHaveBeenCalled();
  });
});

describe('SkillsTab 在线注册（文档 §5.1，验收项 2）', () => {
  const fillRegisterForm = () => {
    fireEvent.change(screen.getByPlaceholderText('如 asset-yoy'), {
      target: { value: 'asset-yoy' },
    });
    fireEvent.change(screen.getByPlaceholderText('如 资产同比分析'), {
      target: { value: '资产同比分析' },
    });
    fireEvent.change(screen.getByPlaceholderText(/如 用户询问资产同比时/), {
      target: { value: '按统一口径输出资产同比对比结论' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: '技能 Markdown 源码' }), {
      target: { value: '当用户询问资产同比分析时，先确认资产范围，再按口径输出。' },
    });
    fireEvent.change(screen.getByPlaceholderText(/"tags"/), {
      target: { value: '{"tags":["资产","同比"]}' },
    });
  };

  it('注册成功后刷新列表且新技能 enabled=true 出现在列表（热生效主路径）', async () => {
    renderTab();
    await screen.findByText('资产同比分析');
    mockedApi.register.mockResolvedValue({ ...skillsFixture[0], version: 1 });
    mockedApi.list.mockResolvedValue([{ ...skillsFixture[0], version: 1 }, skillsFixture[1]]);

    fireEvent.click(screen.getByRole('button', { name: /注册技能/ }));
    await screen.findByText('技能标识（skillId）');
    await screen.findByRole('textbox', { name: '技能 Markdown 源码' });
    fillRegisterForm();
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => {
      expect(mockedApi.register).toHaveBeenCalledWith({
        skillId: 'asset-yoy',
        name: '资产同比分析',
        description: '按统一口径输出资产同比对比结论',
        content: '当用户询问资产同比分析时，先确认资产范围，再按口径输出。',
        metadata: { tags: ['资产', '同比'] },
      });
    });
    expect(message.success).toHaveBeenCalledWith('技能已注册，下一轮对话生效');
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });

  it('重复注册被 409 拦截并提示「技能已存在」后自动刷新列表', async () => {
    renderTab();
    await screen.findByText('资产同比分析');
    mockedApi.register.mockRejectedValue({ code: 409, message: '技能已存在，无法重复注册' });

    fireEvent.click(screen.getByRole('button', { name: /注册技能/ }));
    await screen.findByText('技能标识（skillId）');
    await screen.findByRole('textbox', { name: '技能 Markdown 源码' });
    fillRegisterForm();
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith(SKILL_ERROR_EXISTS);
    });
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });
});

describe('SkillsTab 在线启停（文档 §5.2，验收项 3）', () => {
  it('切换即调 setActive，成功后就地更新 enabled（乐观 UI）', async () => {
    renderTab();
    await screen.findByText('资产同比分析');
    mockedApi.setActive.mockResolvedValue(true);

    fireEvent.click(screen.getAllByRole('switch')[0]);

    await waitFor(() => {
      expect(mockedApi.setActive).toHaveBeenCalledWith('asset-yoy', false);
    });
    await waitFor(() => {
      expect(screen.getAllByRole('switch')[0].getAttribute('aria-checked')).toBe('false');
    });
  });

  it('启停失败提示错误并回滚（enabled 保持不变）', async () => {
    renderTab();
    await screen.findByText('资产同比分析');
    mockedApi.setActive.mockRejectedValue({ code: 500, message: '启停失败' });

    fireEvent.click(screen.getAllByRole('switch')[0]);

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith('启停失败');
    });
    expect(screen.getAllByRole('switch')[0].getAttribute('aria-checked')).toBe('true');
  });
});

describe('SkillsTab 更新（文档 §5.3，验收项 4）', () => {
  const openEditAndSave = async (newContent: string) => {
    fireEvent.click(screen.getAllByRole('button', { name: /编\s*辑/ })[0]);
    await screen.findByText(/编辑技能/);
    fireEvent.change(await screen.findByRole('textbox', { name: '技能 Markdown 源码' }), {
      target: { value: newContent },
    });
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  };

  it('编辑提交回传 skillId 与更新内容，成功提示「下一轮对话生效」并刷新', async () => {
    renderTab();
    await screen.findByText('资产同比分析');
    mockedApi.update.mockResolvedValue({ ...skillsFixture[0], content: 'new', version: 3 });

    await openEditAndSave('当用户询问资产同比时按新版口径输出。');

    await waitFor(() => {
      expect(mockedApi.update).toHaveBeenCalledWith('asset-yoy', {
        skillId: 'asset-yoy',
        name: '资产同比分析',
        description: '按统一口径输出资产同比对比结论',
        content: '当用户询问资产同比时按新版口径输出。',
        expectedVersion: 2,
        metadata: { tags: ['资产'] },
      });
    });
    expect(message.success).toHaveBeenCalledWith('技能已更新，下一轮对话生效');
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });

  it('编辑模式下 skillId 只读（Input 禁用）', async () => {
    renderTab();
    await screen.findByText('资产同比分析');
    fireEvent.click(screen.getAllByRole('button', { name: /编\s*辑/ })[0]);
    await screen.findByText(/编辑技能/);
    expect(screen.getByPlaceholderText('如 asset-yoy')).toHaveProperty('disabled', true);
  });

  it('更新 409（并发编辑）提示「已被他人修改」并自动刷新列表', async () => {
    renderTab();
    await screen.findByText('资产同比分析');
    mockedApi.update.mockRejectedValue({ code: 409, message: '技能乐观版本冲突' });

    await openEditAndSave('并发修改内容。');

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith(SKILL_ERROR_CONFLICT);
    });
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });
});

describe('SkillsTab 删除（文档 §5.4，验收项 5）', () => {
  const confirmDelete = async () => {
    const popover = await waitFor(() => {
      const node = document.querySelector('.ant-popconfirm');
      expect(node).toBeTruthy();
      return node as HTMLElement;
    });
    return within(popover).getAllByRole('button', { name: /删\s*除/ })[0];
  };

  it('Popconfirm 确认后删除并从列表移除', async () => {
    renderTab();
    await screen.findByText('销量排行分析');
    mockedApi.remove.mockResolvedValue(true);

    // 目标为第二行 sales-rank：点击第二行的删除按钮（第一行是 asset-yoy）
    fireEvent.click(screen.getAllByRole('button', { name: /删\s*除/ })[1]);
    fireEvent.click(await confirmDelete());

    await waitFor(() => {
      expect(mockedApi.remove).toHaveBeenCalledWith('sales-rank');
    });
    await waitFor(() => {
      expect(screen.queryByText('销量排行分析')).toBeNull();
    });
  });

  it('删除失败提示错误且列表保留', async () => {
    renderTab();
    await screen.findByText('销量排行分析');
    mockedApi.remove.mockRejectedValue({ code: 500, message: '删除失败' });

    fireEvent.click(screen.getAllByRole('button', { name: /删\s*除/ })[0]);
    fireEvent.click(await confirmDelete());

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith('删除失败');
    });
    expect(screen.getByText('销量排行分析')).toBeTruthy();
  });
});

describe('SkillsTab 详情（文档 §3.4，验收项 6）', () => {
  it('详情 Drawer 展示正文 Markdown 渲染与 metadata JSON', async () => {
    renderTab();
    await screen.findByText('资产同比分析');
    fireEvent.click(screen.getAllByRole('button', { name: /详\s*情/ })[0]);

    const drawer = await screen.findByRole('heading', { name: '指令' });
    expect(drawer).toBeTruthy();
    const drawerScope = document.querySelector('.ant-drawer-content') as HTMLElement;
    expect(drawerScope).toBeTruthy();
    expect(within(drawerScope).getByText(/当用户询问资产同比分析时，先确认资产范围与期间/)).toBeTruthy();
    expect(within(drawerScope).getByText(/"tags"/)).toBeTruthy();
    expect(within(drawerScope).getByText('版本')).toBeTruthy();
    expect(within(drawerScope).getByText('2')).toBeTruthy();
  });
});
