/**
 * 技能管理页面纯逻辑运行时（skills online management，文档 §3 交互流）。
 *
 * 与 ai-agent 既有 stream-runtime/trace-runtime 同范式：React 组件只负责装配与渲染，
 * 错误分流 / 表单校验 / 元数据解析等业务判定集中在此，可单测。
 *
 * 权限语义（文档 §1/§4）：SKILL_READ='agent:skill:read' 控制 Tab 可见性，
 * SKILL_MANAGE='agent:skill:manage' 控制操作（注册/编辑/启停/删除）。
 */

import type { AgentSkillItem } from '@/services/agent';
import { hasPermission } from '@/utils/security/permission';

export const AGENT_SKILL_READ = 'agent:skill:read';
export const AGENT_SKILL_MANAGE = 'agent:skill:manage';

/** 技能管理 Tab 可见性（文档 §1/§4，验收项 1）：无 read 权限不显示；security:root 通配。 */
export const shouldShowSkillTab = (permissionCodes: readonly string[]): boolean =>
  hasPermission(permissionCodes, AGENT_SKILL_READ);

/** skillId 合法格式：字母数字中划线（与后端大小上限一致，前端先拦非法字符）。 */
export const SKILL_ID_PATTERN = /^[A-Za-z0-9-]+$/;
export const SKILL_ID_MAX = 64;
export const SKILL_NAME_MAX = 128;
export const SKILL_DESCRIPTION_MAX = 512;

/** 技能已存在提示（注册 409）。 */
export const SKILL_ERROR_EXISTS = '技能已存在，请更换 skillId 或改用更新';
/** 更新版本冲突提示（更新 409）。 */
export const SKILL_ERROR_CONFLICT = '技能已被他人修改（版本冲突），请刷新后重试';
/** 技能不存在提示（404）。 */
export const SKILL_ERROR_NOT_FOUND = '技能不存在（可能已被删除），已刷新列表';

/** 校验输入合法性：返回错误文案，null 表示通过。 */
export const validateSkillInput = (input: {
  skillId: string;
  name: string;
  description?: string;
  content: string;
}): string | null => {
  const skillId = input.skillId.trim();
  if (!skillId) {
    return '技能标识不能为空';
  }
  if (skillId.length > SKILL_ID_MAX) {
    return `技能标识长度不能超过 ${SKILL_ID_MAX}`;
  }
  if (!SKILL_ID_PATTERN.test(skillId)) {
    return '技能标识仅支持字母、数字与中划线';
  }
  if (!input.name.trim()) {
    return '技能名称不能为空';
  }
  if (input.name.trim().length > SKILL_NAME_MAX) {
    return `技能名称长度不能超过 ${SKILL_NAME_MAX}`;
  }
  if ((input.description ?? '').trim().length > SKILL_DESCRIPTION_MAX) {
    return `技能描述长度不能超过 ${SKILL_DESCRIPTION_MAX}`;
  }
  if (!input.content.trim()) {
    return '技能正文不能为空';
  }
  return null;
};

/** 元数据 JSON 文本 -> 对象；空文本视为未填写（null），非法 JSON 返回错误文案。 */
export const parseSkillMetadata = (raw: string): { error?: string; value?: Record<string, unknown> } => {
  const text = raw.trim();
  if (!text) {
    return { value: undefined };
  }
  try {
    const parsed: unknown = JSON.parse(text);
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { error: '元数据必须是 JSON 对象（例如 {"tags":["资产分析"]}）' };
    }
    return { value: parsed as Record<string, unknown> };
  } catch {
    return { error: '元数据不是合法 JSON' };
  }
};

/** 技能条目标签视觉：skillId 作 Tag 展示名（文档 §2.1），enabled 映射状态色。 */
export const skillTagVisual = (item: { skillId: string; enabled: boolean }) => ({
  id: item.skillId,
  statusText: item.enabled ? '启用' : '停用',
  statusColor: item.enabled ? 'green' : 'default',
});

/** 按错误 code 分流：返回页面提示文案与是否需要刷新列表的动作。 */
export const classifySkillError = (
  error: unknown,
  context: 'register' | 'update' | 'toggle' | 'remove' | 'detail' | 'list',
): { message: string; reload: boolean } => {
  const code = (error as { code?: number })?.code;
  switch (code) {
    case 404:
      return { message: SKILL_ERROR_NOT_FOUND, reload: true };
    case 409:
      return {
        message: context === 'register' ? SKILL_ERROR_EXISTS : SKILL_ERROR_CONFLICT,
        reload: context !== 'toggle',
      };
    case 400:
      return {
        message: (error as { message?: string })?.message || '参数不合法，请检查输入内容',
        reload: false,
      };
    default:
      return {
        message: (error as Error)?.message || '操作失败，请稍后重试',
        reload: false,
      };
  }
};

/** 列表项 version 回传校验：更新提交前确保 version 存在（后端乐观 CAS 依据）。 */
export const requireSkillVersion = (item: { version?: number } | null | undefined): number | null =>
  item && typeof item.version === 'number' ? item.version : null;

/** 编辑器初始值：更新模式回填当前内容，metadata 反序列化为格式化 JSON 文本；注册模式全空。 */
export const skillEditorInitialValues = (editing: AgentSkillItem | null) =>
  editing
    ? {
        skillId: editing.skillId,
        name: editing.name,
        description: editing.description ?? '',
        content: editing.content,
        metadata:
          editing.metadata && Object.keys(editing.metadata).length ? JSON.stringify(editing.metadata, null, 2) : '',
      }
    : { skillId: '', name: '', description: '', content: '', metadata: '' };
