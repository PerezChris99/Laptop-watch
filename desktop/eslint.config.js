import js from '@eslint/js';
import react from 'eslint-plugin-react';
import security from 'eslint-plugin-security';

export default [
  { ignores: ['node_modules/**','dist/**','src-tauri/**','src-python/**'] },
  { files: ['**/*.js','**/*.jsx'],
    languageOptions: { ecmaVersion: 2024, sourceType: 'module',
      parserOptions: { ecmaFeatures: { jsx: true } },
      globals: {
        window: 'readonly', document: 'readonly', setInterval: 'readonly',
        clearInterval: 'readonly', console: 'readonly', process: 'readonly',
      },
    },
    plugins: { react, security },
    rules: {
      ...js.configs.recommended.rules,
      ...react.configs.recommended.rules,
      'react/react-in-jsx-scope': 'off',
      'react/prop-types': 'off',
      'security/detect-object-injection': 'off',
    },
    settings: { react: { version: 'detect' } },
  },
];
