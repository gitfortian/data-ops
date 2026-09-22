import YakButton from '@/components/YakButton';
import { DatePicker, Input, InputNumber, Select } from 'antd';
import moment from 'moment';
import React from 'react';

interface FilterCondition {
  field: string | undefined;
  operator: string | undefined;
  value: string | undefined;
  logicalOperator: 'AND' | 'OR'; // 新增逻辑运算符字段
  type: string | undefined;
}

interface FilterConditionsProps {
  conditions: FilterCondition[];
  onConditionsChange: (conditions: FilterCondition[]) => void;
  fieldOptions: any[];
}

const operatorOptions = [
  { label: '=', value: '=' },
  { label: '>', value: '>' },
  { label: '<', value: '<' },
  { label: '>=', value: '>=' },
  { label: '<=', value: '<=' },
  { label: '!=', value: '!=' },
  { label: 'LIKE', value: 'LIKE' },
];

export const FilterConditions: React.FC<FilterConditionsProps> = ({
  conditions,
  onConditionsChange,
  fieldOptions,
}) => {

  const addCondition = () => {
    onConditionsChange([
      ...conditions,
      { field: undefined, operator: '=', value: undefined, logicalOperator: 'AND', type: 'STRING' },
    ]);
  };

  const removeCondition = (index: number) => {
    const newConditions = conditions.filter((_, i) => i !== index);
    onConditionsChange(newConditions);
  };

  const handleFieldChange = (index: number, value: string, option: any) => {

    const newConditions = [...conditions];
    newConditions[index].field = value;
    newConditions[index].type = option?.type || 'STRING';
    onConditionsChange(newConditions);
  };

  const handleOperatorChange = (index: number, value: string) => {
    const newConditions = [...conditions];
    newConditions[index].operator = value;
    onConditionsChange(newConditions);
  };

  const handleValueChange = (index: number, value: any) => {

    const newConditions = [...conditions];
    newConditions[index].value = value;
    onConditionsChange(newConditions);
  };

  // 切换逻辑运算符
  const toggleLogicalOperator = (index: number) => {
    const newConditions = [...conditions];
    newConditions[index].logicalOperator =
      newConditions[index].logicalOperator === 'AND' ? 'OR' : 'AND';
    onConditionsChange(newConditions);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {conditions.map((condition, index) => {
        const renderValue = (type: string | undefined) => {
          if (type === 'NUMBER') {
            return (
              <InputNumber
                size="small"
                style={{ width: 120 }}
                placeholder="输入值"
                value={condition.value}
                onChange={(value) => {
                  handleValueChange(index, value);
                }}
              />
            );
          }  else {
            return (
              <Input
                size="small"
                style={{ width: 120 }}
                placeholder="输入值"
                value={condition.value}
                onChange={(e) => handleValueChange(index, e.target.value)}
              />
            );
          }
        };
        return (
          <div key={index} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Select
              size="small"
              style={{ width: 120 }}
              placeholder="字段选择"
              value={condition.field}
              onChange={(value, option) => handleFieldChange(index, value, option)}
              options={fieldOptions}
              showSearch
            />
            <Select
              size="small"
              style={{ width: 80 }}
              placeholder="操作符"
              value={condition.operator}
              onChange={(value) => handleOperatorChange(index, value)}
              options={operatorOptions}
              showSearch
            />
            {renderValue(condition.type)}
            {/* 新增 AND/OR 切换按钮 */}
            {index < conditions.length - 1 && (
              <YakButton
                size="small"
                type="text"
                className="!text-[#667085]"
                onClick={() => toggleLogicalOperator(index)}
                style={{
                  minWidth: 30,
                }}
              >
                {condition.logicalOperator}
              </YakButton>
            )}
            <YakButton
              size="small"
              type="text"
              danger
              className="!text-[#667085]"
              onClick={() => removeCondition(index)}
            >
              删除
            </YakButton>
          </div>
        );
      })}

      <YakButton
        size="small"
        type="text"
        className="!text-[#667085]"
        onClick={addCondition}
        style={{ width: 'fit-content' }}
      >
        + 添加条件
      </YakButton>
    </div>
  );
};
