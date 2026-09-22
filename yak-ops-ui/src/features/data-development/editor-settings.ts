import {
  getDevelopmentEditorSettings,
  type YakEditorSettings,
} from '@/services/data-development';
import type * as monaco from 'monaco-editor/esm/vs/editor/editor.api';

export type {
  YakEditorLineHighlight,
  YakEditorSettings,
  YakRenderWhitespace,
  YakSqlCompletionFqn,
} from '@/services/data-development';

export const DEFAULT_YAK_EDITOR_SETTINGS: YakEditorSettings = {
  theme: 'Yak-Light',
  fontSize: 14,
  fontFamily: 'Monaco',
  customFontFamily: '',
  lineHeight: 1.6,
  showLineNumber: true,
  showMinimap: false,
  wordWrap: true,
  folding: true,
  renderLineHighlight: 'line',
  keywordCase: 'lower',
  sqlCompletionFQN: 'none',
  renderWhitespace: 'none',
};

export const YAK_EDITOR_FONTS = [
  'Monaco',
  'JetBrains Mono',
  'Consolas',
  'Menlo',
  'Fira Code',
  'Source Code Pro',
  'Courier New',
];

export interface YakEditorTheme {
  name: string;
  dark: boolean;
  data: monaco.editor.IStandaloneThemeData;
}

const light = (
  background: string,
  foreground: string,
  keyword: string,
): monaco.editor.IStandaloneThemeData => ({
  base: 'vs',
  inherit: true,
  rules: [
    {
      token: 'keyword.sql',
      foreground: keyword.replace('#', ''),
      fontStyle: 'bold',
    },
    { token: 'string.sql', foreground: '067D68' },
    { token: 'number.sql', foreground: 'B54708' },
    {
      token: 'comment.sql',
      foreground: '98A2B3',
      fontStyle: 'italic',
    },
  ],
  colors: {
    'editor.background': background,
    'editor.foreground': foreground,
    'editorLineNumber.foreground': '#A4ABB8',
    'editorLineNumber.activeForeground': foreground,
    'editor.lineHighlightBackground': '#00000008',
    'editor.selectionBackground': '#BFD7FF88',
  },
});

const dark = (
  background: string,
  foreground: string,
  keyword: string,
): monaco.editor.IStandaloneThemeData => ({
  base: 'vs-dark',
  inherit: true,
  rules: [
    {
      token: 'keyword.sql',
      foreground: keyword.replace('#', ''),
      fontStyle: 'bold',
    },
    { token: 'string.sql', foreground: '9ECE6A' },
    { token: 'number.sql', foreground: 'FF9E64' },
    {
      token: 'comment.sql',
      foreground: '6B7280',
      fontStyle: 'italic',
    },
  ],
  colors: {
    'editor.background': background,
    'editor.foreground': foreground,
    'editorLineNumber.foreground': '#667085',
    'editorLineNumber.activeForeground': foreground,
    'editor.lineHighlightBackground': '#FFFFFF08',
    'editor.selectionBackground': '#365A7A99',
  },
});

export const YAK_EDITOR_THEMES: YakEditorTheme[] = [
  {
    name: 'Yak-Light',
    dark: false,
    data: light('#FFFFFF', '#344054', '#245BDB'),
  },
  {
    name: 'Yak-Dark',
    dark: true,
    data: dark('#17181C', '#D0D5DD', '#7AA2F7'),
  },
  {
    name: 'Darcula',
    dark: true,
    data: dark('#2B2B2B', '#A9B7C6', '#CC7832'),
  },
  {
    name: 'Erlang-Dark',
    dark: true,
    data: dark('#1F1F1F', '#D6D6D6', '#F08D49'),
  },
  {
    name: 'GitHub',
    dark: false,
    data: light('#FFFFFF', '#24292F', '#CF222E'),
  },
  {
    name: 'Material',
    dark: true,
    data: dark('#263238', '#EEFFFF', '#C792EA'),
  },
  {
    name: 'Night-Owl',
    dark: true,
    data: dark('#011627', '#D6DEEB', '#C792EA'),
  },
  {
    name: 'One-Dark-Pro',
    dark: true,
    data: dark('#282C34', '#ABB2BF', '#C678DD'),
  },
  {
    name: 'Solarized-Dark',
    dark: true,
    data: dark('#002B36', '#839496', '#B58900'),
  },
  {
    name: 'Solarized-Light',
    dark: false,
    data: light('#FDF6E3', '#657B83', '#B58900'),
  },
  {
    name: 'Tomorrow',
    dark: false,
    data: light('#FFFFFF', '#4D4D4C', '#8959A8'),
  },
  {
    name: 'Twilight',
    dark: true,
    data: dark('#141414', '#F8F8F8', '#CDA869'),
  },
];

const listeners = new Set<(settings: YakEditorSettings) => void>();
let currentSettings = DEFAULT_YAK_EDITOR_SETTINGS;
let loadPromise: Promise<YakEditorSettings> | undefined;

export const setYakEditorSettings = (settings: YakEditorSettings) => {
  currentSettings = { ...DEFAULT_YAK_EDITOR_SETTINGS, ...settings };
  listeners.forEach((listener) => listener(currentSettings));
};

export const subscribeYakEditorSettings = (
  listener: (settings: YakEditorSettings) => void,
) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

export const ensureYakEditorSettingsLoaded = () => {
  if (!loadPromise) {
    loadPromise = getDevelopmentEditorSettings()
      .then((settings) => {
        const nextSettings = {
          ...DEFAULT_YAK_EDITOR_SETTINGS,
          ...(settings || {}),
        };
        setYakEditorSettings(nextSettings);
        return nextSettings;
      })
      .catch(() => {
        loadPromise = undefined;
        return currentSettings;
      });
  }
  return loadPromise;
};

export const getYakEditorSettings = () => {
  if (!loadPromise) void ensureYakEditorSettingsLoaded();
  return currentSettings;
};

export const editorOptionsFromSettings = (
  settings: YakEditorSettings,
): monaco.editor.IStandaloneEditorConstructionOptions => ({
  theme: settings.theme,
  fontSize: settings.fontSize,
  lineHeight: Math.max(
    16,
    Math.round(settings.fontSize * settings.lineHeight),
  ),
  fontFamily: settings.customFontFamily.trim() || settings.fontFamily,
  lineNumbers: settings.showLineNumber ? 'on' : 'off',
  minimap: { enabled: settings.showMinimap },
  wordWrap: settings.wordWrap ? 'on' : 'off',
  folding: settings.folding,
  renderLineHighlight: settings.renderLineHighlight,
  renderWhitespace: settings.renderWhitespace,
});
