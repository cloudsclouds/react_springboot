import { render, screen } from '@testing-library/react';
import App from './App';

test('renders knowledge base route by default', () => {
  window.history.pushState({}, '', '/');
  render(<App />);
  const titleElement = screen.getByRole('heading', { name: /Knowledge Base/i });
  expect(titleElement).toBeInTheDocument();
});

test('renders tiptap online editor page route', () => {
  window.history.pushState({}, '', '/online-editor');
  render(<App />);
  const titleElement = screen.getByRole('heading', { name: /TipTap Editor/i });
  expect(titleElement).toBeInTheDocument();
});
