import js from '@eslint/js'
import boundaries from 'eslint-plugin-boundaries'
import prettier from 'eslint-config-prettier'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig([
  globalIgnores(['dist', 'src/shared/api/generated']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.strictTypeChecked,
      reactHooks.configs.flat['recommended-latest'],
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
  {
    // 模块边界：feature 之间只能通过对方的 index.ts 引用；shared 不依赖 feature 和 app。
    files: ['src/**/*.{ts,tsx}'],
    plugins: { boundaries },
    settings: {
      'import/resolver': { typescript: { project: './tsconfig.app.json' } },
      'boundaries/elements': [
        { type: 'app', pattern: 'src/app' },
        { type: 'feature', pattern: 'src/features/*', capture: ['featureName'] },
        { type: 'shared', pattern: 'src/shared' },
      ],
    },
    rules: {
      'boundaries/dependencies': [
        2,
        {
          default: 'disallow',
          policies: [
            { allow: { dependency: { relationship: { to: 'internal' } } } },
            {
              from: { element: { type: 'app' } },
              allow: { to: { element: { types: { anyOf: ['app', 'shared'] } } } },
            },
            {
              from: { element: { types: { anyOf: ['app', 'feature'] } } },
              allow: { to: { element: { type: 'feature', fileInternalPath: 'index.ts' } } },
            },
            {
              from: { element: { types: { anyOf: ['feature', 'shared'] } } },
              allow: { to: { element: { type: 'shared' } } },
            },
          ],
        },
      ],
    },
  },
  prettier,
])
