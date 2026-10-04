import { ArrowRightOutlined, EyeInvisibleOutlined, EyeOutlined } from '@ant-design/icons';
import YakButton from '@/components/YakButton';
import { login } from '@/services/security/account';
import { extractUnknownErrorMessage } from '@/services/http/response';
import { notifyOnce } from '@/utils/notifyOnce';
import { resetAuthenticationFailure } from '@/utils/request';
import { getSafeReturnTo } from '@/utils/security/redirect';
import { history, useIntl, useModel } from '@umijs/max';
import { Alert, Form, Input, Modal } from 'antd';
import { useRef, useState } from 'react';
import { flushSync } from 'react-dom';

export default function LoginPanel() {
  const [loading, setLoading] = useState(false);
  const [loginError, setLoginError] = useState<string>();
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const submitting = useRef(false);
  const [form] = Form.useForm();

  const { initialState, setInitialState } = useModel('@@initialState');
  const intl = useIntl();

  const fetchUserInfo = async () => {
    const userInfo = await initialState?.fetchUserInfo?.();

    if (userInfo) {
      flushSync(() => {
        setInitialState((state: any) => ({
          ...state,
          currentUser: userInfo,
          currentUserLoadError: false,
        }));
      });
    }

    return userInfo;
  };

  const redirectAfterLogin = () => {
    const requested = new URLSearchParams(window.location.search).get('returnTo');
    history.replace(getSafeReturnTo(requested));
  };

  const handleAccountLogin = async (values: { userName: string; userPassword: string }) => {
    if (submitting.current) return;
    submitting.current = true;
    try {
      setLoading(true);
      setLoginError(undefined);

      await login({
        userName: values.userName,
        pw: values.userPassword,
      });

      const userInfo = await fetchUserInfo();
      if (!userInfo) {
        setLoginError('未能加载当前用户信息，请稍后重试。');
        notifyOnce('login-current-user-missing', {
          type: 'error',
          title: '登录未完成',
          description: '登录请求已提交，但未能加载当前用户信息。',
          meta: '请稍后重试',
        });
        return;
      }

      resetAuthenticationFailure();
      notifyOnce('login-success', {
        type: 'success',
        title: intl.formatMessage({
          id: 'pages.login.success',
          defaultMessage: '登录成功！',
        }),
        description: '正在进入 Data Ops',
        meta: '身份验证完成',
        duration: 2,
      });
      redirectAfterLogin();
    } catch (error) {
      const payload =
        error && typeof error === 'object'
          ? 'data' in error
            ? error.data
            : 'response' in error
              ? error.response
              : undefined
          : undefined;
      setLoginError(
        extractUnknownErrorMessage(
          payload,
          intl.formatMessage({ id: 'pages.login.failure', defaultMessage: '登录失败，请检查账号或稍后重试。' }),
        ),
      );
    } finally {
      submitting.current = false;
      setLoading(false);
    }
  };

  return (
    <section className="dataops-login-panel" aria-labelledby="dataops-login-welcome">
      <h2 id="dataops-login-welcome">
        {intl.formatMessage({ id: 'pages.login.welcome', defaultMessage: '欢迎回来。' })}
      </h2>
      <p className="dataops-login-intro">
        {intl.formatMessage({ id: 'pages.login.workspace', defaultMessage: '登录你的数据工作空间' })}
      </p>
      <Form
        name="dataops-login"
        layout="vertical"
        form={form}
        requiredMark={false}
        onFinish={handleAccountLogin}
        onValuesChange={() => setLoginError(undefined)}
        disabled={loading}
        aria-busy={loading}
        onSubmitCapture={(event) => {
          if (submitting.current) {
            event.preventDefault();
            event.stopPropagation();
          }
        }}
        onFinishFailed={({ errorFields }) => {
          if (errorFields[0]) form.getFieldInstance(errorFields[0].name)?.focus();
        }}
      >
        {loginError ? <Alert className="dataops-login-error" type="error" showIcon message={loginError} /> : null}
        <Form.Item
          className="dataops-login-field"
          label={intl.formatMessage({ id: 'pages.login.username', defaultMessage: '用户名' })}
          name="userName"
          rules={[
            {
              required: true,
              message: intl.formatMessage({ id: 'pages.login.username.placeholder', defaultMessage: '请输入用户名' }),
            },
          ]}
        >
          <Input
            autoComplete="username"
            placeholder={intl.formatMessage({ id: 'pages.login.username.placeholder', defaultMessage: '请输入用户名' })}
          />
        </Form.Item>

        <Form.Item
          className="dataops-login-field"
          label={intl.formatMessage({ id: 'pages.login.password', defaultMessage: '密码' })}
          name="userPassword"
          rules={[
            {
              required: true,
              message: intl.formatMessage({ id: 'pages.login.password.placeholder', defaultMessage: '请输入密码' }),
            },
          ]}
        >
          <Input
            type={passwordVisible ? 'text' : 'password'}
            autoComplete="current-password"
            placeholder={intl.formatMessage({ id: 'pages.login.password.placeholder', defaultMessage: '请输入密码' })}
            onKeyDown={(event) => setCapsLock(event.getModifierState('CapsLock'))}
            onKeyUp={(event) => setCapsLock(event.getModifierState('CapsLock'))}
            onBlur={() => setCapsLock(false)}
            suffix={
              <button
                type="button"
                className="dataops-login-password-toggle"
                aria-label={intl.formatMessage({
                  id: passwordVisible ? 'pages.login.password.hide' : 'pages.login.password.show',
                  defaultMessage: passwordVisible ? '隐藏密码' : '显示密码',
                })}
                aria-pressed={passwordVisible}
                disabled={loading}
                onClick={() => setPasswordVisible((visible) => !visible)}
              >
                {passwordVisible ? <EyeInvisibleOutlined /> : <EyeOutlined />}
              </button>
            }
          />
        </Form.Item>
        <div className="dataops-login-caps-lock" role="status" aria-live="polite">
          {capsLock ? intl.formatMessage({ id: 'pages.login.capsLock', defaultMessage: '大写锁定已开启' }) : null}
        </div>
        <YakButton
          block
          type="primary"
          htmlType="submit"
          loading={loading}
          disabled={loading}
          className="dataops-login-submit"
        >
          <span>
            {intl.formatMessage({
              id: loading ? 'pages.login.submitting' : 'pages.login.submit',
              defaultMessage: loading ? '正在登录…' : '登录',
            })}
          </span>
          {!loading ? <ArrowRightOutlined className="dataops-login-submit-arrow" aria-hidden="true" /> : null}
        </YakButton>
      </Form>
      <button type="button" className="dataops-login-help" onClick={() => setHelpOpen(true)}>
        {intl.formatMessage({ id: 'pages.login.help', defaultMessage: '登录遇到问题？' })}
      </button>
      <Modal
        open={helpOpen}
        title={intl.formatMessage({ id: 'pages.login.help.title', defaultMessage: '登录帮助' })}
        onCancel={() => setHelpOpen(false)}
        footer={null}
        width={400}
        centered
        className="dataops-login-help-modal"
      >
        <p>
          {intl.formatMessage({
            id: 'pages.login.help.content',
            defaultMessage: '请联系所在组织的平台管理员，确认账号状态或重置密码。',
          })}
        </p>
      </Modal>
    </section>
  );
}
