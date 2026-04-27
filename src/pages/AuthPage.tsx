// @ts-nocheck
import { ChangeEvent, FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { LoginPayload, RegisterPayload, userApi } from '../api';

type AuthField = {
  name: keyof LoginPayload;
  label: string;
  placeholder: string;
  type: 'email' | 'password';
};

type AuthMode = 'login' | 'register';

type RegisterFormState = RegisterPayload;

const AUTH_FIELDS: AuthField[] = [
  { name: 'email', label: '邮箱', placeholder: 'team@paperdesk.app', type: 'email' },
  { name: 'password', label: '密码', placeholder: '请输入密码', type: 'password' },
];

const REGISTER_FIELDS = [
  { name: 'email', label: '邮箱', placeholder: 'team@paperdesk.app', type: 'email' },
  { name: 'nickname', label: '昵称', placeholder: '请输入昵称', type: 'text' },
  { name: 'password', label: '密码', placeholder: '请输入密码', type: 'password' },
  { name: 'code', label: '邮箱验证码', placeholder: '请输入验证码', type: 'text' },
] as const;

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validateLoginForm(formData: LoginPayload): string {
  const email = formData.email.trim();
  const password = formData.password.trim();

  if (!email) {
    return '邮箱不能为空。';
  }

  if (!EMAIL_REGEX.test(email)) {
    return '邮箱格式不正确。';
  }

  if (!password) {
    return '密码不能为空。';
  }

  return '';
}

function validateRegisterForm(formData: RegisterFormState): string {
  const email = formData.email.trim();
  const nickname = formData.nickname.trim();
  const password = formData.password.trim();
  const code = formData.code.trim();

  if (!email) return '邮箱不能为空。';
  if (!EMAIL_REGEX.test(email)) return '邮箱格式不正确。';
  if (!nickname) return '昵称不能为空。';
  if (!password) return '密码不能为空。';
  if (!code) return '验证码不能为空。';

  return '';
}

export default function AuthPage() {
  const navigate = useNavigate();
  const [mode, setMode] = useState<AuthMode>('login');
  const [formData, setFormData] = useState<LoginPayload>({ email: '', password: '' });
  const [registerData, setRegisterData] = useState<RegisterFormState>({
    email: '',
    nickname: '',
    password: '',
    code: '',
  });
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isSendingCode, setIsSendingCode] = useState<boolean>(false);
  const [message, setMessage] = useState<string>('');

  const handleLoginChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleRegisterChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setRegisterData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSendRegisterCode = async () => {
    const email = registerData.email.trim();

    if (!email) {
      setMessage('请先填写注册邮箱。');
      return;
    }

    if (!EMAIL_REGEX.test(email)) {
      setMessage('邮箱格式不正确。');
      return;
    }

    try {
      setIsSendingCode(true);
      setMessage('正在生成验证码...');

      const { ok, data } = await userApi.getRegisterCode({ email });

      if (!ok || !data.success) {
        setMessage(data.message || '验证码生成失败。');
        return;
      }

      setMessage(`验证码已生成：${data.code}（开发环境展示，正式环境请改为邮件发送）`);
    } catch {
      setMessage('无法获取验证码，请确认后端已启动。');
    } finally {
      setIsSendingCode(false);
    }
  };

  const handleLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const payload: LoginPayload = {
      email: formData.email.trim(),
      password: formData.password.trim(),
    };

    const validationError = validateLoginForm(payload);
    if (validationError) {
      setMessage(validationError);
      return;
    }

    try {
      setIsSubmitting(true);
      setMessage('正在校验账号...');

      const { ok, data: result } = await userApi.loginUser(payload);

      if (!ok || !result.success) {
        setMessage(result.message || '登录失败，请检查账号密码。');
        return;
      }

      window.localStorage.setItem(
        'paperdesk-user',
        JSON.stringify({
          userId: result.userId,
          nickname: result.nickname,
          email: payload.email,
        })
      );

      setMessage('登录成功，正在进入工作台...');
      navigate('/workspace');
    } catch {
      setMessage('无法连接后端服务，请确认 Spring Boot 已启动。');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRegister = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const payload: RegisterFormState = {
      email: registerData.email.trim(),
      nickname: registerData.nickname.trim(),
      password: registerData.password.trim(),
      code: registerData.code.trim(),
    };

    const validationError = validateRegisterForm(payload);
    if (validationError) {
      setMessage(validationError);
      return;
    }

    try {
      setIsSubmitting(true);
      setMessage('正在提交注册...');

      const { ok, data } = await userApi.registerUser(payload);

      if (!ok || !data.success) {
        setMessage(data.message || '注册失败，请检查验证码。');
        return;
      }

      setMessage('注册成功，请使用新账号登录。');
      setMode('login');
      setFormData({ email: payload.email, password: '' });
    } catch {
      setMessage('无法连接后端服务，请确认 Spring Boot 已启动。');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section className="auth-shell">
      <div className="auth-hero">
        <span className="panel-kicker">Editorial workspace</span>
        <h1>登录与注册</h1>
      </div>

      <div className="auth-panel">
        <div className="auth-tabs" aria-label="Authentication views">
          <button type="button" className={`auth-tab ${mode === 'login' ? 'auth-tab--active' : ''}`} onClick={() => setMode('login')}>
            登录
          </button>
          <button type="button" className={`auth-tab ${mode === 'register' ? 'auth-tab--active' : ''}`} onClick={() => setMode('register')}>
            注册
          </button>
        </div>

        {mode === 'login' ? (
          <form className="auth-form" onSubmit={handleLogin} noValidate>
            {AUTH_FIELDS.map((field) => (
              <label key={field.label} className="auth-field">
                <span>{field.label}</span>
                <input
                  required
                  name={field.name}
                  type={field.type}
                  placeholder={field.placeholder}
                  value={formData[field.name]}
                  onChange={handleLoginChange}
                />
              </label>
            ))}

            <button type="submit" className="primary-button" disabled={isSubmitting}>
              {isSubmitting ? '校验中...' : '进入工作台'}
            </button>
          </form>
        ) : (
          <form className="auth-form" onSubmit={handleRegister} noValidate>
            {REGISTER_FIELDS.map((field) => (
              <label key={field.label} className="auth-field">
                <span>{field.label}</span>
                <input
                  required
                  name={field.name}
                  type={field.type}
                  placeholder={field.placeholder}
                  value={registerData[field.name]}
                  onChange={handleRegisterChange}
                />
              </label>
            ))}

            <button type="button" className="primary-button" onClick={handleSendRegisterCode} disabled={isSendingCode}>
              {isSendingCode ? '生成中...' : '获取验证码'}
            </button>

            <button type="submit" className="primary-button" disabled={isSubmitting}>
              {isSubmitting ? '注册中...' : '注册'}
            </button>
          </form>
        )}

        <div className="auth-panel__footer">
          <p>{message || '请输入账号信息后再提交。'}</p>
        </div>
      </div>
    </section>
  );
}