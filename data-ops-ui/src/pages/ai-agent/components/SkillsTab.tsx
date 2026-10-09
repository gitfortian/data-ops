import { PlusOutlined } from '@ant-design/icons';
import { Alert, Button, message, Space, Typography } from 'antd';
import React from 'react';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import type { AgentSkillItem, AgentSkillSaveInput } from '@/services/agent';
import { agentSkillApi } from '@/services/agent';
import { AGENT_SKILL_MANAGE, classifySkillError, requireSkillVersion } from '../skill-runtime';
import SkillDetailDrawer from './SkillDetailDrawer';
import SkillEditorModal from './SkillEditorModal';
import SkillListTable from './SkillListTable';
import SkillTemplateGallery from './SkillTemplateGallery';

/**
 * 技能管理 Tab 容器（文档 §3.1 状态模型 / §5 交互流）。
 * 列表 + 注册/编辑 Modal + 详情 Drawer；启停为乐观 UI（成功就地更新，失败回滚）。
 * 权限：Tab 可见性由 read 在父级门控，此处 manage 控制注册/编辑/启停/删除入口。
 */
const SkillsTab: React.FC = () => {
  const { can } = usePermissionAccess();
  const canManage = can(AGENT_SKILL_MANAGE);

  const [items, setItems] = React.useState<AgentSkillItem[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [loaded, setLoaded] = React.useState(false);
  const [loadError, setLoadError] = React.useState<string | null>(null);
  const [template, setTemplate] = React.useState<AgentSkillSaveInput | undefined>();
  const [editing, setEditing] = React.useState<AgentSkillItem | null>(null);
  const [editorOpen, setEditorOpen] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [detail, setDetail] = React.useState<AgentSkillItem | null>(null);
  const [pendingToggle, setPendingToggle] = React.useState<string | null>(null);

  const reload = React.useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      setItems(await agentSkillApi.list());
      setLoaded(true);
    } catch (error) {
      const info = classifySkillError(error, 'list');
      setLoaded(false);
      setLoadError(info.message);
      void message.error(info.message);
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void reload();
  }, [reload]);

  const openRegister = () => {
    setTemplate(undefined);
    setEditing(null);
    setEditorOpen(true);
  };

  const openEdit = (skill: AgentSkillItem) => {
    setTemplate(undefined);
    setEditing(skill);
    setEditorOpen(true);
  };

  const registrationKnown = loaded && !loading && !loadError;
  const openTemplate = (input: AgentSkillSaveInput) => {
    if (!canManage || !registrationKnown || items.some((item) => item.skillId === input.skillId)) return;
    setTemplate(input);
    setEditing(null);
    setEditorOpen(true);
  };

  const submit = async (input: AgentSkillSaveInput) => {
    if (!canManage) return;
    if (template && (!registrationKnown || items.some((item) => item.skillId === input.skillId))) {
      void message.warning('请刷新并确认登记状态；已注册技能请使用编辑入口');
      return;
    }
    // 更新模式乐观 CAS 依赖列表项当前 version；缺失（旧数据兼容）则提示并刷新列表重试。
    if (editing && requireSkillVersion(editing) === null) {
      void message.warning('技能版本信息缺失，已刷新列表，请重新提交');
      setEditorOpen(false);
      await reload();
      return;
    }
    setSaving(true);
    try {
      if (editing) {
        await agentSkillApi.update(editing.skillId, input);
        void message.success('技能已更新，下一轮对话生效');
      } else {
        await agentSkillApi.register(input);
        void message.success('技能已注册，下一轮对话生效');
      }
      setEditorOpen(false);
      await reload();
    } catch (error) {
      const info = classifySkillError(error, editing ? 'update' : 'register');
      void message.error(info.message);
      if (info.reload) {
        await reload();
      }
    } finally {
      setSaving(false);
    }
  };

  const toggle = async (skill: AgentSkillItem) => {
    setPendingToggle(skill.skillId);
    try {
      await agentSkillApi.setActive(skill.skillId, !skill.enabled);
      // 成功后就地更新 enabled；失败回滚（本地不清态，由 message 提示）
      setItems((prev) => prev.map((it) => (it.skillId === skill.skillId ? { ...it, enabled: !skill.enabled } : it)));
    } catch (error) {
      const info = classifySkillError(error, 'toggle');
      void message.error(info.message);
      if (info.reload) {
        await reload();
      }
    } finally {
      setPendingToggle(null);
    }
  };

  const remove = async (skill: AgentSkillItem) => {
    try {
      await agentSkillApi.remove(skill.skillId);
      void message.success('技能已删除');
      setItems((prev) => prev.filter((it) => it.skillId !== skill.skillId));
    } catch (error) {
      const info = classifySkillError(error, 'remove');
      void message.error(info.message);
      if (info.reload) {
        await reload();
      }
    }
  };

  return (
    <div>
      <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }} align="start">
        <Typography.Text type="secondary" style={{ fontSize: 12.5 }}>
          技能在线管理：注册/更新/启停/删除即时热生效；不影响正在进行的推理，下一轮对话按新状态注入。
        </Typography.Text>
        {canManage ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={openRegister}>
            注册技能
          </Button>
        ) : null}
      </Space>
      <Typography.Title level={5}>已注册技能</Typography.Title>
      {loadError ? (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          message="已注册技能加载失败，登记状态待确认"
          description={loadError}
          action={<Button onClick={() => void reload()}>重试</Button>}
        />
      ) : null}
      <SkillListTable
        items={items}
        loading={loading}
        emptyText={
          loadError
            ? '无法确认已注册技能，请重试'
            : loading
              ? '正在加载已注册技能'
              : canManage
                ? '当前项目暂无已注册技能，可从下方模板查看并注册。'
                : '当前项目暂无已注册技能，请联系技能管理员注册。'
        }
        canManage={canManage}
        pendingToggle={pendingToggle}
        onToggle={toggle}
        onDetail={(skill) => setDetail(skill)}
        onEdit={openEdit}
        onDelete={remove}
      />
      <SkillTemplateGallery
        items={items}
        registrationKnown={registrationKnown}
        canManage={canManage}
        onRegister={openTemplate}
      />
      <SkillEditorModal
        key={editorOpen ? `open:${editing?.skillId ?? template?.skillId ?? 'custom'}` : 'closed'}
        open={editorOpen}
        editing={editing}
        template={template}
        saving={saving}
        onCancel={() => {
          setEditorOpen(false);
          setEditing(null);
        }}
        onSubmit={submit}
      />
      <SkillDetailDrawer open={detail !== null} skill={detail} onClose={() => setDetail(null)} />
    </div>
  );
};

export default SkillsTab;
