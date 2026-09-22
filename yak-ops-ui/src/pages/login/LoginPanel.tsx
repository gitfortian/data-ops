import { ExclamationCircleOutlined } from "@ant-design/icons";
import YakButton from "@/components/YakButton";
import { login } from "@/services/security/account";
import { notifyOnce } from "@/utils/notifyOnce";
import { resetAuthenticationFailure } from "@/utils/request";
import { getSafeReturnTo } from "@/utils/security/redirect";
import { history, useIntl, useModel } from "@umijs/max";
import { Form, Input, type InputProps } from "antd";
import { useForm } from "antd/es/form/Form";
import { useState } from "react";
import { flushSync } from "react-dom";

const FORM_ITEM_CLASS_NAME =
  "!mb-5 [&_.ant-form-item-explain]:!pt-1.5 [&_.ant-form-item-explain-error]:!text-[12px] [&_.ant-form-item-explain-error]:!leading-[18px] [&_.ant-form-item-explain-error]:!text-[#b42318]";

type FloatingInputProps = InputProps & {
  label: string;
  password?: boolean;
};

function FloatingInput({
  label,
  password = false,
  onBlur,
  onFocus,
  value,
  ...inputProps
}: FloatingInputProps) {
  const [focused, setFocused] = useState(false);
  const { status } = Form.Item.useStatus();
  const floating = focused || String(value ?? "").length > 0;
  const hasError = status === "error";

  const handleFocus: InputProps["onFocus"] = (event) => {
    setFocused(true);
    onFocus?.(event);
  };

  const handleBlur: InputProps["onBlur"] = (event) => {
    setFocused(false);
    onBlur?.(event);
  };

  const className = password
    ? `!h-11 !rounded-full !bg-white !px-4 !shadow-none [&>input.ant-input]:!bg-white [&>input.ant-input]:!text-[15px] ${
        hasError
          ? "!border-[#d92d20] hover:!border-[#d92d20] focus-within:!border-[#d92d20]"
          : "!border-[#dededb] hover:!border-[#bdbdb8] focus-within:!border-[#171717]"
      }`
    : `!h-11 !rounded-full !bg-white !px-4 !text-[15px] !shadow-none ${
        hasError
          ? "!border-[#d92d20] hover:!border-[#d92d20] focus:!border-[#d92d20]"
          : "!border-[#dededb] hover:!border-[#bdbdb8] focus:!border-[#171717]"
      }`;

  const controlProps: InputProps = {
    ...inputProps,
    value,
    className,
    placeholder: "",
    onFocus: handleFocus,
    onBlur: handleBlur,
  };

  return (
    <div className="relative">
      {password ? (
        <Input.Password {...controlProps} />
      ) : (
        <Input {...controlProps} />
      )}
      <label
        htmlFor={inputProps.id}
        className={`pointer-events-none absolute left-4 z-10 bg-white px-1 transition-all duration-200 ease-out ${
          floating
            ? "top-0 -translate-y-1/2 text-[12px] font-medium text-[#333]"
            : "top-1/2 -translate-y-1/2 text-[15px] text-[#aaa]"
        }`}
      >
        {label}
      </label>
    </div>
  );
}

function ValidationMessage({ children }: { children: string }) {
  return (
    <span className="inline-flex h-[18px] items-center gap-1.5 align-middle leading-[18px]" style={{marginBottom: 8}}>
      <ExclamationCircleOutlined className="flex shrink-0 items-center text-[12px] leading-none [&_svg]:block" />
      <span className="leading-[18px]">{children}</span>
    </span>
  );
}

export default function LoginPanel() {
  const [loading, setLoading] = useState(false);
  const [form] = useForm();

  const { initialState, setInitialState } = useModel("@@initialState");
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
    const requested = new URLSearchParams(window.location.search).get(
      "returnTo",
    );
    history.replace(getSafeReturnTo(requested));
  };

  const handleAccountLogin = async (values: {
    userName: string;
    userPassword: string;
  }) => {
    try {
      setLoading(true);

      await login({
        userName: values.userName,
        pw: values.userPassword,
      });

      const userInfo = await fetchUserInfo();
      if (!userInfo) {
        notifyOnce("login-current-user-missing", {
          type: "error",
          title: "登录未完成",
          description: "登录请求已提交，但未能加载当前用户信息。",
          meta: "请稍后重试",
        });
        return;
      }

      resetAuthenticationFailure();
      notifyOnce("login-success", {
        type: "success",
        title: intl.formatMessage({
          id: "pages.login.success",
          defaultMessage: "登录成功！",
        }),
        description: "正在进入 Yak Ops",
        meta: "身份验证完成",
        duration: 2,
      });
      redirectAfterLogin();
    } catch (_error) {
      // Global request handling surfaces HTTP, business and network failures once.
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full rounded-[26px] border border-[#e4e4e1] bg-white p-6 shadow-[0_14px_40px_rgba(15,23,42,0.04)] sm:p-7">
      <Form
        layout="vertical"
        form={form}
        requiredMark={false}
        onFinish={handleAccountLogin}
      >
        <Form.Item
          className={FORM_ITEM_CLASS_NAME}
          name="userName"
          rules={[
            {
              required: true,
              message: <ValidationMessage>请输入用户名</ValidationMessage>,
            },
          ]}
        >
          <FloatingInput label="Username" autoComplete="username" />
        </Form.Item>

        <Form.Item
          className={FORM_ITEM_CLASS_NAME}
          name="userPassword"
          rules={[
            {
              required: true,
              message: <ValidationMessage>请输入密码</ValidationMessage>,
            },
          ]}
        >
          <FloatingInput
            label="Password"
            password
            autoComplete="current-password"
          />
        </Form.Item>

        <YakButton
          block
          effect="glass"
          type="primary"
          htmlType="submit"
          loading={loading}
          className="!h-11 !rounded-full !border-[#171717] !bg-[#171717] !font-medium !text-white !shadow-none hover:!border-[#292929] hover:!bg-[#292929]"
        >
          Log in
        </YakButton>
      </Form>
    </div>
  );
}
