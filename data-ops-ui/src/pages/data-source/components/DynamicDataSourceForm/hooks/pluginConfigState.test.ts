import {
  INITIAL_PLUGIN_CONFIG_STATE,
  PLUGIN_CONFIG_STATUS,
  pluginConfigStateReducer,
} from './pluginConfigState';

const section = {
  key: 'connection',
  title: '连接参数',
  fields: [],
};

describe('datasource plugin config state model', () => {
  it('moves from loading to ready with normalized sections', () => {
    const loading = pluginConfigStateReducer(INITIAL_PLUGIN_CONFIG_STATE, {
      type: 'LOAD_START',
    });
    expect(loading.status).toBe(PLUGIN_CONFIG_STATUS.LOADING);
    expect(loading.sections).toEqual([]);

    const ready = pluginConfigStateReducer(loading, {
      type: 'LOAD_SUCCESS',
      sections: [section],
    });
    expect(ready.status).toBe(PLUGIN_CONFIG_STATUS.READY);
    expect(ready.sections).toEqual([section]);
  });

  it('distinguishes install required from load failure', () => {
    const installRequired = pluginConfigStateReducer(
      INITIAL_PLUGIN_CONFIG_STATE,
      {
        type: 'INSTALL_REQUIRED',
        message: '请先安装插件',
      },
    );
    expect(installRequired.status).toBe(
      PLUGIN_CONFIG_STATUS.INSTALL_REQUIRED,
    );
    expect(installRequired.message).toBe('请先安装插件');

    const loadFailed = pluginConfigStateReducer(INITIAL_PLUGIN_CONFIG_STATE, {
      type: 'LOAD_FAILED',
      message: '网络异常',
    });
    expect(loadFailed.status).toBe(PLUGIN_CONFIG_STATUS.LOAD_FAILED);
    expect(loadFailed.message).toBe('网络异常');
  });

  it('keeps install failures retryable as install required', () => {
    const installing = pluginConfigStateReducer(INITIAL_PLUGIN_CONFIG_STATE, {
      type: 'INSTALL_START',
    });
    expect(installing.status).toBe(PLUGIN_CONFIG_STATUS.INSTALLING);

    const failed = pluginConfigStateReducer(installing, {
      type: 'INSTALL_FAILED',
      message: '插件安装失败',
    });
    expect(failed.status).toBe(PLUGIN_CONFIG_STATUS.INSTALL_REQUIRED);
    expect(failed.message).toBe('插件安装失败');
  });

  it('resets stale sections when a new load starts', () => {
    const ready = pluginConfigStateReducer(INITIAL_PLUGIN_CONFIG_STATE, {
      type: 'LOAD_SUCCESS',
      sections: [section],
    });
    const loading = pluginConfigStateReducer(ready, { type: 'LOAD_START' });

    expect(loading.status).toBe(PLUGIN_CONFIG_STATUS.LOADING);
    expect(loading.sections).toEqual([]);
  });
});
