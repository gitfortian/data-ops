import { Progress, Tooltip } from 'antd';

import { healthGradeColor } from '../constants';

/** 健康度环:分数 + 等级色(A绿/B蓝/C橙/D红),派生值不可手改(D7)。 */
const HealthRing = ({
  score,
  grade,
  size = 44,
}: {
  score?: number | null;
  grade?: string | null;
  size?: number;
}) => {
  if (score == null) {
    return (
      <Tooltip title="尚未完成首次健康度计算">
        <span className="inline-flex items-center text-[12px] text-[#98a2b3]">未评分</span>
      </Tooltip>
    );
  }
  const color = healthGradeColor(grade);
  return (
    <Tooltip title={`健康度 ${score} 分,等级 ${grade ?? '-'}`}>
      <Progress
        type="circle"
        percent={score}
        size={size}
        strokeColor={color}
        format={() => (
          <span style={{ color, fontSize: size / 3.2, fontWeight: 600 }}>
            {grade ?? score}
          </span>
        )}
      />
    </Tooltip>
  );
};

export default HealthRing;
