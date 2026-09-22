import { PageHeader, PendingPanel } from '../shared';

const MetadataOverviewPage = () => (
  <div className="pb-8">
    <PageHeader
      title="元数据概览"
      subtitle="采集健康度、元数据覆盖率与治理待办的总入口"
    />
    <PendingPanel description="这里将展示各实体类型的采集覆盖率、最近一轮采集/对账的成败与熔断情况，以及待补注释、待确认标签等待办数量。数据来自概览接口，随采集能力一并交付。" />
  </div>
);

export default MetadataOverviewPage;
