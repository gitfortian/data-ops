import { Button, Divider, Drawer, Form, Input, message, Select, Space, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import {
  captureModelingStandard,
  recommendModelingStandards,
  reportModelingStandardUsage,
} from '@/services/modeling/recommend';
import type { ModelingStandardCandidate, ModelingStandardRecommendation } from '@/services/modeling/types';

interface StandardAssistantDrawerProps {
  open: boolean;
  modelId: string;
  columnName: string;
  dataType: string;
  applied: {
    stdTypeId?: number | null;
    stdNamingId?: number | null;
    stdCodeSetCode?: string | null;
    stdUnitId?: number | null;
    stdCaliberId?: number | null;
    stdSecurityId?: number | null;
  };
  onClose: () => void;
  /** 一键套用:把选中的标准 ID 写入当前字段草稿(保存仍走既有管道)。 */
  onApply: (patch: {
    stdTypeId?: number | null;
    stdNamingId?: number | null;
    stdCodeSetCode?: string | null;
    stdUnitId?: number | null;
    stdCaliberId?: number | null;
    stdSecurityId?: number | null;
  }) => void;
}

interface CandidateGroup {
  key: 'stdTypeId' | 'stdCodeSetCode' | 'stdUnitId' | 'stdCaliberId' | 'stdSecurityId';
  label: string;
  candidates: ModelingStandardCandidate[];
}

/** 标准助手抽屉(ticket 39):命名校验 + 分类候选,一键套用;仅提示不阻断。 */
const StandardAssistantDrawer = ({
  open,
  modelId,
  columnName,
  dataType,
  applied,
  onClose,
  onApply,
}: StandardAssistantDrawerProps) => {
  const [report, setReport] = useState<ModelingStandardRecommendation | null>(null);
  const [captureForm] = Form.useForm<{
    kind: string;
    code: string;
    name: string;
    ruleExpr?: string;
    stdType?: string;
  }>();
  const [capturing, setCapturing] = useState(false);
  const [captureKind, setCaptureKind] = useState<string>('TYPE');

  const loadReport = useCallback(async () => {
    if (!open || !columnName.trim()) {
      setReport(null);
      return;
    }
    try {
      const result = await recommendModelingStandards(modelId, {
        columnName: columnName.trim(),
        dataType: dataType.trim() || undefined,
      });
      setReport(result);
    } catch {
      message.error('标准推荐加载失败（推荐不阻断编辑）');
      setReport(null);
    }
  }, [open, columnName, dataType, modelId]);

  useEffect(() => {
    void loadReport();
  }, [loadReport]);

  useEffect(() => {
    if (open) {
      captureForm.setFieldsValue({
        kind: 'TYPE',
        code: columnName.toLowerCase().replace(/[^a-z0-9_]/g, '_'),
        name: columnName,
        stdType: dataType || undefined,
      });
    }
  }, [open, columnName, dataType, captureForm]);

  const submitCapture = async () => {
    const values = await captureForm.validateFields();
    setCapturing(true);
    try {
      const result = await captureModelingStandard(modelId, {
        ...values,
        columnName: columnName || undefined,
      });
      message.success(
        result.created ? `已沉淀为标准「${result.name}」，下次同类字段将自动推荐` : result.message || '同名标准已存在',
      );
      await loadReport();
    } catch {
      message.error('沉淀失败，请检查后重试');
    } finally {
      setCapturing(false);
    }
  };

  const groups: CandidateGroup[] = report
    ? [
        { key: 'stdTypeId', label: '类型标准', candidates: report.typeCandidates },
        { key: 'stdCodeSetCode', label: '码值标准（维度）', candidates: report.codeCandidates },
        { key: 'stdUnitId', label: '单位标准（度量）', candidates: report.unitCandidates },
        { key: 'stdCaliberId', label: '口径标准（度量）', candidates: report.caliberCandidates },
        { key: 'stdSecurityId', label: '安全标准（敏感字段）', candidates: report.securityCandidates },
      ]
    : [];

  const renderCandidate = (key: CandidateGroup['key'], candidate: ModelingStandardCandidate) => {
    const isApplied = applied[key] === candidate.standardId;
    return (
      <div
        key={candidate.standardId}
        className="flex items-center justify-between rounded border border-[#f0f0f0] px-3 py-2"
      >
        <Space size={8} wrap>
          <Tag>{candidate.code}</Tag>
          <Typography.Text className="!text-[13px]">{candidate.name}</Typography.Text>
          {candidate.reason ? (
            <Typography.Text type="secondary" className="!text-[12px]">
              {candidate.reason}
            </Typography.Text>
          ) : null}
        </Space>
        <Button
          size="small"
          type={isApplied ? 'default' : 'primary'}
          ghost={!isApplied}
          disabled={isApplied}
          onClick={() => {
            // 码集引用写 code_set_code 字符串;其余写标准 ID。
            if (key === 'stdCodeSetCode') {
              onApply({ stdCodeSetCode: candidate.codeSetCode ?? candidate.code });
            } else {
              onApply({ [key]: candidate.standardId });
            }
            // 42:采纳即上报 APPLY(fail-open,失败静默)。
            reportModelingStandardUsage(modelId, {
              standardId: candidate.standardId,
              usageType: 'APPLY',
              scene: 'EDITOR',
            }).catch(() => undefined);
          }}
        >
          {isApplied ? '已套用' : '一键套用'}
        </Button>
      </div>
    );
  };

  return (
    <Drawer
      open={open}
      width={520}
      title={columnName.trim() ? `标准助手：为字段 ${columnName} 推荐标准` : '标准助手'}
      destroyOnClose
      onClose={onClose}
    >
      {/* D2(2026-09-17):未填字段名时提示先填字段名,不做推荐 */}
      {!columnName.trim() ? (
        <Typography.Paragraph type="warning" className="!mb-4">
          ⚠ 请先在表结构中填写字段名，再打开标准助手获得推荐。
        </Typography.Paragraph>
      ) : (
        <>
          <Typography.Paragraph type="secondary" className="!text-[12px]">
            套用即写入当前字段的数据标准引用，随表结构保存生效；推荐仅提示，不阻断编辑。
          </Typography.Paragraph>

          {/* 一、推荐标准(自动推荐):五组恒显,空组显示"不适用" */}
          <Typography.Text strong className="!block !mb-2 !text-[13px]">
            一、推荐标准（自动推荐）
          </Typography.Text>
          {report?.naming?.evaluated ? (
            <div
              className={`mb-3 rounded border px-3 py-2 ${
                report.naming.matched ? 'border-[#d9f7be] bg-[#f6ffed]' : 'border-[#ffe7ba] bg-[#fff7e6]'
              }`}
            >
              {report.naming.matched ? (
                <Typography.Text className="!text-[13px]">
                  ✓ 字段名符合命名标准「{report.naming.name}」（{report.naming.code}）
                </Typography.Text>
              ) : (
                <div className="flex items-center justify-between gap-2">
                  <Typography.Text className="!text-[13px]">
                    ⚠ 字段名不符合命名标准「{report.naming.name}」（{report.naming.ruleExpr}）
                  </Typography.Text>
                  <Button
                    size="small"
                    onClick={() =>
                      onApply({
                        stdNamingId: report.naming.standardId ?? null,
                      })
                    }
                  >
                    标记采纳
                  </Button>
                </div>
              )}
            </div>
          ) : null}

          {groups.map((group) => (
            <div key={group.key} className="mb-3">
              <Typography.Text strong className="!mb-1 block !text-[13px]">
                {group.label}
              </Typography.Text>
              {group.candidates.length ? (
                <div className="flex flex-col gap-2">
                  {group.candidates.map((candidate) => renderCandidate(group.key, candidate))}
                </div>
              ) : (
                <Typography.Text type="secondary" className="!text-[12px]">
                  —（不适用 / 暂无匹配）
                </Typography.Text>
              )}
            </div>
          ))}

          <Divider className="!my-4" />

          {/* 二、沉淀为标准(把当前字段纳入标准库) */}
          <Typography.Text strong className="!block !mb-2 !text-[13px]">
            二、沉淀为标准（把当前字段纳入标准库，下次自动推荐）
          </Typography.Text>
          <Form form={captureForm} layout="vertical">
            <div className="grid grid-cols-2 gap-x-4 max-sm:grid-cols-1">
              <Form.Item name="kind" label="类别" className="!mb-2">
                <Select
                  options={[
                    { label: '类型标准', value: 'TYPE' },
                    { label: '命名标准', value: 'NAMING' },
                  ]}
                  onChange={(value) => setCaptureKind(value)}
                />
              </Form.Item>
              <Form.Item
                name="code"
                label="编码"
                className="!mb-2"
                extra="按字段名自动生成，可改"
                rules={[
                  { required: true, message: '请输入编码' },
                  { pattern: /^[A-Za-z0-9_]{1,64}$/, message: '仅允许字母、数字和下划线' },
                ]}
              >
                <Input maxLength={64} />
              </Form.Item>
              <Form.Item name="name" label="名称" className="!mb-2" rules={[{ required: true, message: '请输入名称' }]}>
                <Input maxLength={128} />
              </Form.Item>
              {captureKind === 'TYPE' ? (
                <Form.Item name="stdType" label="数据类型" className="!mb-2">
                  <Input maxLength={64} placeholder="从字段类型带出，可改" />
                </Form.Item>
              ) : null}
              {captureKind === 'NAMING' ? (
                <Form.Item
                  name="ruleExpr"
                  label="规则表达式"
                  className="!mb-2 col-span-2"
                  rules={[{ required: true, message: '命名标准必须提供规则表达式' }]}
                >
                  <Input maxLength={1024} placeholder="如 ^[a-z][a-z0-9_]*$" />
                </Form.Item>
              ) : null}
            </div>
            <Button
              type="primary"
              size="small"
              loading={capturing}
              onClick={() => {
                void submitCapture();
              }}
            >
              沉淀为标准
            </Button>
          </Form>
        </>
      )}
    </Drawer>
  );
};

export default StandardAssistantDrawer;
