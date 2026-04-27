// @ts-nocheck
// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom';

jest.mock('@blocknote/react', () => ({
	useCreateBlockNote: () => ({ document: [] }),
}));

jest.mock('@blocknote/mantine', () => {
	const React = require('react');

	const theme = {
		colors: {
			editor: { text: '#000000', background: '#ffffff' },
			menu: { text: '#000000', background: '#ffffff' },
			tooltip: { text: '#000000', background: '#ffffff' },
			hovered: { text: '#000000', background: '#eeeeee' },
			selected: { text: '#ffffff', background: '#222222' },
			disabled: { text: '#999999', background: '#f0f0f0' },
			shadow: '#000000',
			border: '#dddddd',
			sideMenu: '#666666',
			highlights: {},
		},
		borderRadius: 8,
		fontFamily: 'sans-serif',
	};

	return {
		BlockNoteView: () => React.createElement('div', { 'data-testid': 'blocknote-view' }),
		lightDefaultTheme: theme,
		darkDefaultTheme: theme,
	};
});

jest.mock('@blocknote/mantine/style.css', () => ({}), { virtual: true });
jest.mock('@blocknote/core/fonts/inter.css', () => ({}), { virtual: true });

jest.mock('@tiptap/starter-kit', () => ({
	__esModule: true,
	default: {},
}));

jest.mock('@tiptap/react', () => {
	const React = require('react');

	const makeChain = () => {
		const chain = {
			focus: () => chain,
			toggleBold: () => chain,
			toggleItalic: () => chain,
			toggleHeading: () => chain,
			toggleBulletList: () => chain,
			undo: () => chain,
			redo: () => chain,
			run: () => true,
		};

		return chain;
	};

	return {
		useEditor: () => ({
			isActive: () => false,
			chain: () => makeChain(),
		}),
		EditorContent: () => React.createElement('div', { 'data-testid': 'tiptap-editor-content' }),
	};
});

jest.mock('./pages/OnlineEditorPage', () => {
	const React = require('react');

	return function MockOnlineEditorPage() {
		return React.createElement('h1', null, 'TipTap Editor');
	};
});