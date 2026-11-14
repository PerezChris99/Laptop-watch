import js from '@eslint/js';
import react from 'eslint-plugin-react';
import reactNative from 'eslint-plugin-react-native';
import security from 'eslint-plugin-security';

export default [
  { ignores: ['node_modules/**', 'android/**', 'ios/**', 'android-native/**'] },
  {
    files: ['**/*.js', '**/*.jsx'],
    languageOptions: {
      ecmaVersion: 2024, sourceType: 'module',
      parserOptions: { ecmaFeatures: { jsx: true } },
      globals: { globalThis: 'readonly', URL: 'readonly', URLSearchParams: 'readonly', Buffer: 'readonly', Text: 'readonly',
        Date: 'readonly', setInterval: 'readonly', clearInterval: 'readonly',\n        setTimeout: 'readonly', clearTimeout: 'readonly' },
    },
    plugins: { react, 'react-native': reactNative, security },
    rules: {
      ...js.configs.recommended.rules,
      ...react.configs.recommended.rules,
      'react-native/no-unused-styles': 'error',
      'react/react-in-jsx-scope': 'off',
      'react/prop-types': 'off',
      'react-native/no-inline-styles': 'off',
      'security/detect-object-injection': 'off',
    },
    settings: { react: { version: 'detect' } },
  },
];
