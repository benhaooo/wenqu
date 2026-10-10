export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // 取值见 docs/conventions/git.md
    'scope-enum': [
      2,
      'always',
      [
        'auth',
        'workspace',
        'document',
        'ingestion',
        'retrieval',
        'chat',
        'usage',
        'infra',
        'common',
        'backend',
        'frontend',
        'docs',
        'deps',
      ],
    ],
    'subject-case': [0],
  },
}
