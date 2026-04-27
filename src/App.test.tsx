// @ts-nocheck
import { render, screen } from '@testing-library/react';
import App from './App';

test('renders login page route', () => {
  window.history.pushState({}, '', '/login');
  render(<App />);
  const titleElement = screen.getByRole('heading', { name: /登录与注册/i });
  expect(titleElement).toBeInTheDocument();
});

test('renders workspace route by default', () => {
  window.history.pushState({}, '', '/');
  render(<App />);
  const titleElement = screen.getByRole('heading', { name: /工作台布局/i });
  expect(titleElement).toBeInTheDocument();
});