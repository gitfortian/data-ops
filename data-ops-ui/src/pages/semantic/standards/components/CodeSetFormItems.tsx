import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Modal, Tooltip } from 'antd';

import { existsCodeSet } from '@/services/semantic/api';

interface CodeSetFormItemsProps {
  /** 编辑模式:删除已保存的码值行前二次确认(可能有字段引用)。 */
  confirmRemove?: boolean;
  /**
   * 进入表单时已存在的码值。只有删这些才值得确认:
   * 表单内新加的行还没落库,再弹一次确认纯属打断(且确认文案说的"保存后移除"尚未发生)。
   */
  persistedValues?: string[];
  /** 码集编码锁定(编辑常规码集不可改)。 */
  codeDisabled?: boolean;
  /** 存量空码集行:编码可补全,保存后并入新码集。 */
  legacy?: boolean;
  /** 历史数据组内名称不一致:提示保存后统一为码集名称。 */
  nameInconsistent?: boolean;
  /** 新建时码集编码已存在则回调(由父组件转入编辑既有码集)。 */
  onExists?: (codeSetCode: string) => void;
}

interface CodeValueRow {
  codeValue?: string;
  codeLabel?: string;
  sortOrder?: number;
}

const CODE_PATTERN = /^[A-Za-z0-9_]{1,64}$/;

/**
 * 码集表单字段(32.1:一次录入"码集 + 多行码值")。
 * 新建(StandardEditModal 的 CODE 分支)与编辑(CodeSetEditModal)共用。
 */
const CodeSetFormItems = ({
  confirmRemove = false,
  persistedValues = [],
  codeDisabled = false,
  legacy = false,
  nameInconsistent = false,
  onExists,
}: CodeSetFormItemsProps) => {
  const form = Form.useFormInstance();

  /** 该行是否已在库中(按其码值判断)。表单内新加的行码值为空或不在初始集合里。 */
  const isPersistedRow = (index: number): boolean => {
    if (!confirmRemove) {
      return false;
    }
    const rows = (form.getFieldValue('codeValues') ?? []) as CodeValueRow[];
    const value = rows[index]?.codeValue;
    return Boolean(value) && persistedValues.includes(value as string);
  };

  const removeRow = (index: number) => {
    if (!isPersistedRow(index)) {
      return Promise.resolve();
    }
    return new Promise<void>((resolve, reject) => {
      Modal.confirm({
        title: '删除码值',
        content: '保存后该码值将从码集中移除，引用此码集的字段将不再展示该码值。',
        okText: '删除',
        okType: 'danger',
        cancelText: '取消',
        onOk: () => resolve(),
        onCancel: () => reject(new Error('cancelled')),
      });
    });
  };

  const checkCodeExists = async (raw?: string) => {
    if (!onExists || codeDisabled) {
      return;
    }
    const codeSetCode = (raw ?? '').trim();
    if (!CODE_PATTERN.test(codeSetCode)) {
      return;
    }
    try {
      if (await existsCodeSet(codeSetCode)) {
        onExists(codeSetCode);
      }
    } catch {
      // 检查失败静默:保存时服务端判重兜底
    }
  };

  return (
    <>
      <div className="grid grid-cols-2 gap-x-4 max-sm:grid-cols-1">
        <Form.Item
          name="codeSetCode"
          label="码集编码"
          preserve={false}
          rules={[
            { required: true, message: '请输入码集编码' },
            { pattern: CODE_PATTERN, message: '仅允许字母、数字和下划线，1~64 位' },
          ]}
          extra={
            legacy
              ? '存量数据未关联码集，补全编码保存后将并入该码集'
              : codeDisabled
                ? '码集编码创建后不可修改'
                : '行编码将按「码集编码_码值」自动生成'
          }
        >
          <Input
            disabled={codeDisabled}
            maxLength={64}
            placeholder="如 order_status"
            onBlur={(event) => {
              void checkCodeExists(event.target.value);
            }}
          />
        </Form.Item>
        <Form.Item
          name="name"
          label="码集名称"
          preserve={false}
          rules={[{ required: true, message: '请输入码集名称' }]}
          extra={nameInconsistent ? '该码集历史名称不一致，保存后将统一为当前填写的码集名称' : undefined}
        >
          <Input maxLength={128} placeholder="如 订单状态" />
        </Form.Item>
      </div>
      <Form.Item name="description" label="描述" preserve={false}>
        <Input.TextArea rows={2} maxLength={512} placeholder="码集用途说明（可选）" />
      </Form.Item>

      <Form.Item label="码值列表" required>
        <Form.List name="codeValues">
          {(fields, { add, remove }) => (
            <>
              <div className="mb-2 grid grid-cols-[1fr_1fr_80px_32px] gap-x-2 text-xs font-medium text-[#667085]">
                <span>码值 *</span>
                <span>码值标签</span>
                <span>排序</span>
                <span />
              </div>
              {fields.map(({ key, name: index, ...restField }) => (
                <div key={key} className="mb-2 grid grid-cols-[1fr_1fr_80px_32px] items-start gap-x-2">
                  <Form.Item
                    {...restField}
                    name={[index, 'codeValue']}
                    preserve={false}
                    rules={[
                      { required: true, message: '码值必填' },
                      {
                        validator: (_, value?: string) => {
                          if (!value) {
                            return Promise.resolve();
                          }
                          const rows = (form.getFieldValue('codeValues') ?? []) as CodeValueRow[];
                          return rows.filter((row) => row.codeValue === value).length > 1
                            ? Promise.reject(new Error('同一码集内码值不能重复'))
                            : Promise.resolve();
                        },
                      },
                    ]}
                  >
                    <Input maxLength={256} placeholder="如 1" />
                  </Form.Item>
                  <Form.Item {...restField} name={[index, 'codeLabel']} preserve={false}>
                    <Input maxLength={256} placeholder="如 待支付" />
                  </Form.Item>
                  <Form.Item {...restField} name={[index, 'sortOrder']} preserve={false}>
                    <InputNumber min={0} placeholder="0" className="!w-full" />
                  </Form.Item>
                  <Form.Item>
                    {/* 只剩一行时按钮仍要在场:直接消失会让人以为界面坏了。 */}
                    <Tooltip title={fields.length > 1 ? undefined : '码集至少保留一个码值'}>
                      <span>
                        <Button
                          type="text"
                          danger
                          size="small"
                          icon={<DeleteOutlined />}
                          aria-label="删除该码值"
                          disabled={fields.length <= 1}
                          onClick={() => {
                            removeRow(index)
                              .then(() => remove(index))
                              .catch(() => undefined);
                          }}
                        />
                      </span>
                    </Tooltip>
                  </Form.Item>
                </div>
              ))}
              <Button
                type="dashed"
                icon={<PlusOutlined />}
                className="!w-full"
                disabled={fields.length >= 100}
                onClick={() => {
                  add({ codeValue: '', codeLabel: '', sortOrder: fields.length });
                }}
              >
                添加码值
              </Button>
            </>
          )}
        </Form.List>
      </Form.Item>
    </>
  );
};

export default CodeSetFormItems;
