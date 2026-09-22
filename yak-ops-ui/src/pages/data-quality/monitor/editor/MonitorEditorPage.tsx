import { BRAND_THEME } from '@/styles/brand';
import { history, useLocation, useModel, useParams } from '@umijs/max';
import { Button, ConfigProvider, Form, Input, Spin } from 'antd';
import { useMemo } from 'react';

import { BasicConfig } from './BasicConfig';
import { SectionNavigator } from './EditorLayout';
import { useMonitorEditorPage } from './hooks/useMonitorEditorPage';
import { NotificationSettings } from './NotificationSettings';
import { QualityRuleEditor } from './RuleEditor';
import { ScheduleSettings } from './ScheduleSettings';
import { useSectionNavigation } from './useSectionNavigation';

const MonitorEditorPage = () => {
  const params = useParams<{ id?: string }>();
  const location = useLocation();
  const { initialState } = useModel('@@initialState');
  const currentUser = initialState?.currentUser as
    | { realName?: string; username?: string }
    | undefined;
  const query = useMemo(
    () => new URLSearchParams(location.search),
    [location.search],
  );
  const editor = useMonitorEditorPage({
    monitorId: params.id,
    query,
    currentUser,
  });
  const { pageRootRef, activeSection, locateSection } = useSectionNavigation();

  return (
    <ConfigProvider theme={BRAND_THEME} variant="filled">
      <div className="h-[calc(100vh-64px)] overflow-hidden bg-[#f7f8fa] text-[#161823]">
        <div
          ref={pageRootRef}
          className="h-full overflow-y-auto overscroll-contain scroll-smooth"
        >
          <div className="mx-auto grid w-full max-w-[1280px] grid-cols-1 gap-6 px-6 pb-6 pt-6 max-xl:max-w-[1040px] xl:grid-cols-[minmax(0,1fr)_176px]">
            <div className="min-w-0">
              <Spin spinning={editor.loading}>
                <Form form={editor.form} requiredMark={false}>
                  <Form.Item name="dataSourceId" hidden>
                    <Input />
                  </Form.Item>
                  <Form.Item name="dataSourceName" hidden>
                    <Input />
                  </Form.Item>
                  <Form.Item name="databaseName" hidden>
                    <Input />
                  </Form.Item>
                  <Form.Item name="schemaName" hidden>
                    <Input />
                  </Form.Item>
                  <Form.Item name="tableName" hidden>
                    <Input />
                  </Form.Item>

                  <main className="space-y-5 pb-4">
                    <BasicConfig
                      dataSourceId={editor.dataSourceId}
                      dataSourceName={
                        editor.selectedSource?.name || editor.storedDataSourceName
                      }
                      databaseName={editor.databaseName}
                      schemaName={editor.schemaName}
                      tableName={editor.tableName}
                    />
                    <QualityRuleEditor
                      rules={editor.rules}
                      onChange={editor.setRules}
                      columns={editor.columns}
                      templates={editor.templates}
                    />
                    <ScheduleSettings
                      value={editor.schedule}
                      onChange={editor.setSchedule}
                      nextRunTime={editor.nextRunTime}
                    />
                    <NotificationSettings
                      value={editor.notification}
                      onChange={editor.setNotification}
                    />
                  </main>
                </Form>
              </Spin>

              <footer className="sticky bottom-0 z-50 overflow-hidden rounded-t-lg border border-b-0 border-[#eaecf0] bg-white shadow-[0_-8px_16px_rgba(0,0,0,0.06)]">
                <div className="flex min-h-[76px] items-center gap-3 px-8 py-4">
                  <Button
                    type="primary"
                    loading={editor.saving}
                    disabled={!editor.dataSourceId || !editor.tableName}
                    className="!h-9 !min-w-[120px] !rounded-lg"
                    onClick={() => void editor.save()}
                  >
                    保存配置
                  </Button>
                  <Button
                    disabled={editor.saving}
                    className="!h-9 !min-w-[120px] !border-0 !bg-[#f2f3f5]"
                    onClick={() => history.back()}
                  >
                    取消
                  </Button>
                </div>
              </footer>
            </div>

            <aside className="hidden xl:block">
              <div className="sticky top-6">
                <SectionNavigator
                  activeKey={activeSection}
                  onSelect={locateSection}
                />
              </div>
            </aside>
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
};

export default MonitorEditorPage;
