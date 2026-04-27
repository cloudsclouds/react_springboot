// @ts-nocheck
import { ChangeEvent, FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { LoginPayload, userApi } from '../api';

type AuthField = {
  name: keyof LoginPayload;
  label: string;
  placeholder: string;
  type: 'email' | 'password';
};

const AUTH_FIELDS: AuthField[] = [
  { name: 'email', label: '邮箱', placeholder: 'team@paperdesk.app', type: 'email' },
  { name: 'password', label: '密码', placeholder: '请输入密码', type: 'password' },
];

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

export default function AuthPage() {
  const navigate = useNavigate();
  const [formData, setFormData] = useState<LoginPayload>({ email: '', password: '' });
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [message, setMessage] = useState<string>('');

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
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

  return (
    <section className="auth-shell">
      <div className="auth-hero">
        <span className="panel-kicker">Editorial workspace</span>
        <h1>登录与注册</h1>

        <div className="auth-highlights">
          <article className="auth-highlight-card">
            <strong>工作台统一入口</strong>
            <span>文档、AI、知识库、组织、分享都从这里进入。</span>
          </article>
          <article className="auth-highlight-card">
            <strong>桌面感布局</strong>
            <span>保留纸张、注释与工具栏的秩序感，避免模板式首页。</span>
          </article>
        </div>
      </div>

      <div className="auth-panel">
        <div className="auth-tabs" aria-label="Authentication views">
          <button type="button" className="auth-tab auth-tab--active">
            登录
          </button>
          <button type="button" className="auth-tab">
            注册
          </button>
        </div>

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
                onChange={handleChange}
              />
            </label>
          ))}

          <button type="submit" className="primary-button" disabled={isSubmitting}>
            {isSubmitting ? '校验中...' : '进入工作台'}
          </button>
        </form>

        <div className="auth-panel__footer">
          <p>{message}</p>
          <Link to="/workspace" className="ghost-link">
            直接查看工作台布局
          </Link>
        </div>
      </div>
    </section>
  );
}