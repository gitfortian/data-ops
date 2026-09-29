import { history } from '@umijs/max';
import { Button, Card, Space, Typography } from 'antd';

const { Paragraph, Text } = Typography;

export default function HomeConsumptionHub() {
  return (
    <Card
      size="small"
      title="数据消费"
      extra={<Button type="link" onClick={() => history.push('/data-analysis/consumption')}>进入目录</Button>}
    >
      <Space direction="vertical" size={4}>
        <Text strong>从一个入口发现可消费 Dataset 与 Data Service</Text>
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          搜索结果进入统一 ProductKey 详情，并可稳定回到来源对象与资产。
        </Paragraph>
      </Space>
    </Card>
  );
}
